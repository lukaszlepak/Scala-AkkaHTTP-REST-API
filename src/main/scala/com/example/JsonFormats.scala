package com.example

import java.sql.Timestamp

import com.example.Service.MovieService._
import com.example.Service.{Reservation, ReservationResponse, Screening, Screenings}
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat}

object JsonFormats {

  implicit object TimestampJsonFormat extends JsonFormat[Timestamp] {
    override def write(time: Timestamp): JsString = JsString(time.toString)

    override def read(value: JsValue): Timestamp = value match {
      case JsString(time) => Timestamp.valueOf(time)
      case _ => Timestamp.valueOf("")
    }
  }

  import DefaultJsonProtocol._

  implicit val screeningsJsonFormat: RootJsonFormat[Screenings] = jsonFormat1(Screenings)

  implicit val screeningJsonFormat: RootJsonFormat[Screening] = jsonFormat3(Screening)

  implicit val reservationJsonFormat: RootJsonFormat[Reservation] = jsonFormat3(Reservation)

  implicit val reservationResponseJsonFormat: RootJsonFormat[ReservationResponse] = jsonFormat1(ReservationResponse)

  implicit val exceptionErrorJsonFormat: RootJsonFormat[ExceptionError] = jsonFormat1(ExceptionError)

  implicit val notFoundErrorJsonFormat: RootJsonFormat[NotFoundError] = jsonFormat1(NotFoundError)

  implicit val alreadyExistsErrorJsonFormat: RootJsonFormat[AlreadyExists] = jsonFormat1(AlreadyExists)
}
