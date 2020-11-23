package com.example.Registry

import java.sql.Timestamp

import slick.jdbc.JdbcProfile

final case class ScreeningDB(id: Int, movieId: Int, screeningRoomId: Int, time: Timestamp)

case class ScreeningsDB(screenings: List[ScreeningDB])

object ScreeningSchema extends JdbcProfile {

  import api._

  class ScreeningTable(tag: Tag) extends Table[ScreeningDB](tag, "SCREENINGS") {
    def id = column[Int]("ID", O.PrimaryKey, O.AutoInc)

    def movieId = column[Int]("MOVIEID")

    def screeningRoomId = column[Int]("SCREENINGROOMID")

    def time = column[Timestamp]("TIME")

    def * = (id, movieId, screeningRoomId, time) <> ((ScreeningDB.apply _).tupled, ScreeningDB.unapply)
  }

  val screenings = TableQuery[ScreeningTable]
}
