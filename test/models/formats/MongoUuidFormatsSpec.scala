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

import org.bson.UuidRepresentation
import org.bson.codecs.EncoderContext
import org.bson.codecs.UuidCodec
import org.bson.codecs.configuration.CodecRegistries
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

class MongoUuidFormatsSpec
    extends AnyFlatSpec
    with Matchers
    with ScalaCheckPropertyChecks
    with MongoBinaryFormats
    with MongoUuidFormats {

  val mongoWriterSettings = JsonWriterSettings.builder.outputMode(JsonMode.EXTENDED).build
  val mongoEncoderContext = EncoderContext.builder.build

  def mongoWrite(uuid: UUID, uuidRep: UuidRepresentation) = {
    val writer     = new StringWriter()
    val jsonWriter = new JsonWriter(writer, mongoWriterSettings)
    val registry = CodecRegistries.fromRegistries(
      CodecRegistries.fromCodecs(new UuidCodec(uuidRep)),
      MongoClient.DEFAULT_CODEC_REGISTRY
    )
    val mongoCodec = registry.get(classOf[UUID])
    mongoCodec.encode(jsonWriter, uuid, mongoEncoderContext)
    Json.parse(writer.toString())
  }

  "MongoUuidFormats.uuidFormat" should "write UUID as Mongo extended JSON" in forAll { uuid: UUID =>
    uuidFormat.writes(uuid) shouldBe mongoWrite(uuid, UuidRepresentation.STANDARD)
  }

  it should "support reading new UUID format" in forAll { uuid: UUID =>
    val standardMongoUuid = mongoWrite(uuid, UuidRepresentation.STANDARD)
    uuidFormat.reads(standardMongoUuid).asOpt should contain(uuid)
  }

  it should "support reading legacy Java UUID format" in forAll { uuid: UUID =>
    val legacyMongoUuid = mongoWrite(uuid, UuidRepresentation.JAVA_LEGACY)
    uuidFormat.reads(legacyMongoUuid).asOpt should contain(uuid)
  }

  it should "round trip UUID as Mongo extended JSON" in forAll { uuid: UUID =>
    uuidFormat.reads(uuidFormat.writes(uuid)).asOpt should contain(uuid)
  }

  it should "not support other binary subtypes" in forAll { bytes: Array[Byte] =>
    uuidFormat.reads(byteArrayFormat.writes(bytes)) shouldBe JsError(
      "Invalid BSON binary subtype for UUID: '00'"
    )
  }
}
