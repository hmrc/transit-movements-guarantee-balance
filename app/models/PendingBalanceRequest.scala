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

package models

import models.request.AuthenticatedRequest
import models.request.BalanceRequest
import models.values._

import java.time.Instant

case class PendingBalanceRequest(
  requestId: RequestId,
  requestedAt: Instant,
  userInternalId: InternalId,
  userEnrolmentId: EnrolmentId,
  // Might be the same as userEnrolmentId if we have a new enrolment
  taxIdentifier: TaxIdentifier,
  guaranteeReference: GuaranteeReference,
  completedAt: Option[Instant],
  response: Option[BalanceRequestResponse]
)

object PendingBalanceRequest {
  def fromRequest(
    requestId: RequestId,
    requestedAt: Instant,
    request: AuthenticatedRequest[BalanceRequest]
  ) =
    PendingBalanceRequest(
      requestId,
      requestedAt,
      request.internalId,
      request.enrolmentId,
      request.body.taxIdentifier,
      request.body.guaranteeReference,
      completedAt = None,
      response = None
    )
}