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

import models.request.BalanceRequest
import models.values._
import org.scalatest.StreamlinedXmlEquality
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

class XmlFormattingServiceSpec extends AnyFlatSpec with Matchers with StreamlinedXmlEquality {

  val service = new XmlFormattingServiceImpl

  "XmlFormattingService" should "format XML according to CD034 schema requirements" in {
    val uuid        = UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4")
    val requestedAt = LocalDateTime.of(2021, 8, 13, 17, 51, 1).toInstant(ZoneOffset.UTC)
    val reference   = UniqueReference(BigInt("z" * 14, 36))
    val balanceId   = BalanceId(uuid)

    val balanceRequest = BalanceRequest(
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      AccessCode("1234")
    )

    service.formatMessage(balanceId, requestedAt, reference, balanceRequest) should equal(
      <CD034A>
        <SynIdeMES1>UNOC</SynIdeMES1>
        <SynVerNumMES2>3</SynVerNumMES2>
        <MesSenMES3>MDTP-GUA-22b9899e24ee48e6a18997d1</MesSenMES3>
        <MesRecMES6>NTA.GB</MesRecMES6>
        <DatOfPreMES9>20210813</DatOfPreMES9>
        <TimOfPreMES10>1751</TimOfPreMES10>
        <IntConRefMES11>zzzzzzzzzzzzzz</IntConRefMES11>
        <MesIdeMES19>zzzzzzzzzzzzzz</MesIdeMES19>
        <MesTypMES20>GB034A</MesTypMES20>
        <TRAPRIRC1>
          <TINRC159>GB12345678900</TINRC159>
        </TRAPRIRC1>
        <GUAREF2>
          <GuaRefNumGRNREF21>05DE3300BE0001067A001017</GuaRefNumGRNREF21>
          <GUAQUE>
            <QueIdeQUE1>2</QueIdeQUE1>
          </GUAQUE>
          <ACCDOC728>
            <AccCodCOD729>1234</AccCodCOD729>
          </ACCDOC728>
        </GUAREF2>
      </CD034A>
    )
  }

}
