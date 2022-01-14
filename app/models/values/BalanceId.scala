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

package models.values

import cats.effect.IO

import java.nio.ByteBuffer
import java.time.Clock
import java.time.Instant
import java.util.Random
import java.util.UUID

case class BalanceId(value: UUID) extends AnyVal {

  /** The message identifier fields of the NCTS Phase 4 spec are limited to 35 characters.
    * This means that we cannot use a full UUID in these fields.
    * This method returns only the initial 12 bytes of the balance ID for this purpose.
    *
    * @return the first 12 bytes of the UUID as a MessageIdentifier value
    */
  def messageIdentifier: MessageIdentifier = {
    val bottom4Mask = 0xffffffffL
    val first4Bytes = (value.getLeastSignificantBits >> 32) & bottom4Mask
    val buffer      = ByteBuffer.wrap(new Array[Byte](12))
    buffer.putLong(value.getMostSignificantBits)
    buffer.putInt(first4Bytes.intValue)
    MessageIdentifier(buffer.array())
  }
}

object BalanceId {

  /** Produces a sequential balance ID from a random UUID.
    *
    * This balance ID consists of a standard version 4 UUID,
    * with the first 32 bits replaced by the epoch second.
    *
    * @param clock The clock to use to retrieve the system time
    * @param random The generator to use to produce a random UUID
    * @return an IO action which produces a new balance ID
    */
  def next(clock: Clock, random: Random): IO[BalanceId] =
    for {
      instant <- IO(clock.instant())
      uuid    <- randomUUID(random)
    } yield create(instant, uuid)

  /** Produces a random v4 UUID using the provided [[java.util.Random]] instance.
    *
    * Provides a way to seed a [[java.util.UUID]] with a particular generator for
    * testing purposes, in the same way that we can for time with [[java.util.Clock]].
    *
    * @param random The generator to use to produce a random UUID
    * @return an IO action which produces a random type 4 UUID
    */
  private def randomUUID(random: Random): IO[UUID] = IO.blocking {
    val randomBuffer = ByteBuffer.wrap(new Array[Byte](16))
    randomBuffer.putLong(random.nextLong())
    randomBuffer.putLong(random.nextLong())

    val randomBytes = randomBuffer.array
    // Zero out top 4 bits
    randomBytes(6) = (randomBytes(6) & 0x0f).toByte
    // Set them to version 4
    randomBytes(6) = (randomBytes(6) | 0x40).toByte
    // Zero out top 2 bits
    randomBytes(8) = (randomBytes(8) & 0x3f).toByte
    // Set them to variant 2 (Leach-Salz)
    randomBytes(8) = (randomBytes(8) | 0x80).toByte

    val finalBuffer = ByteBuffer.wrap(randomBytes)

    new UUID(finalBuffer.getLong(), finalBuffer.getLong())
  }

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
