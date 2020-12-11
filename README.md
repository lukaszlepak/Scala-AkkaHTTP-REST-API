# Scala-AkkaHTTP-REST-API

Application to explore movies, screenings and to reserve seats

To test type:
`sbt test`

To run type:
`sbt run`

While running use demo script:
`sh demo.sh`

Possible requests: 

- to print movies screenings by given time period:

`curl -X GET -H 'Content-Type: application/json' -i 'http://localhost:8080/movies?from=2020-12-01%2001:00:00&until=2020-12-09%2001:00:00'` 


- to print screening details by id:

`curl -X GET -H 'Content-Type: application/json' -i http://localhost:8080/movies/1`


- to maka a reservation:

`curl -X POST -H 'Content-Type: application/json' -i http://localhost:8080/movies/reservations --data 
'{ "name": "Jack Sparrow", "screeningId": 1, "seats": [[1,1], [1,2]], "adult_tickets": 1, "student_tickets": 1, "child_tickets": 0 }'`



To change ticket values, data in db on start, edit:
`src/main/resources/application.conf`
