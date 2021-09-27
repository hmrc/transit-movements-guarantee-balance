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

import play.api.Logging
import play.api.mvc.PathBindable

import java.nio.ByteBuffer

case class MessageIdentifier(value: Array[Byte]) extends AnyVal {
  def hexString = {
    val sb = new StringBuilder
    for (byte <- value)
      sb.append(f"${byte}%02x")

    sb.toString
  }
}

object MessageIdentifier extends Logging {
  val MessageIdRegex = """MDTP-GUA-([0-9a-fA-F]{24})""".r

  implicit val messageIdentifierPathBindable: PathBindable[MessageIdentifier] =
    new PathBindable.Parsing[MessageIdentifier](
      { case MessageIdRegex(hexString) =>
        val buffer   = ByteBuffer.wrap(new Array[Byte](12))
        val hexBytes = hexString.sliding(2, 2)
        for (hexByte <- hexBytes) { buffer.put(Integer.parseInt(hexByte, 16).toByte) }
        MessageIdentifier(buffer.array())
      },
      id => s"MDTP-GUA-${id.hexString}",
      (key, exc) => {
        logger.warn("Unable to parse message identifier value", exc)
        s"Cannot parse parameter $key as a message identifier value"
      }
    )
}
