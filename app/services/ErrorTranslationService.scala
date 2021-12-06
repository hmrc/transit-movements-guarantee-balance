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

import javax.inject.Inject
import javax.inject.Singleton
import com.google.inject.ImplementedBy

@ImplementedBy(classOf[ErrorTranslationServiceImpl])
trait ErrorTranslationService {
  def translateErrorPointer(pointer: ErrorPointer): String
  def translateFunctionalError(error: FunctionalError): String
  def translateXmlError(error: XmlError): String
}

@Singleton
class ErrorTranslationServiceImpl @Inject() extends ErrorTranslationService {
  val AccessCodePointer        = ErrorPointer("GRR(1).ACC(1).Access code")
  val GuaranteeRefPointer      = ErrorPointer("GRR(1).Guarantee reference number (GRN)")
  val QueryIdentPointer        = ErrorPointer("GRR(1).GQY(1).Query identifier")
  val GuaranteeTaxIdentPointer = ErrorPointer("GRR(1).OTG(1).TIN")
  val TaxIdentPointer          = ErrorPointer("RC1.TIN")

  override def translateErrorPointer(pointer: ErrorPointer) = pointer match {
    case AccessCodePointer        => "Access Code"
    case GuaranteeRefPointer      => "Guarantee Reference Number"
    case QueryIdentPointer        => "Guarantee Query Identifier"
    case GuaranteeTaxIdentPointer => "Guarantee Principal EORI"
    case TaxIdentPointer          => "EORI Number"
    case other                    => other.value
  }

  override def translateFunctionalError(error: FunctionalError) = error match {
    case FunctionalError(ErrorType(12), AccessCodePointer, _) =>
      "Incorrect Access Code"
    case FunctionalError(ErrorType(12), GuaranteeRefPointer, _) =>
      "Incorrect Guarantee Reference Number"
    case FunctionalError(ErrorType(14), QueryIdentPointer, Some("R261")) =>
      "Unsupported Guarantee Type"
    case FunctionalError(ErrorType(12), GuaranteeTaxIdentPointer, _) =>
      "EORI and Guarantee Reference Number do not match"
    case FunctionalError(ErrorType(12), TaxIdentPointer, _) =>
      "Incorrect EORI Number"
    case FunctionalError(errorType, errorPointer, Some(errorReason)) =>
      s"Functional error ${errorType.value} with reason code $errorReason for ${translateErrorPointer(errorPointer)} element"
    case FunctionalError(errorType, errorPointer, None) =>
      s"Functional error ${errorType.value} for ${translateErrorPointer(errorPointer)} element"
  }

  override def translateXmlError(error: XmlError) = error match {
    case XmlError(errorType, errorPointer, Some(errorReason)) =>
      s"XML error ${errorType.value} with reason code $errorReason for ${translateErrorPointer(errorPointer)} element"
    case XmlError(errorType, errorPointer, None) =>
      s"XML error ${errorType.value} for ${translateErrorPointer(errorPointer)} element"
  }
}
