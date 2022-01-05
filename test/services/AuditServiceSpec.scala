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

import cats.data.NonEmptyList
import cats.effect.unsafe.implicits.global
import models.BalanceRequestFunctionalError
import models.BalanceRequestSuccess
import models.BalanceRequestXmlError
import models.audit._
import models.errors.FunctionalError
import models.errors.XmlError
import models.request.AuthenticatedRequest
import models.request.BalanceRequest
import models.values.AccessCode
import models.values.BalanceId
import models.values.CurrencyCode
import models.values.ErrorPointer
import models.values.ErrorType
import models.values.GuaranteeReference
import models.values.InternalId
import models.values.TaxIdentifier
import org.mockito.scalatest.AsyncIdiomaticMockito
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Writes
import play.api.test.FakeRequest
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import java.util.UUID
import scala.concurrent.ExecutionContext

class AuditServiceSpec
  extends AsyncFlatSpec
  with Matchers
  with AsyncIdiomaticMockito
  with BeforeAndAfterEach {
  val auditConnector = mock[AuditConnector]

  val translator   = new ErrorTranslationServiceImpl
  val auditService = new AuditServiceImpl(translator, auditConnector)

  implicit val hc = HeaderCarrier()

  override protected def beforeEach(): Unit = {
    reset(auditConnector)
  }

  val uuid      = UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4")
  val balanceId = BalanceId(uuid)

  "AuditService.auditBalanceRequest" should "audit balance request" in {
    val balanceRequest =
      AuthenticatedRequest(
        FakeRequest().withBody(
          BalanceRequest(
            TaxIdentifier("GB12345678900"),
            GuaranteeReference("05DE3300BE0001067A001017"),
            AccessCode("1234")
          )
        ),
        InternalId("ABC123")
      )

    val requestEvent = RequestEvent(
      balanceId.messageIdentifier,
      InternalId("ABC123"),
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      AccessCode("1234")
    )

    auditService
      .auditBalanceRequest(balanceId, balanceRequest)
      .map { _ =>
        auditConnector.sendExplicitAudit("RequestSent", requestEvent)(
          any[HeaderCarrier],
          any[ExecutionContext],
          any[Writes[RequestEvent]]
        ) wasCalled once
      }
      .unsafeToFuture()
  }

  "AuditService.auditBalanceRequestNotFound" should "audit missing balance request" in {
    val notFoundEvent = RequestNotFoundEvent(balanceId.messageIdentifier)

    auditService
      .auditBalanceRequestNotFound(balanceId.messageIdentifier)
      .map { _ =>
        auditConnector.sendExplicitAudit("RequestTimedOut", notFoundEvent)(
          any[HeaderCarrier],
          any[ExecutionContext],
          any[Writes[RequestNotFoundEvent]]
        ) wasCalled once
      }
      .unsafeToFuture()
  }

  "AuditService.auditBalanceResponse" should "audit successful response" in {
    val balanceRequestSuccess =
      BalanceRequestSuccess(BigDecimal("12345678.90"), CurrencyCode("GBP"))

    val responseEvent = SuccessResponseEvent(
      balanceId.messageIdentifier,
      BigDecimal("12345678.90"),
      CurrencyCode("GBP")
    )

    auditService
      .auditBalanceResponse(balanceId.messageIdentifier, balanceRequestSuccess)
      .map { _ =>
        auditConnector.sendExplicitAudit("SuccessResponse", responseEvent)(
          any[HeaderCarrier],
          any[ExecutionContext],
          any[Writes[SuccessResponseEvent]]
        ) wasCalled once
      }
      .unsafeToFuture()
  }

  it should "audit incorrect access code response" in {
    val accessCodeError =
      BalanceRequestFunctionalError(
        NonEmptyList.one(
          FunctionalError(ErrorType(12), ErrorPointer("GRR(1).ACC(1).Access code"), None)
        )
      )

    val responseEvent = ErrorResponseEvent(
      balanceId.messageIdentifier,
      NonEmptyList.one("Incorrect Access Code")
    )

    auditService
      .auditBalanceResponse(balanceId.messageIdentifier, accessCodeError)
      .map { _ =>
        auditConnector.sendExplicitAudit("ErrorResponse", responseEvent)(
          any[HeaderCarrier],
          any[ExecutionContext],
          any[Writes[ErrorResponseEvent]]
        ) wasCalled once
      }
      .unsafeToFuture()
  }

  it should "audit incorrect guarantee reference response" in {
    val grnError =
      BalanceRequestFunctionalError(
        NonEmptyList.one(
          FunctionalError(
            ErrorType(12),
            ErrorPointer("GRR(1).Guarantee reference number (GRN)"),
            None
          )
        )
      )

    val responseEvent = ErrorResponseEvent(
      balanceId.messageIdentifier,
      NonEmptyList.one("Incorrect Guarantee Reference Number")
    )

    auditService
      .auditBalanceResponse(balanceId.messageIdentifier, grnError)
      .map { _ =>
        auditConnector.sendExplicitAudit("ErrorResponse", responseEvent)(
          any[HeaderCarrier],
          any[ExecutionContext],
          any[Writes[ErrorResponseEvent]]
        ) wasCalled once
      }
      .unsafeToFuture()
  }

  it should "audit unsupported guarantee type response" in {
    val guaranteeTypeError =
      BalanceRequestFunctionalError(
        NonEmptyList.one(
          FunctionalError(
            ErrorType(14),
            ErrorPointer("GRR(1).GQY(1).Query identifier"),
            Some("R261")
          )
        )
      )

    val responseEvent = ErrorResponseEvent(
      balanceId.messageIdentifier,
      NonEmptyList.one("Unsupported Guarantee Type")
    )

    auditService
      .auditBalanceResponse(balanceId.messageIdentifier, guaranteeTypeError)
      .map { _ =>
        auditConnector.sendExplicitAudit("ErrorResponse", responseEvent)(
          any[HeaderCarrier],
          any[ExecutionContext],
          any[Writes[ErrorResponseEvent]]
        ) wasCalled once
      }
      .unsafeToFuture()
  }

  it should "audit non matching EORI response" in {
    val nonMatchingEoriError =
      BalanceRequestFunctionalError(
        NonEmptyList.one(FunctionalError(ErrorType(12), ErrorPointer("GRR(1).OTG(1).TIN"), None))
      )

    val responseEvent = ErrorResponseEvent(
      balanceId.messageIdentifier,
      NonEmptyList.one("EORI and Guarantee Reference Number do not match")
    )

    auditService
      .auditBalanceResponse(balanceId.messageIdentifier, nonMatchingEoriError)
      .map { _ =>
        auditConnector.sendExplicitAudit("ErrorResponse", responseEvent)(
          any[HeaderCarrier],
          any[ExecutionContext],
          any[Writes[ErrorResponseEvent]]
        ) wasCalled once
      }
      .unsafeToFuture()
  }

  it should "audit incorrect EORI response" in {
    val nonMatchingEoriError =
      BalanceRequestFunctionalError(
        NonEmptyList.one(FunctionalError(ErrorType(12), ErrorPointer("RC1.TIN"), None))
      )

    val responseEvent = ErrorResponseEvent(
      balanceId.messageIdentifier,
      NonEmptyList.one("Incorrect EORI Number")
    )

    auditService
      .auditBalanceResponse(balanceId.messageIdentifier, nonMatchingEoriError)
      .map { _ =>
        auditConnector.sendExplicitAudit("ErrorResponse", responseEvent)(
          any[HeaderCarrier],
          any[ExecutionContext],
          any[Writes[ErrorResponseEvent]]
        ) wasCalled once
      }
      .unsafeToFuture()
  }

  it should "audit unknown functional error responses" in {
    val balanceRequestFunctionalError =
      BalanceRequestFunctionalError(
        NonEmptyList.one(
          FunctionalError(ErrorType(89), ErrorPointer("Foo.Bar(1).Baz"), Some("R999"))
        )
      )

    val responseEvent = ErrorResponseEvent(
      balanceId.messageIdentifier,
      NonEmptyList.one("Functional error 89 with reason code R999 for Foo.Bar(1).Baz element")
    )

    auditService
      .auditBalanceResponse(balanceId.messageIdentifier, balanceRequestFunctionalError)
      .map { _ =>
        auditConnector.sendExplicitAudit("ErrorResponse", responseEvent)(
          any[HeaderCarrier],
          any[ExecutionContext],
          any[Writes[ErrorResponseEvent]]
        ) wasCalled once
      }
      .unsafeToFuture()
  }

  it should "audit unknown XML error responses" in {
    val balanceRequestFunctionalError =
      BalanceRequestXmlError(
        NonEmptyList.one(XmlError(ErrorType(89), ErrorPointer("Foo.Bar(1).Baz"), Some("R999")))
      )

    val responseEvent = ErrorResponseEvent(
      balanceId.messageIdentifier,
      NonEmptyList.one("XML error 89 with reason code R999 for Foo.Bar(1).Baz element")
    )

    auditService
      .auditBalanceResponse(balanceId.messageIdentifier, balanceRequestFunctionalError)
      .map { _ =>
        auditConnector.sendExplicitAudit("ErrorResponse", responseEvent)(
          any[HeaderCarrier],
          any[ExecutionContext],
          any[Writes[ErrorResponseEvent]]
        ) wasCalled once
      }
      .unsafeToFuture()
  }

  it should "audit invalid response" in {
    val responseEvent = InvalidResponseEvent(
      balanceId.messageIdentifier,
      "<CD037A></CD037A>",
      "Unable to parse required values from IE037 message"
    )

    auditService
      .auditBalanceResponseInvalid(
        balanceId.messageIdentifier,
        "<CD037A></CD037A>",
        "Unable to parse required values from IE037 message"
      )
      .map { _ =>
        auditConnector.sendExplicitAudit("InvalidResponse", responseEvent)(
          any[HeaderCarrier],
          any[ExecutionContext],
          any[Writes[InvalidResponseEvent]]
        ) wasCalled once
      }
      .unsafeToFuture()
  }
}
