package com.example

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._


class MovieRoutesSpec extends WordSpec with Matchers with ScalaFutures with ScalatestRouteTest {

  lazy val testKit = ActorTestKit()
  implicit def typedSystem = testKit.system
  override def createActorSystem(): akka.actor.ActorSystem =
    testKit.system.classicSystem

  val movieRegistry = testKit.spawn(MovieRegistry())
  lazy val routes = new MovieRoutes(movieRegistry).movieRoutes

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
    "return not found error with missing parameters"  in {
      val request = HttpRequest(uri = "/movies")

      request ~> routes ~> check {
        status shouldEqual StatusCodes.NotFound

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
  }
}
