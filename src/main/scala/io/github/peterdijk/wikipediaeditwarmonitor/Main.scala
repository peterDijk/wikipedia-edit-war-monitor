package io.github.peterdijk.wikipediaeditwarmonitor

import cats.effect.{ExitCode, IO, IOApp}
import fs2.io.net.Network

object Main extends IOApp:
  def run(args: List[String]): IO[ExitCode] =
    WikipediaEditWarMonitorServer.run[IO].as(ExitCode.Success)
