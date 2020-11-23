package com.example

import java.sql.Timestamp

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

  implicit val reservationResponseJsonFormat: RootJsonFormat[ReservationResponse] = jsonFormat2(ReservationResponse)

  implicit val movieExceptionErrorJsonFormat: RootJsonFormat[Service.MovieService.ExceptionError] = jsonFormat1(Service.MovieService.ExceptionError)
  implicit val movieNotFoundErrorJsonFormat: RootJsonFormat[Service.MovieService.NotFoundError] = jsonFormat1(Service.MovieService.NotFoundError)

  implicit val reservationExceptionErrorJsonFormat: RootJsonFormat[Service.ReservationService.ExceptionError] = jsonFormat1(Service.ReservationService.ExceptionError)
  implicit val reservationNotFoundErrorJsonFormat: RootJsonFormat[Service.ReservationService.NotFoundError] = jsonFormat1(Service.ReservationService.NotFoundError)
  implicit val reservationWrongParameterErrorJsonFormat: RootJsonFormat[Service.ReservationService.WrongParameter] = jsonFormat1(Service.ReservationService.WrongParameter)
}
