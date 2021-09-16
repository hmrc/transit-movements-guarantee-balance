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

import cats.data.NonEmptyList
import models.BalanceRequestFunctionalError
import models.BalanceRequestSuccess
import models.BalanceRequestXmlError
import models.MessageType
import models.errors.BadRequestError
import models.errors.FunctionalError
import models.errors.XmlError
import models.values.CurrencyCode
import models.values.ErrorType
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.xml.NodeSeq

class XmlParsingServiceSpec extends AnyFlatSpec with Matchers with EitherValues {
  val service = new XmlParsingServiceImpl

  val validIe037Xml =
    <CD037A>
      <SynIdeMES1>UNOC</SynIdeMES1>
      <SynVerNumMES2>3</SynVerNumMES2>
      <MesSenMES3>NTA.GB</MesSenMES3>
      <MesRecMES6>MDTP-GUA-22b9899e24ee48e6a18997d1</MesRecMES6>
      <DatOfPreMES9>20210806</DatOfPreMES9>
      <TimOfPreMES10>1505</TimOfPreMES10>
      <IntConRefMES11>60b420bb3851d9</IntConRefMES11>
      <MesIdeMES19>60b420bb3851d9</MesIdeMES19>
      <MesTypMES20>GB037A</MesTypMES20>
      <TRAPRIRC1>
        <TINRC159>GB12345678900</TINRC159>
      </TRAPRIRC1>
      <CUSTOFFGUARNT>
        <RefNumRNT1>GB000001</RefNumRNT1>
      </CUSTOFFGUARNT>
      <GUAREF2>
        <GuaRefNumGRNREF21>21GB3300BE0001067A001017</GuaRefNumGRNREF21>
        <AccDatREF24>20210114</AccDatREF24>
        <GuaTypREF22>4</GuaTypREF22>
        <GuaMonCodREF23>1</GuaMonCodREF23>
        <GUAQUE>
          <QueIdeQUE1>2</QueIdeQUE1>
        </GUAQUE>
        <EXPEXP>
          <ExpEXP1>2751.95</ExpEXP1>
          <ExpCouEXP2>2448</ExpCouEXP2>
          <BalEXP3>1212211848.45</BalEXP3>
          <CurEXP4>GBP</CurEXP4>
        </EXPEXP>
      </GUAREF2>
    </CD037A>

  val invalidIe037Xml =
    <CD037A>
      <SynIdeMES1>UNOC</SynIdeMES1>
      <SynVerNumMES2>3</SynVerNumMES2>
      <MesSenMES3>NTA.GB</MesSenMES3>
      <MesRecMES6>MDTP-GUA-22b9899e24ee48e6a18997d1</MesRecMES6>
      <DatOfPreMES9>20210806</DatOfPreMES9>
      <TimOfPreMES10>1505</TimOfPreMES10>
      <IntConRefMES11>60b420bb3851d9</IntConRefMES11>
      <MesIdeMES19>60b420bb3851d9</MesIdeMES19>
      <MesTypMES20>GB037A</MesTypMES20>
      <TRAPRIRC1>
        <TINRC159>GB12345678900</TINRC159>
      </TRAPRIRC1>
      <CUSTOFFGUARNT>
        <RefNumRNT1>GB000001</RefNumRNT1>
      </CUSTOFFGUARNT>
      <GUAREF2>
        <GuaRefNumGRNREF21>21GB3300BE0001067A001017</GuaRefNumGRNREF21>
        <AccDatREF24>20210114</AccDatREF24>
        <GuaTypREF22>4</GuaTypREF22>
        <GuaMonCodREF23>1</GuaMonCodREF23>
        <GUAQUE>
          <QueIdeQUE1>2</QueIdeQUE1>
        </GUAQUE>
        <EXPEXP>
          <ExpEXP1>2751.95</ExpEXP1>
          <ExpCouEXP2>2448</ExpCouEXP2>
          <CurEXP4>GBP</CurEXP4>
        </EXPEXP>
      </GUAREF2>
    </CD037A>

  val validIe906Xml =
    <CD906A>
      <SynIdeMES1>UNOC</SynIdeMES1>
      <SynVerNumMES2>3</SynVerNumMES2>
      <MesSenMES3>NTA.GB</MesSenMES3>
      <MesRecMES6>MDTP-GUA-22b9899e24ee48e6a18997d1</MesRecMES6>
      <DatOfPreMES9>20210907</DatOfPreMES9>
      <TimOfPreMES10>1553</TimOfPreMES10>
      <IntConRefMES11>60b420bb3851d9</IntConRefMES11>
      <MesIdeMES19>60b420bb3851d9</MesIdeMES19>
      <MesTypMES20>GB906A</MesTypMES20>
      <OriMesIdeMES22>MDTP-GUA-00000000000000000000001-01</OriMesIdeMES22>
      <FUNERRER1>
        <ErrTypER11>12</ErrTypER11>
        <ErrPoiER12>Foo.Bar(1).Baz</ErrPoiER12>
        <ErrReaER13>Invalid something or other</ErrReaER13>
      </FUNERRER1>
    </CD906A>

  val invalidIe906Xml =
    <CD906A>
      <SynIdeMES1>UNOC</SynIdeMES1>
      <SynVerNumMES2>3</SynVerNumMES2>
      <MesSenMES3>NTA.GB</MesSenMES3>
      <MesRecMES6>MDTP-GUA-22b9899e24ee48e6a18997d1</MesRecMES6>
      <DatOfPreMES9>20210907</DatOfPreMES9>
      <TimOfPreMES10>1553</TimOfPreMES10>
      <IntConRefMES11>60b420bb3851d9</IntConRefMES11>
      <MesIdeMES19>60b420bb3851d9</MesIdeMES19>
      <MesTypMES20>GB906A</MesTypMES20>
      <OriMesIdeMES22>MDTP-GUA-00000000000000000000001-01</OriMesIdeMES22>
      <FUNERRER1>
        <ErrTypER11>12</ErrTypER11>
        <ErrReaER13>Invalid something or other</ErrReaER13>
      </FUNERRER1>
    </CD906A>

  val validIe917Xml =
    <CC917A>
      <SynIdeMES1>UNOC</SynIdeMES1>
      <SynVerNumMES2>3</SynVerNumMES2>
      <MesSenMES3>NTA.GB</MesSenMES3>
      <MesRecMES6>MDTP-GUA-22b9899e24ee48e6a18997d1</MesRecMES6>
      <DatOfPreMES9>20210907</DatOfPreMES9>
      <TimOfPreMES10>1553</TimOfPreMES10>
      <IntConRefMES11>60b420bb3851d9</IntConRefMES11>
      <MesIdeMES19>60b420bb3851d9</MesIdeMES19>
      <MesTypMES20>GB917A</MesTypMES20>
      <HEAHEA>
        <OriMesIdeMES22>MDTP-GUA-00000000000000000000001-01</OriMesIdeMES22>
      </HEAHEA>
      <FUNERRER1>
        <ErrTypER11>14</ErrTypER11>
        <ErrPoiER12>MesRecMES3</ErrPoiER12>
      </FUNERRER1>
    </CC917A>

  val invalidIe917Xml =
    <CC917A>
      <SynIdeMES1>UNOC</SynIdeMES1>
      <SynVerNumMES2>3</SynVerNumMES2>
      <MesSenMES3>NTA.GB</MesSenMES3>
      <MesRecMES6>MDTP-GUA-22b9899e24ee48e6a18997d1</MesRecMES6>
      <DatOfPreMES9>20210907</DatOfPreMES9>
      <TimOfPreMES10>1553</TimOfPreMES10>
      <IntConRefMES11>60b420bb3851d9</IntConRefMES11>
      <MesIdeMES19>60b420bb3851d9</MesIdeMES19>
      <MesTypMES20>GB917A</MesTypMES20>
      <HEAHEA>
        <OriMesIdeMES22>MDTP-GUA-00000000000000000000001-01</OriMesIdeMES22>
      </HEAHEA>
      <FUNERRER1>
        <ErrTypER11>14</ErrTypER11>
      </FUNERRER1>
    </CC917A>

  "XmlParsingServiceSpec" should "parse data from a valid IE037 message" in {
    service
      .parseResponseMessage(MessageType.ResponseQueryOnGuarantees, validIe037Xml)
      .value shouldBe BalanceRequestSuccess(BigDecimal("1212211848.45"), CurrencyCode("GBP"))
  }

  it should "return an error when incorrect message data is provided with IE037 message type" in {
    service
      .parseResponseMessage(MessageType.ResponseQueryOnGuarantees, validIe906Xml)
      .shouldBe(
        Left(BadRequestError("Root node of XML document does not match message type header"))
      )
  }

  it should "return an error when required data is missing from IE037 message" in {
    service
      .parseResponseMessage(MessageType.ResponseQueryOnGuarantees, invalidIe037Xml)
      .shouldBe(Left(BadRequestError("Unable to parse required values from IE037 message")))
  }

  it should "parse data from IE906 message" in {
    service
      .parseResponseMessage(MessageType.FunctionalNack, validIe906Xml)
      .value shouldBe BalanceRequestFunctionalError(
      NonEmptyList.one(
        FunctionalError(ErrorType(12), "Foo.Bar(1).Baz", Some("Invalid something or other"))
      )
    )
  }

  it should "return an error when incorrect message data is provided with IE906 message type" in {
    service
      .parseResponseMessage(MessageType.FunctionalNack, validIe037Xml)
      .shouldBe(
        Left(BadRequestError("Root node of XML document does not match message type header"))
      )
  }

  it should "return an error when required data is missing from IE906 message" in {
    service
      .parseResponseMessage(MessageType.FunctionalNack, invalidIe906Xml)
      .shouldBe(Left(BadRequestError("Unable to parse required values from IE906 message")))
  }

  it should "parse data from IE917 message" in {
    service
      .parseResponseMessage(MessageType.XmlNack, validIe917Xml)
      .value shouldBe BalanceRequestXmlError(
      NonEmptyList.one(
        XmlError(ErrorType(14), "MesRecMES3", None)
      )
    )
  }

  it should "return an error when incorrect message data is provided with IE917 message type" in {
    service
      .parseResponseMessage(MessageType.XmlNack, validIe906Xml)
      .shouldBe(
        Left(BadRequestError("Root node of XML document does not match message type header"))
      )
  }

  it should "return an error when required data is missing from IE917 message" in {
    service
      .parseResponseMessage(MessageType.XmlNack, invalidIe917Xml)
      .shouldBe(Left(BadRequestError("Unable to parse required values from IE917 message")))
  }

  it should "return an error when the XML document has no root node" in {
    service
      .parseResponseMessage(MessageType.XmlNack, NodeSeq.Empty)
      .shouldBe(Left(BadRequestError("Missing root node in XML document")))
  }
}
