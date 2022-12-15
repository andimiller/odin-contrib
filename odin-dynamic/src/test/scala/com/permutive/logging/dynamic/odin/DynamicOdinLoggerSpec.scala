/*
 * Copyright 2022 Permutive
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

package com.permutive.logging.dynamic.odin

import cats.effect.unsafe.IORuntime
import cats.effect.{IO, Resource}
import com.permutive.logging.odin.testing.OdinRefLogger
import io.odin.{Level, LoggerMessage}
import io.odin.formatter.Formatter
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.scalacheck.effect.PropF

import scala.collection.immutable.Queue
import scala.concurrent.duration._

class DynamicOdinLoggerSpec extends CatsEffectSuite with ScalaCheckEffectSuite {

  implicit val runtime: IORuntime = IORuntime.global

  test("record a message") {
    PropF.forAllF { (message: String) =>
      val messages = runTest(_.info(message))

      messages.map(_.map(_.message.value).toList).assertEquals(List(message))
    }
  }

  test("update global log level") {
    PropF.forAllF { (message1: String, message2: String) =>
      val messages = runTest { logger =>
        logger.info(message1) >> IO.sleep(10.millis) >> logger.update(
          DynamicOdinConsoleLogger.RuntimeConfig(Level.Warn)
        ) >> logger.info(
          message2
        )
      }
      messages.map(_.map(_.message.value).toList).assertEquals(List(message1))
    }
  }

  test("update enclosure log level") {
    PropF.forAllF { (message1: String, message2: String, message3: String) =>
      val messages = runTest { logger =>
        logger.info(message1) >> IO.sleep(10.millis) >> logger.update(
          DynamicOdinConsoleLogger.RuntimeConfig(
            Level.Info,
            Map("com.permutive" -> Level.Warn)
          )
        ) >> logger.info(
          message2
        ) >> logger.warn(message3)
      }
      messages
        .map(_.map(_.message.value).toList)
        .assertEquals(List(message1, message3))
    }
  }

  def runTest(
      useLogger: DynamicOdinConsoleLogger[IO] => IO[Unit]
  ): IO[Queue[LoggerMessage]] = (for {
    testLogger <- Resource.eval(OdinRefLogger.create[IO]())
    dynamic <- DynamicOdinConsoleLogger.create[IO](
      DynamicOdinConsoleLogger
        .Config(formatter = Formatter.default, asyncTimeWindow = 0.nanos),
      DynamicOdinConsoleLogger.RuntimeConfig(Level.Info)
    )(config => testLogger.withMinimalLevel(config.minLevel))
    _ <- Resource.eval(useLogger(dynamic))
  } yield testLogger)
    .use { testLogger =>
      IO.sleep(50.millis) >> testLogger.getMessages
    }
}
