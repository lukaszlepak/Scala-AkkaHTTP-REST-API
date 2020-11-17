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
      val request = HttpRequest(uri = "/movies")

      request ~> routes ~> check {
        status shouldEqual StatusCodes.OK

        contentType shouldEqual ContentTypes.`application/json`

        entityAs[String] should include ("id")
        entityAs[String] should include ("title")
        entityAs[String] should include ("time")
      }
    }
  }
}
