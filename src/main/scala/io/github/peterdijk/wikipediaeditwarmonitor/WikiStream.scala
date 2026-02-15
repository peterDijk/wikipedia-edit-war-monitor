package io.github.peterdijk.wikipediaeditwarmonitor

import cats.effect.Async
import org.http4s.client.Client
import org.http4s.implicits.*
import scala.concurrent.duration.*
import org.http4s.ServerSentEvent
import fs2.concurrent.Topic

trait WikiStream[F[_]]:
  def start: F[Unit]

object WikiStream:
  def apply[F[_]](implicit evidence: WikiStream[F]): WikiStream[F] = evidence

  def impl[F[_]: Async](
      httpClient: Client[F],
      broadcastHub: Topic[F, ServerSentEvent]
  ): WikiStream[F] =
    new WikiStream[F]:
      def start: F[Unit] =
        val sseClient = SseClient(httpClient, None, 1.second)
        sseClient
          .stream(
            uri"https://stream.wikimedia.org/v2/stream/recentchange"
          )
          .through(broadcastHub.publish)
          .compile
          .drain
