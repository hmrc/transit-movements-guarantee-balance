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

package services

import com.google.inject.ImplementedBy
import models.request.BalanceRequest
import models.values.MessageIdentifier
import models.values._

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import scala.xml.Elem

@ImplementedBy(classOf[XmlFormattingServiceImpl])
trait XmlFormattingService {
  def formatMessage(
    balanceId: BalanceId,
    requestedAt: Instant,
    reference: UniqueReference,
    request: BalanceRequest
  ): Elem
}

@Singleton
class XmlFormattingServiceImpl @Inject() () extends XmlFormattingService {
  private val dateFormatter =
    DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC)
  private val timeFormatter =
    DateTimeFormatter.ofPattern("HHmm").withZone(ZoneOffset.UTC)
  private def messageIdentifier(id: MessageIdentifier) =
    s"MDTP-GUA-${id.hexString}"

  override def formatMessage(
    balanceId: BalanceId,
    requestedAt: Instant,
    reference: UniqueReference,
    request: BalanceRequest
  ): Elem =
    <CD034A>
      <SynIdeMES1>UNOC</SynIdeMES1>
      <SynVerNumMES2>3</SynVerNumMES2>
      <MesSenMES3>{messageIdentifier(balanceId.messageIdentifier)}</MesSenMES3>
      <MesRecMES6>NTA.GB</MesRecMES6>
      <DatOfPreMES9>{dateFormatter.format(requestedAt)}</DatOfPreMES9>
      <TimOfPreMES10>{timeFormatter.format(requestedAt)}</TimOfPreMES10>
      <IntConRefMES11>{reference.base36String}</IntConRefMES11>
      <MesIdeMES19>{reference.base36String}</MesIdeMES19>
      <MesTypMES20>GB034A</MesTypMES20>
      <TRAPRIRC1>
        <TINRC159>{request.taxIdentifier.value}</TINRC159>
      </TRAPRIRC1>
      <GUAREF2>
        <GuaRefNumGRNREF21>{request.guaranteeReference.value}</GuaRefNumGRNREF21>
        <GUAQUE>
          <QueIdeQUE1>2</QueIdeQUE1>
        </GUAQUE>
        <TRAPRIOTG>
          <TINOTG59>{request.taxIdentifier.value}</TINOTG59>
        </TRAPRIOTG>
        <ACCDOC728>
          <AccCodCOD729>{request.accessCode.value}</AccCodCOD729>
        </ACCDOC728>
      </GUAREF2>
    </CD034A>

}
