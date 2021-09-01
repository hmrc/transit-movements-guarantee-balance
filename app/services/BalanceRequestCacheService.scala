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
import models.BalanceRequestResponse
import models.errors._
import models.request.BalanceRequest
import models.values.BalanceId
import models.values.EnrolmentId
import models.values.GuaranteeReference
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
    guaranteeReference: GuaranteeReference,
    response: BalanceRequestResponse
  ): IO[Unit]
}

@Singleton
class BalanceRequestCacheServiceImpl @Inject() (
  appConfig: AppConfig,
  metrics: Metrics,
  service: BalanceRequestService
) extends BalanceRequestCacheService {

  type CacheKey         = (EnrolmentId, GuaranteeReference)
  type DeferredResponse = Deferred[IO, BalanceRequestResponse]

  private val cache: LoadingCache[CacheKey, DeferredResponse] = Scaffeine()
    .expireAfterWrite(appConfig.balanceRequestCacheTtl)
    .recordStats(() => new MetricsStatsCounter(metrics.defaultRegistry, "balance-request-cache"))
    .build[CacheKey, DeferredResponse] { (_: CacheKey) =>
      Deferred.unsafe[IO, BalanceRequestResponse]
    }

  private def submitRequest(balanceRequest: BalanceRequest)(implicit
    hc: HeaderCarrier
  ): EitherT[IO, BalanceRequestError, BalanceId] =
    EitherT(service.submitBalanceRequest(balanceRequest))

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
    balanceRequest: BalanceRequest,
    deferredResponse: DeferredResponse
  )(implicit hc: HeaderCarrier): IO[Either[BalanceRequestError, BalanceRequestResponse]] = {

    val balanceResponse = for {
      balanceId <- submitRequest(balanceRequest)
      response  <- awaitResponse(balanceId, deferredResponse)
    } yield response

    balanceResponse.value
  }

  def getBalance(
    enrolmentId: EnrolmentId,
    balanceRequest: BalanceRequest
  )(implicit hc: HeaderCarrier): IO[Either[BalanceRequestError, BalanceRequestResponse]] = {

    val cacheKey = (enrolmentId, balanceRequest.guaranteeReference)

    IO(cache.getIfPresent(cacheKey)).flatMap {
      case Some(deferred) =>
        deferred.get.map(Right.apply)
      case None =>
        for {
          deferred <- IO(cache.get(cacheKey))
          response <- fetchNewBalance(balanceRequest, deferred)
        } yield response
    }
  }

  def putBalance(
    enrolmentId: EnrolmentId,
    guaranteeReference: GuaranteeReference,
    response: BalanceRequestResponse
  ): IO[Unit] = {
    val cacheKey = (enrolmentId, guaranteeReference)

    for {
      deferred <- IO(cache.get(cacheKey))
      _        <- deferred.complete(response)
    } yield ()
  }
}
