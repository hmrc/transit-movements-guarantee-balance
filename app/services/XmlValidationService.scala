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

package services

import cats.data.NonEmptyList
import cats.syntax.all._
import models.MessageType
import models.SchemaValidationError
import org.xml.sax.SAXParseException

import javax.inject.Singleton
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParser
import javax.xml.parsers.SAXParserFactory
import javax.xml.validation.SchemaFactory
import scala.xml.Elem

@Singleton
class XmlValidationService {

  val parsersByType: Map[MessageType, SAXParser] =
    MessageType.values.map { typ =>
      typ -> buildParser(typ)
    }.toMap

  def buildParser(messageType: MessageType): SAXParser = {
    val schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
    val parser        = SAXParserFactory.newInstance()
    val schemaUrl     = getClass.getResource(messageType.xsdPath)
    val schema        = schemaFactory.newSchema(schemaUrl)
    parser.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    parser.setFeature("http://xml.org/sax/features/external-general-entities", false)
    parser.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    parser.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    parser.setNamespaceAware(true)
    parser.setXIncludeAware(false)
    parser.setSchema(schema)
    parser.newSAXParser()
  }

  def validate(
    messageType: MessageType,
    xml: String
  ): Either[NonEmptyList[SchemaValidationError], Elem] = {
    val parser = parsersByType(messageType)
    val loader = new ErrorCapturingXmlLoader(parser)

    val parseElem = Either
      .catchOnly[SAXParseException] {
        loader.loadString(xml)
      }
      .leftMap { exc =>
        NonEmptyList.of(SchemaValidationError.fromSaxParseException(exc))
      }

    NonEmptyList
      .fromList(loader.errors)
      .map(Either.left)
      .getOrElse(parseElem)
  }
}
