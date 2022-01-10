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

import models.MessageType
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.security.SecureRandom
import javax.xml.bind.DatatypeConverter
import scala.xml.NodeSeq

class XmlValidationServiceSpec extends AnyFlatSpec with Matchers with EitherValues {

  val random = new SecureRandom

  val uniqueReference = {
    val bytes = new Array[Byte](7)
    random.nextBytes(bytes)
    DatatypeConverter.printHexBinary(bytes)
  }

  val service = new XmlValidationServiceImpl

  val validQueryXml = {
    <CD034A>
      <SynIdeMES1>UNOC</SynIdeMES1>
      <SynVerNumMES2>3</SynVerNumMES2>
      <MesSenMES3>MDTP-GUA-00000000000000000000001-01</MesSenMES3>
      <MesRecMES6>NTA.GB</MesRecMES6>
      <DatOfPreMES9>20210806</DatOfPreMES9>
      <TimOfPreMES10>1504</TimOfPreMES10>
      <IntConRefMES11>{uniqueReference}</IntConRefMES11>
      <MesIdeMES19>{uniqueReference}</MesIdeMES19>
      <MesTypMES20>GB034A</MesTypMES20>
      <TRAPRIRC1>
        <TINRC159>GB12345678900</TINRC159>
      </TRAPRIRC1>
      <GUAREF2>
        <GuaRefNumGRNREF21>05DE3300BE0001067A001017</GuaRefNumGRNREF21>
        <GUAQUE>
          <QueIdeQUE1>2</QueIdeQUE1>
        </GUAQUE>
        <TRAPRIOTG>
          <TINOTG59>GB12345678900</TINOTG59>
        </TRAPRIOTG>
        <ACCDOC728>
          <AccCodCOD729>ABC1</AccCodCOD729>
        </ACCDOC728>
      </GUAREF2>
    </CD034A>
  }

  val validResponseXml = {
    <CD037A>
      <SynIdeMES1>UNOC</SynIdeMES1>
      <SynVerNumMES2>3</SynVerNumMES2>
      <MesSenMES3>NTA.GB</MesSenMES3>
      <MesRecMES6>MDTP-GUA-00000000000000000000001-01</MesRecMES6>
      <DatOfPreMES9>20210806</DatOfPreMES9>
      <TimOfPreMES10>1505</TimOfPreMES10>
      <IntConRefMES11>{uniqueReference}</IntConRefMES11>
      <MesIdeMES19>{uniqueReference}</MesIdeMES19>
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
  }

  val queryMissingMesTypXml = {
    <CD034A>
      <SynIdeMES1>UNOC</SynIdeMES1>
      <SynVerNumMES2>3</SynVerNumMES2>
      <MesSenMES3>MDTP-GUA-00000000000000000000001-01</MesSenMES3>
      <MesRecMES6>NTA.GB</MesRecMES6>
      <DatOfPreMES9>20210806</DatOfPreMES9>
      <TimOfPreMES10>1504</TimOfPreMES10>
      <IntConRefMES11>{uniqueReference}</IntConRefMES11>
      <MesIdeMES19>{uniqueReference}</MesIdeMES19>
      <TRAPRIRC1>
        <TINRC159>GB12345678900</TINRC159>
      </TRAPRIRC1>
      <GUAREF2>
        <GuaRefNumGRNREF21>05DE3300BE0001067A001017</GuaRefNumGRNREF21>
        <GUAQUE>
          <QueIdeQUE1>2</QueIdeQUE1>
        </GUAQUE>
        <TRAPRIOTG>
          <TINOTG59>GB12345678900</TINOTG59>
        </TRAPRIOTG>
        <ACCDOC728>
          <AccCodCOD729>ABC1</AccCodCOD729>
        </ACCDOC728>
      </GUAREF2>
    </CD034A>
  }

  val queryInvalidDateXml = {
    <CD034A>
      <SynIdeMES1>UNOC</SynIdeMES1>
      <SynVerNumMES2>3</SynVerNumMES2>
      <MesSenMES3>MDTP-GUA-00000000000000000000001-01</MesSenMES3>
      <MesRecMES6>NTA.GB</MesRecMES6>
      <DatOfPreMES9>ABC12345</DatOfPreMES9>
      <TimOfPreMES10>1504</TimOfPreMES10>
      <IntConRefMES11>{uniqueReference}</IntConRefMES11>
      <MesIdeMES19>{uniqueReference}</MesIdeMES19>
      <MesTypMES20>GB034A</MesTypMES20>
      <TRAPRIRC1>
        <TINRC159>GB12345678900</TINRC159>
      </TRAPRIRC1>
      <GUAREF2>
        <GuaRefNumGRNREF21>05DE3300BE0001067A001017</GuaRefNumGRNREF21>
        <GUAQUE>
          <QueIdeQUE1>2</QueIdeQUE1>
        </GUAQUE>
        <TRAPRIOTG>
          <TINOTG59>GB12345678900</TINOTG59>
        </TRAPRIOTG>
        <ACCDOC728>
          <AccCodCOD729>ABC1</AccCodCOD729>
        </ACCDOC728>
      </GUAREF2>
    </CD034A>
  }

  "XmlValidationService" should "validate valid query XML successfully" in {
    service.validate(
      MessageType.QueryOnGuarantees,
      validQueryXml.toString
    ) shouldBe Right(validQueryXml)
  }

  it should "validate valid response XML successfully" in {
    service.validate(
      MessageType.ResponseQueryOnGuarantees,
      validResponseXml.toString
    ) shouldBe Right(validResponseXml)
  }

  it should "return an error when the XML document is empty" in {
    val result = service.validate(
      MessageType.QueryOnGuarantees,
      NodeSeq.Empty.toString
    )

    result shouldBe a[Left[_, _]]

    val errors = result.left.value.toList

    errors should have length 1

    errors.head.message should include("Premature end of file")
  }

  it should "return an error when there is a missing field" in {
    val result = service.validate(
      MessageType.QueryOnGuarantees,
      queryMissingMesTypXml.toString
    )

    result shouldBe a[Left[_, _]]

    val errors = result.left.value.toList

    errors should have length 1

    errors.head.message should include("One of '{MesTypMES20}' is expected")
  }

  it should "return an error when the data is of incorrect type" in {
    val result = service.validate(
      MessageType.QueryOnGuarantees,
      queryInvalidDateXml.toString
    )

    result shouldBe a[Left[_, _]]

    val errors = result.left.value.toList

    errors should have length 2

    errors(0).message should include("Value 'ABC12345' is not facet-valid with respect to pattern")

    errors(1).message should include("The value 'ABC12345' of element 'DatOfPreMES9' is not valid")
  }
}
