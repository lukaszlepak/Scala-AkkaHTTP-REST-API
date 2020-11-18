package com.example

import java.sql.Timestamp

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

  implicit val screeningJsonFormat: RootJsonFormat[ScreeningSeats] = jsonFormat3(ScreeningSeats)

  implicit val exceptionErrorJsonFormat: RootJsonFormat[ExceptionError] = jsonFormat1(ExceptionError)

  implicit val notFoundErrorJsonFormat: RootJsonFormat[NotFoundError] = jsonFormat1(NotFoundError)
}
