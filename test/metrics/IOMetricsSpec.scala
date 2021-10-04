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

package metrics

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.effect.unsafe.implicits.global
import com.codahale.metrics.Counter
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.Timer
import com.kenshoo.play.metrics.Metrics
import controllers.actions.IOActions
import org.mockito.ArgumentMatchersSugar
import org.mockito.IdiomaticMockito
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.test.FakeRequest
import play.api.test.Helpers
import play.api.test.Helpers._
import runtime.IOFutures
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.util.concurrent.CancellationException
import scala.concurrent.Future
import scala.concurrent.duration._

class IOMetricsSpec
  extends AnyFlatSpec
  with Matchers
  with BeforeAndAfterEach
  with IdiomaticMockito
  with ArgumentMatchersSugar {

  class IOMetricsConnector(val metrics: Metrics) extends IOFutures with IOMetrics {
    def okHttpCall =
      withMetricsTimerResponse("connector-ok") {
        IO.runFuture { _ => Future.successful(Right(1)) }
      }

    def clientErrorHttpCall =
      withMetricsTimerResponse("connector-client-error") {
        IO.runFuture { _ => Future.successful(Left(UpstreamErrorResponse("Arghhh!!!", 400))) }
      }

    def serverErrorHttpCall =
      withMetricsTimerResponse("connector-server-error") {
        IO.runFuture { _ => Future.successful(Left(UpstreamErrorResponse("Kaboom!!!", 502))) }
      }

    def unhandledExceptionHttpCall =
      withMetricsTimerResponse("connector-unhandled-exception") {
        IO.runFuture { _ => Future.failed(new RuntimeException) }
      }

    def cancelledHttpCall =
      withMetricsTimerResponse("connector-unhandled-exception") {
        IO.canceled.as(Right(1))
      }

    def autoCompleteWithSuccessCall =
      withMetricsTimer("connector-auto-success")(_ => IO.unit)

    def autoCompleteWithFailureErrorCall =
      withMetricsTimer("connector-auto-success")(_ => IO.raiseError[Int](new RuntimeException))

    def autoCompleteWithFailureCancelledCall =
      withMetricsTimer("connector-auto-success")(_ => IO.canceled)

    def manualCompleteSuccessCall =
      withMetricsTimer("connector-manual-success")(_.completeWithSuccess())

    def manualCompleteFailureCall =
      withMetricsTimer("connector-manual-failure")(_.completeWithFailure())
  }

  class IOMetricsController(val metrics: Metrics, val runtime: IORuntime)
    extends BackendController(Helpers.stubControllerComponents())
    with IOActions
    with IOMetrics {

    def okEndpoint = Action.io {
      withMetricsTimerResult("controller-ok") {
        IO.sleep(50.millis).as(Ok)
      }
    }

    def clientErrorEndpoint = Action.io {
      withMetricsTimerResult("controller-client-error") {
        IO.sleep(50.millis).as(BadRequest)
      }
    }

    def serverErrorEndpoint = Action.io {
      withMetricsTimerResult("controller-server-error") {
        IO.sleep(50.millis).as(BadGateway)
      }
    }

    def unhandledExceptionEndpoint = Action.io {
      withMetricsTimerResult("controller-unhandled-exception") {
        IO.raiseError(new RuntimeException).as(Ok)
      }
    }

    def cancelledEndpoint = Action.io {
      withMetricsTimerResult("controller-unhandled-exception") {
        IO.canceled.as(Ok)
      }
    }
  }

  val metrics        = mock[Metrics]
  val registry       = mock[MetricRegistry]
  val timer          = mock[Timer]
  val timerContext   = mock[Timer.Context]
  val successCounter = mock[Counter]
  val failureCounter = mock[Counter]

  override protected def beforeEach(): Unit = {
    reset(metrics, registry, timer, timerContext, successCounter, failureCounter)
    metrics.defaultRegistry returns registry
    registry.timer(*[String]) returns timer
    registry.counter(endsWith("-success-counter")) returns successCounter
    registry.counter(endsWith("-failed-counter")) returns failureCounter
    timer.time() returns timerContext
  }

  "IOMetrics.withMetricsTimer" should "complete with success when the call completes successfully" in {
    val connector = new IOMetricsConnector(metrics)
    connector.autoCompleteWithSuccessCall.unsafeRunSync()
    timer.time() wasCalled once
    timerContext.stop() wasCalled once
    successCounter.inc() wasCalled once
  }

  it should "complete with failure when the IO action raises an error" in {
    val connector = new IOMetricsConnector(metrics)
    assertThrows[RuntimeException] {
      connector.autoCompleteWithFailureErrorCall.unsafeRunSync()
    }
    timer.time() wasCalled once
    timerContext.stop() wasCalled once
    failureCounter.inc() wasCalled once
  }

  it should "complete with failure when there is an unhandled runtime exception in the HTTP Future call" in {
    val connector = new IOMetricsConnector(metrics)
    assertThrows[RuntimeException] {
      connector.unhandledExceptionHttpCall.unsafeRunSync()
    }
    timer.time() wasCalled once
    timerContext.stop() wasCalled once
    failureCounter.inc() wasCalled once
  }

  it should "complete with failure when the IO is cancelled" in {
    val connector = new IOMetricsConnector(metrics)
    assertThrows[CancellationException] {
      connector.cancelledHttpCall.unsafeRunSync()
    }
    timer.time() wasCalled once
    timerContext.stop() wasCalled once
    failureCounter.inc() wasCalled once
  }

  it should "complete with success when the user calls completeWithSuccess explicitly" in {
    val connector = new IOMetricsConnector(metrics)
    connector.manualCompleteSuccessCall.unsafeRunSync()
    timer.time() wasCalled once
    timerContext.stop() wasCalled once
    successCounter.inc() wasCalled once
  }

  it should "complete with failure when the user calls completeWithFailure explicitly" in {
    val connector = new IOMetricsConnector(metrics)
    connector.manualCompleteFailureCall.unsafeRunSync()
    timer.time() wasCalled once
    timerContext.stop() wasCalled once
    failureCounter.inc() wasCalled once
  }

  "IOMetrics.withMetricsTimerResponse" should "complete with success when the HTTP response is a success" in {
    val connector = new IOMetricsConnector(metrics)
    connector.okHttpCall.unsafeRunSync()
    timer.time() wasCalled once
    timerContext.stop() wasCalled once
    successCounter.inc() wasCalled once
  }

  it should "complete with failure when the HTTP response is a client error" in {
    val connector = new IOMetricsConnector(metrics)
    connector.clientErrorHttpCall.unsafeRunSync()
    timer.time() wasCalled once
    timerContext.stop() wasCalled once
    failureCounter.inc() wasCalled once
  }

  it should "complete with failure when the HTTP response is a server error" in {
    val connector = new IOMetricsConnector(metrics)
    connector.serverErrorHttpCall.unsafeRunSync()
    timer.time() wasCalled once
    timerContext.stop() wasCalled once
    failureCounter.inc() wasCalled once
  }

  it should "complete with failure when the IO action is cancelled" in {
    val connector = new IOMetricsConnector(metrics)
    assertThrows[CancellationException] {
      connector.cancelledHttpCall.unsafeRunSync()
    }
    timer.time() wasCalled once
    timerContext.stop() wasCalled once
    failureCounter.inc() wasCalled once
  }

  it should "complete with failure when there is an unhandled runtime exception" in {
    val connector = new IOMetricsConnector(metrics)
    assertThrows[RuntimeException] {
      connector.unhandledExceptionHttpCall.unsafeRunSync()
    }
    timer.time() wasCalled once
    timerContext.stop() wasCalled once
    failureCounter.inc() wasCalled once
  }

  "IOMetrics.withMetricsTimerResult" should "complete with success when the result has a successful status code" in {
    val controller = new IOMetricsController(metrics, global)
    val result     = controller.okEndpoint(FakeRequest())
    status(result) shouldBe OK
    timer.time() wasCalled once
    timerContext.stop() wasCalled once
    successCounter.inc() wasCalled once
  }

  it should "complete with failure when the result has a client error status code" in {
    val controller = new IOMetricsController(metrics, global)
    val result     = controller.clientErrorEndpoint(FakeRequest())
    status(result) shouldBe BAD_REQUEST
    timer.time() wasCalled once
    timerContext.stop() wasCalled once
    failureCounter.inc() wasCalled once
  }

  it should "complete with failure when the result has a server error status code" in {
    val controller = new IOMetricsController(metrics, global)
    val result     = controller.serverErrorEndpoint(FakeRequest())
    status(result) shouldBe BAD_GATEWAY
    timer.time() wasCalled once
    timerContext.stop() wasCalled once
    failureCounter.inc() wasCalled once
  }

  it should "complete with failure when the server has an unhandled runtime exception" in {
    val controller = new IOMetricsController(metrics, global)
    assertThrows[RuntimeException] {
      await(controller.unhandledExceptionEndpoint(FakeRequest()))
    }
    timer.time() wasCalled once
    timerContext.stop() wasCalled once
    failureCounter.inc() wasCalled once
  }

  it should "complete with failure when the request handling IO action is cancelled" in {
    val controller = new IOMetricsController(metrics, global)
    assertThrows[CancellationException] {
      await(controller.cancelledEndpoint(FakeRequest()))
    }
    timer.time() wasCalled once
    timerContext.stop() wasCalled once
    failureCounter.inc() wasCalled once
  }
}
