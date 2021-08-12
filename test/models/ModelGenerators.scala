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

import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

trait ModelGenerators {
  def clock: Clock

  private lazy val now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)

  implicit val arbInstant: Arbitrary[Instant] =
    Arbitrary {
      arbitrary[LocalDateTime].map { dt =>
        dt.withYear(now.getYear)
          .withNano(0)
          .toInstant(ZoneOffset.UTC)
      }
    }

  implicit val arbPendingBalanceRequest: Arbitrary[PendingBalanceRequest] =
    Arbitrary {
      for {
        balanceId   <- arbitrary[Int].map(BalanceId.apply)
        requestedAt <- arbitrary[Instant]
      } yield PendingBalanceRequest(
        balanceId,
        requestedAt,
        completedAt = None,
        response = None
      )
    }

  implicit val arbErrorType: Arbitrary[ErrorType] =
    Arbitrary {
      arbitrary[Int].map(ErrorType.apply)
    }

  implicit val arbFunctionalError: Arbitrary[FunctionalError] =
    Arbitrary {
      for {
        errorType    <- arbitrary[ErrorType]
        errorPointer <- Gen.alphaNumStr
        errorReason  <- Gen.option(Gen.alphaNumStr)
      } yield FunctionalError(errorType, errorPointer, errorReason)
    }

  implicit val arbXmlError: Arbitrary[XmlError] =
    Arbitrary {
      for {
        errorType    <- arbitrary[ErrorType]
        errorPointer <- Gen.alphaNumStr
        errorReason  <- Gen.option(Gen.alphaNumStr)
      } yield XmlError(errorType, errorPointer, errorReason)
    }

  implicit val arbBalanceRequestSuccess: Arbitrary[BalanceRequestSuccess] =
    Arbitrary {
      for {
        balance <- Gen
          .chooseNum(BigDecimal(0), BigDecimal("9999999999999.99"))
          .map(
            _.abs.setScale(2, scala.math.BigDecimal.RoundingMode.HALF_UP)
          )
        currencyCode <- Gen
          .stringOfN(3, Gen.alphaNumChar)
          .map(_.toUpperCase)
          .map(CurrencyCode.apply)
      } yield BalanceRequestSuccess(balance, currencyCode)
    }

  implicit val arbBalanceRequestFunctionalError: Arbitrary[BalanceRequestFunctionalError] =
    Arbitrary {
      for {
        numErrors <- Gen.choose(1, 10)
        errors    <- Gen.listOfN(numErrors, arbitrary[FunctionalError])
      } yield BalanceRequestFunctionalError(NonEmptyList.fromListUnsafe(errors))
    }

  implicit val arbBalanceRequestXmlError: Arbitrary[BalanceRequestXmlError] =
    Arbitrary {
      for {
        numErrors <- Gen.choose(1, 10)
        errors    <- Gen.listOfN(numErrors, arbitrary[XmlError])
      } yield BalanceRequestXmlError(NonEmptyList.fromListUnsafe(errors))
    }

  implicit val arbBalanceRequestResponse: Arbitrary[BalanceRequestResponse] =
    Arbitrary {
      Gen.oneOf(
        arbitrary[BalanceRequestSuccess],
        arbitrary[BalanceRequestFunctionalError],
        arbitrary[BalanceRequestXmlError]
      )
    }
}
