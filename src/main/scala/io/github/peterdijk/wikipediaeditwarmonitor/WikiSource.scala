package io.github.peterdijk.wikipediaeditwarmonitor

import cats.effect.{Async, Concurrent, Ref}
import cats.implicits.*

import org.http4s.*
import org.http4s.implicits.*
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.Method.*

trait WikiSource[F[_]]:
  def streamEvents: F[Unit]

object WikiSource:
  def apply[F[_]](implicit ev: WikiSource[F]): WikiSource[F] = ev

  private final case class WikiStreamError(e: Throwable) extends RuntimeException

  /*
  In Scala 3, when you write new WikiSource[F]:, it's creating an anonymous class that
  implements the trait. This is valid syntax - the colon (:) after the trait name
  indicates that the implementation follows using indentation-based syntax.
   */
  def impl[F[_]: Async](client: Client[F]): WikiSource[F] = new WikiSource[F]:
    val dsl: Http4sClientDsl[F] = new Http4sClientDsl[F] {}
    import dsl.*

    def processResponseLines(response: Response[F]): fs2.Stream[F, String] =
      response.body
        .through(fs2.text.utf8.decode)
        .through(fs2.text.lines)
        .filter(_.nonEmpty) // Filter out empty lines
        .filter(!_.startsWith(":")) // Filter out SSE comment lines (heartbeats)
        .filter(_.startsWith("data: ")) // Only process data lines
        .map(_.stripPrefix("data: "))

    def streamEvents: F[Unit] =
      val request = GET(uri"https://stream.wikimedia.org/v2/stream/recentchange")

      for {
        startTime <- Async[F].monotonic
        counterRef <- Ref[F].of(0)
        _ <- client.stream(request).flatMap { response =>
          processResponseLines(response)
            .evalMap { line =>
              for {
                count <- counterRef.updateAndGet(_ + 1)
                currentTime <- Async[F].monotonic
                elapsedTime = currentTime - startTime
                _ <- Concurrent[F].delay(println(s"Event #$count (elapsed: ${elapsedTime.toSeconds}s) | Average rate: ${count.toDouble / elapsedTime.toSeconds} events/s | Line: ${line.take(100)}"))
              } yield ()
            }
        }.compile.drain
          .adaptError { case t => WikiStreamError(t) }
      } yield ()
