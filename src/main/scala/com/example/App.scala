package com.example

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import com.example.Registry.Registry
import com.example.Routes.Routes
import com.example.Service.{MovieService, ReservationService}

import scala.util.Failure
import scala.util.Success

object App {
  private def startHttpServer(routes: Route)(implicit system: ActorSystem[_]): Unit = {
    import system.executionContext

    val futureBinding = Http().newServerAt("localhost", 8080).bind(routes)
    futureBinding.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info("Server online at http://{}:{}/", address.getHostString, address.getPort)
      case Failure(ex) =>
        system.log.error("Failed to bind HTTP endpoint, terminating system", ex)
        system.terminate()
    }
  }
  def main(args: Array[String]): Unit = {
    val rootBehavior = Behaviors.setup[Nothing] { context =>
      val registryActor = context.spawn(Registry(), "MovieRegistryActor")
      context.watch(registryActor)

      val movieServiceActor = context.spawn(MovieService(registryActor), "MovieServiceActor")
      context.watch(movieServiceActor)

      val reservationServiceActor = context.spawn(ReservationService(registryActor), "ReservationServiceActor")
      context.watch(reservationServiceActor)

      val routes = new Routes(movieServiceActor, reservationServiceActor)(context.system)
      startHttpServer(routes.movieRoutes)(context.system)

      Behaviors.empty
    }
    val system = ActorSystem[Nothing](rootBehavior, "CinemaServer")
  }
}
