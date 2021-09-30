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

package controllers

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.syntax.all._
import config.Constants
import controllers.actions.IOActions
import logging.Logging
import models.MessageType
import models.errors.BadRequestError
import models.errors.BalanceRequestError
import models.errors.InternalServiceError
import models.errors.NotFoundError
import models.errors.XmlValidationError
import models.formats.HttpFormats
import models.values.MessageIdentifier
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import play.api.mvc.Request
import play.api.mvc.Result
import services.BalanceRequestCacheService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import javax.inject.Singleton
import scala.util.control.NonFatal

@Singleton
class BalanceRequestResponseController @Inject() (
  cache: BalanceRequestCacheService,
  cc: ControllerComponents,
  val runtime: IORuntime
) extends BackendController(cc)
  with IOActions
  with HttpFormats
  with Logging
  with ErrorLogging {

  private def requireMessageTypeHeader[A](
    result: MessageType => IO[Result]
  )(implicit request: Request[A]): IO[Result] =
    request.headers
      .get(Constants.MessageTypeHeader)
      .flatMap(MessageType.withName)
      .map(result)
      .getOrElse {
        val error     = BadRequestError("Missing or incorrect X-Message-Type header")
        val errorJson = Json.toJson[BalanceRequestError](error)
        IO.pure(BadRequest(errorJson))
      }

  def updateBalanceRequest(recipient: MessageIdentifier) = Action.io(parse.tolerantText) {
    implicit request =>
      requireMessageTypeHeader { messageType =>
        cache
          .updateBalance(recipient, messageType, request.body)
          .flatTap(logServiceError("updating balance request", _))
          .map {
            case Right(_) =>
              Ok
            case Left(error @ BadRequestError(_)) =>
              BadRequest(Json.toJson[BalanceRequestError](error))
            case Left(error @ XmlValidationError(_, _)) =>
              BadRequest(Json.toJson[BalanceRequestError](error))
            case Left(error @ NotFoundError(_)) =>
              NotFound(Json.toJson[BalanceRequestError](error))
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
