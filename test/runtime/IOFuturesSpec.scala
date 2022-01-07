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

package runtime

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.test.DefaultAwaitTimeout
import play.api.test.FutureAwaits

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class IOFuturesSpec
  extends AnyFlatSpec
  with Matchers
  with ScalaCheckPropertyChecks
  with FutureAwaits
  with DefaultAwaitTimeout
  with IOFutures {

  implicit val ec: ExecutionContext = ExecutionContext.global

  "IO.runFuture" should "run successful Futures to identical result" in forAll {
    (fn: (String => Int), str: String) =>
      val viaIO =
        IO.runFuture { implicit ec => Future(str)(ec).map(fn)(ec) }.unsafeToFuture()

      val viaFuture =
        Future(str).map(fn)

      await {
        for {
          io  <- viaIO
          fut <- viaFuture
        } yield io shouldBe fut
      }
  }

  it should "run failed Futures to identical result" in forAll { num: Int =>
    val exc = new RuntimeException

    val viaIO =
      IO.runFuture { _ => Future(num).map[Int](_ => throw exc) }.unsafeToFuture()

    val viaFuture =
      Future(num).map[Int](_ => throw exc)

    assertThrows[RuntimeException] {
      await(viaIO)
    }

    assertThrows[RuntimeException] {
      await(viaFuture)
    }
  }
}
