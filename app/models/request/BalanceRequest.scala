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

package models.request

import models.values._
import play.api.libs.functional.syntax._
import play.api.libs.json._

// import java.time.Instant
// import java.time.format.DateTimeFormatter
// import java.util.UUID
// import scala.xml.Elem

case class BalanceRequest(
  // If we have a new enrolment, do we need separate tax ID in the request?
  taxIdentifier: TaxIdentifier,
  guaranteeReference: GuaranteeReference,
  accessCode: AccessCode
)

object BalanceRequest {
  implicit val balanceRequestReads: Reads[BalanceRequest] = (
    (__ \ "taxIdentifier").read[TaxIdentifier] and
      (__ \ "guaranteeReference").read[GuaranteeReference] and
      (__ \ "accessCode").read[AccessCode]
  )(BalanceRequest.apply _)

  // private val dateFormatter =
  //   DateTimeFormatter.ofPattern("yyyyMMdd")
  // private val timeFormatter =
  //   DateTimeFormatter.ofPattern("HHmmss")
  // private def messageSender(requestId: RequestId): String =
  //   f"MDTP-GUA-${requestId.value}%023d-01"

  // def toXml(
  //   requestId: RequestId,
  //   requestedAt: Instant,
  //   uniqueReference: UUID,
  //   request: BalanceRequest
  // ): Elem = {
  //   <CD034A>
  //     <SynIdeMES1>UNOC</SynIdeMES1>
  //     <SynVerNumMES2>3</SynVerNumMES2>
  //     <MesSenMES3>{messageSender(requestId)}</MesSenMES3>
  //     <MesRecMES6>NTA.GB</MesRecMES6>
  //     <DatOfPreMES9>{dateFormatter.format(requestedAt)}</DatOfPreMES9>
  //     <TimOfPreMES10>{timeFormatter.format(requestedAt)}</TimOfPreMES10>
  //     <IntConRefMES11>{uniqueReference.toString}</IntConRefMES11>
  //     <MesIdeMES19>{uniqueReference.toString}</MesIdeMES19>
  //     <MesTypMES20>GB034A</MesTypMES20>
  //     <TRAPRIRC1>
  //       <TINRC159>{request.taxIdentifier.value}</TINRC159>
  //     </TRAPRIRC1>
  //     <GUAREF2>
  //       <GuaRefNumGRNREF21>{request.guaranteeReference.value}</GuaRefNumGRNREF21>
  //       <GUAQUE>
  //         <QueIdeQUE1>2</QueIdeQUE1>
  //       </GUAQUE>
  //       <ACCDOC728>
  //         <AccCodCOD729>{request.accessCode.value}</AccCodCOD729>
  //       </ACCDOC728>
  //     </GUAREF2>
  //   </CD034A>
  // }
}
