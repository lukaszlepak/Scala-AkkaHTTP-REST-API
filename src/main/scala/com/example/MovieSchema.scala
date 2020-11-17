package com.example

import java.sql.Timestamp

import slick.jdbc.JdbcProfile

object MovieSchema extends JdbcProfile {

  import api._

  class MovieTable(tag: Tag) extends Table[Movie](tag, "MOVIES") {
    def id = column[Int]("ID", O.PrimaryKey, O.AutoInc)

    def title = column[String]("TITLE")

    def timestamp = column[Timestamp]("TIMESTAMP")

    def * = (id, title, timestamp) <> ((Movie.apply _).tupled, Movie.unapply)
  }

  val movies = TableQuery[MovieTable]
}
