/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package services

import cats.data.EitherT
import cats.effect.IO
import cats.effect.kernel.Deferred
import cats.syntax.all._
import com.codahale.metrics.caffeine.MetricsStatsCounter
import com.github.blemale.scaffeine.LoadingCache
import com.github.blemale.scaffeine.Scaffeine
import com.google.inject.ImplementedBy
import com.kenshoo.play.metrics.Metrics
import config.AppConfig
import logging.Logging
import models.BalanceRequestResponse
import models.PendingBalanceRequest
import models.errors._
import models.request.BalanceRequest
import models.values.BalanceId
import models.values.EnrolmentId
import models.values.GuaranteeReference
import models.values.TaxIdentifier
import uk.gov.hmrc.http.HeaderCarrier

import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton

@ImplementedBy(classOf[BalanceRequestCacheServiceImpl])
trait BalanceRequestCacheService {
  def getBalance(
    enrolmentId: EnrolmentId,
    balanceRequest: BalanceRequest
  )(implicit hc: HeaderCarrier): IO[Either[BalanceRequestError, BalanceRequestResponse]]

  def putBalance(
    enrolmentId: EnrolmentId,
    taxIdentifier: TaxIdentifier,
    guaranteeReference: GuaranteeReference,
    response: BalanceRequestResponse
  ): IO[Unit]
}

@Singleton
class BalanceRequestCacheServiceImpl @Inject() (
  appConfig: AppConfig,
  metrics: Metrics,
  service: BalanceRequestService
) extends BalanceRequestCacheService
    with Logging {

  type CacheKey         = (EnrolmentId, TaxIdentifier, GuaranteeReference)
  type DeferredResponse = Deferred[IO, BalanceRequestResponse]

  private val cache: LoadingCache[CacheKey, DeferredResponse] = Scaffeine()
    .expireAfterWrite(appConfig.balanceRequestCacheTtl)
    .recordStats(() => new MetricsStatsCounter(metrics.defaultRegistry, "balance-request-cache"))
    .build[CacheKey, DeferredResponse] { (_: CacheKey) =>
      Deferred.unsafe[IO, BalanceRequestResponse]
    }

  private def submitRequest(enrolmentId: EnrolmentId, balanceRequest: BalanceRequest)(implicit
    hc: HeaderCarrier
  ): EitherT[IO, BalanceRequestError, BalanceId] =
    EitherT(service.submitBalanceRequest(enrolmentId, balanceRequest))

  private def getRequest(
    enrolmentId: EnrolmentId,
    balanceRequest: BalanceRequest
  ): EitherT[IO, BalanceRequestError, Option[PendingBalanceRequest]] =
    EitherT.right[BalanceRequestError](
      service.getBalanceRequest(
        enrolmentId,
        balanceRequest.taxIdentifier,
        balanceRequest.guaranteeReference
      )
    )

  private def awaitResponse(
    balanceId: BalanceId,
    deferredResponse: DeferredResponse
  ): EitherT[IO, BalanceRequestError, BalanceRequestResponse] = {

    val awaitWithTimeout = deferredResponse.get
      .timeout(appConfig.balanceRequestTimeout)
      .attemptNarrow[TimeoutException]

    EitherT(awaitWithTimeout).leftMap { _ =>
      BalanceRequestError.upstreamTimeoutError(balanceId)
    }
  }

  private def fetchNewBalance(
    enrolmentId: EnrolmentId,
    balanceRequest: BalanceRequest,
    deferredResponse: DeferredResponse
  )(implicit hc: HeaderCarrier): IO[Either[BalanceRequestError, BalanceRequestResponse]] = {

    val balanceResponse = for {
      balanceId <- submitRequest(enrolmentId, balanceRequest)
      response  <- awaitResponse(balanceId, deferredResponse)
    } yield response

    balanceResponse.value
  }

  def awaitExistingBalanceRequest(
    enrolmentId: EnrolmentId,
    balanceRequest: BalanceRequest,
    deferred: DeferredResponse
  ): IO[Either[BalanceRequestError, BalanceRequestResponse]] = {
    val getBalanceResponse = for {
      maybeRequest <- getRequest(enrolmentId, balanceRequest)

      balanceId <- maybeRequest
        .map { request =>
          EitherT.pure[IO, BalanceRequestError](request.balanceId)
        }
        .getOrElse {
          EitherT
            .right[BalanceRequestError](
              logger.error("Unable to fetch database record for a cached balance request")
            )
            .flatMap { _ =>
              EitherT.leftT[IO, BalanceId](BalanceRequestError.internalServiceError())
            }
        }

      response <- awaitResponse(balanceId, deferred)

    } yield response

    getBalanceResponse.value
  }

  def getBalance(
    enrolmentId: EnrolmentId,
    balanceRequest: BalanceRequest
  )(implicit hc: HeaderCarrier): IO[Either[BalanceRequestError, BalanceRequestResponse]] = {

    val cacheKey = (enrolmentId, balanceRequest.taxIdentifier, balanceRequest.guaranteeReference)

    IO(cache.getIfPresent(cacheKey)).flatMap {
      case Some(deferred) =>
        awaitExistingBalanceRequest(enrolmentId, balanceRequest, deferred)
      case None =>
        for {
          deferred <- IO(cache.get(cacheKey))
          response <- fetchNewBalance(enrolmentId, balanceRequest, deferred)
        } yield response
    }
  }

  def putBalance(
    enrolmentId: EnrolmentId,
    taxIdentifier: TaxIdentifier,
    guaranteeReference: GuaranteeReference,
    response: BalanceRequestResponse
  ): IO[Unit] = {
    val cacheKey = (enrolmentId, taxIdentifier, guaranteeReference)

    for {
      deferred <- IO(cache.get(cacheKey))
      _        <- deferred.complete(response)
    } yield ()
  }
}
