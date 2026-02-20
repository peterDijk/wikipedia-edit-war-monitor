package io.github.peterdijk.wikipediaeditwarmonitor

import io.circe.Decoder.Result
import io.circe.{Decoder, Encoder, HCursor, Json}
import io.github.peterdijk.wikipediaeditwarmonitor.WikiTypes.{WikiEdit, WikiCountsSnapshot}

object WikiDecoder {
  given wikiEditDecoder: Decoder[WikiEdit] = new Decoder[WikiEdit] {
    override def apply(c: HCursor): Result[WikiEdit] =
      for {
        id <- c.downField("meta").downField("id").as[String]
        title <- c.downField("title").as[String]
        title_url <- c.downField("title_url").as[String]
        user <- c.downField("user").as[String]
        bot <- c.downField("bot").as[Boolean]
        timestamp <- c.downField("timestamp").as[Long]
        comment <- c.downField("comment").as[String]
        serverName <- c.downField("server_name").as[String]
      } yield {
        WikiEdit(id, title, title_url, user, bot, timestamp, comment, serverName)
      }
  }

  given wikiCountsSnapshotEncoder: Encoder[WikiCountsSnapshot] = new Encoder[WikiCountsSnapshot] {
    override def apply(s: WikiCountsSnapshot): Json = {
      def objFromIntMap(m: Map[String, Int]): Json =
        Json.obj(m.toList.map { case (k, v) => (k, Json.fromInt(v)) }*)

      def objFromPage(title_url: String, count: Int): Json =
        Json.obj(
          "count" -> Json.fromInt(count),
          "title_url" -> Json.fromString(title_url)
        )

      def objFromPageMap(m: Map[(String, String), Int]): Json =
        Json.obj(m.toList.map {
          case ((title, title_url), count) =>
            (title, objFromPage(title_url, count))
          }*)

      val botsJson = Json.obj(s.bots.toList.map { case (k, v) => (k.toString, Json.fromInt(v)) }*)

      Json.obj(
        "users" -> objFromIntMap(s.users),
        "titles" -> objFromPageMap(s.titles),
        "bots" -> botsJson
      )
    }
  }
}
