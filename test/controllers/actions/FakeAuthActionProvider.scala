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

package controllers.actions

import cats.arrow.FunctionK
import models.request.AuthenticatedRequest
import models.values.InternalId
import play.api.mvc._
import play.api.test.Helpers

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class FakeAuthActionProvider(
  authenticate: ActionRefiner[Request, AuthenticatedRequest],
  cc: ControllerComponents
) extends AuthActionProvider {
  override def apply(): ActionBuilder[AuthenticatedRequest, AnyContent] =
    cc.actionBuilder andThen authenticate
}

object FakeAuthActionProvider
  extends FakeAuthActionProvider(FakeAuthAction, Helpers.stubControllerComponents())

object FakeAuthAction
  extends FakeAuthAction(new FunctionK[Request, AuthenticatedRequest] {
    def apply[A](request: Request[A]): AuthenticatedRequest[A] =
      AuthenticatedRequest(request, InternalId("internalId"))
  })

class FakeAuthAction(result: FunctionK[Request, AuthenticatedRequest])
  extends ActionTransformer[Request, AuthenticatedRequest] {

  override protected def executionContext: ExecutionContext = ExecutionContext.global

  override protected def transform[A](request: Request[A]): Future[AuthenticatedRequest[A]] =
    Future.successful(result(request))
}
