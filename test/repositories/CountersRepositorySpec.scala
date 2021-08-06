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

package repositories

import cats.effect.unsafe.implicits.global
import models.values.RequestId
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import CountersRepository.CounterValue

class CountersRepositorySpec
    extends AnyFlatSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[CounterValue] {

  implicit val ec              = ExecutionContext.global
  override lazy val repository = new CountersRepository(mongoComponent)

  "CountersRepository" should "have the correct name" in {
    repository.collectionName shouldBe "counters"
  }

  "CountersRepository.nextRequestId" should "return 1 when first initialised" in {
    repository.nextRequestId.unsafeToFuture().futureValue shouldBe RequestId(1)
  }

  it should "increment the next ID value on each subsequent call" in {
    repository.nextRequestId.unsafeToFuture().futureValue shouldBe RequestId(1)
    repository.nextRequestId.unsafeToFuture().futureValue shouldBe RequestId(2)
    repository.nextRequestId.unsafeToFuture().futureValue shouldBe RequestId(3)
    repository.nextRequestId.unsafeToFuture().futureValue shouldBe RequestId(4)
  }

  // This intermittently fails due to https://jira.mongodb.org/browse/SERVER-14322
  // It should be fixed in MongoDB 4.2 by https://jira.mongodb.org/browse/SERVER-37124
  // In the meantime we have added retrying behaviour to this code
  it should "not produce duplicate ID values for concurrent calls" in {
    val requests = for (_ <- 1 to 1000) yield repository.nextRequestId.unsafeToFuture()
    val results  = Future.sequence(requests).futureValue

    val original     = results.toList.sortBy(_.value)
    val deduplicated = results.toSet[RequestId].toList.sortBy(_.value)

    deduplicated shouldBe original
  }
}
