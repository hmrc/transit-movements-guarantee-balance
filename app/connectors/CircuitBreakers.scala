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

package connectors

import akka.pattern.CircuitBreaker
import akka.stream.Materializer
import config.CircuitBreakerConfig
import logging.Logging

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

trait CircuitBreakers { self: Logging =>
  def materializer: Materializer
  def circuitBreakerConfig: CircuitBreakerConfig

  private val clazz = getClass.getSimpleName

  def circuitBreaker(implicit ec: ExecutionContext) = new CircuitBreaker(
    scheduler = materializer.system.scheduler,
    maxFailures = circuitBreakerConfig.maxFailures,
    callTimeout = circuitBreakerConfig.callTimeout,
    resetTimeout = circuitBreakerConfig.resetTimeout,
    maxResetTimeout = circuitBreakerConfig.maxResetTimeout,
    exponentialBackoffFactor = circuitBreakerConfig.exponentialBackoffFactor,
    randomFactor = circuitBreakerConfig.randomFactor
  )
    .onOpen(slf4jLogger.error(s"Circuit breaker for ${clazz} opening due to failures"))
    .onHalfOpen(slf4jLogger.warn(s"Circuit breaker for ${clazz} resetting after failures"))
    .onClose {
      slf4jLogger.warn(s"Circuit breaker for ${clazz} closing after trial connection success")
    }
    .onCallFailure(_ => slf4jLogger.error(s"Circuit breaker for ${clazz} recorded failed call"))
    .onCallBreakerOpen {
      slf4jLogger.error(s"Circuit breaker for ${clazz} rejected call due to previous failures")
    }
    .onCallTimeout { elapsed =>
      val duration = Duration.fromNanos(elapsed)
      slf4jLogger.error(
        s"Circuit breaker for ${clazz} recorded failed call due to timeout after ${duration.toMillis}ms"
      )
    }
}
