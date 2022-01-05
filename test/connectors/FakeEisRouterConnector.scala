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

import cats.effect.IO
import models.values.BalanceId
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse

import java.time.Instant
import scala.xml.Elem

case class FakeEisRouterConnector(
  sendMessageResponse: IO[Either[UpstreamErrorResponse, Unit]] = IO.stub
) extends EisRouterConnector {

  override def sendMessage(balanceId: BalanceId, requestedAt: Instant, message: Elem)(implicit
    hc: HeaderCarrier
  ): IO[Either[UpstreamErrorResponse, Unit]] =
    sendMessageResponse
}
