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

package controllers

import cats.effect.IO
import logging.Logging
import models.errors._
import uk.gov.hmrc.http.HeaderCarrier

trait ErrorLogging { self: Logging =>
  def logServiceError[A](action: String, result: Either[BalanceRequestError, A])(implicit
    hc: HeaderCarrier
  ): IO[Unit] = {
    result.fold(
      {
        case UpstreamServiceError(_, cause) =>
          logger.error(cause)("Error when calling upstream service")
        case InternalServiceError(_, Some(cause)) =>
          logger.error(cause)(s"Error when $action")
        case InternalServiceError(message, None) =>
          logger.error(s"Error when $action: ${message}")
        case BadRequestError(message) =>
          logger.error(s"Error in request data: ${message}")
        case NotFoundError(message) =>
          logger.error(s"Error when $action: ${message}")
        case error @ XmlValidationError(_, _) =>
          logger.error(s"Error when $action: ${error.message}")
        case UpstreamTimeoutError(balanceId, message) =>
          logger.error(
            s"Error awaiting upstream response for balance ID ${balanceId.value}: ${message}"
          )
      },
      _ => IO.unit
    )
  }
}
