package io.github.peterdijk.wikipediaeditwarmonitor.consumers

import cats.effect.{IO, Ref, Sync}
import fs2.concurrent.Topic
import io.github.peterdijk.wikipediaeditwarmonitor.WikiTypes.{TracedWikiEdit, WikiEdit}
import munit.CatsEffectSuite
import org.typelevel.otel4s.trace.SpanContext

class StatsConsumerTest extends CatsEffectSuite {

  private def createTracedWikiEdit(user: String, title: String, bot: Boolean): TracedWikiEdit = {
    TracedWikiEdit(
      WikiEdit(
        id = "1",
        title = title,
        user = user,
        bot = bot,
        timestamp = 123456789L,
        comment = "test comment",
        serverName = "en.wikipedia.org"
      ),
      SpanContext.invalid
    )
  }

  test("incrementCounts should correctly update refs") {
    for {
      topic         <- Topic[IO, TracedWikiEdit]
      consumer      = StatsConsumer[IO](topic)
      userCountRef  <- Ref.of[IO, Map[String, Int]](Map.empty)
      titleCountRef <- Ref.of[IO, Map[String, Int]](Map.empty)
      botCountRef   <- Ref.of[IO, Map[Boolean, Int]](Map.empty)

      event1 = createTracedWikiEdit("user1", "title1", bot = false)
      event2 = createTracedWikiEdit("user1", "title2", bot = true)
      event3 = createTracedWikiEdit("user2", "title1", bot = false)

      _ <- consumer.incrementCounts(event1, userCountRef, titleCountRef, botCountRef)
      _ <- consumer.incrementCounts(event2, userCountRef, titleCountRef, botCountRef)
      _ <- consumer.incrementCounts(event3, userCountRef, titleCountRef, botCountRef)

      userCounts  <- userCountRef.get
      titleCounts <- titleCountRef.get
      botCounts   <- botCountRef.get

      _ = assertEquals(userCounts, Map("user1" -> 2, "user2" -> 1))
      _ = assertEquals(titleCounts, Map("title1" -> 2, "title2" -> 1))
      _ = assertEquals(botCounts, Map(false -> 2, true -> 1))
    } yield ()
  }

  test("generateHtml should replace __REPLACE_ME__ with stats") {
    for {
      topic    <- Topic[IO, TracedWikiEdit]
      consumer = StatsConsumer[IO](topic)
      template = "<html><body>__REPLACE_ME__</body></html>"
      userCounts = Map("user1" -> 10)
      titleCounts = Map("title1" -> 5)
      botCounts = Map(false -> 8, true -> 2)

      html = consumer.generateHtml(template, userCounts, titleCounts, botCounts)

      _ = assert(html.contains("<html><body>"))
      _ = assert(html.contains("user1: 10"))
      _ = assert(html.contains("title1: 5"))
      _ = assert(html.contains("Human: 8"))
      _ = assert(html.contains("Bot: 2"))
      _ = assert(html.contains("</body></html>"))
      _ = assert(!html.contains("__REPLACE_ME__"))
    } yield ()
  }

  test("generateHtml should only show top 20") {
    for {
      topic    <- Topic[IO, TracedWikiEdit]
      consumer = StatsConsumer[IO](topic)
      template = "__REPLACE_ME__"
      userCounts = (1 to 30).map(i => s"user$i" -> i).toMap

      html = consumer.generateHtml(template, userCounts, Map.empty, Map.empty)

      // It should contain user30 (count 30) down to user11 (count 11)
      _ = assert(html.contains("user30: 30"))
      _ = assert(html.contains("user11: 11"))
      // It should NOT contain user10
      _ = assert(!html.contains("user10: 10"))
    } yield ()
  }
}
