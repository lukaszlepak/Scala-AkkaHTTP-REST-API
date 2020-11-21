package com.example.Registry

import slick.jdbc.JdbcProfile

final case class MovieDB(id: Int, title: String)

object MovieSchema extends JdbcProfile {

  import api._

  class MovieTable(tag: Tag) extends Table[MovieDB](tag, "MOVIES") {
    def id = column[Int]("ID", O.PrimaryKey, O.AutoInc)

    def title = column[String]("TITLE")

    def * = (id, title) <> ((MovieDB.apply _).tupled, MovieDB.unapply)
  }

  val movies = TableQuery[MovieTable]
}
