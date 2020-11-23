package com.example

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import com.example.Registry.Registry
import com.example.Routes.Routes
import com.example.Service.{MovieService, Reservation, ReservationService}


class RoutesSpec extends WordSpec with Matchers with ScalaFutures with ScalatestRouteTest {

  lazy val testKit = ActorTestKit()
  implicit def typedSystem = testKit.system
  override def createActorSystem(): akka.actor.ActorSystem =
    testKit.system.classicSystem

  val movieRegistry = testKit.spawn(Registry())
  val movieService = testKit.spawn(MovieService(movieRegistry))
  val reservationService = testKit.spawn(ReservationService(movieRegistry))
  lazy val routes = new Routes(movieService, reservationService).movieRoutes

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import JsonFormats._

  "MovieRoutes" should {
    "return example movies (GET /movies)" in {
      val request = HttpRequest(uri = "/movies?from=2020-12-01%2001:00:00&until=2020-12-09%2001:00:00")

      request ~> routes ~> check {
        status shouldEqual StatusCodes.OK

        contentType shouldEqual ContentTypes.`application/json`

        entityAs[String] should include ("Secretariat")
        entityAs[String] should include ("2020-12-05 17:00:00.0")
      }
    }
    "return bad request error with malformed parameters"  in {
      val request = HttpRequest(uri = "/movies?from=2020-12-&until=test")

      request ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest

        contentType shouldEqual ContentTypes.`application/json`

        entityAs[String] should include ("error")
      }
    }



    "return screening details (GET /movies/id)"  in {
      val request = HttpRequest(uri = "/movies/1")

      request ~> routes ~> check {
        status shouldEqual StatusCodes.OK

        contentType shouldEqual ContentTypes.`application/json`

        entityAs[String] should include ("screeningRoomId")
      }
    }
    "return not found error with wrong id"  in {
      val request = HttpRequest(uri = "/movies/999")

      request ~> routes ~> check {
        status shouldEqual StatusCodes.NotFound

        contentType shouldEqual ContentTypes.`application/json`

        entityAs[String] should include ("error")
      }
    }
    "return not found error with missing parameters"  in {
      val request = HttpRequest(uri = "/movies")

      request ~> routes ~> check {
        status shouldEqual StatusCodes.NotFound

        contentType shouldEqual ContentTypes.`application/json`

        entityAs[String] should include ("error")
      }
    }



    "return total amount and expiration time after reservation(PUT /movies/reservations)"  in {
      val request = HttpRequest(
        method = HttpMethods.POST,
        uri = "/movies/reservations",
        entity = HttpEntity(ContentTypes.`application/json`, "{\"name\": \"Jack Sparrow\", \"screeningId\": 1, \"seats\":[[1,2],[1,1]], \"adult_tickets\": 1, \"student_tickets\": 1, \"child_tickets\": 0}")
      )

      request ~> routes ~> check {
        status shouldEqual StatusCodes.OK

        contentType shouldEqual ContentTypes.`application/json`

        entityAs[String] should include ("total_amount")
        entityAs[String] should include ("expiration_time")
      }
    }

    "return error after reservation taken seats"  in {
      val request = HttpRequest(
        method = HttpMethods.POST,
        uri = "/movies/reservations",
        entity = HttpEntity(ContentTypes.`application/json`, "{\"name\": \"Jack Sparrow\", \"screeningId\": 1, \"seats\":[[1,2],[1,1]], \"adult_tickets\": 1, \"student_tickets\": 1, \"child_tickets\": 0}")
      )

      request ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest

        contentType shouldEqual ContentTypes.`application/json`

        entityAs[String] should include ("error")
      }
    }

    "return error after reservation with middle seat left"  in {
      val request = HttpRequest(
        method = HttpMethods.POST,
        uri = "/movies/reservations",
        entity = HttpEntity(ContentTypes.`application/json`, "{\"name\": \"Jack Sparrow\", \"screeningId\": 1, \"seats\":[[2,2],[2,4]]}")
      )

      request ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest

        contentType shouldEqual ContentTypes.`application/json`

        entityAs[String] should include ("error")
      }
    }

    "return error after reservation with invalid name"  in {
      val request = HttpRequest(
        method = HttpMethods.POST,
        uri = "/movies/reservations",
        entity = HttpEntity(ContentTypes.`application/json`, "{\"name\": \"JackSparrowInvalid49\", \"screeningId\": 1, \"seats\":[[1,2],[1,1]]}")
      )

      request ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest

        contentType shouldEqual ContentTypes.`application/json`

        entityAs[String] should include ("error")
      }
    }
  }
}
