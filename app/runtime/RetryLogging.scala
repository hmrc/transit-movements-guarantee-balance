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

package runtime

import cats.effect.IO
import play.api.Logger
import retry.RetryDetails

import scala.concurrent.duration.Duration

object RetryLogging {
  def retryMessage(operation: String, details: RetryDetails): String = details match {
    case RetryDetails.GivingUp(retries, _) =>
      s"Error while ${operation} after ${retries} retries, giving up"
    case RetryDetails.WillDelayAndRetry(Duration.Zero, 0, _) =>
      s"Error while ${operation}, trying again immediately"
    case RetryDetails.WillDelayAndRetry(delay, 0, _) =>
      s"Error while ${operation}, trying again in ${delay.toMillis}ms"
    case RetryDetails.WillDelayAndRetry(Duration.Zero, 1, _) =>
      s"Error while ${operation} after 1 retry, trying again immediately"
    case RetryDetails.WillDelayAndRetry(delay, 1, _) =>
      s"Error while ${operation} after 1 retry, trying again in ${delay.toMillis}ms"
    case RetryDetails.WillDelayAndRetry(Duration.Zero, retries, _) =>
      s"Error while ${operation} after ${retries} retries, trying again immediately"
    case RetryDetails.WillDelayAndRetry(delay, retries, _) =>
      s"Error while ${operation} after ${retries} retries, trying again in ${delay.toMillis}ms"
  }

  def log(operation: String, logger: Logger)(exc: Throwable, details: RetryDetails): IO[Unit] = {
    val message = retryMessage(operation, details)

    if (details.givingUp)
      IO(logger.error(message, exc))
    else
      IO(logger.warn(message, exc))
  }
}
