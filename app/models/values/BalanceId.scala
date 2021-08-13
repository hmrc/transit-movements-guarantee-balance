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

package models.values

import cats.effect.IO

import java.nio.ByteBuffer
import java.time.Clock
import java.time.Instant
import java.util.UUID

case class BalanceId(value: UUID) extends AnyVal {

  /** The message sender fields of the NCTS Phase 4 spec are limited to 35 characters.
    * This means that we cannot use a full UUID in these fields.
    * This method returns only the initial 12 bytes of the balance ID for this purpose.
    *
    * @return the first 12 bytes of the UUID as a MessageSender value
    */
  def messageSender: MessageSender = {
    val bottom4Mask = 0xffffffffL
    val first4Bytes = (value.getLeastSignificantBits >> 32) & bottom4Mask
    val buffer      = ByteBuffer.wrap(new Array[Byte](12))
    buffer.putLong(value.getMostSignificantBits)
    buffer.putInt(first4Bytes.intValue)
    MessageSender(buffer.array())
  }
}

object BalanceId {

  /** Produces a sequential balance ID from a random UUID.
    *
    * This balance ID consists of a standard version 4 UUID,
    * with the first 32 bits replaced by the epoch second.
    *
    * @param clock The clock to use to retrieve the system time
    * @return an IO action which produces a new balance ID
    */
  def next(clock: Clock): IO[BalanceId] =
    for {
      instant <- IO(clock.instant())
      uuid    <- IO.blocking(UUID.randomUUID)
    } yield create(instant, uuid)

  /** Produces a sequential balance ID from the given UUID.
    *
    * This balance ID consists of a standard version 4 UUID,
    * with the first 32 bits replaced by the epoch second.
    *
    * @param instant The instant to use for the initial 32 bits
    * @param uuid The UUID to use for the rest of the balance ID
    * @return a new balance ID
    */
  def create(instant: Instant, uuid: UUID): BalanceId = {
    val timeComponent   = instant.getEpochSecond << 32
    val bottom8Mask     = 0xffffffffL
    val randomComponent = uuid.getMostSignificantBits & bottom8Mask
    val newHighBytes    = timeComponent | randomComponent
    val newLowBytes     = uuid.getLeastSignificantBits
    val squuid          = new UUID(newHighBytes, newLowBytes)
    BalanceId(squuid)
  }
}
