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

import config.AppConfig
import models.PendingBalanceRequest
import models.formats.MongoFormats
import org.mongodb.scala.model.IndexModel
import org.mongodb.scala.model.IndexOptions
import org.mongodb.scala.model.Indexes
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class BalanceRequestRepository @Inject() (mongoComponent: MongoComponent, appConfig: AppConfig)(
  implicit ec: ExecutionContext
) extends PlayMongoRepository[PendingBalanceRequest](
      mongoComponent = mongoComponent,
      collectionName = BalanceRequestRepository.collectionName,
      domainFormat = MongoFormats.pendingBalanceRequestFormat,
      indexes = Seq(
        // index for internalId, enrolmentId needed here too?
        IndexModel(
          Indexes.descending("requestedAt"),
          IndexOptions()
            .background(false)
            .expireAfter(
              appConfig.mongoBalanceRequestTtl.length,
              appConfig.mongoBalanceRequestTtl.unit
            )
        )
      )
    )

object BalanceRequestRepository {
  val collectionName = "balance-requests"
}
