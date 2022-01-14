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

package models.formats

import cats.data.NonEmptyList
import models.BalanceRequestFunctionalError
import models.BalanceRequestResponse
import models.BalanceRequestResponseStatus
import models.BalanceRequestSuccess
import models.BalanceRequestXmlError
import models.PendingBalanceRequest
import models.SchemaValidationError
import models.errors._
import models.values._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.play.json.Union

trait HttpFormats extends CommonFormats {
  implicit val balanceIdFormat: Format[BalanceId] =
    Json.valueFormat[BalanceId]

  def withStatusField(jsObject: JsObject, status: String): JsObject =
    jsObject ++ Json.obj(ErrorCode.FieldName -> status)

  implicit lazy val upstreamServiceErrorWrites: OWrites[UpstreamServiceError] =
    (__ \ "message").write[String].contramap(_.message)

  implicit lazy val internalServiceErrorWrites: OWrites[InternalServiceError] =
    (__ \ "message").write[String].contramap(_.message)

  implicit lazy val upstreamTimeoutErrorWrites: OWrites[UpstreamTimeoutError] =
    (__ \ "message").write[String].contramap(_.message)

  implicit lazy val badRequestErrorWrites: OWrites[BadRequestError] =
    (__ \ "message").write[String].contramap(_.message)

  implicit lazy val notFoundErrorWrites: OWrites[NotFoundError] =
    (__ \ "message").write[String].contramap(_.message)

  implicit lazy val xmlValidationErrorWrites: OWrites[XmlValidationError] = (
    (__ \ "message").write[String] and
      (__ \ "errors").write[NonEmptyList[SchemaValidationError]]
  )(error => (error.message, error.errors))

  implicit lazy val balanceRequestErrorWrites: OWrites[BalanceRequestError] =
    OWrites {
      case err @ BadRequestError(_) =>
        withStatusField(badRequestErrorWrites.writes(err), ErrorCode.BadRequest)

      case err @ UpstreamServiceError(_, _) =>
        withStatusField(upstreamServiceErrorWrites.writes(err), ErrorCode.InternalServerError)

      case err @ InternalServiceError(_, _) =>
        withStatusField(internalServiceErrorWrites.writes(err), ErrorCode.InternalServerError)

      case err @ UpstreamTimeoutError(_, _) =>
        withStatusField(upstreamTimeoutErrorWrites.writes(err), ErrorCode.GatewayTimeout)

      case err @ NotFoundError(_) =>
        withStatusField(notFoundErrorWrites.writes(err), ErrorCode.NotFound)

      case err @ XmlValidationError(_, _) =>
        withStatusField(xmlValidationErrorWrites.writes(err), ErrorCode.SchemaValidation)
    }

  implicit lazy val schemaValidationError: OFormat[SchemaValidationError] =
    Json.format[SchemaValidationError]

  implicit lazy val functionalErrorFormat: OFormat[FunctionalError] =
    Json.format[FunctionalError]

  implicit lazy val xmlErrorFormat: OFormat[XmlError] =
    Json.format[XmlError]

  implicit lazy val balanceRequestSuccessFormat: OFormat[BalanceRequestSuccess] =
    Json.format[BalanceRequestSuccess]

  implicit lazy val balanceRequestFunctionalErrorFormat: OFormat[BalanceRequestFunctionalError] =
    Json.format[BalanceRequestFunctionalError]

  implicit lazy val balanceRequestXmlErrorFormat: OFormat[BalanceRequestXmlError] =
    Json.format[BalanceRequestXmlError]

  implicit lazy val balanceRequestResponseFormat: OFormat[BalanceRequestResponse] =
    Union
      .from[BalanceRequestResponse](BalanceRequestResponseStatus.FieldName)
      .and[BalanceRequestSuccess](BalanceRequestResponseStatus.Success)
      .and[BalanceRequestFunctionalError](BalanceRequestResponseStatus.FunctionalError)
      .and[BalanceRequestXmlError](BalanceRequestResponseStatus.XmlError)
      .format

  implicit lazy val pendingBalanceRequestFormat: OFormat[PendingBalanceRequest] =
    Json.format[PendingBalanceRequest]
}
