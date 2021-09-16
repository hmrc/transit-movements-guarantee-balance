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

sealed abstract class MessageType(val code: String, val rootNode: String, val xsdPath: String)
    extends Product
    with Serializable

object MessageType {
  case object QueryOnGuarantees         extends MessageType("IE034", "CD034A", "/xsd/CD034A.xsd")
  case object ResponseQueryOnGuarantees extends MessageType("IE037", "CD037A", "/xsd/CD037A.xsd")
  case object XmlNack                   extends MessageType("IE917", "CC917A", "/xsd/CC917A.xsd")
  case object FunctionalNack            extends MessageType("IE906", "CD906A", "/xsd/CD906A.xsd")

  val values = Set(QueryOnGuarantees, ResponseQueryOnGuarantees, XmlNack, FunctionalNack)

  def withName(name: String): Option[MessageType] =
    values.find(_.code == name)
}
