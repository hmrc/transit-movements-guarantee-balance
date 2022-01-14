/*
 * Copyright 2022 HM Revenue & Customs
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
import controllers.ErrorLogging
import logging.Logging
import models.BalanceRequestResponse
import models.MessageType
import models.PendingBalanceRequest
import models.errors._
import models.request.BalanceRequest
import models.values.BalanceId
import models.values.MessageIdentifier
import uk.gov.hmrc.http.HeaderCarrier

import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton

@ImplementedBy(classOf[BalanceRequestCacheServiceImpl])
trait BalanceRequestCacheService {
  def submitBalanceRequest(
    balanceRequest: BalanceRequest
  )(implicit hc: HeaderCarrier): IO[Either[BalanceRequestError, BalanceRequestResponse]]

  def getBalance(
    balanceId: BalanceId
  )(implicit hc: HeaderCarrier): IO[Option[PendingBalanceRequest]]

  def putBalance(
    balanceId: BalanceId,
    response: BalanceRequestResponse
  ): IO[Unit]

  def updateBalance(
    recipient: MessageIdentifier,
    messageType: MessageType,
    responseMessage: String
  ): IO[Either[BalanceRequestError, Unit]]
}

@Singleton
class BalanceRequestCacheServiceImpl @Inject() (
  appConfig: AppConfig,
  metrics: Metrics,
  service: BalanceRequestService
) extends BalanceRequestCacheService
  with Logging
  with ErrorLogging {

  type CacheKey         = BalanceId
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

  def submitBalanceRequest(
    balanceRequest: BalanceRequest
  )(implicit hc: HeaderCarrier): IO[Either[BalanceRequestError, BalanceRequestResponse]] = {

    val balanceResponse = for {
      balanceId <- submitRequest(balanceRequest)
      deferred = cache.get(balanceId)
      response <- awaitResponse(balanceId, deferred)
      _ = cache.invalidate(balanceId)
    } yield response

    balanceResponse.value
  }

  def getBalance(
    balanceId: BalanceId
  )(implicit hc: HeaderCarrier): IO[Option[PendingBalanceRequest]] =
    service.getBalanceRequest(balanceId)

  def putBalance(
    balanceId: BalanceId,
    response: BalanceRequestResponse
  ): IO[Unit] = {
    for {
      deferred <- IO(cache.get(balanceId))
      _        <- deferred.complete(response)
    } yield ()
  }

  def updateBalance(
    recipient: MessageIdentifier,
    messageType: MessageType,
    responseMessage: String
  ): IO[Either[BalanceRequestError, Unit]] =
    EitherT {
      service.updateBalanceRequest(recipient, messageType, responseMessage)
    }.void.value
}
