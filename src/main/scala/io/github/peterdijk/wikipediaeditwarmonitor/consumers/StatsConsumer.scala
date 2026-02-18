package io.github.peterdijk.wikipediaeditwarmonitor.consumers
import cats.effect.{Async, Ref}
import com.typesafe.config.ConfigFactory
import fs2.concurrent.Topic
import io.github.peterdijk.wikipediaeditwarmonitor.WikiTypes.{WikiCountsSnapshot, TracedWikiEdit}
import cats.syntax.all.*

import scala.concurrent.duration.*
import fs2.io.file.Files
import fs2.Stream

final case class StatsConsumer[F[_]: Async: cats.Parallel: Files](
    broadcastHub: Topic[F, TracedWikiEdit],
    broadcastHubWikiCounts: Topic[F, WikiCountsSnapshot]
):

  def countEdits: F[Unit] = {
    val stream = broadcastHub.subscribe(1000)

    // Load output directory from configuration (application.conf: app.outDir)
    val loadOutDir: F[String] = Async[F].delay {
      val conf = ConfigFactory.load()
      if (conf.hasPath("app.outDir")) conf.getString("app.outDir")
      else ""
    }

    for {
      userCountRef  <- Ref.of[F, Map[String, Int]](Map.empty)
      titleCountRef <- Ref.of[F, Map[String, Int]](Map.empty)
      botCountRef   <- Ref.of[F, Map[Boolean, Int]](Map.empty)

      producer = Stream
        .awakeEvery[F](5.seconds)
        .evalMap { _ =>
          for {
            userCounts  <- userCountRef.get
            titleCounts <- titleCountRef.get
            botCounts   <- botCountRef.get
          } yield WikiCountsSnapshot(userCounts, titleCounts, botCounts)
        }
        .through(broadcastHubWikiCounts.publish)

      counter = stream
        .parEvalMap(10) { event =>
          incrementCounts(event, userCountRef, titleCountRef, botCountRef)
        }
      _ <- Stream(counter, producer.drain).parJoinUnbounded.compile.drain
    } yield ()
  }

  def incrementCounts(
      event: TracedWikiEdit,
      userCountRef: Ref[F, Map[String, Int]],
      titleCountRef: Ref[F, Map[String, Int]],
      botCountRef: Ref[F, Map[Boolean, Int]]
  ): F[Unit] = {
    val suffix = if (event.edit.bot) then "Bot" else "Human"
    (
      incrementCount(s"${event.edit.user} ($suffix)", userCountRef, "user"),
      incrementCount(event.edit.title, titleCountRef, "page"),
      incrementCount(event.edit.bot, botCountRef, "bot")
    ).parTupled.void
  }

  private def incrementCount[K](key: K, ref: Ref[F, Map[K, Int]], label: String): F[Unit] =
    ref.updateAndGet { counts =>
      val newCount = counts.getOrElse(key, 0) + 1
      counts + (key -> newCount)
    }.flatMap(m => Async[F].delay(println(s"Current $label counts: $m")))