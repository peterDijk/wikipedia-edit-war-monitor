package io.github.peterdijk.wikipediaeditwarmonitor

import cats.effect.{Async, Ref}
import fs2.concurrent.Topic
import io.github.peterdijk.wikipediaeditwarmonitor.WikiTypes.WikiEdit
import cats.syntax.all.*
import scala.concurrent.duration.*
import fs2.io.file.Files
import fs2.Stream

final case class WikiPageCounter[F[_]: Async: cats.Parallel: Files](
    broadcastHub: Topic[F, WikiEdit]
):

  def countEdits: F[Unit] = {
    val stream = broadcastHub.subscribe(1000)

    def incrementUser(user: String, ref: Ref[F, Map[String, Int]]): F[Unit] =
      ref.updateAndGet { counts =>
        val newCount = counts.getOrElse(user, 0) + 1
        counts + (user -> newCount)
      }.flatMap(m => Async[F].delay(println(s"Current user counts: $m")))

    def incrementTitle(title: String, ref: Ref[F, Map[String, Int]]): F[Unit] =
      ref.updateAndGet { counts =>
        val newCount = counts.getOrElse(title, 0) + 1
        counts + (title -> newCount)
      }.flatMap(m => Async[F].delay(println(s"Current page counts: $m")))

    def incrementBot(bot: Boolean, ref: Ref[F, Map[Boolean, Int]]): F[Unit] =
      ref.updateAndGet { counts =>
        val newCount = counts.getOrElse(bot, 0) + 1
        counts + (bot -> newCount)
      }.flatMap(m => Async[F].delay(println(s"Current bot counts: $m")))
      
    for {
      userCountRef <- Ref.of[F, Map[String, Int]](Map.empty)
      titleCountRef <- Ref.of[F, Map[String, Int]](Map.empty)
      botCountRef <- Ref.of[F, Map[Boolean, Int]](Map.empty)

      htmlWriter = Stream
        .awakeEvery[F](10.seconds)
        .evalMap { _ =>
          for {
            userCounts <- userCountRef.get
            titleCounts <- titleCountRef.get
            botCounts <- botCountRef.get
            top20Users = userCounts.toList.sortBy(-_._2).take(20)
            top20Titles = titleCounts.toList.sortBy(-_._2).take(20)
            botCount = botCounts.getOrElse(true, 0)
            humanCount = botCounts.getOrElse(false, 0)
            html =
              s"""<html>
                 |<body>
                 |<h1>Top 20 User Edit Counts</h1>
                 |<ul>
                 |${top20Users.map((user, count) => s"<li>$user: $count</li>").mkString("\n")}
                 |</ul>
                 |<h1>Top 20 Page Title Edit Counts</h1>
                 |<ul>
                 |${top20Titles.map((title, count) => s"<li>$title: $count</li>").mkString("\n")}
                 |</ul>
                 |<h1>Bot count ratio Human vs Bot</h1>
                 |<ul>
                 |<li>Human: $humanCount</li>
                 |<li>Bot: $botCount</li>
                 |</ul>
                 |</body>
                 |</html>""".stripMargin
            _ <- Stream
              .emit(html)
              .through(fs2.text.utf8.encode)
              .through(Files[F].writeAll(fs2.io.file.Path("stats.html")))
              .compile
              .drain
          } yield ()
        }

      counter = stream
        .parEvalMap(10) { event =>
          (
            incrementUser(event.user, userCountRef),
            incrementTitle(event.title, titleCountRef),
            incrementBot(event.bot, botCountRef)
          ).parTupled.void
        }

      _ <- Stream(counter, htmlWriter.drain).parJoinUnbounded.compile.drain
    } yield ()
  }