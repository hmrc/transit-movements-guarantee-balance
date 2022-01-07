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

package controllers.actions

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.mvc.AnyContent
import play.api.mvc.Request
import play.api.mvc.Result
import play.api.mvc.Results._
import play.api.test.FakeRequest
import play.api.test.Helpers
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.io.IOException
import java.util.concurrent.CancellationException

class IOActionsSpec extends AsyncFlatSpec with Matchers {
  class TestController(
    runIOResult: IO[Result] = IO.stub,
    runIOWithRequestResult: Request[AnyContent] => IO[Result] = _ => IO.stub,
    runIOWithJsonParserResult: Request[JsValue] => IO[Result] = _ => IO.stub
  ) extends BackendController(Helpers.stubControllerComponents())
    with IOActions {
    val runtime = IORuntime.global

    def runIO = Action.io { runIOResult }

    def runIOWithRequest = Action.io { runIOWithRequestResult(_) }

    def runIOWithJsonParser = Action.io(parse.json) { runIOWithJsonParserResult(_) }
  }

  "IOAction.io" should "succeed when IO succeeds" in {
    val controller = new TestController(runIOResult = IO.pure(Ok))
    val result     = controller.runIO(FakeRequest())
    result.map(_ shouldBe Ok)
  }

  it should "fail when IO throws exception" in {
    val controller = new TestController(runIOResult = IO.raiseError(new IOException))
    recoverToSucceededIf[IOException] {
      controller.runIO(FakeRequest())
    }
  }

  it should "fail when IO is cancelled" in {
    val controller = new TestController(runIOResult = IO.canceled.map(_ => Ok))
    recoverToSucceededIf[CancellationException] {
      controller.runIO(FakeRequest())
    }
  }

  "IOAction.io using request" should "succeed when IO succeeds" in {
    val controller = new TestController(runIOWithRequestResult = _ => IO.pure(Ok))
    val result     = controller.runIOWithRequest(FakeRequest())
    result.map(_ shouldBe Ok)
  }

  it should "fail when IO throws exception" in {
    val controller =
      new TestController(runIOWithRequestResult = _ => IO.raiseError(new IOException))
    recoverToSucceededIf[IOException] {
      controller.runIOWithRequest(FakeRequest())
    }
  }

  it should "fail when IO is cancelled" in {
    val controller = new TestController(runIOWithRequestResult = _ => IO.canceled.map(_ => Ok))
    recoverToSucceededIf[CancellationException] {
      controller.runIOWithRequest(FakeRequest())
    }
  }

  "IOAction.io using body parser" should "succeed when IO succeeds" in {
    val controller = new TestController(runIOWithJsonParserResult = _ => IO.pure(Ok))
    val result     = controller.runIOWithJsonParser(FakeRequest().withBody(Json.obj("foo" -> 1)))
    result.map(_ shouldBe Ok)
  }

  it should "fail when IO throws exception" in {
    val controller =
      new TestController(runIOWithJsonParserResult = _ => IO.raiseError(new IOException))
    recoverToSucceededIf[IOException] {
      controller.runIOWithJsonParser(FakeRequest().withBody(Json.obj("foo" -> 1)))
    }
  }

  it should "fail when IO is cancelled" in {
    val controller = new TestController(runIOWithJsonParserResult = _ => IO.canceled.map(_ => Ok))
    recoverToSucceededIf[CancellationException] {
      controller.runIOWithJsonParser(FakeRequest().withBody(Json.obj("foo" -> 1)))
    }
  }
}
