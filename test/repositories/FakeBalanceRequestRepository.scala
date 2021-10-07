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
import models.BalanceRequestResponse
import models.PendingBalanceRequest
import models.request.BalanceRequest
import models.values.BalanceId
import models.values.EnrolmentId
import models.values.MessageIdentifier

import java.time.Instant

case class FakeBalanceRequestRepository(
  getBalanceRequestResponse: IO[Option[PendingBalanceRequest]] = IO.stub,
  insertBalanceRequestResponse: IO[BalanceId] = IO.stub,
  updateBalanceRequestResponse: IO[Option[PendingBalanceRequest]] = IO.stub
) extends BalanceRequestRepository {

  override def getBalanceRequest(balanceId: BalanceId): IO[Option[PendingBalanceRequest]] =
    getBalanceRequestResponse

  override def insertBalanceRequest(
    enrolmentId: EnrolmentId,
    balanceRequest: BalanceRequest,
    requestedAt: Instant
  ): IO[BalanceId] =
    insertBalanceRequestResponse

  override def updateBalanceRequest(
    messageIdentifier: MessageIdentifier,
    completedAt: Instant,
    response: BalanceRequestResponse
  ): IO[Option[PendingBalanceRequest]] =
    updateBalanceRequestResponse
}
