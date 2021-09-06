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
import com.google.inject.ImplementedBy
import config.AppConfig
import models.BalanceRequestResponse
import models.PendingBalanceRequest
import models.formats.MongoFormats
import models.request.BalanceRequest
import models.values.BalanceId
import models.values.EnrolmentId
import models.values.GuaranteeReference
import models.values.MessageSender
import models.values.TaxIdentifier
import org.bson.UuidRepresentation
import org.bson.codecs.UuidCodec
import org.bson.codecs.configuration.CodecRegistries
import org.mongodb.scala.MongoClient
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.model.Filters
import org.mongodb.scala.model.FindOneAndUpdateOptions
import org.mongodb.scala.model.IndexModel
import org.mongodb.scala.model.IndexOptions
import org.mongodb.scala.model.Indexes
import org.mongodb.scala.model.ReturnDocument
import org.mongodb.scala.model.Sorts
import org.mongodb.scala.model.Updates
import play.api.Logging
import retry.RetryPolicies
import retry.syntax.all._
import runtime.RetryLogging
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.MongoUtils
import uk.gov.hmrc.mongo.play.json.Codecs
import uk.gov.hmrc.mongo.play.json.CollectionFactory
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext

@ImplementedBy(classOf[BalanceRequestRepositoryImpl])
trait BalanceRequestRepository {
  def getBalanceRequest(balanceId: BalanceId): IO[Option[PendingBalanceRequest]]

  def getBalanceRequest(
    enrolmentId: EnrolmentId,
    taxIdentifier: TaxIdentifier,
    guaranteeReference: GuaranteeReference
  ): IO[Option[PendingBalanceRequest]]

  def insertBalanceRequest(
    enrolmentId: EnrolmentId,
    balanceRequest: BalanceRequest,
    requestedAt: Instant
  ): IO[BalanceId]

  def updateBalanceRequest(
    messageSender: MessageSender,
    completedAt: Instant,
    response: BalanceRequestResponse
  ): IO[Option[PendingBalanceRequest]]
}

@Singleton
class BalanceRequestRepositoryImpl @Inject() (
  mongoComponent: MongoComponent,
  appConfig: AppConfig,
  clock: Clock,
  random: SecureRandom
)(implicit
  ec: ExecutionContext
) extends PlayMongoRepository[PendingBalanceRequest](
      mongoComponent = mongoComponent,
      collectionName = BalanceRequestRepository.collectionName,
      domainFormat = MongoFormats.pendingBalanceRequestFormat,
      indexes = Seq(
        IndexModel(
          Indexes.ascending("messageSender"),
          IndexOptions()
            .background(false)
        ),
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
    with BalanceRequestRepository
    with IOObservables
    with Logging {

  override lazy val collection: MongoCollection[PendingBalanceRequest] =
    CollectionFactory
      .collection(mongoComponent.database, collectionName, domainFormat)
      .withCodecRegistry(
        CodecRegistries.fromRegistries(
          CodecRegistries.fromCodecs(
            Codecs.playFormatCodec(domainFormat),
            new UuidCodec(UuidRepresentation.STANDARD),
            Codecs.playFormatCodec(MongoFormats.balanceRequestResponseFormat),
            Codecs.playFormatCodec(MongoFormats.balanceRequestSuccessFormat),
            Codecs.playFormatCodec(MongoFormats.balanceRequestFunctionalErrorFormat),
            Codecs.playFormatCodec(MongoFormats.balanceRequestXmlErrorFormat)
          ),
          MongoClient.DEFAULT_CODEC_REGISTRY
        )
      )

  private def isDuplicateKey(exc: Throwable) = exc match {
    case MongoUtils.DuplicateKey(_) => IO.pure(true)
    case _                          => IO.pure(false)
  }

  def getBalanceRequest(balanceId: BalanceId): IO[Option[PendingBalanceRequest]] =
    IO.observeFirstOption {
      collection.find(Filters.eq("_id", balanceId.value))
    }

  def getBalanceRequest(
    enrolmentId: EnrolmentId,
    taxIdentifier: TaxIdentifier,
    guaranteeReference: GuaranteeReference
  ): IO[Option[PendingBalanceRequest]] =
    IO.observeFirstOption {
      collection
        .find(
          Filters.and(
            Filters.eq("enrolmentId", enrolmentId.value),
            Filters.eq("taxIdentifier", taxIdentifier.value),
            Filters.eq("guaranteeReference", guaranteeReference.value)
          )
        )
        .sort(Sorts.descending("requestedAt"))
    }

  def insertBalanceRequest(
    enrolmentId: EnrolmentId,
    balanceRequest: BalanceRequest,
    requestedAt: Instant
  ): IO[BalanceId] = {
    val insertResult = BalanceId.next(clock, random).flatMap { id =>
      val pendingRequest = PendingBalanceRequest(
        balanceId = id,
        enrolmentId = enrolmentId,
        taxIdentifier = balanceRequest.taxIdentifier,
        guaranteeReference = balanceRequest.guaranteeReference,
        requestedAt = requestedAt,
        completedAt = None,
        response = None
      )

      IO.observeFirst {
        collection.insertOne(pendingRequest)
      }
    }

    insertResult
      .retryingOnSomeErrors(
        isWorthRetrying = isDuplicateKey,
        policy = RetryPolicies.limitRetries(3),
        onError = RetryLogging.log("inserting balance request", logger)
      )
      .map { result =>
        BalanceId(result.getInsertedId.asBinary.asUuid)
      }
  }

  def updateBalanceRequest(
    messageSender: MessageSender,
    completedAt: Instant,
    response: BalanceRequestResponse
  ): IO[Option[PendingBalanceRequest]] =
    IO.observeFirstOption {
      collection.findOneAndUpdate(
        Filters.eq("messageSender", messageSender.value),
        Updates.combine(
          Updates.set("completedAt", completedAt),
          Updates.set("response", response)
        ),
        FindOneAndUpdateOptions()
          .returnDocument(ReturnDocument.AFTER)
      )
    }
}

object BalanceRequestRepository {
  val collectionName = "balance-requests"
}
