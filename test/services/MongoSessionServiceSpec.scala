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

package services

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.bson.types.ObjectId
import org.mongodb.scala._
import org.mongodb.scala.bson.BsonArray
import org.mongodb.scala.model.Filters
import org.scalacheck.Arbitrary
import org.scalacheck.Gen
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Ignore
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.libs.functional.syntax._
import play.api.libs.json.Format
import play.api.libs.json._
import play.api.test.DefaultAwaitTimeout
import play.api.test.FutureAwaits
import repositories.IOObservables
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.play.json.formats.MongoFormats
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import scala.concurrent.ExecutionContext

case class TestData(id: ObjectId, data: String)

object TestData extends MongoFormats.Implicits {
  implicit val testDataFormat: Format[TestData] = (
    (__ \ "_id").format[ObjectId] and
      (__ \ "data").format[String]
  )(TestData.apply, unlift(TestData.unapply))
}

// TODO cannot be enabled until https://jira.tools.tax.service.gov.uk/browse/PBD-3294 is completed
@Ignore
class MongoSessionServiceSpec
    extends AnyFlatSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[TestData]
    with ScalaCheckPropertyChecks
    with FutureAwaits
    with DefaultAwaitTimeout
    with BeforeAndAfterAll {

  implicit val ec: ExecutionContext = ExecutionContext.global

  class TestDataRepository(mongoComponent: MongoComponent)
      extends PlayMongoRepository(
        mongoComponent,
        "test-data",
        TestData.testDataFormat,
        Seq.empty
      )
      with IOObservables {
    def insertTestData(session: ClientSession, data: TestData): IO[ObjectId] =
      IO.observeFirst { collection.insertOne(session, data) }
        .map(_.getInsertedId.asObjectId().getValue())
    def getTestData(id: ObjectId): IO[Option[TestData]] =
      IO.observeFirstOption { collection.find(Filters.eq("_id", id)) }
  }

  override val repository = new TestDataRepository(mongoComponent)

  val service = new MongoSessionServiceImpl(mongoComponent)

  implicit val arbTestData: Arbitrary[TestData] = Arbitrary {
    for {
      id   <- Gen.delay(Gen.const(ObjectId.get()))
      data <- Gen.alphaNumStr
    } yield TestData(id, data)
  }

  override def beforeAll(): Unit = {
    await(
      mongoClient
        .getDatabase("admin")
        .withReadPreference(ReadPreference.primaryPreferred())
        .runCommand(
          Document(
            "replSetInitiate" -> Document(
              "_id" -> "rs0",
              "members" -> BsonArray(
                Document("host" -> "localhost:27017")
              )
            )
          )
        )
        .toFuture()
    )
  }

  "MongoSessionService" should "commit the transaction when the wrapped action is successful" in forAll {
    testData: TestData =>
      val assertion = for {
        _            <- service.withTransaction(repository.insertTestData(_, testData))
        insertedData <- repository.getTestData(testData.id)
      } yield insertedData should contain(testData)

      await(assertion.unsafeToFuture())
  }

  it should "abort the transaction when the wrapped action raises an error" in forAll {
    testData: TestData =>
      val assertion = for {
        _ <- service.withTransaction { session =>
          for {
            id <- repository.insertTestData(session, testData)
            _  <- IO.raiseError(new RuntimeException)
          } yield id
        }.attempt
        insertedData <- repository.getTestData(testData.id)
      } yield insertedData shouldBe None

      await(assertion.unsafeToFuture())
  }

  it should "abort the transaction when the wrapped action is cancelled" in forAll {
    testData: TestData =>
      val assertion = for {
        fiber        <- service.withTransaction(repository.insertTestData(_, testData)).start
        _            <- fiber.cancel
        insertedData <- repository.getTestData(testData.id)
      } yield insertedData shouldBe None

      await(assertion.unsafeToFuture())
  }
}
