#!/bin/sh

export CLIENT_ID="<place client_id here>"
export SERVER_PORT=8081
export CLIENT_PORT=8080
export CORS_URL="/**"
export DATASOURCE_URL="<place datasource connection url here>"
export DATASOURCE_USER="<place datasource connection username here>"
export DATASOURCE_PASS="<place datasource connection password here>"

bazel run //server:image
bazel run //client:image