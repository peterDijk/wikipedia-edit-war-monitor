package io.github.peterdijk.wikipediaeditwarmonitor

import org.typelevel.otel4s.trace.SpanContext

object WikiTypes {
  case class WikiEdit(
      id: String,
      title: String,
      user: String,
      bot: Boolean,
      timestamp: Long,
      comment: String,
      serverName: String
  )

  case class TracedWikiEdit(
      edit: WikiEdit,
      spanContext: SpanContext
  )

  case class WikiCountsSnapshot(
      users: Map[String, Int],
      titles: Map[String, Int],
      bots: Map[Boolean, Int]
  )
}
