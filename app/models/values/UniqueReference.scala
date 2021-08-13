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

import java.security.SecureRandom

case class UniqueReference(value: Array[Byte]) extends AnyVal {
  def hexString = {
    val sb = new StringBuilder
    for (byte <- value)
      sb.append(f"${byte}%02x")

    sb.toString
  }
}

object UniqueReference {
  private val random = new SecureRandom

  def next: IO[UniqueReference] =
    IO.blocking {
      val bytes = new Array[Byte](7)
      random.nextBytes(bytes)
      UniqueReference(bytes)
    }
}
