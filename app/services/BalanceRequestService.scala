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
import cats.syntax.all._
import config.AppConfig
import connectors.EisRouterConnector
import logging.Logging
import models.BalanceRequestResponse
import models.MessageType
import models.PendingBalanceRequest
import models.errors._
import models.request.BalanceRequest
import models.values.BalanceId
import models.values.EnrolmentId
import models.values.GuaranteeReference
import models.values.MessageIdentifier
import models.values.TaxIdentifier
import models.values.UniqueReference
import repositories.BalanceRequestRepository
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse._

import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import scala.xml.Elem

@Singleton
class BalanceRequestService @Inject() (
  repository: BalanceRequestRepository,
  formatter: XmlFormattingService,
  validator: XmlValidationService,
  parser: XmlParsingService,
  connector: EisRouterConnector,
  appConfig: AppConfig,
  clock: Clock,
  random: SecureRandom
) extends Logging {

  def submitBalanceRequest(
    enrolmentId: EnrolmentId,
    request: BalanceRequest
  )(implicit hc: HeaderCarrier): IO[Either[BalanceRequestError, BalanceId]] =
    for {
      requestedAt <- IO(clock.instant())

      id <- repository.insertBalanceRequest(enrolmentId, request, requestedAt)

      uniqueRef <- UniqueReference.next(random)

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

    } yield result

  def getBalanceRequest(
    balanceId: BalanceId
  ): IO[Option[PendingBalanceRequest]] =
    repository.getBalanceRequest(balanceId)

  def getBalanceRequest(
    enrolmentId: EnrolmentId,
    taxIdentifier: TaxIdentifier,
    guaranteeReference: GuaranteeReference
  ): IO[Option[PendingBalanceRequest]] =
    repository.getBalanceRequest(enrolmentId, taxIdentifier, guaranteeReference)

  private def validateResponseMessage(
    messageType: MessageType,
    responseMessage: String
  ): EitherT[IO, BalanceRequestError, Elem] =
    EitherT
      .fromEither[IO] {
        validator.validate(messageType, responseMessage)
      }
      .leftMap { errors =>
        BalanceRequestError.xmlValidationError(messageType, errors)
      }

  private def parseResponseMessage(
    messageType: MessageType,
    message: Elem
  ): EitherT[IO, BalanceRequestError, BalanceRequestResponse] =
    EitherT.fromEither[IO] {
      parser.parseResponseMessage(messageType, message)
    }

  private def updateBalanceRequestRecord(
    recipient: MessageIdentifier,
    completedAt: Instant,
    response: BalanceRequestResponse
  ): EitherT[IO, BalanceRequestError, PendingBalanceRequest] =
    EitherT.fromOptionF(
      repository.updateBalanceRequest(recipient, completedAt, response),
      BalanceRequestError.notFoundError(recipient)
    )

  def updateBalanceRequest(
    recipient: MessageIdentifier,
    messageType: MessageType,
    responseMessage: String
  ): IO[Either[BalanceRequestError, PendingBalanceRequest]] = {
    val updateBalance = for {
      validated <- validateResponseMessage(messageType, responseMessage)

      response <- parseResponseMessage(messageType, validated)

      completedAt <- EitherT.liftF(IO(clock.instant()))

      updated <- updateBalanceRequestRecord(recipient, completedAt, response)
    } yield updated

    updateBalance.value
  }
}
