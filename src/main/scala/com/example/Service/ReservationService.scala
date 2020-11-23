package com.example.Service

import java.sql.Timestamp

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.pattern.StatusReply

import scala.util.{Failure, Success}
import com.example.Registry.Registry.{CreateReservationDB, GetReservationsScreeningDB, GetScreeningScreeningRoomDB, GetScreeningTimeDB}
import com.example.Registry.{Registry, ReservationCreatedDB, ReservationsScreeningDB, ScreeningScreeningRoomDB, ScreeningTime}

import scala.concurrent.duration._
import akka.util.Timeout

import scala.util.matching.Regex


final case class Reservation(screeningId: Int, name: String, seats: Seq[(Int, Int)], adult_tickets: Int, student_tickets: Int, child_tickets: Int)
final case class ReservationResponse(total_amount: String, expiration_time: Timestamp)

object ReservationService {
  sealed trait ReservationServiceCommand
  final case class CreateReservation(reservation: Reservation, replyTo: ActorRef[Either[ReservationServiceError, ReservationResponse]]) extends ReservationServiceCommand

  final case class GetReservationsScreeningDBResponse(reservedSeats: Seq[(Int, Int)], reservation: Reservation, replyTo: ActorRef[Either[ReservationService.ReservationServiceError, ReservationResponse]]) extends ReservationServiceCommand
  final case class GetScreeningRoomDBResponse(reservation: Reservation, replyTo: ActorRef[Either[ReservationServiceError, ReservationResponse]]) extends ReservationServiceCommand
  final case class GetScreeningTimeDBResponse(reservation: Reservation, time: Timestamp, replyTo: ActorRef[Either[ReservationServiceError, ReservationResponse]]) extends ReservationServiceCommand

  final case class CreateReservationDBResponse(response: Either[ReservationServiceError, (Reservation, Timestamp)], replyTo: ActorRef[Either[ReservationServiceError, ReservationResponse]]) extends ReservationServiceCommand

  trait ReservationServiceError
  case class ExceptionError(error: String) extends ReservationServiceError
  case class NotFoundError(error: String) extends ReservationServiceError
  case class WrongParameter(error: String) extends ReservationServiceError

  def apply(registry: ActorRef[Registry.RegistryCommand]): Behavior[ReservationServiceCommand] = {
    reservationService(registry)
  }

  private def reservationService(registry: ActorRef[Registry.RegistryCommand]): Behavior[ReservationServiceCommand] = {
    Behaviors.setup[ReservationServiceCommand] { context =>
      implicit val timeout: Timeout = 3.seconds
      Behaviors.receiveMessage {
        case CreateReservation(reservation, replyTo) =>
          val ticketsSum = reservation.adult_tickets + reservation.student_tickets + reservation.child_tickets

          if(reservation.seats.isEmpty || ticketsSum > reservation.seats.length)
            replyTo ! Left(WrongParameter("Wrong seats"))
          else {
            val properName: Regex = "^[A-ZŻŹĆĄŚĘŁÓŃ][a-zżźćńółęąś]{2,}$".r

            val splitNames = reservation.name match {
              case s"$name $surname1-$surname2" => Some(List(name, surname1, surname2))
              case s"$name $surname" => Some(List(name, surname))
              case _ => None
            }

            splitNames match {
              case Some(list) if list.forall(s => properName.matches(s)) =>
                context.askWithStatus(registry, (ref: ActorRef[StatusReply[ReservationsScreeningDB]]) => GetReservationsScreeningDB(reservation.screeningId, ref)) {
                  case Success(ReservationsScreeningDB(reservedSeats)) => GetReservationsScreeningDBResponse(reservedSeats, reservation, replyTo)
                  case Failure(StatusReply.ErrorMessage(errorMessage)) => CreateReservationDBResponse(Left(ExceptionError(errorMessage)), replyTo)
                  case Failure(exception) => CreateReservationDBResponse(Left(ExceptionError(exception.getMessage)), replyTo)
                }
              case _ => replyTo ! Left(WrongParameter("Wrong name"))
            }
          }
          Behaviors.same

        case GetReservationsScreeningDBResponse(reservedSeats, reservation, replyTo) =>
            context.askWithStatus(registry, (ref: ActorRef[StatusReply[Option[ScreeningScreeningRoomDB]]]) => GetScreeningScreeningRoomDB(reservation.screeningId, ref)) {
              case Success(Some(screeningRoom)) =>
                if(!reservation.seats.exists( s =>
                    reservedSeats.contains(s) ||
                    s._1 < 1 ||
                    s._2 < 1 ||
                    s._1 > screeningRoom.rows ||
                    s._2 > screeningRoom.seatsPerRow))
                {
                  val seats = for {
                    r <- 1 to screeningRoom.rows
                    s <- 1 to screeningRoom.seatsPerRow
                  } yield (r,s)

                  val availableSeatsMap = seats.groupMap(_._1)(seat => if(!reservation.seats.contains(seat) && !reservedSeats.contains(seat)) false else true)
                  val listOfSeatsByThree = availableSeatsMap.values.flatMap(row => row.iterator.sliding(3).withPartial(false).toList)
                  if(listOfSeatsByThree.exists(_ == List(true, false, true)))
                                                                                  CreateReservationDBResponse(Left(WrongParameter("Wrong seats to reserve, can t leave one in the middle")), replyTo)
                  else
                                                                                  GetScreeningRoomDBResponse(reservation, replyTo)
                }
                else                                                              CreateReservationDBResponse(Left(WrongParameter("Wrong seats to reserve, already reserved")), replyTo)
              case Success(None) =>                                               CreateReservationDBResponse(Left(NotFoundError("Not found screening")), replyTo)
              case Failure(StatusReply.ErrorMessage(errorMessage)) =>             CreateReservationDBResponse(Left(ExceptionError(errorMessage)), replyTo)
              case Failure(exception) =>                                          CreateReservationDBResponse(Left(ExceptionError(exception.getMessage)), replyTo)
            }
          Behaviors.same

        case GetScreeningRoomDBResponse(reservation, replyTo) =>
          context.askWithStatus(registry, (ref: ActorRef[StatusReply[Option[ScreeningTime]]]) => GetScreeningTimeDB(reservation.screeningId, ref)) {
            case Success(Some(ScreeningTime(time))) =>
              if((time.getTime - System.currentTimeMillis()) > 900000)
                                                                                  GetScreeningTimeDBResponse(reservation, time, replyTo)
              else
                                                                                  CreateReservationDBResponse(Left(WrongParameter("It is less than 15 minutes until screening")), replyTo)
            case Success(None) =>                                                 CreateReservationDBResponse(Left(NotFoundError("Not found screening")), replyTo)
            case Failure(StatusReply.ErrorMessage(errorMessage)) =>               CreateReservationDBResponse(Left(ExceptionError(errorMessage)), replyTo)
            case Failure(exception) =>                                            CreateReservationDBResponse(Left(ExceptionError(exception.getMessage)), replyTo)
          }
          Behaviors.same

        case GetScreeningTimeDBResponse(reservation, time, replyTo) =>
          context.askWithStatus(registry, (ref: ActorRef[StatusReply[ReservationCreatedDB]]) => CreateReservationDB(reservation, ref)) {
            case Success(_) =>                                                    CreateReservationDBResponse(Right((reservation, time)), replyTo)
            case Failure(StatusReply.ErrorMessage(errorMessage)) =>               CreateReservationDBResponse(Left(ExceptionError(errorMessage)), replyTo)
            case Failure(exception) =>                                            CreateReservationDBResponse(Left(ExceptionError(exception.getMessage)), replyTo)
          }
          Behaviors.same

        case CreateReservationDBResponse(either, replyTo) =>
          either match {
            case Right(reservationAndTime) => {
              val reservation = reservationAndTime._1

              val price = reservation.adult_tickets * 25 + reservation.student_tickets * 18 + reservation.child_tickets * 12.5
              replyTo ! Right(ReservationResponse(price.toString, new Timestamp(reservationAndTime._2.getTime - 900000)))
            }
            case Left(e) => replyTo ! Left(e)
          }
          Behaviors.same
      }
    }
  }
}





