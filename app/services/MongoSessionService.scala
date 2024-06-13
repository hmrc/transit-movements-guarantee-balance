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
import cats.effect.kernel.Resource
import cats.effect.kernel.Resource.ExitCase._
import com.google.inject.ImplementedBy
import org.mongodb.scala._
import repositories.IOObservables
import uk.gov.hmrc.mongo.MongoComponent

import javax.inject.Inject
import javax.inject.Singleton

@ImplementedBy(classOf[MongoSessionServiceImpl])
trait MongoSessionService {
  def withTransaction[A](action: ClientSession => IO[A]): IO[A]
}

@Singleton
class MongoSessionServiceImpl @Inject() (mongoComponent: MongoComponent)
    extends MongoSessionService
    with IOObservables {

  private val startTransaction = IO
    .observeFirst {
      mongoComponent.client.startSession()
    }
    .map { session =>
      if (!session.hasActiveTransaction()) { session.startTransaction() }
      session
    }

  private val transactionResource = Resource.makeCase(startTransaction) {
    case (session, Succeeded) =>
      IO.whenA(session.hasActiveTransaction()) {
        IO.observeFirstOption { session.commitTransaction() }.void
      }
    case (session, Errored(_)) =>
      IO.whenA(session.hasActiveTransaction()) {
        IO.observeFirstOption { session.abortTransaction() }.void
      }
    case (session, Canceled) =>
      IO.whenA(session.hasActiveTransaction()) {
        IO.observeFirstOption { session.abortTransaction() }.void
      }
  }

  def withTransaction[A](action: ClientSession => IO[A]) =
    transactionResource.use(action)
}
