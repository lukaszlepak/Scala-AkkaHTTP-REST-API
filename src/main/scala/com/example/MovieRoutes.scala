package com.example

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

import scala.concurrent.Future

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout

import com.example.MovieRegistry._

class MovieRoutes(movieRegistry: ActorRef[MovieRegistry.Command])(implicit val system: ActorSystem[_]) {

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import JsonFormats._

  private implicit val timeout: Timeout = Timeout.create(system.settings.config.getDuration("my-app.routes.ask-timeout"))

  def getMovies: Future[Either[Error, Movies]] =
    movieRegistry.ask(GetMovies)

  val movieRoutes: Route =
    pathPrefix("movies") {
      concat(
        pathEnd {
          concat(
            get {
              onSuccess(getMovies) {
                case Left(error) => complete(500, error)
                case Right(movies) => complete(movies)
              }
            })
        })
    }
}
