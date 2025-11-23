test:
	sbt test

docker-build:
	sbt buildImages

format:
	sbt scalafmtAll

lint:
	sbt scalafixAll
