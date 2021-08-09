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

import cats.data.NonEmptyList
import models.errors.FunctionalError
import models.errors.XmlError
import models.values._
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen

import java.time.LocalDateTime
import java.time.ZoneOffset

trait Generators {
  private val now = LocalDateTime.now

  implicit val arbPendingBalanceRequest: Arbitrary[PendingBalanceRequest] = Arbitrary {
    for {
      requestId <- arbitrary[Int].map(RequestId.apply)
      requestedAt <- arbitrary[LocalDateTime].map { dt =>
        dt.withYear(now.getYear).toInstant(ZoneOffset.UTC)
      }
      userInternalId     <- Gen.alphaNumStr.map(InternalId.apply)
      userEnrolmentId    <- Gen.alphaNumStr.map(EnrolmentId.apply)
      taxIdentifier      <- Gen.stringOfN(17, Gen.alphaNumChar).map(TaxIdentifier.apply)
      guaranteeReference <- Gen.stringOfN(24, Gen.alphaNumChar).map(GuaranteeReference.apply)
    } yield PendingBalanceRequest(
      requestId,
      requestedAt,
      userInternalId,
      userEnrolmentId,
      taxIdentifier,
      guaranteeReference,
      completedAt = None,
      response = None
    )
  }

  implicit val arbErrorType: Arbitrary[ErrorType] = Arbitrary {
    arbitrary[Int].map(ErrorType.apply)
  }

  implicit val arbFunctionalError: Arbitrary[FunctionalError] = Arbitrary {
    for {
      errorType    <- arbitrary[ErrorType]
      errorPointer <- Gen.alphaNumStr
      errorReason  <- Gen.option(Gen.alphaNumStr)
    } yield FunctionalError(errorType, errorPointer, errorReason)
  }

  implicit val arbXmlError: Arbitrary[XmlError] = Arbitrary {
    for {
      errorType    <- arbitrary[ErrorType]
      errorPointer <- Gen.alphaNumStr
      errorReason  <- Gen.option(Gen.alphaNumStr)
    } yield XmlError(errorType, errorPointer, errorReason)
  }

  implicit val arbBalanceRequestSuccess: Arbitrary[BalanceRequestSuccess] = Arbitrary {
    for {
      balance <- arbitrary[BigDecimal].map(
        _.abs.setScale(2, scala.math.BigDecimal.RoundingMode.HALF_UP)
      )
      currencyCode <- Gen.stringOfN(3, Gen.alphaNumChar).map(_.toUpperCase).map(CurrencyCode.apply)
    } yield BalanceRequestSuccess(balance, currencyCode)
  }

  implicit val arbBalanceRequestFunctionalError: Arbitrary[BalanceRequestFunctionalError] =
    Arbitrary {
      for {
        numErrors <- Gen.choose(1, 10)
        errors    <- Gen.listOfN(numErrors, arbitrary[FunctionalError])
      } yield BalanceRequestFunctionalError(NonEmptyList.fromListUnsafe(errors))
    }

  implicit val arbBalanceRequestXmlError: Arbitrary[BalanceRequestXmlError] = Arbitrary {
    for {
      numErrors <- Gen.choose(1, 10)
      errors    <- Gen.listOfN(numErrors, arbitrary[XmlError])
    } yield BalanceRequestXmlError(NonEmptyList.fromListUnsafe(errors))
  }

  implicit val arbBalanceRequestResponse: Arbitrary[BalanceRequestResponse] = Arbitrary {
    Gen.oneOf(
      arbitrary[BalanceRequestSuccess],
      arbitrary[BalanceRequestFunctionalError],
      arbitrary[BalanceRequestXmlError]
    )
  }
}
