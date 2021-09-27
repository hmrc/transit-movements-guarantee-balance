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

import java.util.Random

case class UniqueReference(value: BigInt) extends AnyVal {
  def base36String: String = {
    val base36   = value.toString(UniqueReference.Radix)
    val reversed = base36.reverse
    val padded   = reversed.padTo(UniqueReference.MaxChars, '0')
    padded.reverse
  }
}

object UniqueReference {
  private val Radix    = 36
  private val MaxChars = 14
  private val MaxValue = BigInt("z" * MaxChars, Radix)

  def next(random: Random): IO[UniqueReference] = {
    val nextValue = IO.blocking(BigInt(MaxValue.bitLength, random))

    nextValue.flatMap { value =>
      if (value > MaxValue)
        next(random)
      else
        IO.pure(UniqueReference(value))
    }
  }
}
