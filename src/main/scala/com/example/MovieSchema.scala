package com.example

import slick.jdbc.JdbcProfile

final case class Movie(id: Int, title: String)

object MovieSchema extends JdbcProfile {

  import api._

  class MovieTable(tag: Tag) extends Table[Movie](tag, "MOVIES") {
    def id = column[Int]("ID", O.PrimaryKey, O.AutoInc)

    def title = column[String]("TITLE")

    def * = (id, title) <> ((Movie.apply _).tupled, Movie.unapply)
  }

  val movies = TableQuery[MovieTable]
}
