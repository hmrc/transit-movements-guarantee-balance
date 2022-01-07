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

package repositories

import akka.actor.ActorSystem
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.testkit.TestKit
import cats.effect.IO
import cats.effect.unsafe.implicits._
import org.mongodb.scala._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.IOException

class IOObservablesSpec
  extends AsyncFlatSpec
  with Matchers
  with IOObservables
  with BeforeAndAfterAll {

  private implicit val system = ActorSystem(suiteName)

  override protected def afterAll(): Unit =
    TestKit.shutdownActorSystem(system)

  "IO.observeFirst" should "return the first element" in {
    IO.observeFirst(Observable(Seq(1, 2, 3)))
      .map(_ shouldBe 1)
      .unsafeToFuture()
  }

  it should "fail with NoSuchElementException when the Observable is empty" in {
    recoverToSucceededIf[NoSuchElementException] {
      IO.observeFirst(Observable(Seq.empty).toSingle())
        .unsafeToFuture()
    }
  }

  it should "fail with an exception when the Observable calls onError" in {
    recoverToSucceededIf[IOException] {
      val observable = Source
        .failed[Int](new IOException)
        .runWith(Sink.asPublisher(fanout = false))
        .toSingle()

      IO.observeFirst(observable)
        .unsafeToFuture()
    }
  }

  "IO.observeFirstOption" should "return Some of the first element" in {
    IO.observeFirstOption(Observable(Seq(2, 3, 4)))
      .map(_ shouldBe Some(2))
      .unsafeToFuture()
  }

  it should "return None when the Observable is empty" in {
    IO.observeFirstOption(Observable(Seq.empty))
      .map(_ shouldBe None)
      .unsafeToFuture()
  }

  it should "fail with an exception when the Observable calls onError" in {
    recoverToSucceededIf[IOException] {
      val observable = Source
        .failed[Int](new IOException)
        .runWith(Sink.asPublisher(fanout = false))
        .toObservable()

      IO.observeFirstOption(observable)
        .unsafeToFuture()
    }
  }

  "IO.observeAll" should "return a Seq of elements" in {
    IO.observeAll(Observable(Seq(1, 2, 3)))
      .map(_ shouldBe Seq(1, 2, 3))
      .unsafeToFuture()
  }

  it should "return an empty Seq when the Observable is empty" in {
    IO.observeAll(Observable(Seq.empty))
      .map(_ shouldBe Seq.empty)
      .unsafeToFuture()
  }

  it should "fail with an exception when the Observable calls onError" in {
    recoverToSucceededIf[IOException] {
      val observable = Source
        .failed[Int](new IOException)
        .runWith(Sink.asPublisher(fanout = false))
        .toObservable()

      IO.observeAll(observable)
        .unsafeToFuture()
    }
  }
}
