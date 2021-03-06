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

package connectors

import akka.stream.Materializer
import cats.effect.IO
import com.google.inject.ImplementedBy
import com.kenshoo.play.metrics.Metrics
import config.AppConfig
import config.Constants
import logging.Logging
import metrics.IOMetrics
import metrics.MetricsKeys
import models.MessageType
import models.values.BalanceId
import play.api.http.ContentTypes
import play.api.http.HeaderNames
import play.api.http.MimeTypes
import runtime.IOFutures
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.UpstreamErrorResponse

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.xml.Elem

@ImplementedBy(classOf[EisRouterConnectorImpl])
trait EisRouterConnector {
  def sendMessage(balanceId: BalanceId, requestedAt: Instant, message: Elem)(implicit
    hc: HeaderCarrier
  ): IO[Either[UpstreamErrorResponse, Unit]]
}

@Singleton
class EisRouterConnectorImpl @Inject() (
  val appConfig: AppConfig,
  http: HttpClient,
  val metrics: Metrics
)(implicit val materializer: Materializer)
  extends EisRouterConnector
  with IOFutures
  with IOMetrics
  with CircuitBreakers
  with Logging {

  import MetricsKeys.Connectors._

  override lazy val circuitBreakerConfig = appConfig.eisRouterCircuitBreakerConfig

  def isFailure[A](result: Try[Either[UpstreamErrorResponse, A]]): Boolean =
    result match {
      case Success(Left(_)) => true
      case Failure(_)       => true
      case _                => false
    }

  def sendMessage(balanceId: BalanceId, requestedAt: Instant, message: Elem)(implicit
    hc: HeaderCarrier
  ): IO[Either[UpstreamErrorResponse, Unit]] =
    withMetricsTimerResponse(SendMessage) {
      IO.runFuture { implicit ec =>
        circuitBreaker.withCircuitBreaker(
          {
            val dateTime       = OffsetDateTime.ofInstant(requestedAt, ZoneOffset.UTC)
            val urlString      = appConfig.eisRouterUrl.toString
            val wrappedMessage = <transitRequest>{message}</transitRequest>
            val headers = hc.headers(Seq(Constants.ChannelHeader)) ++ Seq(
              HeaderNames.ACCEPT       -> MimeTypes.XML,
              HeaderNames.DATE         -> DateTimeFormatter.RFC_1123_DATE_TIME.format(dateTime),
              HeaderNames.CONTENT_TYPE -> ContentTypes.XML,
              "X-Message-Sender"       -> s"MDTP-GUA-${balanceId.messageIdentifier.hexString}",
              "X-Message-Type"         -> MessageType.QueryOnGuarantees.code
            )
            http.POSTString[Either[UpstreamErrorResponse, Unit]](
              urlString,
              wrappedMessage.toString,
              headers
            )
          },
          isFailure
        )
      }
    }
}
