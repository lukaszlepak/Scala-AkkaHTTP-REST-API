package com.example

import java.sql.Timestamp

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import scala.concurrent.duration.{Duration, SECONDS}
import scala.concurrent.Await
import scala.util.{Failure, Success}


final case class Screenings(screenings: Map[String, Seq[(Int, Timestamp)]])
final case class ScreeningSeats(screeningId: Int, screeningRoomId: Int, numberOfSeats: Int)

trait Error
case class ExceptionError(error: String) extends Error
case class NotFoundError(error: String) extends Error

import scala.concurrent.ExecutionContext.Implicits.global

object MovieRegistry {
  sealed trait Command
  final case class GetScreenings(from: Timestamp, until: Timestamp, replyTo: ActorRef[Either[Error, Screenings]]) extends Command
  final case class GetScreening(id: Int, replyTo: ActorRef[Either[Error, ScreeningSeats]]) extends Command

  import slick.jdbc.H2Profile.api._
  val db = Database.forConfig("h2mem1")
  val movies = MovieSchema.movies
  val screeningRooms = ScreeningRoomSchema.screeningRooms
  val screenings = ScreeningSchema.screenings

  def apply(): Behavior[Command] = {

    Await.result(db.run (movies.schema.create), Duration(1, SECONDS))
    Await.result(db.run (screeningRooms.schema.create), Duration(1, SECONDS))
    Await.result(db.run (screenings.schema.create), Duration(1, SECONDS))

    db.run(movies ++= exampleMovies)
    db.run(screeningRooms ++= exampleScreeningRooms)
    db.run(screenings ++= exampleScreenings)

    registry()
  }

  private def registry(): Behavior[Command] =
    Behaviors.receiveMessage {
      case GetScreenings(from, until, replyTo) =>
        val selectMoviesWithTimes = db.run {
          val joinScreeningsOnMovies = for {
            screening <- screenings if screening.time < until && screening.time > from
            movie <- movies if movie.id === screening.movieId
          } yield (screening.id, movie.title, screening.time)
          joinScreeningsOnMovies.result
        }
        selectMoviesWithTimes.onComplete {
          case Success(movies) => replyTo ! Right(Screenings(movies.groupMap(_._2)(pair => (pair._1, pair._3))))
          case Failure(exception) => replyTo ! Left(ExceptionError(exception.getMessage))
        }
        Behaviors.same
      case GetScreening(id, replyTo) =>
        val selectScreeningWithRoom = db.run {
          val joinScreeningsOnRooms = for {
            screening <- screenings if screening.id === id
            room <- screeningRooms if screening.screeningRoomId === room.id
          } yield (screening.id, room.id, room.rows*room.seatsPerRow)
          joinScreeningsOnRooms.result.headOption
        }
        selectScreeningWithRoom.onComplete {
          case Success(Some(screening)) => replyTo ! Right(ScreeningSeats(screening._1, screening._2, screening._3))
          case Success(None) => replyTo ! Left(NotFoundError("Screening not found"))
          case Failure(exception) => replyTo ! Left(ExceptionError(exception.getMessage))
        }
        Behaviors.same
    }

  private val exampleMovies = Seq(
    Movie(-1, "Secretariat"),
    Movie(-1, "Matrix 4"),
    Movie(-1, "Deadpool 3")
  )

  private val exampleScreeningRooms = Seq(
    ScreeningRoom(-1, 2, 4),
    ScreeningRoom(-1, 3, 3),
    ScreeningRoom(-1, 1, 5)
  )

  private val exampleScreenings = Seq(
    Screening(-1, 1, 1, Timestamp.valueOf("2020-12-02 16:00:00")),
    Screening(-1, 1, 2, Timestamp.valueOf("2020-12-04 21:00:00")),
    Screening(-1, 1, 3, Timestamp.valueOf("2020-12-06 18:00:00")),

    Screening(-1, 2, 1, Timestamp.valueOf("2020-12-03 19:00:00")),
    Screening(-1, 2, 2, Timestamp.valueOf("2020-12-05 17:00:00")),
    Screening(-1, 3, 3, Timestamp.valueOf("2020-12-07 20:00:00"))
  )
}
