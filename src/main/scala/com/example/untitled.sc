val name = "Jack Sparrow"

val matched = name match {
  case s"$firstname $surname1-$surname2" => "PAS2"
  case s"$firstname $surname" => "PASUJE"
  case _ => "Wrooong"
}

println(matched)