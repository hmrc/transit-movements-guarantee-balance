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

import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.mvc.DefaultActionBuilder
import play.api.mvc.Results
import play.api.test.FakeRequest
import play.api.test.Helpers
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.MissingBearerToken

import java.io.IOException
import scala.concurrent.Future

class AuthActionSpec extends AsyncFlatSpec with Matchers {

  val cc = Helpers.stubControllerComponents()

  val internalId = "internalId"

  def mkAuthActionBuilder(authConnector: AuthConnector) = {
    val authAction           = new AuthAction(authConnector)
    val defaultActionBuilder = DefaultActionBuilder(cc.parsers.default)
    val authActionProvider   = new AuthActionProviderImpl(defaultActionBuilder, authAction)
    authActionProvider()
  }

  "AuthAction" should "return internal ID when successful" in {
    val retrieval     = Some(internalId)
    val authConnector = FakeAuthConnector(Future.successful(retrieval))
    val authAction    = mkAuthActionBuilder(authConnector)
    val result        = authAction(_ => Results.Ok)(FakeRequest())

    result.map(_ shouldBe Results.Ok)
  }

  it should "return Forbidden when internal ID is missing" in {
    val retrieval     = None
    val authConnector = FakeAuthConnector(Future.successful(retrieval))
    val authAction    = mkAuthActionBuilder(authConnector)
    val result        = authAction(_ => Results.Ok)(FakeRequest())

    result.map(_ shouldBe Results.Forbidden)
  }

  it should "return Unauthorized when there is any other kind of authorization failure" in {
    val authConnector = FakeAuthConnector(Future.failed(MissingBearerToken()))
    val authAction    = mkAuthActionBuilder(authConnector)
    val result        = authAction(_ => Results.Ok)(FakeRequest())

    result.map(_ shouldBe Results.Unauthorized)
  }

  it should "rethrow if there is any other kind of exception" in {
    val authConnector = FakeAuthConnector(Future.failed(new IOException))
    val authAction    = mkAuthActionBuilder(authConnector)
    recoverToSucceededIf[IOException] {
      authAction(_ => Results.Ok)(FakeRequest())
    }
  }
}
