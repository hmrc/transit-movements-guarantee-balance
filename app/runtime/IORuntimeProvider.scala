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

package runtime

import akka.actor.ActorSystem
import cats.effect.unsafe.IORuntime
import cats.effect.unsafe.IORuntimeConfig
import play.api.inject.ApplicationLifecycle

import javax.inject.Inject
import javax.inject.Provider
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class IORuntimeProvider @Inject() (lifecycle: ApplicationLifecycle, system: ActorSystem)(implicit
  compute: ExecutionContext
) extends Provider[IORuntime] {

  private lazy val blocking: ExecutionContext =
    system.dispatchers.lookup("blocking-io-dispatcher")

  private lazy val runtime: IORuntime = {
    val (scheduler, shutdownScheduler) =
      IORuntime.createDefaultScheduler()

    lifecycle.addStopHook { () =>
      Future(runtime.shutdown())
    }

    val config = IORuntimeConfig()

    IORuntime(compute, blocking, scheduler, () => shutdownScheduler(), config)
  }

  override def get(): IORuntime = runtime
}
