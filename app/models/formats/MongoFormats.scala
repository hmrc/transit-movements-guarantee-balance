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

trait MongoFormats
    extends CommonFormats
    with MongoBinaryFormats
    with MongoUuidFormats
    with MongoJavatimeFormats.Implicits {

  implicit val functionalErrorFormat: Format[FunctionalError] =
    Json.format[FunctionalError]
  implicit val xmlErrorFormat: Format[XmlError] =
    Json.format[XmlError]

  implicit val balanceIdFormat: Format[BalanceId] =
    Json.valueFormat[BalanceId]

  implicit val messageIdentifierFormat: Format[MessageIdentifier] =
    Json.valueFormat[MessageIdentifier]

  def withStatusField[A <: BalanceRequestResponse](format: OWrites[A], status: String): OWrites[A] =
    format.transform { (obj: JsObject) =>
      obj ++ Json.obj(BalanceRequestResponseStatus.FieldName -> status)
    }

  implicit val balanceRequestSuccessFormat: OFormat[BalanceRequestSuccess] = OFormat(
    Json.reads[BalanceRequestSuccess],
    withStatusField(Json.writes[BalanceRequestSuccess], BalanceRequestResponseStatus.Success)
  )
  implicit val balanceRequestXmlErrorFormat: OFormat[BalanceRequestXmlError] = OFormat(
    Json.reads[BalanceRequestXmlError],
    withStatusField(Json.writes[BalanceRequestXmlError], BalanceRequestResponseStatus.XmlError)
  )
  implicit val balanceRequestFunctionalErrorFormat: OFormat[BalanceRequestFunctionalError] =
    OFormat(
      Json.reads[BalanceRequestFunctionalError],
      withStatusField(
        Json.writes[BalanceRequestFunctionalError],
        BalanceRequestResponseStatus.FunctionalError
      )
    )

  implicit val balanceRequestResponseFormat: Format[BalanceRequestResponse] = Union
    .from[BalanceRequestResponse](BalanceRequestResponseStatus.FieldName)
    .and[BalanceRequestSuccess](BalanceRequestResponseStatus.Success)
    .and[BalanceRequestXmlError](BalanceRequestResponseStatus.XmlError)
    .and[BalanceRequestFunctionalError](BalanceRequestResponseStatus.FunctionalError)
    .format

  private val balanceRequestFormat: OFormat[PendingBalanceRequest] = (
    (__ \ "_id").format[BalanceId] and
      (__ \ "enrolmentId").format[EnrolmentId] and
      (__ \ "taxIdentifier").format[TaxIdentifier] and
      (__ \ "guaranteeReference").format[GuaranteeReference] and
      (__ \ "requestedAt").format[Instant] and
      (__ \ "completedAt").formatNullable[Instant] and
      (__ \ "response").formatNullable[BalanceRequestResponse]
  )(PendingBalanceRequest.apply _, unlift(PendingBalanceRequest.unapply _))

  implicit val pendingBalanceRequestWrites: OWrites[PendingBalanceRequest] =
    OWrites.transform(balanceRequestFormat) { case (request, obj) =>
      obj ++ Json.obj("messageIdentifier" -> request.balanceId.messageIdentifier)
    }

  implicit val pendingBalanceRequestFormat: OFormat[PendingBalanceRequest] =
    OFormat(balanceRequestFormat, pendingBalanceRequestWrites)
}
