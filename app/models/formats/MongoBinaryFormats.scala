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

package models.formats

import play.api.libs.json.Writes
import play.api.libs.json._

import java.util.Base64

object MongoBinaryFormats extends MongoBinaryFormats

trait MongoBinaryFormats {
  private val encoder = Base64.getEncoder
  private val decoder = Base64.getDecoder

  private val BinarySubtype = "00"

  implicit val byteArrayReads: Reads[Array[Byte]] =
    Reads.at[String](__ \ "$binary" \ "subType").flatMap {
      case `BinarySubtype` =>
        Reads
          .at[String](__ \ "$binary" \ "base64")
          .map(decoder.decode)
      case other =>
        Reads.failed(s"Invalid BSON binary subtype for generic binary data: '$other'")
    }

  implicit val byteArrayWrites: Writes[Array[Byte]] = Writes { bytes =>
    Json.obj(
      "$binary" -> Json.obj(
        "base64"  -> encoder.encodeToString(bytes),
        "subType" -> BinarySubtype
      )
    )
  }

  implicit val byteArrayFormat: Format[Array[Byte]] =
    Format(byteArrayReads, byteArrayWrites)
}
