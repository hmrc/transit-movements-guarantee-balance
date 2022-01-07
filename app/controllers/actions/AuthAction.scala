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

import models.request.AuthenticatedRequest
import models.values.InternalId
import play.api.Logging
import play.api.mvc.ActionRefiner
import play.api.mvc.Request
import play.api.mvc.Result
import play.api.mvc.Results._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.AuthProvider
import uk.gov.hmrc.auth.core.AuthProviders
import uk.gov.hmrc.auth.core.AuthorisationException
import uk.gov.hmrc.auth.core.AuthorisedFunctions
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class AuthAction @Inject() (val authConnector: AuthConnector)(implicit
  ec: ExecutionContext
) extends ActionRefiner[Request, AuthenticatedRequest]
  with AuthorisedFunctions
  with Logging {

  override protected def executionContext: ExecutionContext = ec

  override protected def refine[A](
    request: Request[A]
  ): Future[Either[Result, AuthenticatedRequest[A]]] = {
    implicit val hc = HeaderCarrierConverter.fromRequest(request)

    authorised(AuthProviders(AuthProvider.GovernmentGateway))
      .retrieve(Retrievals.internalId) {
        case Some(internalId) =>
          Future.successful(Right(AuthenticatedRequest(request, InternalId(internalId))))
        case None =>
          Future.successful(Left(Forbidden))
      }
      .recover { case e: AuthorisationException =>
        logger.warn(s"Failed to authorise", e)
        Left(Unauthorized)
      }
  }
}
