package com.example

import java.sql.Timestamp

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

import scala.concurrent.Future
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.util.Timeout
import com.example.MovieRegistry._

class MovieRoutes(movieRegistry: ActorRef[MovieRegistry.Command])(implicit val system: ActorSystem[_]) {

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import JsonFormats._

  private implicit val timeout: Timeout = Timeout.create(system.settings.config.getDuration("my-app.routes.ask-timeout"))

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

  def getScreenings(from: Timestamp, until: Timestamp): Future[Either[Error, Screenings]] =
    movieRegistry.ask(GetScreenings(from, until, _))
  def getScreening(id: Int): Future[Either[Error, ScreeningSeats]] =
    movieRegistry.ask(GetScreening(id, _))

  val movieRoutes: Route =
    Route.seal(
    pathPrefix("movies") {
      concat(
        pathEnd {
          concat(
            get {
              parameters("from".as[Timestamp], "until".as[Timestamp]) { (from, until) =>
                onSuccess(getScreenings(from, until)) {
                  case Left(error: ExceptionError) => complete(500, error)
                  case Right(screenings) => complete(screenings)
                }
              }
            })
        }, path(IntNumber) { id =>
          concat(
            get {
                onSuccess(getScreening(id)) {
                  case Left(error: ExceptionError) => complete(500, error)
                  case Left(error: NotFoundError) => complete(404, error)
                  case Right(screening) => complete(screening)
                }

            }
          )
        }
      )
    })
}
