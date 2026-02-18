package io.github.peterdijk.wikipediaeditwarmonitor.consumers
import cats.effect.{Async, Ref}
import com.typesafe.config.ConfigFactory
import fs2.concurrent.Topic
import io.github.peterdijk.wikipediaeditwarmonitor.WikiTypes.TracedWikiEdit
import cats.syntax.all.*

import scala.concurrent.duration.*
import fs2.io.file.Files
import fs2.Stream

final case class StatsConsumer[F[_]: Async: cats.Parallel: Files](
    broadcastHub: Topic[F, TracedWikiEdit]
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

      htmlWriter = Stream
        .awakeEvery[F](10.seconds)
        .evalMap { _ =>
          for {
            outDir      <- loadOutDir
            userCounts  <- userCountRef.get
            titleCounts <- titleCountRef.get
            botCounts   <- botCountRef.get

            // Read the template file from resources
            template <- Async[F].delay {
              val is = getClass.getClassLoader.getResourceAsStream("templates/wiki.template.html")
              if (is != null) {
                scala.io.Source.fromInputStream(is).mkString
              } else {
                "__REPLACE_ME__" // Fallback if template is missing
              }
            }

            finalHtml = generateHtml(template, userCounts, titleCounts, botCounts)
            _         <- writeStats(outDir, finalHtml)
          } yield ()
        }

      counter = stream
        .parEvalMap(10) { event =>
          incrementCounts(event, userCountRef, titleCountRef, botCountRef)
        }

      _ <- Stream(counter, htmlWriter.drain).parJoinUnbounded.compile.drain
    } yield ()
  }

  def incrementCounts(
      event: TracedWikiEdit,
      userCountRef: Ref[F, Map[String, Int]],
      titleCountRef: Ref[F, Map[String, Int]],
      botCountRef: Ref[F, Map[Boolean, Int]]
  ): F[Unit] =
    (
      incrementCount(event.edit.user, userCountRef, "user"),
      incrementCount(event.edit.title, titleCountRef, "page"),
      incrementCount(event.edit.bot, botCountRef, "bot")
    ).parTupled.void

  private def incrementCount[K](key: K, ref: Ref[F, Map[K, Int]], label: String): F[Unit] =
    ref.updateAndGet { counts =>
      val newCount = counts.getOrElse(key, 0) + 1
      counts + (key -> newCount)
    }.flatMap(m => Async[F].delay(println(s"Current $label counts: $m")))

  def generateHtml(
      template: String,
      userCounts: Map[String, Int],
      titleCounts: Map[String, Int],
      botCounts: Map[Boolean, Int]
  ): String = {
    val top20Users  = userCounts.toList.sortBy(-_._2).take(20)
    val top20Titles = titleCounts.toList.sortBy(-_._2).take(20)
    val botCount    = botCounts.getOrElse(true, 0)
    val humanCount  = botCounts.getOrElse(false, 0)

    val html =
      s"""<div class='wikirow'>
         |<div class='wikicolumn'>
         |<h2>Top 20 User Edit Counts</h1>
         |<ul>
         |${top20Users.map((user, count) => s"<li>$user: $count</li>").mkString("\n")}
         |</ul>
         |</div>
         |<div class='wikicolumn'>
         |<h2>Top 20 Page Title Edit Counts</h1>
         |<ul>
         |${top20Titles.map((title, count) => s"<li>$title: $count</li>").mkString("\n")}
         |</ul>
         |</div>
         |<div class='wikicolumn'>
         |<h2>Bot count ratio Human vs Bot</h1>
         |<ul>
         |<li>Human: $humanCount</li>
         |<li>Bot: $botCount</li>
         |</ul>
         |</div>
         |</div>""".stripMargin

    template.replace("__REPLACE_ME__", html)
  }

  private def writeStats(outDir: String, html: String): F[Unit] =
    for {
      _ <- Files[F].createDirectories(fs2.io.file.Path(outDir))
      _ <- Stream
        .emit(html)
        .through(fs2.text.utf8.encode)
        .through(Files[F].writeAll(fs2.io.file.Path(outDir).resolve("stats.html")))
        .compile
        .drain
    } yield ()