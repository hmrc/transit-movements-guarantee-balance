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

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import config.AppConfig
import connectors.FakeEisRouterConnector
import models.PendingBalanceRequest
import models.errors.InternalServiceError
import models.errors.SelfCheckError
import models.errors.UpstreamServiceError
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
import scala.xml.Elem

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
      new XmlValidationService,
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
}
