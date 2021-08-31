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
import controllers.actions.AuthActionProvider
import controllers.actions.IOActions
import models.BalanceRequestFunctionalError
import models.BalanceRequestResponse
import models.BalanceRequestSuccess
import models.BalanceRequestXmlError
import models.errors._
import models.formats.HttpFormats
import models.request.BalanceRequest
import models.values.BalanceId
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
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
  val runtime: IORuntime
) extends BackendController(cc)
    with IOActions
    with HttpFormats {

  def submitBalanceRequest: Action[BalanceRequest] =
    authenticate().io(parse.json[BalanceRequest]) { implicit request =>
      service
        .getBalance(request.enrolmentId, request.body)
        .map {
          case Right(success @ BalanceRequestSuccess(_, _)) =>
            Ok(Json.toJson[BalanceRequestResponse](success))

          case Right(functionalError @ BalanceRequestFunctionalError(_)) =>
            BadRequest(Json.toJson[BalanceRequestResponse](functionalError))

          case Right(BalanceRequestXmlError(_)) =>
            InternalServerError(Json.toJson(BalanceRequestError.internalServiceError()))

          case Left(error @ UpstreamTimeoutError(_, _)) =>
            Accepted(Json.toJson[BalanceId](error.balanceId))

          case Left(error @ UpstreamServiceError(_)) =>
            InternalServerError(Json.toJson[BalanceRequestError](error))

          case Left(error @ InternalServiceError(_)) =>
            InternalServerError(Json.toJson[BalanceRequestError](error))
        }
        .recover { case NonFatal(_) =>
          InternalServerError(Json.toJson(BalanceRequestError.internalServiceError()))
        }
    }

  def getBalanceRequest(id: BalanceId): Action[AnyContent] =
    authenticate().io { _ =>
      IO.pure(NotFound)
    }
}
