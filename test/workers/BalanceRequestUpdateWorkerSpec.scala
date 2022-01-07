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

package workers

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.testkit.TestKit
import cats.effect.IO
import cats.effect.kernel.Deferred
import cats.effect.kernel.Ref
import cats.effect.unsafe.implicits.global
import com.mongodb.client.model.changestream.ChangeStreamDocument
import com.mongodb.client.model.changestream.OperationType
import models.BalanceRequestSuccess
import models.PendingBalanceRequest
import models.request.BalanceRequest
import models.values.AccessCode
import models.values.BalanceId
import models.values.CurrencyCode
import models.values.GuaranteeReference
import models.values.TaxIdentifier
import org.mongodb.scala.bson.BsonObjectId
import org.mongodb.scala.bson.collection.immutable.Document
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.inject.DefaultApplicationLifecycle
import play.api.test.DefaultAwaitTimeout
import play.api.test.FutureAwaits
import repositories.FakeBalanceRequestRepository
import services.FakeBalanceRequestCacheService

import java.security.SecureRandom
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID

class BalanceRequestUpdateWorkerSpec
  extends AnyFlatSpec
  with Matchers
  with FutureAwaits
  with DefaultAwaitTimeout
  with BeforeAndAfterAll {

  implicit val system       = ActorSystem(suiteName)
  implicit val materializer = Materializer(system)
  implicit val ec           = materializer.executionContext

  val clock  = Clock.tickSeconds(ZoneOffset.UTC)
  val random = new SecureRandom

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }

  val uuid      = UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4")
  val balanceId = BalanceId(uuid)

  val taxIdentifier      = TaxIdentifier("GB12345678900")
  val guaranteeReference = GuaranteeReference("05DE3300BE0001067A001017")
  val accessCode         = AccessCode("ABC1")

  val balanceRequest = BalanceRequest(
    taxIdentifier,
    guaranteeReference,
    accessCode
  )

  val balanceRequestSuccess = BalanceRequestSuccess(
    BigDecimal("1212211848.45"),
    CurrencyCode("GBP")
  )

  val pendingBalanceRequest = PendingBalanceRequest(
    balanceId,
    balanceRequest.taxIdentifier,
    balanceRequest.guaranteeReference,
    clock.instant(),
    Some(clock.instant()),
    Some(balanceRequestSuccess)
  )

  def mkChangeStream(entries: List[ChangeStreamDocument[PendingBalanceRequest]]) = {
    // Should emit the entries only once so we don't get an infinite cycle on restarts
    @volatile var remaining = entries

    Source.single(()).statefulMapConcat { () => _ =>
      val emit = remaining
      remaining = List.empty
      emit
    }
  }

  def mkChangeDoc(
    balanceRequest: Option[PendingBalanceRequest] = None,
    operationType: OperationType = OperationType.UPDATE
  ): ChangeStreamDocument[PendingBalanceRequest] =
    new ChangeStreamDocument[PendingBalanceRequest](
      operationType,
      Document("_id" -> BsonObjectId()).toBsonDocument(),
      null,
      null,
      balanceRequest.orNull,
      null,
      null,
      null,
      null,
      null
    )

  "BalanceRequestUpdateWorker" should "shut down when given stop signal" in {
    val changeStream = Source.unfold(()) { _ =>
      Some(((), mkChangeDoc(Some(pendingBalanceRequest))))
    }

    val lifecycle = new DefaultApplicationLifecycle

    val worker = new BalanceRequestUpdateWorker(
      FakeBalanceRequestCacheService(),
      FakeBalanceRequestRepository(changeStreamResponse = changeStream),
      lifecycle
    )

    await(lifecycle.stop())

    await(worker.changeStreamCompleted)

    assert(worker.isShutdown.get())
  }

  it should "update the balance request cache when there is a change stream update entry" in {
    val putBalanceCalled = Deferred.unsafe[IO, Unit]
    val completeDeferred = putBalanceCalled.complete(()).void

    val changeDoc    = mkChangeDoc(Some(pendingBalanceRequest))
    val changeStream = Source(List(changeDoc))

    val lifecycle = new DefaultApplicationLifecycle

    val worker = new BalanceRequestUpdateWorker(
      FakeBalanceRequestCacheService(putBalanceResponse = completeDeferred),
      FakeBalanceRequestRepository(changeStreamResponse = changeStream),
      lifecycle
    )

    await(putBalanceCalled.get.unsafeToFuture())

    await(lifecycle.stop())

    worker.resumeToken.get() shouldBe Some(Document(changeDoc.getResumeToken))

  }

  it should "only update the cache for update operations" in {
    val putBalanceRef = Ref.unsafe[IO, Int](0)
    val incPutCalls   = putBalanceRef.update(_ + 1)

    val doc1 = mkChangeDoc(Some(pendingBalanceRequest))
    val doc2 = mkChangeDoc(operationType = OperationType.DELETE)
    val doc3 = mkChangeDoc(Some(pendingBalanceRequest), operationType = OperationType.INSERT)
    val doc4 = mkChangeDoc(Some(pendingBalanceRequest))

    val changeStream = mkChangeStream(List(doc1, doc2, doc3, doc4))

    val lifecycle = new DefaultApplicationLifecycle

    val worker = new BalanceRequestUpdateWorker(
      FakeBalanceRequestCacheService(putBalanceResponse = incPutCalls),
      FakeBalanceRequestRepository(changeStreamResponse = changeStream),
      lifecycle
    )

    val calledTwice = putBalanceRef.get.iterateUntil(_ >= 2).unsafeToFuture()

    await(calledTwice.flatMap(_ => lifecycle.stop()))

    await(worker.changeStreamCompleted)

    worker.resumeToken.get() shouldBe Some(Document(doc4.getResumeToken))
  }

  it should "ignore update entries without a full document" in {
    val putBalanceRef = Ref.unsafe[IO, Int](0)
    val incPutCalls   = putBalanceRef.update(_ + 1)

    val doc1 = mkChangeDoc(Some(pendingBalanceRequest))
    val doc2 = mkChangeDoc(None)
    val doc3 = mkChangeDoc(Some(pendingBalanceRequest))

    val changeStream = mkChangeStream(List(doc1, doc2, doc3))

    val lifecycle = new DefaultApplicationLifecycle

    val worker = new BalanceRequestUpdateWorker(
      FakeBalanceRequestCacheService(putBalanceResponse = incPutCalls),
      FakeBalanceRequestRepository(changeStreamResponse = changeStream),
      lifecycle
    )

    val calledTwice = putBalanceRef.get.iterateUntil(_ >= 2).unsafeToFuture()

    await(calledTwice.flatMap(_ => lifecycle.stop()))

    await(worker.changeStreamCompleted)

    worker.resumeToken.get() shouldBe Some(Document(doc3.getResumeToken))
  }

  it should "restart on non fatal errors" in {
    val putBalanceRef = Ref.unsafe[IO, Int](0)

    val incPutCalls = for {
      calls <- putBalanceRef.updateAndGet(_ + 1)
      _     <- if (calls == 2) IO.raiseError(new RuntimeException) else IO.unit
    } yield ()

    val doc1 = mkChangeDoc(Some(pendingBalanceRequest))
    val doc2 = mkChangeDoc(Some(pendingBalanceRequest))
    val doc3 = mkChangeDoc(Some(pendingBalanceRequest))

    val changeStream = mkChangeStream(List(doc1, doc2, doc3))

    val lifecycle = new DefaultApplicationLifecycle

    val worker = new BalanceRequestUpdateWorker(
      FakeBalanceRequestCacheService(putBalanceResponse = incPutCalls),
      FakeBalanceRequestRepository(changeStreamResponse = changeStream),
      lifecycle
    )

    val calledThreeTimes = putBalanceRef.get.iterateUntil(_ >= 3).unsafeToFuture()

    await(calledThreeTimes.flatMap(_ => lifecycle.stop()))

    await(worker.changeStreamCompleted)

    worker.resumeToken.get() shouldBe Some(Document(doc3.getResumeToken))
  }
}
