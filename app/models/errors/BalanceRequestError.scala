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

import models.values.BalanceId

sealed abstract class BalanceRequestError extends Product with Serializable {
  def message: String
}

case class UpstreamServiceError(message: String = "Internal server error")
    extends BalanceRequestError

case class InternalServiceError(message: String = "Internal server error")
    extends BalanceRequestError

case class UpstreamTimeoutError(balanceId: BalanceId, message: String = "Gateway timeout")
    extends BalanceRequestError

object BalanceRequestError {
  def upstreamServiceError(message: String = "Internal server error"): BalanceRequestError =
    UpstreamServiceError(message)

  def internalServiceError(message: String = "Internal server error"): BalanceRequestError =
    InternalServiceError(message)

  def upstreamTimeoutError(
    balanceId: BalanceId,
    message: String = "Gateway timeout"
  ): BalanceRequestError =
    UpstreamTimeoutError(balanceId, message)
}
