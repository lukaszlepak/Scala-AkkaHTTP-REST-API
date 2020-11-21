package com.example.Service

import java.sql.Timestamp

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.pattern.StatusReply
import akka.util.Timeout
import com.example.Registry.MovieRegistry._
import com.example.Registry._

import scala.concurrent.duration._
import scala.util.{Failure, Success}


final case class Screening(screeningId: Int, screeningRoomId: Int, availableSeats: Seq[(Int, Int)])
final case class Screenings(screenings: Map[String, Seq[(Int, Timestamp)]])
final case class ReservationResponse(total_amount: Int)

final case class Reservation(screeningId: Int, name: String, seats: Seq[(Int, Int)])

object MovieService {
  sealed trait ServiceCommand
  final case class GetScreening(screeningId: Int, replyTo: ActorRef[Either[Error, Screening]]) extends ServiceCommand
  final case class GetScreenings(from: Timestamp, until: Timestamp, replyTo: ActorRef[Either[Error, Screenings]]) extends ServiceCommand
  final case class CreateReservation(reservation: Reservation, replyTo: ActorRef[Either[Error, ReservationResponse]]) extends ServiceCommand

  final case class GetScreeningDBResponse(response: Either[Error, Screening], replyTo: ActorRef[Either[Error, Screening]]) extends ServiceCommand
  final case class GetScreeningsDBResponse(response: Either[Error, Screenings], replyTo: ActorRef[Either[Error, Screenings]]) extends ServiceCommand
  final case class GetReservationsScreeningDBResponse(screeningId: Int, response: ReservationsScreeningDB, replyTo: ActorRef[Either[Error, Screening]]) extends ServiceCommand
  final case class CreateReservationDBResponse(response: Either[Error, (Int, Reservation)], replyTo: ActorRef[Either[Error, ReservationResponse]]) extends ServiceCommand

  trait Error
  case class ExceptionError(error: String) extends Error
  case class NotFoundError(error: String) extends Error
  case class AlreadyExists(error: String) extends Error

  def apply(movieRegistry: ActorRef[MovieRegistry.RegistryCommand]): Behavior[ServiceCommand] = {
    service(movieRegistry)
  }

  private def service(movieRegistry: ActorRef[MovieRegistry.RegistryCommand]): Behavior[ServiceCommand] = {
    Behaviors.setup[ServiceCommand] { context =>
      implicit val timeout: Timeout = 3.seconds
      Behaviors.receiveMessage {

        case GetScreenings(from, until, replyTo) =>
          context.askWithStatus(movieRegistry, (ref: ActorRef[StatusReply[ScreeningTitlesDB]]) => GetScreeningTitlesDB(from, until, ref)) {
            case Success(screeningTitles) =>
              val screenings = Screenings(
                screeningTitles.screeningTitles.groupMap(_.movieTitle)(pair => (pair.screeningId, pair.screeningTime))
              )
                                                                      GetScreeningsDBResponse(Right(screenings), replyTo)
            case Failure(StatusReply.ErrorMessage(errorMessage)) =>   GetScreeningsDBResponse(Left(ExceptionError(errorMessage)), replyTo)
            case Failure(exception) =>                                GetScreeningsDBResponse(Left(ExceptionError(exception.getMessage)), replyTo)
          }
          Behaviors.same

        case GetScreeningsDBResponse(screenings, replyTo) =>
          replyTo ! screenings
          Behaviors.same

        case GetScreening(id, replyTo) =>
          context.askWithStatus(movieRegistry, (ref: ActorRef[StatusReply[ReservationsScreeningDB]]) => GetReservationsScreeningDB(id, ref)) {
            case Success(reservations) =>                             GetReservationsScreeningDBResponse(id, reservations, replyTo)
            case Failure(StatusReply.ErrorMessage(errorMessage)) =>   GetScreeningDBResponse(Left(ExceptionError(errorMessage)), replyTo)
            case Failure(exception) =>                                GetScreeningDBResponse(Left(ExceptionError(exception.getMessage)), replyTo)
          }
          Behaviors.same

        case GetReservationsScreeningDBResponse(id, reservationsDB, replyTo) =>
          context.askWithStatus(movieRegistry, (ref: ActorRef[StatusReply[Option[ScreeningScreeningRoomDB]]]) => GetScreeningScreeningRoomDB(id, ref)) {
            case Success(Some(screening)) =>
              val availableSeats: Seq[(Int, Int)] = for {
                r <- 1 to screening.rows
                s <- 1 to screening.seatsPerRow
                if !reservationsDB.reservations.contains((r,s))
              } yield (r,s)
                                                                      GetScreeningDBResponse(Right(Screening(screening.screeningId, screening.screeningRoomId, availableSeats)), replyTo)
            case Success(None) =>                                     GetScreeningDBResponse(Left(NotFoundError("Screening Not Found")), replyTo)
            case Failure(StatusReply.ErrorMessage(errorMessage)) =>   GetScreeningDBResponse(Left(ExceptionError(errorMessage)), replyTo)
            case Failure(exception) =>                                GetScreeningDBResponse(Left(ExceptionError(exception.getMessage)), replyTo)
          }
          Behaviors.same

        case GetScreeningDBResponse(screening, replyTo) =>
          replyTo ! screening
          Behaviors.same

        case CreateReservation(reservation, replyTo) =>

          context.askWithStatus(movieRegistry, (ref: ActorRef[StatusReply[ReservationCreatedDB]]) => CreateReservationDB(reservation, ref)) {
            case Success(ReservationCreatedDB(0)) =>                  CreateReservationDBResponse(Left(AlreadyExists("Seats reserved already")), replyTo)
            case Success(ReservationCreatedDB(insertedRows)) =>       CreateReservationDBResponse(Right(insertedRows, reservation), replyTo)
            case Failure(StatusReply.ErrorMessage(errorMessage)) =>   CreateReservationDBResponse(Left(ExceptionError(errorMessage)), replyTo)
            case Failure(exception) =>                                CreateReservationDBResponse(Left(ExceptionError(exception.getMessage)), replyTo)
          }
          Behaviors.same

        case CreateReservationDBResponse(either, replyTo) =>

          either match {
            case Right(tuple) => {
              val price = tuple._2.seats.foldLeft(0)((sum, _) => sum + 20)
              replyTo ! Right(ReservationResponse(price))
            }
            case Left(e) => replyTo ! Left(e)
          }
          Behaviors.same
      }
    }
  }
}
