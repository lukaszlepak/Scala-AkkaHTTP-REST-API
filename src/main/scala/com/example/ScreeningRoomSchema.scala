package com.example

import slick.jdbc.JdbcProfile

final case class ScreeningRoom(id: Int, rows: Int, seatsPerRow: Int)

object ScreeningRoomSchema extends JdbcProfile {

  import api._

  class ScreeningRoomTable(tag: Tag) extends Table[ScreeningRoom](tag, "SCREENINGROOMS") {
    def id = column[Int]("ID", O.PrimaryKey, O.AutoInc)

    def rows = column[Int]("ROWS")

    def seatsPerRow = column[Int]("SEATSPERROW")

    def * = (id, rows, seatsPerRow) <> ((ScreeningRoom.apply _).tupled, ScreeningRoom.unapply)
  }

  val screeningRooms = TableQuery[ScreeningRoomTable]
}
