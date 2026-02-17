package io.github.peterdijk.wikipediaeditwarmonitor

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
}
