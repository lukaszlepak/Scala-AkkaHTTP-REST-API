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

  implicit val movieJsonFormat: RootJsonFormat[Movie] = jsonFormat3(Movie)

  implicit val moviesJsonFormat: RootJsonFormat[Movies] = jsonFormat1(Movies)

  implicit val errorJsonFormat: RootJsonFormat[Error] = jsonFormat1(Error)
}
