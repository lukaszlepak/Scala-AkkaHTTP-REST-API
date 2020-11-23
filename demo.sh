curl -X GET -H 'Content-Type: application/json' -i 'http://localhost:8080/movies?from=2020-12-01%2001:00:00&until=2020-12-09%2001:00:00' 
curl -X GET -H 'Content-Type: application/json' -i http://localhost:8080/movies/1 
curl -X POST -H 'Content-Type: application/json' -i http://localhost:8080/movies/reservations --data '{ "name": "Jack Sparrow", "screeningId": 1, "seats": [[1,1], [1,2]], "adult_tickets": 1, "student_tickets": 1, "child_tickets": 0 }'

