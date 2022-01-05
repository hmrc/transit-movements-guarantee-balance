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

package repositories

import cats.effect.IO
import org.mongodb.scala.Observable

trait IOObservables {
  implicit class IOObservableOps[A](io: IO.type) {
    def observeFirst(obs: => Observable[A]): IO[A] =
      IO.fromFuture { IO(obs.headOption) }.map(_.get)

    def observeFirstOption(obs: => Observable[A]): IO[Option[A]] =
      IO.fromFuture { IO(obs.headOption) }

    def observeAll(obs: => Observable[A]): IO[Seq[A]] =
      IO.fromFuture { IO(obs.collect().head()) }
  }
}
