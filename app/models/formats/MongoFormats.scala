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

package models
package formats

import models.errors._
import models.values._
import play.api.libs.functional.syntax._
import play.api.libs.json.Format
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.libs.json._
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.play.json.Union

import java.time.Instant

object MongoFormats extends MongoFormats

trait MongoFormats extends CommonFormats with MongoJavatimeFormats {
  implicit val functionalErrorFormat: Format[FunctionalError] =
    Json.format[FunctionalError]
  implicit val xmlErrorFormat: Format[XmlError] =
    Json.format[XmlError]

  implicit val balanceRequestSuccessFormat: OFormat[BalanceRequestSuccess] =
    Json.format[BalanceRequestSuccess]
  implicit val balanceRequestFunctionalErrorFormat: OFormat[BalanceRequestFunctionalError] =
    Json.format[BalanceRequestFunctionalError]
  implicit val balanceRequestXmlErrorFormat: OFormat[BalanceRequestXmlError] =
    Json.format[BalanceRequestXmlError]

  implicit val balanceRequestResponseFormat: Format[BalanceRequestResponse] = Union
    .from[BalanceRequestResponse](BalanceRequestResponseStatus.FieldName)
    .and[BalanceRequestSuccess](BalanceRequestResponseStatus.Success)
    .and[BalanceRequestXmlError](BalanceRequestResponseStatus.XmlError)
    .and[BalanceRequestFunctionalError](BalanceRequestResponseStatus.FunctionalError)
    .format

  implicit val pendingBalanceRequestFormat: OFormat[PendingBalanceRequest] = (
    (__ \ "_id").format[RequestId] and
      (__ \ "requestedAt").format[Instant] and
      (__ \ "userInternalId").format[InternalId] and
      (__ \ "userEnrolmentId").format[EnrolmentId] and
      (__ \ "taxIdentifier").format[TaxIdentifier] and
      (__ \ "guaranteeReference").format[GuaranteeReference] and
      (__ \ "completedAt").formatNullable[Instant] and
      (__ \ "response").formatNullable[BalanceRequestResponse]
  )(PendingBalanceRequest.apply _, unlift(PendingBalanceRequest.unapply _))
}
