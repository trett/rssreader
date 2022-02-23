#!/bin/sh

export CLIENT_ID="<place client_id here>"
export SERVER_PORT=8081
export CLIENT_PORT=8080

bazel run //server:image
bazel run //client:image