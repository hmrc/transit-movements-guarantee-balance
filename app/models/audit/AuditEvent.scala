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

package models.audit

import cats.data.NonEmptyList
import models.formats.HttpFormats._
import models.request.AuthenticatedRequest
import models.request.BalanceRequest
import models.values.AccessCode
import models.values.BalanceId
import models.values.CurrencyCode
import models.values.GuaranteeReference
import models.values.InternalId
import models.values.MessageIdentifier
import models.values.TaxIdentifier
import play.api.libs.json.Json
import play.api.libs.json.OWrites

sealed abstract class AuditEvent extends Product with Serializable

case class RequestEvent(
  requestMessageId: MessageIdentifier,
  userInternalId: InternalId,
  eoriNumber: TaxIdentifier,
  guaranteeReference: GuaranteeReference,
  accessCode: AccessCode
) extends AuditEvent

object RequestEvent {
  implicit val requestEventWrites: OWrites[RequestEvent] =
    Json.writes[RequestEvent]

  def fromRequest(
    request: AuthenticatedRequest[BalanceRequest],
    balanceId: BalanceId
  ): RequestEvent =
    RequestEvent(
      balanceId.messageIdentifier,
      request.internalId,
      request.body.taxIdentifier,
      request.body.guaranteeReference,
      request.body.accessCode
    )
}

case class RequestNotFoundEvent(requestMessageId: MessageIdentifier) extends AuditEvent

object RequestNotFoundEvent {
  implicit val RequestNotFoundEvent: OWrites[RequestNotFoundEvent] =
    Json.writes[RequestNotFoundEvent]
}

sealed abstract class ResponseEvent extends AuditEvent

case class SuccessResponseEvent(
  requestMessageId: MessageIdentifier,
  balance: BigDecimal,
  currency: CurrencyCode
) extends ResponseEvent

case class ErrorResponseEvent(
  requestMessageId: MessageIdentifier,
  errors: NonEmptyList[String]
) extends ResponseEvent

case class InvalidResponseEvent(
  requestMessageId: MessageIdentifier,
  responseMessage: String,
  errorMessage: String
) extends ResponseEvent

object ResponseEvent {
  implicit val successResponseEventWrites: OWrites[SuccessResponseEvent] =
    Json.writes[SuccessResponseEvent]

  implicit val errorResponseEventWrites: OWrites[ErrorResponseEvent] =
    Json.writes[ErrorResponseEvent]

  implicit val invalidResponseEventWrites: OWrites[InvalidResponseEvent] =
    Json.writes[InvalidResponseEvent]

  implicit val responseEventWrites: OWrites[ResponseEvent] =
    OWrites {
      case event: SuccessResponseEvent =>
        successResponseEventWrites.writes(event)
      case event: ErrorResponseEvent =>
        errorResponseEventWrites.writes(event)
      case event: InvalidResponseEvent =>
        invalidResponseEventWrites.writes(event)
    }
}
