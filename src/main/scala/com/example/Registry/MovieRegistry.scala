package com.example.Registry

import java.sql.Timestamp

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.pattern.StatusReply
import com.example.Service.Reservation
import com.example._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, SECONDS}
import scala.util.{Failure, Success}


final case class ScreeningTitleDB(movieTitle: String, screeningId: Int, screeningTime: Timestamp)
final case class ScreeningTitlesDB(screeningTitles: Seq[ScreeningTitleDB])

final case class ScreeningScreeningRoomDB(screeningId: Int, screeningRoomId: Int, rows: Int, seatsPerRow: Int)

final case class ReservationsScreeningDB(reservations: Seq[(Int, Int)])

final case class ReservationCreatedDB(insertedRows: Int)

object MovieRegistry {
  sealed trait RegistryCommand
  final case class GetScreeningTitlesDB(from: Timestamp, until: Timestamp, replyTo: ActorRef[StatusReply[ScreeningTitlesDB]]) extends RegistryCommand

  final case class GetScreeningScreeningRoomDB(id: Int, replyTo: ActorRef[StatusReply[Option[ScreeningScreeningRoomDB]]]) extends RegistryCommand

  final case class CreateReservationDB(reservation: Reservation, replyTo: ActorRef[StatusReply[ReservationCreatedDB]]) extends RegistryCommand

  final case class GetReservationsScreeningDB(screeningId: Int, replyTo: ActorRef[StatusReply[ReservationsScreeningDB]]) extends RegistryCommand

  import slick.jdbc.H2Profile.api._
  val db = Database.forConfig("h2mem1")

  val movies = MovieSchema.movies
  val screeningRooms = ScreeningRoomSchema.screeningRooms
  val screenings = ScreeningSchema.screenings
  val reservations = ReservationSchema.reservations

  def apply(): Behavior[RegistryCommand] = {

    Await.result( db.run {
      (
        movies.schema ++
        screeningRooms.schema ++
        screenings.schema ++
        reservations.schema
        ).create
    }, Duration(1, SECONDS))

    db.run(movies ++= exampleMovies)
    db.run(screeningRooms ++= exampleScreeningRooms)
    db.run(screenings ++= exampleScreenings)

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
          {
            reservations
              .filter( _.screeningId === reservation.screeningId )
              .map(r => (r.row, r.seatInRow))
              .result
              .flatMap { reservedSeats =>
                if(reservedSeats.exists(record => reservation.seats.contains(record)))
                  DBIO.successful(Some(0))
                else
                  reservations ++= reservation.seats.map(seat => ReservationDB(-1, reservation.screeningId, reservation.name, seat._1, seat._2))
              }
          }.transactionally
        }.onComplete {
          case Success(Some(value)) =>            replyTo ! StatusReply.Success(Registry.ReservationCreatedDB(value))
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
    }

  private val exampleMovies = Seq(
    MovieDB(-1, "Secretariat"),
    MovieDB(-1, "Matrix 4"),
    MovieDB(-1, "Deadpool 3")
  )

  private val exampleScreeningRooms = Seq(
    ScreeningRoomDB(-1, 2, 4),
    ScreeningRoomDB(-1, 3, 3),
    ScreeningRoomDB(-1, 1, 5)
  )

  private val exampleScreenings = Seq(
    ScreeningDB(-1, 1, 1, Timestamp.valueOf("2020-12-02 16:00:00")),
    ScreeningDB(-1, 1, 2, Timestamp.valueOf("2020-12-04 21:00:00")),
    ScreeningDB(-1, 1, 3, Timestamp.valueOf("2020-12-06 18:00:00")),

    ScreeningDB(-1, 2, 1, Timestamp.valueOf("2020-12-03 19:00:00")),
    ScreeningDB(-1, 2, 2, Timestamp.valueOf("2020-12-05 17:00:00")),
    ScreeningDB(-1, 3, 3, Timestamp.valueOf("2020-12-07 20:00:00"))
  )
}
