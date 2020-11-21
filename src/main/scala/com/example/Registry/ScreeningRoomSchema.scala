package com.example.Registry

import slick.jdbc.JdbcProfile

final case class ScreeningRoomDB(id: Int, rows: Int, seatsPerRow: Int)

object ScreeningRoomSchema extends JdbcProfile {

  import api._

  class ScreeningRoomTable(tag: Tag) extends Table[ScreeningRoomDB](tag, "SCREENINGROOMS") {
    def id = column[Int]("ID", O.PrimaryKey, O.AutoInc)

    def rows = column[Int]("ROWS")

    def seatsPerRow = column[Int]("SEATSPERROW")

    def * = (id, rows, seatsPerRow) <> ((ScreeningRoomDB.apply _).tupled, ScreeningRoomDB.unapply)
  }

  val screeningRooms = TableQuery[ScreeningRoomTable]
}
