package com.example.Service

import java.sql.Timestamp

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.pattern.StatusReply
import akka.util.Timeout
import com.example.Registry.Registry._
import com.example.Registry._

import scala.concurrent.duration._
import scala.util.{Failure, Success}


final case class Screening(screeningId: Int, screeningRoomId: Int, availableSeats: Seq[(Int, Int)])
final case class Screenings(screenings: Map[String, Seq[(Int, Timestamp)]])

object MovieService {
  sealed trait MovieServiceCommand
  final case class GetScreenings(from: Timestamp, until: Timestamp, replyTo: ActorRef[Either[MovieServiceError, Screenings]]) extends MovieServiceCommand
  final case class GetScreeningsDBResponse(response: Either[MovieServiceError, Screenings], replyTo: ActorRef[Either[MovieServiceError, Screenings]]) extends MovieServiceCommand

  final case class GetScreening(screeningId: Int, replyTo: ActorRef[Either[MovieServiceError, Screening]]) extends MovieServiceCommand
  final case class GetReservationsScreeningDBResponse(screeningId: Int, response: ReservationsScreeningDB, replyTo: ActorRef[Either[MovieServiceError, Screening]]) extends MovieServiceCommand
  final case class GetScreeningDBResponse(response: Either[MovieServiceError, Screening], replyTo: ActorRef[Either[MovieServiceError, Screening]]) extends MovieServiceCommand

  trait MovieServiceError
  case class ExceptionError(error: String) extends MovieServiceError
  case class NotFoundError(error: String) extends MovieServiceError

  def apply(movieRegistry: ActorRef[Registry.RegistryCommand]): Behavior[MovieServiceCommand] = {
    movieService(movieRegistry)
  }

  private def movieService(movieRegistry: ActorRef[Registry.RegistryCommand]): Behavior[MovieServiceCommand] = {
    Behaviors.setup[MovieServiceCommand] { context =>
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
      }
    }
  }
}
