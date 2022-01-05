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

package controllers.actions

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import play.api.mvc.Action
import play.api.mvc.ActionBuilder
import play.api.mvc.AnyContent
import play.api.mvc.BaseController
import play.api.mvc.BodyParser
import play.api.mvc.Result

trait IOActions { self: BaseController =>
  def runtime: IORuntime

  implicit class IOActionBuilderOps[+R[_], A](builder: ActionBuilder[R, A]) {
    def io(block: IO[Result]): Action[AnyContent] =
      builder.async(block.unsafeToFuture()(runtime))
    def io(block: R[A] => IO[Result]): Action[A] =
      builder.async(block(_).unsafeToFuture()(runtime))
    def io[B](parser: BodyParser[B])(block: R[B] => IO[Result]): Action[B] =
      builder.async(parser)(block(_).unsafeToFuture()(runtime))
  }
}
