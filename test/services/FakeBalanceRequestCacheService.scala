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

package services

import cats.effect.IO
import models.BalanceRequestResponse
import models.MessageType
import models.PendingBalanceRequest
import models.errors.BalanceRequestError
import models.request.AuthenticatedRequest
import models.request.BalanceRequest
import models.values.BalanceId
import models.values.MessageIdentifier
import uk.gov.hmrc.http.HeaderCarrier

case class FakeBalanceRequestCacheService(
  getBalanceResponse: IO[Either[BalanceRequestError, BalanceRequestResponse]] = IO.stub,
  getBalanceByIdResponse: IO[Option[PendingBalanceRequest]] = IO.stub,
  putBalanceResponse: IO[Unit] = IO.unit,
  updateBalanceResponse: IO[Either[BalanceRequestError, Unit]] = IO.stub
) extends BalanceRequestCacheService {

  override def getBalance(balanceId: BalanceId)(implicit
    hc: HeaderCarrier
  ): IO[Option[PendingBalanceRequest]] =
    getBalanceByIdResponse

  override def submitBalanceRequest(request: AuthenticatedRequest[BalanceRequest])(implicit
    hc: HeaderCarrier
  ): IO[Either[BalanceRequestError, BalanceRequestResponse]] =
    getBalanceResponse

  override def putBalance(
    balanceId: BalanceId,
    response: BalanceRequestResponse
  ): IO[Unit] =
    putBalanceResponse

  override def updateBalance(
    recipient: MessageIdentifier,
    messageType: MessageType,
    responseMessage: String
  )(implicit hc: HeaderCarrier): IO[Either[BalanceRequestError, Unit]] =
    updateBalanceResponse
}
