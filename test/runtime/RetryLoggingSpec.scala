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

import cats.effect.unsafe.implicits.global
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.test.DefaultAwaitTimeout
import play.api.test.FutureAwaits
import play.api.{Logger => PlayLogger}
import retry.RetryDetails

import scala.concurrent.duration._

class RetryLoggingSpec
    extends AnyFlatSpec
    with Matchers
    with FutureAwaits
    with DefaultAwaitTimeout {

  val operation = "reticulating splines"

  def withLogAppender[A](test: (PlayLogger, ListAppender[ILoggingEvent]) => A) = {
    val logger        = PlayLogger(suiteName)
    val listAppender  = new ListAppender[ILoggingEvent]()
    val logbackLogger = logger.underlyingLogger.asInstanceOf[Logger]
    listAppender.start()
    logbackLogger.detachAndStopAllAppenders()
    logbackLogger.addAppender(listAppender)
    try test(logger, listAppender)
    finally logbackLogger.detachAndStopAllAppenders()
  }

  "RetryLogging.log" should "produce a warn log message when retrying" in withLogAppender {
    (logger, appender) =>
      val retryLog = RetryLogging.log(operation, logger)(
        new RuntimeException,
        RetryDetails.WillDelayAndRetry(5.seconds, 2, 30.seconds)
      )

      await(retryLog.unsafeToFuture())

      val logEvent = appender.list.get(0)
      logEvent.getLevel shouldBe Level.WARN
  }

  it should "produce an error log message when giving up" in withLogAppender { (logger, appender) =>
    val retryLog = RetryLogging.log(operation, logger)(
      new RuntimeException,
      RetryDetails.GivingUp(3, 30.seconds)
    )

    await(retryLog.unsafeToFuture())

    val logEvent = appender.list.get(0)
    logEvent.getLevel shouldBe Level.ERROR
  }

  "RetryLogging.retryMessage" should "produce the right error message on the initial attempt" in {
    RetryLogging.retryMessage(
      operation,
      RetryDetails.WillDelayAndRetry(0.millis, 0, 600.millis)
    ) shouldBe "Error while reticulating splines, trying again immediately"

    RetryLogging.retryMessage(
      operation,
      RetryDetails.WillDelayAndRetry(300.millis, 0, 600.millis)
    ) shouldBe "Error while reticulating splines, trying again in 300ms"
  }

  it should "produce the right error message after one retry" in {
    RetryLogging.retryMessage(
      operation,
      RetryDetails.WillDelayAndRetry(0.millis, 1, 600.millis)
    ) shouldBe "Error while reticulating splines after 1 retry, trying again immediately"

    RetryLogging.retryMessage(
      operation,
      RetryDetails.WillDelayAndRetry(20.millis, 1, 600.millis)
    ) shouldBe "Error while reticulating splines after 1 retry, trying again in 20ms"

  }

  it should "produce the right error message after multiple retries" in {
    RetryLogging.retryMessage(
      operation,
      RetryDetails.WillDelayAndRetry(0.millis, 3, 600.millis)
    ) shouldBe "Error while reticulating splines after 3 retries, trying again immediately"

    RetryLogging.retryMessage(
      operation,
      RetryDetails.WillDelayAndRetry(100.millis, 3, 600.millis)
    ) shouldBe "Error while reticulating splines after 3 retries, trying again in 100ms"
  }

  it should "produce the right error message after exceeding the maximum number of retries" in {
    RetryLogging.retryMessage(
      operation,
      RetryDetails.GivingUp(4, 4.minutes)
    ) shouldBe "Error while reticulating splines after 4 retries, giving up"
  }
}
