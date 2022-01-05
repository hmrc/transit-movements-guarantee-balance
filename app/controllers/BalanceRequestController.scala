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

package controllers

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.syntax.all._
import com.kenshoo.play.metrics.Metrics
import config.Constants
import controllers.actions.AuthActionProvider
import controllers.actions.IOActions
import logging.Logging
import metrics.IOMetrics
import metrics.MetricsKeys
import models.BalanceRequestFunctionalError
import models.BalanceRequestResponse
import models.BalanceRequestSuccess
import models.BalanceRequestXmlError
import models.errors._
import models.formats.HttpFormats
import models.request.BalanceRequest
import models.request.Channel
import models.values.BalanceId
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import play.api.mvc.Request
import play.api.mvc.Result
import services.BalanceRequestCacheService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import javax.inject.Singleton
import scala.util.control.NonFatal

@Singleton
class BalanceRequestController @Inject() (
  authenticate: AuthActionProvider,
  service: BalanceRequestCacheService,
  cc: ControllerComponents,
  val runtime: IORuntime,
  val metrics: Metrics
) extends BackendController(cc)
  with IOActions
  with IOMetrics
  with HttpFormats
  with Logging
  with ErrorLogging {

  import MetricsKeys.Controllers._

  private def requireChannelHeader[A](
    result: => IO[Result]
  )(implicit request: Request[A]): IO[Result] =
    request.headers
      .get(Constants.ChannelHeader)
      .flatMap(Channel.withName)
      .map(_ => result)
      .getOrElse {
        val error     = BadRequestError("Missing or incorrect Channel header")
        val errorJson = Json.toJson[BalanceRequestError](error)
        IO.pure(BadRequest(errorJson))
      }

  def submitBalanceRequest: Action[BalanceRequest] =
    authenticate().io(parse.json[BalanceRequest]) { implicit request =>
      withMetricsTimerResult(SubmitBalanceRequest) {
        requireChannelHeader {
          service
            .submitBalanceRequest(request)
            .flatTap(logServiceError("submitting balance request", _))
            .map {
              case Right(success @ BalanceRequestSuccess(_, _)) =>
                Ok(Json.toJson[BalanceRequestResponse](success))

              case Right(functionalError @ BalanceRequestFunctionalError(_)) =>
                Ok(Json.toJson[BalanceRequestResponse](functionalError))

              case Right(BalanceRequestXmlError(_)) =>
                InternalServerError(Json.toJson(BalanceRequestError.internalServiceError()))

              case Left(error @ BadRequestError(_)) =>
                BadRequest(Json.toJson[BalanceRequestError](error))

              case Left(error @ UpstreamTimeoutError(_, _)) =>
                Accepted(Json.toJson[BalanceId](error.balanceId))

              case Left(error @ UpstreamServiceError(_, _)) =>
                InternalServerError(Json.toJson[BalanceRequestError](error))

              case Left(error @ InternalServiceError(_, _)) =>
                InternalServerError(Json.toJson[BalanceRequestError](error))

              case Left(_) =>
                InternalServerError(Json.toJson(BalanceRequestError.internalServiceError()))
            }
            .recoverWith { case NonFatal(e) =>
              logger.error(e)("Unhandled exception thrown").map { _ =>
                val error     = InternalServiceError.causedBy(e)
                val errorJson = Json.toJson[BalanceRequestError](error)
                InternalServerError(errorJson)
              }
            }
        }
      }
    }

  def getBalanceRequest(balanceId: BalanceId): Action[AnyContent] =
    authenticate().io { implicit request =>
      withMetricsTimerResult(GetBalanceRequest) {
        service
          .getBalance(balanceId)
          .map {
            case Some(request) =>
              Ok(Json.toJson(request))
            case None =>
              NotFound(Json.toJson(BalanceRequestError.notFoundError(balanceId)))
          }
          .recoverWith { case NonFatal(e) =>
            logger.error(e)("Unhandled exception thrown").map { _ =>
              val error     = InternalServiceError.causedBy(e)
              val errorJson = Json.toJson[BalanceRequestError](error)
              InternalServerError(errorJson)
            }
          }
      }
    }
}
