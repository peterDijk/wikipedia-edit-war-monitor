package io.github.peterdijk.wikipediaeditwarmonitor.consumers

import cats.effect.{IO, Ref}
import fs2.Stream
import fs2.concurrent.Topic
import munit.CatsEffectSuite
import org.typelevel.otel4s.trace.{Tracer, SpanContext}
import io.github.peterdijk.wikipediaeditwarmonitor.WikiTypes.*

import scala.concurrent.duration.*

class EditWarConsumerSpec extends CatsEffectSuite:

  given Tracer[IO] = Tracer.noop[IO]

  private def mkEdit(
      title: String = "Test Article",
      titleUrl: String = "https://en.wikipedia.org/wiki/Test_Article",
      user: String = "TestUser",
      comment: String = "some edit",
      editType: EditType = EditType.edit,
      bot: Boolean = false
  ): TracedWikiEdit =
    TracedWikiEdit(
      WikiEdit(
        id = "id-1",
        title = title,
        title_url = titleUrl,
        user = user,
        bot = bot,
        timestamp = 1638360000L,
        comment = comment,
        serverName = "en.wikipedia.org",
        editType = editType
      ),
      SpanContext.invalid
    )

  // --- filterTypeEdit tests ---

  test("filterTypeEdit passes through events with EditType.edit") {
    val consumer = EditWarConsumer[IO](
      broadcastHub = null.asInstanceOf[Topic[IO, TracedWikiEdit]],
      broadcastEditWarCount = null.asInstanceOf[Topic[IO, WikiRevertsSnapshot]]
    )

    val events = List(
      mkEdit(editType = EditType.edit),
      mkEdit(editType = EditType.edit, title = "Another")
    )

    val result = Stream
      .emits(events)
      .through(consumer.filterTypeEdit)
      .compile
      .toList

    assertIO(result, events)
  }

  test("filterTypeEdit filters out non-edit events") {
    val consumer = EditWarConsumer[IO](
      broadcastHub = null.asInstanceOf[Topic[IO, TracedWikiEdit]],
      broadcastEditWarCount = null.asInstanceOf[Topic[IO, WikiRevertsSnapshot]]
    )

    val events = List(
      mkEdit(editType = EditType.categorize),
      mkEdit(editType = EditType.log),
      mkEdit(editType = EditType.`new`)
    )

    val result = Stream
      .emits(events)
      .through(consumer.filterTypeEdit)
      .compile
      .toList

    assertIO(result, List.empty)
  }

  test("filterTypeEdit keeps only edit events from mixed input") {
    val consumer = EditWarConsumer[IO](
      broadcastHub = null.asInstanceOf[Topic[IO, TracedWikiEdit]],
      broadcastEditWarCount = null.asInstanceOf[Topic[IO, WikiRevertsSnapshot]]
    )

    val editEvent = mkEdit(editType = EditType.edit, title = "EditArticle")
    val events = List(
      mkEdit(editType = EditType.categorize),
      editEvent,
      mkEdit(editType = EditType.log),
      mkEdit(editType = EditType.`new`)
    )

    val result = Stream
      .emits(events)
      .through(consumer.filterTypeEdit)
      .compile
      .toList

    assertIO(result, List(editEvent))
  }

  // --- filterReverts tests ---

  test("filterReverts passes through events with revert keywords in comment") {
    val consumer = EditWarConsumer[IO](
      broadcastHub = null.asInstanceOf[Topic[IO, TracedWikiEdit]],
      broadcastEditWarCount = null.asInstanceOf[Topic[IO, WikiRevertsSnapshot]]
    )

    val revertEvent = mkEdit(comment = "Reverted edits by vandal")

    val result = Stream
      .emit(revertEvent)
      .through(consumer.filterReverts)
      .compile
      .toList

    assertIO(result.map(_.map(_.edit.title)), List("Test Article"))
  }

  test("filterReverts filters out events without revert keywords") {
    val consumer = EditWarConsumer[IO](
      broadcastHub = null.asInstanceOf[Topic[IO, TracedWikiEdit]],
      broadcastEditWarCount = null.asInstanceOf[Topic[IO, WikiRevertsSnapshot]]
    )

    val normalEvent = mkEdit(comment = "Added a new section about history")

    val result = Stream
      .emit(normalEvent)
      .through(consumer.filterReverts)
      .compile
      .toList

    assertIO(result, List.empty)
  }

  test("filterReverts detects 'undid' keyword") {
    val consumer = EditWarConsumer[IO](
      broadcastHub = null.asInstanceOf[Topic[IO, TracedWikiEdit]],
      broadcastEditWarCount = null.asInstanceOf[Topic[IO, WikiRevertsSnapshot]]
    )

    val event = mkEdit(comment = "Undid revision 12345 by SomeUser")

    val result = Stream
      .emit(event)
      .through(consumer.filterReverts)
      .compile
      .toList

    assertIO(result.map(_.length), 1)
  }

  test("filterReverts detects 'undo' keyword") {
    val consumer = EditWarConsumer[IO](
      broadcastHub = null.asInstanceOf[Topic[IO, TracedWikiEdit]],
      broadcastEditWarCount = null.asInstanceOf[Topic[IO, WikiRevertsSnapshot]]
    )

    val event = mkEdit(comment = "undo vandalism")

    val result = Stream
      .emit(event)
      .through(consumer.filterReverts)
      .compile
      .toList

    assertIO(result.map(_.length), 1)
  }

  test("filterReverts detects 'rollback' keyword") {
    val consumer = EditWarConsumer[IO](
      broadcastHub = null.asInstanceOf[Topic[IO, TracedWikiEdit]],
      broadcastEditWarCount = null.asInstanceOf[Topic[IO, WikiRevertsSnapshot]]
    )

    val event = mkEdit(comment = "Rollback edit by 192.168.1.1")

    val result = Stream
      .emit(event)
      .through(consumer.filterReverts)
      .compile
      .toList

    assertIO(result.map(_.length), 1)
  }

  test("filterReverts detects non-English revert keywords (French: annuler)") {
    val consumer = EditWarConsumer[IO](
      broadcastHub = null.asInstanceOf[Topic[IO, TracedWikiEdit]],
      broadcastEditWarCount = null.asInstanceOf[Topic[IO, WikiRevertsSnapshot]]
    )

    val event = mkEdit(comment = "Annuler la modification précédente")

    val result = Stream
      .emit(event)
      .through(consumer.filterReverts)
      .compile
      .toList

    assertIO(result.map(_.length), 1)
  }

  test("filterReverts detects German keyword (rückgängig)") {
    val consumer = EditWarConsumer[IO](
      broadcastHub = null.asInstanceOf[Topic[IO, TracedWikiEdit]],
      broadcastEditWarCount = null.asInstanceOf[Topic[IO, WikiRevertsSnapshot]]
    )

    val event = mkEdit(comment = "Rückgängig gemacht")

    val result = Stream
      .emit(event)
      .through(consumer.filterReverts)
      .compile
      .toList

    assertIO(result.map(_.length), 1)
  }

  test("filterReverts detects Japanese keyword (差し戻し)") {
    val consumer = EditWarConsumer[IO](
      broadcastHub = null.asInstanceOf[Topic[IO, TracedWikiEdit]],
      broadcastEditWarCount = null.asInstanceOf[Topic[IO, WikiRevertsSnapshot]]
    )

    val event = mkEdit(comment = "差し戻しを実施")

    val result = Stream
      .emit(event)
      .through(consumer.filterReverts)
      .compile
      .toList

    assertIO(result.map(_.length), 1)
  }

  test("filterReverts detects Chinese keyword (回退)") {
    val consumer = EditWarConsumer[IO](
      broadcastHub = null.asInstanceOf[Topic[IO, TracedWikiEdit]],
      broadcastEditWarCount = null.asInstanceOf[Topic[IO, WikiRevertsSnapshot]]
    )

    val event = mkEdit(comment = "回退破坏性编辑")

    val result = Stream
      .emit(event)
      .through(consumer.filterReverts)
      .compile
      .toList

    assertIO(result.map(_.length), 1)
  }

  test("filterReverts handles multiple events and keeps only reverts") {
    val consumer = EditWarConsumer[IO](
      broadcastHub = null.asInstanceOf[Topic[IO, TracedWikiEdit]],
      broadcastEditWarCount = null.asInstanceOf[Topic[IO, WikiRevertsSnapshot]]
    )

    val events = List(
      mkEdit(title = "Article1", comment = "Added content"),
      mkEdit(title = "Article2", comment = "Reverted to last good version"),
      mkEdit(title = "Article3", comment = "Fixed typo"),
      mkEdit(title = "Article4", comment = "Undid revision 456 by BadUser"),
      mkEdit(title = "Article5", comment = "Normal edit here")
    )

    val result = Stream
      .emits(events)
      .through(consumer.filterReverts)
      .compile
      .toList

    assertIO(result.map(_.map(_.edit.title).sorted), List("Article2", "Article4"))
  }

  // --- incrementCounts tests ---

  test("incrementCounts increments count for a new page") {
    val consumer = EditWarConsumer[IO](
      broadcastHub = null.asInstanceOf[Topic[IO, TracedWikiEdit]],
      broadcastEditWarCount = null.asInstanceOf[Topic[IO, WikiRevertsSnapshot]]
    )

    val event = mkEdit(title = "Page A", titleUrl = "https://en.wikipedia.org/wiki/Page_A")
    val page = WikiPage("Page A", "https://en.wikipedia.org/wiki/Page_A")

    for {
      ref <- Ref.of[IO, Map[WikiPage, Int]](Map.empty)
      _ <- consumer.incrementCounts(event, ref)
      counts <- ref.get
    } yield {
      assertEquals(counts(page), 1)
    }
  }

  test("incrementCounts increments count for an existing page") {
    val consumer = EditWarConsumer[IO](
      broadcastHub = null.asInstanceOf[Topic[IO, TracedWikiEdit]],
      broadcastEditWarCount = null.asInstanceOf[Topic[IO, WikiRevertsSnapshot]]
    )

    val event = mkEdit(title = "Page B", titleUrl = "https://en.wikipedia.org/wiki/Page_B")
    val page = WikiPage("Page B", "https://en.wikipedia.org/wiki/Page_B")

    for {
      ref <- Ref.of[IO, Map[WikiPage, Int]](Map(page -> 3))
      _ <- consumer.incrementCounts(event, ref)
      counts <- ref.get
    } yield {
      assertEquals(counts(page), 4)
    }
  }

  test("incrementCounts handles multiple pages independently") {
    val consumer = EditWarConsumer[IO](
      broadcastHub = null.asInstanceOf[Topic[IO, TracedWikiEdit]],
      broadcastEditWarCount = null.asInstanceOf[Topic[IO, WikiRevertsSnapshot]]
    )

    val eventA = mkEdit(title = "Page A", titleUrl = "https://en.wikipedia.org/wiki/Page_A")
    val eventB = mkEdit(title = "Page B", titleUrl = "https://en.wikipedia.org/wiki/Page_B")
    val pageA = WikiPage("Page A", "https://en.wikipedia.org/wiki/Page_A")
    val pageB = WikiPage("Page B", "https://en.wikipedia.org/wiki/Page_B")

    for {
      ref <- Ref.of[IO, Map[WikiPage, Int]](Map.empty)
      _ <- consumer.incrementCounts(eventA, ref)
      _ <- consumer.incrementCounts(eventB, ref)
      _ <- consumer.incrementCounts(eventA, ref)
      counts <- ref.get
    } yield {
      assertEquals(counts(pageA), 2)
      assertEquals(counts(pageB), 1)
    }
  }

  // --- full pipeline tests ---

  test("full pipeline: only revert edits are counted") {
    for {
      broadcastHub <- Topic[IO, TracedWikiEdit]
      broadcastEditWar <- Topic[IO, WikiRevertsSnapshot]
      consumer = EditWarConsumer[IO](broadcastHub, broadcastEditWar)

      events = List(
        mkEdit(title = "Art1", comment = "Normal edit", editType = EditType.edit),
        mkEdit(title = "Art2", comment = "Reverted vandalism", editType = EditType.edit),
        mkEdit(title = "Art3", comment = "Categorize stuff", editType = EditType.categorize),
        mkEdit(title = "Art2", comment = "Undid revision 789", editType = EditType.edit)
      )

      ref <- Ref.of[IO, Map[WikiPage, Int]](Map.empty)

      result <- Stream
        .emits(events)
        .through(consumer.filterTypeEdit)
        .through(consumer.filterReverts)
        .parEvalMap(10)(event => consumer.incrementCounts(event, ref))
        .compile
        .drain

      counts <- ref.get
    } yield {
      val page2 = WikiPage("Art2", "https://en.wikipedia.org/wiki/Test_Article")
      assertEquals(counts.get(page2), Some(2))
      assertEquals(counts.size, 1) // Only Art2 should have counts
    }
  }

  test("full pipeline: empty stream produces no counts") {
    for {
      broadcastHub <- Topic[IO, TracedWikiEdit]
      broadcastEditWar <- Topic[IO, WikiRevertsSnapshot]
      consumer = EditWarConsumer[IO](broadcastHub, broadcastEditWar)

      ref <- Ref.of[IO, Map[WikiPage, Int]](Map.empty)

      _ <- Stream
        .empty
        .covary[IO]
        .through(consumer.filterTypeEdit)
        .through(consumer.filterReverts)
        .parEvalMap(10)(event => consumer.incrementCounts(event, ref))
        .compile
        .drain

      counts <- ref.get
    } yield {
      assertEquals(counts, Map.empty[WikiPage, Int])
    }
  }

  test("filterReverts is case-insensitive") {
    val consumer = EditWarConsumer[IO](
      broadcastHub = null.asInstanceOf[Topic[IO, TracedWikiEdit]],
      broadcastEditWarCount = null.asInstanceOf[Topic[IO, WikiRevertsSnapshot]]
    )

    val events = List(
      mkEdit(comment = "REVERT this edit"),
      mkEdit(comment = "Revert to previous version"),
      mkEdit(comment = "revert vandalism")
    )

    val result = Stream
      .emits(events)
      .through(consumer.filterReverts)
      .compile
      .toList

    assertIO(result.map(_.length), 3)
  }

