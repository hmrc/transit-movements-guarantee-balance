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

package models.errors

import cats.data.NonEmptyList
import models.MessageType
import models.SchemaValidationError
import models.values.BalanceId
import models.values.MessageIdentifier
import uk.gov.hmrc.http.UpstreamErrorResponse

sealed abstract class BalanceRequestError extends Product with Serializable {
  def message: String
}

case class BadRequestError(message: String) extends BalanceRequestError

case class NotFoundError(message: String) extends BalanceRequestError

case class XmlValidationError(messageType: MessageType, errors: NonEmptyList[SchemaValidationError])
  extends BalanceRequestError {
  lazy val message: String = s"Error while validating ${messageType.code} message"
}

case class UpstreamServiceError(
  message: String = "Internal server error",
  cause: UpstreamErrorResponse
) extends BalanceRequestError

object UpstreamServiceError {
  def causedBy(cause: UpstreamErrorResponse): BalanceRequestError =
    BalanceRequestError.upstreamServiceError(cause = cause)
}

case class InternalServiceError(
  message: String = "Internal server error",
  cause: Option[Throwable] = None
) extends BalanceRequestError

object InternalServiceError {
  def causedBy(cause: Throwable): BalanceRequestError =
    BalanceRequestError.internalServiceError(cause = Some(cause))
}

case class UpstreamTimeoutError(balanceId: BalanceId, message: String = "Gateway timeout")
  extends BalanceRequestError

object BalanceRequestError {
  def notFoundError(message: String): BalanceRequestError =
    NotFoundError(message)

  def notFoundError(recipient: MessageIdentifier): BalanceRequestError =
    NotFoundError(
      s"The balance request with message identifier MDTP-GUA-${recipient.hexString} was not found"
    )

  def notFoundError(balanceId: BalanceId): BalanceRequestError =
    NotFoundError(
      s"The balance request with ID ${balanceId.value} was not found"
    )

  def badRequestError(message: String): BadRequestError =
    BadRequestError(message)

  def xmlValidationError(
    messageType: MessageType,
    errors: NonEmptyList[SchemaValidationError]
  ): BalanceRequestError =
    XmlValidationError(messageType, errors)

  def upstreamServiceError(
    message: String = "Internal server error",
    cause: UpstreamErrorResponse
  ): BalanceRequestError =
    UpstreamServiceError(message, cause)

  def internalServiceError(
    message: String = "Internal server error",
    cause: Option[Throwable] = None
  ): BalanceRequestError =
    InternalServiceError(message, cause)

  def upstreamTimeoutError(
    balanceId: BalanceId,
    message: String = "Gateway timeout"
  ): BalanceRequestError =
    UpstreamTimeoutError(balanceId, message)
}
