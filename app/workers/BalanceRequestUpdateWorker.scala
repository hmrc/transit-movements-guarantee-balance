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

package workers

import akka.stream.ActorAttributes
import akka.stream.Materializer
import akka.stream.Supervision
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.mongodb.client.model.changestream.OperationType
import org.mongodb.scala.bson.collection.immutable.Document
import play.api.Logging
import play.api.inject.ApplicationLifecycle
import repositories.BalanceRequestRepository
import services.BalanceRequestCacheService

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

class BalanceRequestUpdateWorker @Inject() (
  cache: BalanceRequestCacheService,
  repository: BalanceRequestRepository,
  lifecycle: ApplicationLifecycle
)(implicit runtime: IORuntime, materializer: Materializer)
  extends Logging {

  implicit val ec: ExecutionContext = materializer.executionContext

  val isShutdown  = new AtomicBoolean(false)
  val resumeToken = new AtomicReference[Option[Document]](None)

  lifecycle.addStopHook { () =>
    Future.successful(isShutdown.set(true))
  }

  repository
    .changeStream(resumeToken.get())
    .takeWhile(_ => !isShutdown.get())
    .mapAsync(1) { doc =>
      Future {
        resumeToken.set(Some(doc.getResumeToken))
        doc
      }
    }
    .mapAsync(Runtime.getRuntime.availableProcessors) { doc =>
      if (doc.getOperationType != OperationType.UPDATE)
        Future.unit
      else {
        val balanceRequest  = doc.getFullDocument
        val balanceId       = balanceRequest.balanceId
        val balanceResponse = balanceRequest.response
        balanceResponse
          .map(cache.putBalance(balanceId, _))
          .getOrElse(IO.unit)
          .unsafeToFuture()
      }
    }
    .withAttributes(ActorAttributes.supervisionStrategy {
      case NonFatal(e) =>
        logger.error("Exception while updating balance request cache", e)
        Supervision.Restart
      case _ =>
        Supervision.Stop
    })
    .run()
}
