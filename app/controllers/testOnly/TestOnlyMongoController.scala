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

package controllers.testOnly

import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.play.json.collection.JSONCollection
import repositories.BalanceRequestRepository
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class TestOnlyMongoController @Inject()(
  override val messagesApi: MessagesApi,
  mongo: ReactiveMongoApi,
  cc: ControllerComponents
)(implicit ec: ExecutionContext)
    extends BackendController(cc) {

  def dropMongoCollection(): Action[AnyContent] = Action.async {
    _ =>
      mongo.database
        .map(_.collection[JSONCollection](BalanceRequestRepository.collectionName))
        .flatMap(
          _.drop(failIfNotFound = false).map {
            case true => Ok
            case false => InternalServerError
          }
        )
  }

}
