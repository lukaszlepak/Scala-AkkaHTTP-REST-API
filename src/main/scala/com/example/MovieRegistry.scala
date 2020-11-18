package com.example

import java.sql.Timestamp

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import scala.concurrent.duration.{Duration, SECONDS}
import scala.concurrent.Await
import scala.util.{Failure, Success}

final case class Movie(id: Int, title: String, time: Timestamp)
final case class Movies(movies: Seq[Movie])
final case class Error(error: String)

import scala.concurrent.ExecutionContext.Implicits.global

object MovieRegistry {
  sealed trait Command
  final case class GetMovies(from: Timestamp, until: Timestamp, replyTo: ActorRef[Either[Error, Movies]]) extends Command

  import slick.jdbc.H2Profile.api._

  val db = Database.forConfig("h2mem1")

  val movies = MovieSchema.movies

  def apply(): Behavior[Command] = {

    Await.result(db.run (movies.schema.create), Duration(1, SECONDS))

    db.run(movies ++= exampleMovies)

    registry()
  }

  private def registry(): Behavior[Command] =
    Behaviors.receiveMessage {
      case GetMovies(from, until, replyTo) =>
        val selectAll = db.run {
          movies
            .filter(movie => movie.timestamp >= from && movie.timestamp <= until )
            .sortBy(movie => (movie.title, movie.timestamp))
            .result
        }
        selectAll.onComplete {
          case Success(movies) => replyTo ! Right(Movies(movies))
          case Failure(exception) => replyTo ! Left(Error(exception.getMessage))
        }
        Behaviors.same
    }

  private val exampleMovies = Seq(
    Movie(-1, "Secretariat", Timestamp.valueOf("2020-12-04 16:00:00")),
    Movie(-1, "Matrix 4", Timestamp.valueOf("2020-12-05 21:00:00")),
    Movie(-1, "Deadpool 3", Timestamp.valueOf("2020-12-06 18:00:00")),
    Movie(-1, "Secretariat", Timestamp.valueOf("2020-12-03 18:00:00"))
  )
}
