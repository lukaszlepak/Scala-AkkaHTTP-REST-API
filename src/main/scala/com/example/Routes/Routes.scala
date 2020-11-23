package com.example.Routes

import java.sql.Timestamp

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.example.JsonFormats
import com.example.Service.MovieService._
import com.example.Service.ReservationService._
import com.example.Service._

import scala.concurrent.Future

class Routes(movieService: ActorRef[MovieService.MovieServiceCommand], reservationService: ActorRef[ReservationService.ReservationServiceCommand])(implicit val system: ActorSystem[_]) {

  import JsonFormats._
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

  implicit val timeout: Timeout = Timeout.create(system.settings.config.getDuration("my-app.routes.ask-timeout"))

  import akka.http.scaladsl.unmarshalling.Unmarshaller

  implicit val pairTimestamp = Unmarshaller.strict[String, Timestamp] { string =>
    Timestamp.valueOf(string)
  }

  import akka.http.scaladsl.server.RejectionHandler

  implicit def jsonRejectionHandler =
    RejectionHandler.default.mapRejectionResponse {
      case res @ HttpResponse(_, _, ent: HttpEntity.Strict, _) =>
        val message = ent.data.utf8String.replaceAll("\n", " ")

        res.withEntity(HttpEntity(ContentTypes.`application/json`, s"""{"error": "$message"}"""))

      case x => x
    }

  def getScreenings(from: Timestamp, until: Timestamp): Future[Either[MovieServiceError, Screenings]] =
    movieService.ask(GetScreenings(from, until, _))
  def getScreening(id: Int): Future[Either[MovieServiceError, Screening]] =
    movieService.ask(GetScreening(id, _))
  def createReservation(reservation: Reservation): Future[Either[ReservationServiceError, ReservationResponse]] =
    reservationService.ask(CreateReservation(reservation, _))

  val movieRoutes: Route =
    Route.seal(
      pathPrefix("movies") {
        concat(
          pathEnd {
            concat(
              get {
                parameters("from".as[Timestamp], "until".as[Timestamp]) { (from, until) =>
                  onSuccess(getScreenings(from, until)) {
                    case Left(error: MovieService.ExceptionError) => complete(500, error)
                    case Right(screenings) => complete(screenings)
                  }
                }
              }
            )
          }, path(IntNumber) { id =>
            concat(
              get {
                  onSuccess(getScreening(id)) {
                    case Left(error: MovieService.ExceptionError) => complete(500, error)
                    case Left(error: MovieService.NotFoundError) => complete(404, error)
                    case Right(screening) => complete(screening)
                  }
              }
            )
          }, path("reservations") {
            concat(
              post {
                entity(as[Reservation]){ reservation =>
                  onSuccess(createReservation(reservation)) {
                    case Left(error: ReservationService.ExceptionError) => complete(500, error)
                    case Left(error: ReservationService.WrongParameter) => complete(400, error)
                    case Left(error: ReservationService.NotFoundError) => complete(404, error)
                    case Right(reservation) => complete(reservation)
                  }
                }
              }
            )
          }
        )
      }
    )
}
