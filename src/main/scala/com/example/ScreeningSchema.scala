package com.example

import java.sql.Timestamp

import slick.jdbc.JdbcProfile

final case class Screening(id: Int, movieId: Int, screeningRoomId: Int, time: Timestamp)

object ScreeningSchema extends JdbcProfile {

  import api._

  class ScreeningTable(tag: Tag) extends Table[Screening](tag, "SCREENINGS") {
    def id = column[Int]("ID", O.PrimaryKey, O.AutoInc)

    def movieId = column[Int]("MOVIEID")

    def screeningRoomId = column[Int]("SCREENINGROOMID")

    def time = column[Timestamp]("TIME")

    def * = (id, movieId, screeningRoomId, time) <> ((Screening.apply _).tupled, Screening.unapply)
  }

  val screenings = TableQuery[ScreeningTable]
}
