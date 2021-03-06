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

package connectors

import cats.effect.unsafe.implicits.global
import com.github.tomakehurst.wiremock.client.WireMock._
import config.Constants
import models.values.BalanceId
import org.scalatest.EitherValues
import org.scalatest.Inside
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.http.ContentTypes
import play.api.http.HeaderNames
import play.api.http.MimeTypes
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse._

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import scala.util.Right

class EisRouterConnectorSpec
  extends AsyncFlatSpec
  with Matchers
  with EitherValues
  with Inside
  with WireMockSpec {

  override def portConfigKeys = Seq("microservice.services.eis-router.port")

  implicit val hc = HeaderCarrier(otherHeaders = Seq(Constants.ChannelHeader -> "api"))

  val uuid      = UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4")
  val balanceId = BalanceId(uuid)

  val dateTime    = OffsetDateTime.of(LocalDateTime.of(2021, 9, 3, 18, 6, 20), ZoneOffset.UTC)
  val requestedAt = dateTime.toInstant()

  "EisRouterConnector" should "send XML message to downstream component" in {
    val connector = injector.instanceOf[EisRouterConnector]

    wireMockServer.stubFor(
      post(urlEqualTo("/movements/messages"))
        .withHeader(HeaderNames.ACCEPT, equalTo(MimeTypes.XML))
        .withHeader(HeaderNames.CONTENT_TYPE, equalTo(ContentTypes.XML))
        .withHeader(HeaderNames.DATE, equalTo("Fri, 3 Sep 2021 18:06:20 GMT"))
        .withHeader("Channel", equalTo("api"))
        .withHeader("X-Message-Sender", equalTo("MDTP-GUA-22b9899e24ee48e6a18997d1"))
        .withHeader("X-Message-Type", equalTo("IE034"))
        .withRequestBody(equalTo("<transitRequest><foo></foo></transitRequest>"))
        .willReturn(aResponse().withStatus(ACCEPTED))
    )

    connector
      .sendMessage(balanceId, requestedAt, <foo></foo>)
      .map { response =>
        response shouldBe a[Right[_, _]]
        response.value shouldBe (())
      }
      .unsafeToFuture()
  }

  it should "return an error response if the downstream component returns a client error" in {
    val connector = injector.instanceOf[EisRouterConnector]

    wireMockServer.stubFor(
      post(urlEqualTo("/movements/messages"))
        .willReturn(aResponse().withStatus(FORBIDDEN))
    )

    connector
      .sendMessage(balanceId, requestedAt, <foo></foo>)
      .map { response =>
        response shouldBe a[Left[_, _]]
        inside(response.left.value) { case Upstream4xxResponse(response) =>
          response.statusCode shouldBe FORBIDDEN
        }
      }
      .unsafeToFuture()
  }

  it should "return an error response if the downstream component returns a server error" in {
    val connector = injector.instanceOf[EisRouterConnector]

    wireMockServer.stubFor(
      post(urlEqualTo("/movements/messages"))
        .willReturn(aResponse().withStatus(BAD_GATEWAY))
    )

    connector
      .sendMessage(balanceId, requestedAt, <foo></foo>)
      .map { response =>
        response shouldBe a[Left[_, _]]
        inside(response.left.value) { case Upstream5xxResponse(response) =>
          response.statusCode shouldBe BAD_GATEWAY
        }
      }
      .unsafeToFuture()
  }
}
