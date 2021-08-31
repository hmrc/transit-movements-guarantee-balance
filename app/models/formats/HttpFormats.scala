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

package models.formats

import models.BalanceRequestFunctionalError
import models.BalanceRequestResponse
import models.BalanceRequestResponseStatus
import models.BalanceRequestSuccess
import models.BalanceRequestXmlError
import models.errors._
import models.values._
import play.api.libs.json.Format
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import uk.gov.hmrc.play.json.Union

trait HttpFormats extends CommonFormats {
  implicit val balanceIdFormat: Format[BalanceId] =
    Json.valueFormat[BalanceId]

  implicit lazy val upstreamServiceErrorFormat: OFormat[UpstreamServiceError] =
    Json.format[UpstreamServiceError]

  implicit lazy val internalServiceErrorFormat: OFormat[InternalServiceError] =
    Json.format[InternalServiceError]

  implicit lazy val upstreamTimeoutErrorFormat: OFormat[UpstreamTimeoutError] =
    Json.format[UpstreamTimeoutError]

  implicit lazy val balanceRequestErrorFormat: OFormat[BalanceRequestError] =
    Union
      .from[BalanceRequestError](ErrorCode.FieldName)
      .and[UpstreamServiceError](ErrorCode.InternalServerError)
      .and[InternalServiceError](ErrorCode.InternalServerError)
      .and[UpstreamTimeoutError](ErrorCode.GatewayTimeout)
      .format

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
}
