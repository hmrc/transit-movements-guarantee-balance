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
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import config.AppConfig
import connectors.FakeEisRouterConnector
import models.BalanceRequestResponse
import models.BalanceRequestSuccess
import models.MessageType
import models.PendingBalanceRequest
import models.SchemaValidationError
import models.errors.BadRequestError
import models.errors.BalanceRequestError
import models.errors.InternalServiceError
import models.errors.NotFoundError
import models.errors.SelfCheckError
import models.errors.UpstreamServiceError
import models.errors.XmlValidationError
import models.request.BalanceRequest
import models.values._
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.Configuration
import repositories.FakeBalanceRequestRepository
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.Clock
import java.time.Instant
import java.util.UUID
import scala.util.Random
import scala.xml.Elem
import scala.xml.NodeSeq
import scala.xml.Utility

class BalanceRequestServiceSpec extends AsyncFlatSpec with Matchers {

  implicit val hc = HeaderCarrier()

  def mkAppConfig(config: Configuration) = {
    val servicesConfig = new ServicesConfig(config)
    new AppConfig(config, servicesConfig)
  }

  def service(
    getBalanceRequestResponse: IO[Option[PendingBalanceRequest]] = IO.stub,
    insertBalanceRequestResponse: IO[BalanceId] = IO.stub,
    updateBalanceRequestResponse: IO[Option[PendingBalanceRequest]] = IO.stub,
    sendMessageResponse: IO[Either[UpstreamErrorResponse, Unit]] = IO.stub,
    formatter: XmlFormattingService = new XmlFormattingServiceImpl,
    validator: XmlValidationService = new XmlValidationServiceImpl,
    parser: XmlParsingService = new XmlParsingServiceImpl,
    appConfig: AppConfig = mkAppConfig(Configuration())
  ) = {
    val repository = FakeBalanceRequestRepository(
      getBalanceRequestResponse,
      insertBalanceRequestResponse,
      updateBalanceRequestResponse
    )
    new BalanceRequestService(
      repository,
      formatter,
      validator,
      parser,
      FakeEisRouterConnector(sendMessageResponse),
      appConfig,
      Clock.systemUTC()
    )
  }

  val uuid        = UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4")
  val balanceId   = BalanceId(uuid)
  val enrolmentId = EnrolmentId("12345678ABC")

  val balanceRequest = BalanceRequest(
    TaxIdentifier("GB12345678900"),
    GuaranteeReference("05DE3300BE0001067A001017"),
    AccessCode("1234")
  )

  val pendingBalanceRequest = PendingBalanceRequest(
    balanceId,
    enrolmentId,
    balanceRequest.taxIdentifier,
    balanceRequest.guaranteeReference,
    Instant.now,
    completedAt = None,
    response = None
  )

  "BalanceRequestService.submitBalanceRequest" should "return inserted balance ID when successful" in {
    service(
      insertBalanceRequestResponse = IO.pure(balanceId),
      sendMessageResponse = IO.unit.map(Right.apply)
    ).submitBalanceRequest(enrolmentId, balanceRequest)
      .map {
        _ shouldBe Right(balanceId)
      }
      .unsafeToFuture()
  }

  it should "return InternalServiceError when there is an upstream client error" in {
    val error = UpstreamErrorResponse("Aarghh!!!", 400)

    service(
      insertBalanceRequestResponse = IO.pure(balanceId),
      sendMessageResponse = IO.pure(Left(error))
    ).submitBalanceRequest(enrolmentId, balanceRequest)
      .map {
        _ shouldBe Left(InternalServiceError.causedBy(error))
      }
      .unsafeToFuture()
  }

  it should "return UpstreamServiceError when there is an upstream server error" in {
    val error = UpstreamErrorResponse("Kaboom!!!", 500)

    service(
      insertBalanceRequestResponse = IO.pure(balanceId),
      sendMessageResponse = IO.pure(Left(error))
    ).submitBalanceRequest(enrolmentId, balanceRequest)
      .map {
        _ shouldBe Left(UpstreamServiceError.causedBy(error))
      }
      .unsafeToFuture()
  }

  it should "propagate database exceptions to the caller" in {
    recoverToSucceededIf[RuntimeException] {
      service(insertBalanceRequestResponse = IO.raiseError(new RuntimeException))
        .submitBalanceRequest(enrolmentId, balanceRequest)
        .unsafeToFuture()
    }
  }

  it should "throw an error when an invalid XML message is generated in self-check mode" in {
    val selfCheckConfig = mkAppConfig(
      Configuration(
        "features.self-check" -> "true"
      )
    )

    val invalidDateTypeXml = {
      <CD034A>
        <SynIdeMES1>UNOC</SynIdeMES1>
        <SynVerNumMES2>3</SynVerNumMES2>
        <MesSenMES3>MDTP-GUA-00000000000000000000001-01</MesSenMES3>
        <MesRecMES6>NTA.GB</MesRecMES6>
        <DatOfPreMES9>ABC12345</DatOfPreMES9>
        <TimOfPreMES10>1504</TimOfPreMES10>
        <IntConRefMES11>deadbeefcafeba</IntConRefMES11>
        <MesIdeMES19>deadbeefcafeba</MesIdeMES19>
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
            <AccCodCOD729>ABC1</AccCodCOD729>
          </ACCDOC728>
        </GUAREF2>
      </CD034A>
    }

    val badXmlFormatter = new XmlFormattingService {
      override def formatMessage(
        balanceId: BalanceId,
        requestedAt: Instant,
        reference: UniqueReference,
        request: BalanceRequest
      ): Elem = invalidDateTypeXml
    }

    recoverToExceptionIf[SelfCheckError] {
      service(
        insertBalanceRequestResponse = IO.pure(balanceId),
        sendMessageResponse = IO.unit.map(Right.apply),
        appConfig = selfCheckConfig,
        formatter = badXmlFormatter
      ).submitBalanceRequest(enrolmentId, balanceRequest)
        .unsafeToFuture()
    }.map {
      _.getMessage shouldBe
        """|Errors while validating generated IE034 message:
           |6:46 cvc-pattern-valid: Value 'ABC12345' is not facet-valid with respect to pattern '[0-9]{6,8}' for type 'Numeric_Min6Max8'.
           |6:46 cvc-type.3.1.3: The value 'ABC12345' of element 'DatOfPreMES9' is not valid.
           |""".trim.stripMargin
    }
  }

  it should "allow invalid XML messages to be generated when self-check mode is disabled" in {
    val selfCheckDisabledConfig = mkAppConfig(
      Configuration(
        "features.self-check" -> "false"
      )
    )

    val badXmlFormatter = new XmlFormattingService {
      override def formatMessage(
        balanceId: BalanceId,
        requestedAt: Instant,
        reference: UniqueReference,
        request: BalanceRequest
      ): Elem = <CD034A></CD034A>
    }

    service(
      insertBalanceRequestResponse = IO.pure(balanceId),
      sendMessageResponse = IO.unit.map(Right.apply),
      appConfig = selfCheckDisabledConfig,
      formatter = badXmlFormatter
    ).submitBalanceRequest(enrolmentId, balanceRequest)
      .map {
        _ shouldBe Right(balanceId)
      }
      .unsafeToFuture()
  }

  "BalanceRequestService.getBalanceRequest" should "return balance request for provided identifiers when found" in {
    service(
      getBalanceRequestResponse = IO.pure(Some(pendingBalanceRequest))
    ).getBalanceRequest(
      enrolmentId,
      balanceRequest.taxIdentifier,
      balanceRequest.guaranteeReference
    ).map {
      _ shouldBe Some(pendingBalanceRequest)
    }.unsafeToFuture()
  }

  it should "return None for provided identifiers when the balance request is not found" in {
    service(
      getBalanceRequestResponse = IO.pure(None)
    ).getBalanceRequest(
      enrolmentId,
      balanceRequest.taxIdentifier,
      balanceRequest.guaranteeReference
    ).map {
      _ shouldBe None
    }.unsafeToFuture()
  }

  "BalanceRequestService.getBalanceRequest by ID" should "delegate to repository" in {
    val uuid        = UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4")
    val balanceId   = BalanceId(uuid)
    val enrolmentId = EnrolmentId("12345678ABC")

    val pendingBalanceRequest = PendingBalanceRequest(
      balanceId,
      enrolmentId,
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      Instant.now.minusSeconds(5),
      completedAt = None,
      response = None
    )

    service(
      getBalanceRequestResponse = IO.some(pendingBalanceRequest)
    ).getBalanceRequest(balanceId)
      .map {
        _ shouldBe Some(pendingBalanceRequest)
      }
      .unsafeToFuture()
  }

  "BalanceService.updateBalanceRequest" should "return updated balance request when everything is successful" in {
    val uuid        = UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4")
    val balanceId   = BalanceId(uuid)
    val enrolmentId = EnrolmentId("12345678ABC")

    val balanceRequestSuccess =
      BalanceRequestSuccess(BigDecimal("1212211848.45"), CurrencyCode("GBP"))

    val updatedBalanceRequest = PendingBalanceRequest(
      balanceId,
      enrolmentId,
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("21GB3300BE0001067A001017"),
      Instant.now.minusSeconds(5),
      completedAt = Some(Instant.now),
      Some(balanceRequestSuccess)
    )

    val validResponseXml = Utility
      .trim(
        <CD037A>
          <SynIdeMES1>UNOC</SynIdeMES1>
          <SynVerNumMES2>3</SynVerNumMES2>
          <MesSenMES3>NTA.GB</MesSenMES3>
          <MesRecMES6>MDTP-GUA-00000000000000000000001-01</MesRecMES6>
          <DatOfPreMES9>20210806</DatOfPreMES9>
          <TimOfPreMES10>1505</TimOfPreMES10>
          <IntConRefMES11>{Random.alphanumeric.take(14).mkString}</IntConRefMES11>
          <MesIdeMES19>{Random.alphanumeric.take(14).mkString}</MesIdeMES19>
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
      )
      .toString

    service(
      updateBalanceRequestResponse = IO.some(updatedBalanceRequest)
    ).updateBalanceRequest(
      balanceId.messageIdentifier,
      MessageType.ResponseQueryOnGuarantees,
      validResponseXml
    ).map {
      _ shouldBe Right(updatedBalanceRequest)
    }.unsafeToFuture()
  }

  it should "return XML validation error when the XML does not pass schema validation" in {
    val responseMissingMesTypXml = Utility
      .trim(
        <CD037A>
          <SynIdeMES1>UNOC</SynIdeMES1>
          <SynVerNumMES2>3</SynVerNumMES2>
          <MesSenMES3>NTA.GB</MesSenMES3>
          <MesRecMES6>MDTP-GUA-00000000000000000000001-01</MesRecMES6>
          <DatOfPreMES9>20210806</DatOfPreMES9>
          <TimOfPreMES10>1505</TimOfPreMES10>
          <IntConRefMES11>{Random.alphanumeric.take(14).mkString}</IntConRefMES11>
          <MesIdeMES19>{Random.alphanumeric.take(14).mkString}</MesIdeMES19>
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
      )
      .toString

    service(
      updateBalanceRequestResponse = IO.none
    ).updateBalanceRequest(
      balanceId.messageIdentifier,
      MessageType.ResponseQueryOnGuarantees,
      responseMissingMesTypXml
    ).map {
      _ shouldBe Left(
        XmlValidationError(
          MessageType.ResponseQueryOnGuarantees,
          NonEmptyList.one(
            SchemaValidationError(
              1,
              332,
              "cvc-complex-type.2.4.a: Invalid content was found starting with element 'TRAPRIRC1'. One of '{MesTypMES20}' is expected."
            )
          )
        )
      )
    }.unsafeToFuture()
  }

  it should "return bad request error if XML with missing data somehow passes schema validation" in {
    val responseMissingDataXml =
      <CD037A>
        <SynIdeMES1>UNOC</SynIdeMES1>
        <SynVerNumMES2>3</SynVerNumMES2>
        <MesSenMES3>NTA.GB</MesSenMES3>
        <MesRecMES6>MDTP-GUA-00000000000000000000001-01</MesRecMES6>
        <DatOfPreMES9>20210806</DatOfPreMES9>
        <TimOfPreMES10>1505</TimOfPreMES10>
        <IntConRefMES11>{Random.alphanumeric.take(14).mkString}</IntConRefMES11>
        <MesIdeMES19>{Random.alphanumeric.take(14).mkString}</MesIdeMES19>
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

    val dummyXmlValidator = new XmlValidationService {
      override def validate(
        messageType: MessageType,
        xml: String
      ): Either[NonEmptyList[SchemaValidationError], Elem] =
        Right(responseMissingDataXml)
    }

    service(
      updateBalanceRequestResponse = IO.none,
      validator = dummyXmlValidator
    ).updateBalanceRequest(
      balanceId.messageIdentifier,
      MessageType.ResponseQueryOnGuarantees,
      responseMissingDataXml.toString
    ).map {
      _ shouldBe Left(BadRequestError("Unable to parse required values from IE037 message"))
    }.unsafeToFuture()
  }

  it should "return not found error if the balance request to update can't be found" in {

    val balanceRequestSuccess =
      BalanceRequestSuccess(BigDecimal("12345678.90"), CurrencyCode("GBP"))

    val responseMissingDataXml =
      <CD037A>
        <SynIdeMES1>UNOC</SynIdeMES1>
        <SynVerNumMES2>3</SynVerNumMES2>
        <MesSenMES3>NTA.GB</MesSenMES3>
        <MesRecMES6>MDTP-GUA-00000000000000000000001-01</MesRecMES6>
        <DatOfPreMES9>20210806</DatOfPreMES9>
        <TimOfPreMES10>1505</TimOfPreMES10>
        <IntConRefMES11>{Random.alphanumeric.take(14).mkString}</IntConRefMES11>
        <MesIdeMES19>{Random.alphanumeric.take(14).mkString}</MesIdeMES19>
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

    val dummyXmlValidator = new XmlValidationService {
      override def validate(
        messageType: MessageType,
        xml: String
      ): Either[NonEmptyList[SchemaValidationError], Elem] =
        Right(responseMissingDataXml)
    }

    val dummyXmlParser = new XmlParsingService {
      override def parseResponseMessage(
        messageType: MessageType,
        message: NodeSeq
      ): Either[BalanceRequestError, BalanceRequestResponse] =
        Right(balanceRequestSuccess)
    }

    service(
      updateBalanceRequestResponse = IO.none,
      validator = dummyXmlValidator,
      parser = dummyXmlParser
    )
      .updateBalanceRequest(
        balanceId.messageIdentifier,
        MessageType.ResponseQueryOnGuarantees,
        responseMissingDataXml.toString
      )
      .map {
        _ shouldBe Left(
          NotFoundError(
            "The balance request with message identifier MDTP-GUA-22b9899e24ee48e6a18997d1 was not found"
          )
        )
      }
      .unsafeToFuture()
  }
}
