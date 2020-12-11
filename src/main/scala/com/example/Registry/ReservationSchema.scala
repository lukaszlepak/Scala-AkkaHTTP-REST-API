package com.example.Registry

import slick.jdbc.JdbcProfile

final case class ReservationDB(id: Int, screeningId: Int, name: String, row: Int, seatInRow: Int)

object ReservationSchema extends JdbcProfile {

  import api._

  class ReservationTable(tag: Tag) extends Table[ReservationDB](tag, "RESERVATIONS") {
    def id = column[Int]("ID", O.PrimaryKey, O.AutoInc)

    def screeningId = column[Int]("SCREENINGID")

    def name = column[String]("NAME")

    def row = column[Int]("ROW")

    def seatInRow = column[Int]("SEATINROW")

    def * = (id, screeningId, name, row, seatInRow) <> ((ReservationDB.apply _).tupled, ReservationDB.unapply)
  }

  val reservations = TableQuery[ReservationTable]
}
