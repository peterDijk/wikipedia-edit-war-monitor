package io.github.peterdijk.wikipediaeditwarmonitor.consumers
import cats.effect.{Async, Ref}
import com.typesafe.config.ConfigFactory
import fs2.concurrent.Topic
import io.github.peterdijk.wikipediaeditwarmonitor.WikiTypes.{EditType, WikiRevertsSnapshot, TracedWikiEdit, WikiPage}
import cats.syntax.all.*

import scala.util.matching.Regex

import scala.concurrent.duration.*
import fs2.io.file.Files
import fs2.Stream

final case class EditWarConsumer[F[_]: Async](
    broadcastHub: Topic[F, TracedWikiEdit],
    broadcastEditWarCount: Topic[F, WikiRevertsSnapshot]
):

  def countEditWars: F[Unit] = {
    val stream = broadcastHub.subscribe(1000)

    // Load output directory from configuration (application.conf: app.outDir)
    val loadOutDir: F[String] = Async[F].delay {
      val conf = ConfigFactory.load()
      if (conf.hasPath("app.outDir")) conf.getString("app.outDir")
      else ""
    }

    for {
      titleCountRef <- Ref.of[F, Map[WikiPage, Int]](Map.empty)

      producer = Stream
        .awakeEvery[F](2.seconds)
        .evalMap { _ =>
          for {
            titleCounts <- titleCountRef.get
          } yield WikiRevertsSnapshot(titleCounts)
        }
        .through(broadcastEditWarCount.publish)

      counter = stream
        .through(filterTypeEdit)
        .through(filterReverts)
        .parEvalMap(10) { event =>
          incrementCounts(event, titleCountRef)
        }
      _ <- Stream(counter, producer.drain).parJoinUnbounded.compile.drain
    } yield ()
  }

  def incrementCounts(
      event: TracedWikiEdit,
      titleCountRef: Ref[F, Map[WikiPage, Int]]
  ): F[Unit] = {
    val suffix = if (event.edit.bot) then "Bot" else "Human"
    (
      incrementTitlesCount(WikiPage(event.edit.title, event.edit.title_url), titleCountRef, "page")
    ).void
  }

  private def incrementTitlesCount[K](key: WikiPage, ref: Ref[F, Map[WikiPage, Int]], label: String): F[Unit] =
    ref.updateAndGet { counts =>
      val newCount = counts.getOrElse(key, 0) + 1
      counts + (key -> newCount)
    }.void

  private def filterTypeEdit: fs2.Pipe[F, TracedWikiEdit, TracedWikiEdit] =
    _.filter(_.edit.editType == EditType.edit)

  def filterReverts[F[_]: Async]: fs2.Pipe[F, TracedWikiEdit, TracedWikiEdit] = {
    val revertKeywords = List(
      // English
      "revert", "undid", "undo", "rollback", "rv",
      // Spanish
      "deshacer",
      // French
      "annuler", "révocation", "rétablir", "annulé", "révoqué",
      // German
      "rückgängig", "zurücksetzen", "rückgängigmachen",
      // Italian
      "annullare", "ripristinare", "annullato", "ripristinato",
      // Portuguese
      "desfazer", "revertido", "desfeito",
      // Dutch
      "ongedaan", "herstellen", "terugdraaien", "ongedaan maken",
      // Russian
      "отменить", "откатить", "отмена", "откат",
      // Polish
      "cofnij", "przywróć", "cofnięcia", "przywrócenie",
      // Swedish
      "återställ", "ångra", "återställning",
      // Norwegian
      "tilbakestill", "angre", "tilbakestilling",
      // Danish
      "fortryd", "gendanne", "tilbagerulning",
      // Finnish
      "kumoa", "palauta", "kumoaminen", "palautus",
      // Japanese
      "差し戻し", "取り消し", "巻き戻し",
      // Chinese (Simplified & Traditional)
      "回退", "撤销", "恢复", "復原", "撤銷",
      // Arabic
      "تراجع", "إلغاء", "استرجاع",
      // Hebrew
      "ביטול", "החזרה", "שחזור",
      // Korean
      "되돌리기", "취소", "복구",
      // Turkish
      "geri al", "iptal", "geri alma"
    ).mkString("|")

    _.filter(x => revertKeywords.r.findFirstMatchIn(x.edit.comment.toLowerCase).isDefined)
  }

