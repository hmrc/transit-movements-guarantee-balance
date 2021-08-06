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

import cats.data.NonEmptyList
import models.errors._
import models.values.CurrencyCode

// import scala.xml.NodeSeq

sealed abstract class BalanceRequestResponse extends Product with Serializable

case class BalanceRequestSuccess(
  balance: BigDecimal,
  currency: CurrencyCode
) extends BalanceRequestResponse

case class BalanceRequestFunctionalError(
  errors: NonEmptyList[FunctionalError]
) extends BalanceRequestResponse

case class BalanceRequestXmlError(
  errors: NonEmptyList[XmlError]
) extends BalanceRequestResponse

// object BalanceRequestResponse {
//   // Do schema validation before this so we can rely on valid XML structure
//   // Consider creating an XML wrapper data type for each kind of message as argument to this function?
//   def fromXml(xml: NodeSeq): Option[BalanceRequestResponse] =
//     xml.headOption.flatMap {
//       case node if node.label == "CD037A" =>
//         for {
//           balanceNode <- (node \\ "BalEXP3").headOption
//           balance = BigDecimal(balanceNode.text.trim)
//           currencyNode <- (node \\ "CurEXP4").headOption
//           currency = CurrencyCode(currencyNode.text.trim)
//         } yield BalanceRequestSuccess(balance, currency)
//       case node if node.label == "CC917A" =>
//         Some(
//           // BalanceRequestFunctionalError(
//           // )
//           ???
//         )
//       case node if node.label == "CD906A" =>
//         Some(
//           // BalanceRequestXmlError(
//           // )
//           ???
//         )
//       case _ =>
//         None
//     }
// }
