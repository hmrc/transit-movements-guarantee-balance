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
import cats.syntax.all._
import com.google.inject.ImplementedBy
import models.BalanceRequestFunctionalError
import models.BalanceRequestResponse
import models.BalanceRequestSuccess
import models.BalanceRequestXmlError
import models.MessageType
import models.errors.BalanceRequestError
import models.errors.FunctionalError
import models.errors.XmlError
import models.values.CurrencyCode
import models.values.ErrorType

import javax.inject.Inject
import javax.inject.Singleton
import scala.xml.NodeSeq

@ImplementedBy(classOf[XmlParsingServiceImpl])
trait XmlParsingService {
  def parseResponseMessage(
    messageType: MessageType,
    message: NodeSeq
  ): Either[BalanceRequestError, BalanceRequestResponse]
}

@Singleton
class XmlParsingServiceImpl @Inject() () extends XmlParsingService {

  private def hasMatchingRootNode(
    messageType: MessageType,
    message: NodeSeq
  ): Boolean = message.headOption.exists(_.label == messageType.rootNode)

  def parseResponseMessage(
    messageType: MessageType,
    message: NodeSeq
  ): Either[BalanceRequestError, BalanceRequestResponse] = messageType match {
    case MessageType.ResponseQueryOnGuarantees if hasMatchingRootNode(messageType, message) =>
      val response = for {
        balanceNode <- (message \\ "BalEXP3").headOption
        balance = BigDecimal(balanceNode.text)
        currencyNode <- (message \\ "CurEXP4").headOption
        currency = CurrencyCode(currencyNode.text)
      } yield BalanceRequestSuccess(balance, currency)

      response.toRight {
        BalanceRequestError.badRequestError(
          s"Unable to parse required values from ${messageType.code} message"
        )
      }

    case MessageType.FunctionalNack if hasMatchingRootNode(messageType, message) =>
      val errorNodes = (message \\ "FUNERRER1")

      val errors = errorNodes.toList.flatMap { errorNode =>
        for {
          errorTypeNode <- (errorNode \ "ErrTypER11").headOption
          errorType = ErrorType(errorTypeNode.text.toInt)
          errorPointerNode <- (errorNode \ "ErrPoiER12").headOption
          errorPointer    = errorPointerNode.text
          errorReasonNode = (errorNode \ "ErrReaER13").headOption
          errorReason     = errorReasonNode.map(_.text)
        } yield FunctionalError(errorType, errorPointer, errorReason)
      }

      Either.fromOption(
        NonEmptyList.fromList(errors).map(BalanceRequestFunctionalError.apply),
        BalanceRequestError.badRequestError(
          s"Unable to parse required values from ${messageType.code} message"
        )
      )

    case MessageType.XmlNack if hasMatchingRootNode(messageType, message) =>
      val errorNodes = (message \\ "FUNERRER1")

      val errors = errorNodes.toList.flatMap { errorNode =>
        for {
          errorTypeNode <- (errorNode \ "ErrTypER11").headOption
          errorType = ErrorType(errorTypeNode.text.toInt)
          errorPointerNode <- (errorNode \ "ErrPoiER12").headOption
          errorPointer    = errorPointerNode.text
          errorReasonNode = (errorNode \ "ErrReaER13").headOption
          errorReason     = errorReasonNode.map(_.text)
        } yield XmlError(errorType, errorPointer, errorReason)
      }

      Either.fromOption(
        NonEmptyList.fromList(errors).map(BalanceRequestXmlError.apply),
        BalanceRequestError.badRequestError(
          s"Unable to parse required values from ${messageType.code} message"
        )
      )

    case _ =>
      message.headOption
        .map { _ =>
          Left(
            BalanceRequestError.badRequestError(
              "Root node of XML document does not match message type header"
            )
          )
        }
        .getOrElse {
          Left(
            BalanceRequestError.badRequestError("Missing root node in XML document")
          )
        }
  }
}
