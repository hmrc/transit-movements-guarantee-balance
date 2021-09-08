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

import cats.effect.IO
import cats.syntax.all._
import config.AppConfig
import connectors.EisRouterConnector
import logging.Logging
import models.MessageType
import models.PendingBalanceRequest
import models.errors._
import models.request.BalanceRequest
import models.values.BalanceId
import models.values.EnrolmentId
import models.values.GuaranteeReference
import models.values.TaxIdentifier
import models.values.UniqueReference
import repositories.BalanceRequestRepository
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse._

import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BalanceRequestService @Inject() (
  repository: BalanceRequestRepository,
  formatter: XmlFormattingService,
  validator: XmlValidationService,
  connector: EisRouterConnector,
  appConfig: AppConfig,
  clock: Clock
) extends Logging {
  def submitBalanceRequest(
    enrolmentId: EnrolmentId,
    request: BalanceRequest
  )(implicit hc: HeaderCarrier): IO[Either[BalanceRequestError, BalanceId]] =
    for {
      requestedAt <- IO(clock.instant())

      id <- repository.insertBalanceRequest(enrolmentId, request, requestedAt)

      uniqueRef <- UniqueReference.next

      message = formatter.formatMessage(id, requestedAt, uniqueRef, request)

      validated =
        if (appConfig.selfCheck)
          validator.validate(MessageType.QueryOnGuarantees, message.toString)
        else
          Either.right(message)

      _ <- validated.fold(
        errors => {
          IO.raiseError(SelfCheckError(errors))
        },
        _ => {
          IO.unit
        }
      )

      result <- connector.sendMessage(id, requestedAt, message).map {
        _.leftMap {
          case cause @ Upstream4xxResponse(_) => InternalServiceError.causedBy(cause)
          case cause @ Upstream5xxResponse(_) => UpstreamServiceError.causedBy(cause)
        }.map { _ =>
          id
        }
      }

      _ <- result.fold(
        {
          case UpstreamServiceError(_, cause) =>
            logger.error(cause)("Error when calling upstream service")
          case InternalServiceError(_, Some(cause)) =>
            logger.error(cause)("Error when requesting balance")
          case BadRequestError(message) =>
            logger.error(s"Error in request data: ${message}")
          case InternalServiceError(message, None) =>
            logger.error(s"Error when requesting balance: ${message}")
          case UpstreamTimeoutError(balanceId, message) =>
            logger.error(
              s"Error awaiting upstream response for balance ID ${balanceId.value}: ${message}"
            )
        },
        _ => IO.unit
      )

    } yield result

  def getBalanceRequest(
    enrolmentId: EnrolmentId,
    taxIdentifier: TaxIdentifier,
    guaranteeReference: GuaranteeReference
  ): IO[Option[PendingBalanceRequest]] =
    repository.getBalanceRequest(enrolmentId, taxIdentifier, guaranteeReference)
}
