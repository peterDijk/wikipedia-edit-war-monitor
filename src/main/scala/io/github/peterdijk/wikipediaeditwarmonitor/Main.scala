package io.github.peterdijk.wikipediaeditwarmonitor

import cats.effect.{IO, IOApp}

object Main extends IOApp.Simple:
  val run = WikipediaeditwarmonitorServer.run[IO]
