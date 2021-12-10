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

import models.errors.FunctionalError
import models.errors.XmlError
import models.values.ErrorPointer
import models.values.ErrorType
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ErrorTranslationServiceSpec extends AnyFlatSpec with Matchers {

  val translator = new ErrorTranslationServiceImpl

  "ErrorTranslationService" should "translate known errors" in {
    val knownErrors = List(
      translator.AccessCodePointer,
      translator.GuaranteeRefPointer,
      translator.GuaranteeTaxIdentPointer,
      translator.TaxIdentPointer
    )

    val knownMessages = List(
      "Incorrect Access Code",
      "Incorrect Guarantee Reference Number",
      "EORI and Guarantee Reference Number do not match",
      "Incorrect EORI Number"
    )

    knownErrors.zip(knownMessages).foreach { case (error, expectedMessage) =>
      val actualMessage =
        translator.translateFunctionalError(FunctionalError(ErrorType(12), error, None))
      withClue(s"For known error $error:") { actualMessage shouldBe expectedMessage }
    }

    val invalidGuaranteeTypeMessage = translator.translateFunctionalError(
      FunctionalError(ErrorType(14), translator.QueryIdentPointer, Some("R261"))
    )

    withClue(s"For known error ${translator.GuaranteeTaxIdentPointer}:") {
      invalidGuaranteeTypeMessage shouldBe "Unsupported Guarantee Type"
    }
  }

  it should "translate unknown errors with known error pointers" in {
    val knownErrors = List(
      translator.AccessCodePointer,
      translator.GuaranteeRefPointer,
      translator.GuaranteeTaxIdentPointer,
      translator.TaxIdentPointer,
      ErrorPointer("Foo.Bar(1).Baz")
    )

    val knownElements = List(
      "Access Code",
      "Guarantee Reference Number",
      "Guarantee Principal EORI",
      "EORI Number",
      "Foo.Bar(1).Baz"
    )

    knownErrors.zip(knownElements).foreach { case (error, element) =>
      val functionalMessage =
        translator.translateFunctionalError(FunctionalError(ErrorType(99), error, None))
      withClue(s"For element $error:") {
        functionalMessage shouldBe s"Functional error 99 for $element element"
      }

      val xmlMessage = translator.translateXmlError(XmlError(ErrorType(99), error, None))
      withClue(s"For element $error:") { xmlMessage shouldBe s"XML error 99 for $element element" }
    }
  }
}
