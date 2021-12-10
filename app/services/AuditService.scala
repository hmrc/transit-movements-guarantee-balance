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
import com.google.inject.ImplementedBy
import models.BalanceRequestFunctionalError
import models.BalanceRequestResponse
import models.BalanceRequestSuccess
import models.BalanceRequestXmlError
import models.audit.AuditEventType
import models.audit._
import models.request.AuthenticatedRequest
import models.request.BalanceRequest
import models.values.BalanceId
import models.values.MessageIdentifier
import runtime.IOFutures
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import javax.inject.Inject
import javax.inject.Singleton

@ImplementedBy(classOf[AuditServiceImpl])
trait AuditService {
  def auditBalanceRequest(
    balanceId: BalanceId,
    request: AuthenticatedRequest[BalanceRequest]
  )(implicit
    hc: HeaderCarrier
  ): IO[Unit]

  def auditBalanceRequestNotFound(
    messageId: MessageIdentifier
  )(implicit
    hc: HeaderCarrier
  ): IO[Unit]

  def auditBalanceResponse(
    messageId: MessageIdentifier,
    response: BalanceRequestResponse
  )(implicit
    hc: HeaderCarrier
  ): IO[Unit]

  def auditBalanceResponseInvalid(
    messageId: MessageIdentifier,
    responseMessage: String,
    errorMessage: String
  )(implicit
    hc: HeaderCarrier
  ): IO[Unit]
}

@Singleton
class AuditServiceImpl @Inject() (translator: ErrorTranslationService, connector: AuditConnector)
  extends AuditService
  with IOFutures {

  def auditBalanceRequest(
    balanceId: BalanceId,
    request: AuthenticatedRequest[BalanceRequest]
  )(implicit
    hc: HeaderCarrier
  ): IO[Unit] = {
    val requestEvent = RequestEvent.fromRequest(request, balanceId)

    IO.executionContext.flatMap { implicit ec =>
      IO {
        connector.sendExplicitAudit[RequestEvent](
          AuditEventType.RequestSent.name,
          requestEvent
        )
      }
    }
  }

  override def auditBalanceRequestNotFound(messageId: MessageIdentifier)(implicit
    hc: HeaderCarrier
  ): IO[Unit] = {
    IO.executionContext.flatMap { implicit ec =>
      IO {
        connector.sendExplicitAudit[RequestNotFoundEvent](
          AuditEventType.RequestNotFound.name,
          RequestNotFoundEvent(messageId)
        )
      }
    }
  }

  def auditBalanceResponse(
    messageId: MessageIdentifier,
    response: BalanceRequestResponse
  )(implicit
    hc: HeaderCarrier
  ): IO[Unit] = {
    val responseEvent = response match {
      case BalanceRequestSuccess(balance, currency) =>
        SuccessResponseEvent(messageId, balance, currency)
      case BalanceRequestFunctionalError(errors) =>
        ErrorResponseEvent(messageId, errors.map(translator.translateFunctionalError))
      case BalanceRequestXmlError(errors) =>
        ErrorResponseEvent(messageId, errors.map(translator.translateXmlError))
    }

    val auditType = response match {
      case BalanceRequestSuccess(_, _) =>
        AuditEventType.SuccessResponse
      case _ =>
        AuditEventType.ErrorResponse
    }

    IO.executionContext.flatMap { implicit ec =>
      IO {
        connector.sendExplicitAudit[ResponseEvent](
          auditType.name,
          responseEvent
        )
      }
    }
  }

  override def auditBalanceResponseInvalid(
    messageId: MessageIdentifier,
    responseMessage: String,
    errorMessage: String
  )(implicit hc: HeaderCarrier): IO[Unit] = {
    IO.executionContext.flatMap { implicit ec =>
      IO {
        connector.sendExplicitAudit[InvalidResponseEvent](
          AuditEventType.InvalidResponse.name,
          InvalidResponseEvent(messageId, responseMessage, errorMessage)
        )
      }
    }
  }
}
