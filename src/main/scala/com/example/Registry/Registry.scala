package com.example.Registry

import java.sql.Timestamp

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.pattern.StatusReply
import com.example.Service.Reservation
import pureconfig.{ConfigReader, ConfigSource}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, SECONDS}
import scala.util.{Failure, Success}

import pureconfig.generic.auto._

final case class ScreeningTitleDB(movieTitle: String, screeningId: Int, screeningTime: Timestamp)
final case class ScreeningTitlesDB(screeningTitles: Seq[ScreeningTitleDB])

final case class ScreeningScreeningRoomDB(screeningId: Int, screeningRoomId: Int, rows: Int, seatsPerRow: Int)

final case class ReservationsScreeningDB(reservations: Seq[(Int, Int)])

final case class ScreeningTime(time: Timestamp)

final case class ReservationCreatedDB(insertedRows: Int)

object Registry {
  sealed trait RegistryCommand
  final case class GetScreeningTitlesDB(from: Timestamp, until: Timestamp, replyTo: ActorRef[StatusReply[ScreeningTitlesDB]]) extends RegistryCommand

  final case class GetScreeningScreeningRoomDB(id: Int, replyTo: ActorRef[StatusReply[Option[ScreeningScreeningRoomDB]]]) extends RegistryCommand

  final case class CreateReservationDB(reservation: Reservation, replyTo: ActorRef[StatusReply[ReservationCreatedDB]]) extends RegistryCommand

  final case class GetScreeningTimeDB(id: Int, replyTo: ActorRef[StatusReply[Option[ScreeningTime]]]) extends RegistryCommand

  final case class GetReservationsScreeningDB(screeningId: Int, replyTo: ActorRef[StatusReply[ReservationsScreeningDB]]) extends RegistryCommand

  import slick.jdbc.H2Profile.api._
  val db = Database.forConfig("h2mem1")

  val movies = MovieSchema.movies
  val screeningRooms = ScreeningRoomSchema.screeningRooms
  val screenings = ScreeningSchema.screenings
  val reservations = ReservationSchema.reservations

  def apply(): Behavior[RegistryCommand] = {

    implicit val timestampReader = ConfigReader[String].map(s => Timestamp.valueOf(s))

    Await.result(
      db.run {(
          movies.schema ++
          screeningRooms.schema ++
          screenings.schema ++
          reservations.schema
        ).create
      }, Duration(1, SECONDS))

    db.run(movies ++= ConfigSource.default.loadOrThrow[MoviesDB].movies)
    db.run(screeningRooms ++= ConfigSource.default.loadOrThrow[ScreeningRoomsDB].rooms)
    db.run(screenings ++= ConfigSource.default.loadOrThrow[ScreeningsDB].screenings)

    registry()
  }

  private def registry(): Behavior[RegistryCommand] =
    Behaviors.receiveMessage {

      case GetScreeningTitlesDB(from, until, replyTo) =>
        db.run {
          (for {
            scr <- screenings                     if scr.time < until && scr.time > from
            movie <- movies                       if movie.id === scr.movieId
          } yield (movie.title, scr.id, scr.time))
            .result
        }.onComplete {
          case Success(movies) =>                 replyTo ! StatusReply.Success(ScreeningTitlesDB(movies.map(movie => ScreeningTitleDB(movie._1, movie._2, movie._3))))
          case Failure(exception) =>              replyTo ! StatusReply.Error(exception.getMessage)
        }
        Behaviors.same

      case GetScreeningScreeningRoomDB(id, replyTo) =>
        db.run {
          (for {
            scr <- screenings                     if scr.id === id
            room <- screeningRooms                if scr.screeningRoomId === room.id
          } yield (scr.id, room.id, room.rows, room.seatsPerRow))
            .result
            .headOption
        }.onComplete {
          case Success(Some(scr)) =>              replyTo ! StatusReply.Success(Some(ScreeningScreeningRoomDB(scr._1, scr._2, scr._3, scr._4)))
          case Success(None) =>                   replyTo ! StatusReply.Success(None)
          case Failure(exception) =>              replyTo ! StatusReply.Error(exception.getMessage)
        }
        Behaviors.same
        
      case CreateReservationDB(reservation, replyTo) => {
        db.run {
          reservations ++= reservation.seats.map(seat => ReservationDB(-1, reservation.screeningId, reservation.name, seat._1, seat._2))
        }.onComplete {
          case Success(Some(value)) =>            replyTo ! StatusReply.Success(ReservationCreatedDB(value))
          case Failure(exception) =>              replyTo ! StatusReply.Error(exception.getMessage)
        }
        Behaviors.same
      }

      case GetReservationsScreeningDB(screeningId, replyTo) =>
        db.run {
          (for {
            reservation <- reservations if reservation.screeningId === screeningId
          } yield (reservation.row, reservation.seatInRow))
            .result
        }.onComplete {
          case Success(reservations) =>           replyTo ! StatusReply.Success(ReservationsScreeningDB(reservations.map(reservation => (reservation._1, reservation._2))))
          case Failure(exception) =>              replyTo ! StatusReply.Error(exception.getMessage)
        }
        Behaviors.same

      case GetScreeningTimeDB(id, replyTo) =>
        db.run{
          (for {
            screening <- screenings if screening.id === id
          } yield screening.time)
            .result
            .headOption
        }.onComplete {
          case Success(Some(time)) =>             replyTo ! StatusReply.Success(Some(ScreeningTime(time)))
          case Success(None) =>                   replyTo ! StatusReply.Success(None)
          case Failure(exception) =>              replyTo ! StatusReply.Error(exception.getMessage)
        }
        Behaviors.same
    }
}
