
# transit-movements-guarantee-balance

The backend service which manages balance request data for the CTC Guarantee Balance API.

### Running

##### To run this Service you will need:

1) [Service Manager](https://github.com/hmrc/service-manager) installed
2) [SBT](https://www.scala-sbt.org) Version `>=1.x` installed
3) [MongoDB](https://www.mongodb.com/) version `>=4.0` installed and running on port 27017 as a replica set

The easiest way to run MongoDB for local development is to use [Docker](https://docs.docker.com/get-docker/).

##### To run MongoDB

```
> docker run --restart unless-stopped -d -p 27017-27019:27017-27019 --name mongodb mongo:4.0 --replSet rs
```

##### To configure MongoDB to run as a replica set

```
> docker exec -it mongodb mongo
> rs.initiate()
> exit
> exit
```

#### Starting the application:

Launch the service and all dependencies using `sm --start CTC_GUARANTEE_BALANCE_API`.

This application runs on port 10208.

To run with sbt, stop the Service Manager instance of this service using `sm --stop TRANSIT_MOVEMENTS_GUARANTEE_BALANCE` before running with `sbt run` from the project folder.

### Testing

Run `./run_all_tests.sh`. This also checks code formatting and does coverage testing.

Use `sbt test it:test` to run only the tests without the additional checks.

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
