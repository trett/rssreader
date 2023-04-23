#!/bin/sh

echo "Building app"
bazel run //server:image || exit 1
bazel run //client:image --define SERVER_URL=${SERVER_URL} --define CLIENT_ID=${CLIENT_ID} --define REDIRECT_URI=${REDIRECT_URI} || exit 1

