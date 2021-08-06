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

import cats.effect.IO
import models.values.RequestId
import org.mongodb.scala.model.Filters
import org.mongodb.scala.model.FindOneAndUpdateOptions
import org.mongodb.scala.model.ReturnDocument
import org.mongodb.scala.model.Updates
import play.api.libs.functional.syntax._
import play.api.libs.json.OFormat
import play.api.libs.json._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.Inject
import scala.concurrent.ExecutionContext

import CountersRepository.CounterValue

class CountersRepository @Inject() (mongoComponent: MongoComponent)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[CounterValue](
      mongoComponent = mongoComponent,
      collectionName = CountersRepository.collectionName,
      domainFormat = CountersRepository.counterValueFormat,
      indexes = Seq.empty
    )
    with IOObservables {

  private def nextId[A](counter: Counter[A]): IO[A] = {
    IO.observeFirst {
      collection
        .findOneAndUpdate(
          Filters.eq("_id", counter.name),
          Updates.inc("value", 1),
          FindOneAndUpdateOptions()
            .upsert(true)
            .returnDocument(ReturnDocument.AFTER)
        )
        .map(v => counter.fromValue(v.value))
    }
  }

  def nextRequestId: IO[RequestId] =
    nextId(Counter.RequestId)
}

object CountersRepository {
  val collectionName = "counters"

  case class CounterValue(id: String, value: Int)

  private implicit val counterValueFormat: OFormat[CounterValue] = (
    (__ \ "_id").format[String] and
      (__ \ "value").format[Int]
  )(CounterValue.apply, unlift(CounterValue.unapply _))
}
