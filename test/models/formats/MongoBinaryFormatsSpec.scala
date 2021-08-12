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

import org.bson.codecs.EncoderContext
import org.bson.json.JsonMode
import org.bson.json.JsonWriter
import org.bson.json.JsonWriterSettings
import org.mongodb.scala.MongoClient
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.libs.json._

import java.io.StringWriter
import java.util.UUID

class MongoBinaryFormatsSpec
    extends AnyFlatSpec
    with Matchers
    with ScalaCheckPropertyChecks
    with MongoBinaryFormats
    with MongoUuidFormats {

  val mongoCodec          = MongoClient.DEFAULT_CODEC_REGISTRY.get(classOf[Array[Byte]])
  val mongoWriterSettings = JsonWriterSettings.builder.outputMode(JsonMode.EXTENDED).build
  val mongoEncoderContext = EncoderContext.builder.build

  def mongoWrite(bytes: Array[Byte]) = {
    val writer     = new StringWriter()
    val jsonWriter = new JsonWriter(writer, mongoWriterSettings)
    mongoCodec.encode(jsonWriter, bytes, mongoEncoderContext)
    Json.parse(writer.toString())
  }

  "MongoBinaryFormats.byteArrayFormat" should "write bytes as Mongo extended JSON" in forAll {
    bytes: Array[Byte] =>
      byteArrayFormat.writes(bytes) shouldBe mongoWrite(bytes)
  }

  it should "support reading bytes from Mongo extended JSON" in forAll { bytes: Array[Byte] =>
    byteArrayFormat.reads(mongoWrite(bytes)).asOpt should contain(bytes)
  }

  it should "round trip bytes as Mongo extended JSON" in forAll { bytes: Array[Byte] =>
    byteArrayFormat.reads(byteArrayFormat.writes(bytes)).asOpt should contain(bytes)
  }

  it should "not support other binary subtypes" in forAll { uuid: UUID =>
    byteArrayFormat.reads(uuidFormat.writes(uuid)) shouldBe JsError(
      "Invalid BSON binary subtype for generic binary data: '04'"
    )
  }
}
