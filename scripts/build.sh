#!/bin/sh

echo "Building app"
bazel run //server:tarball || exit 1
#bazel run //client:image --define SERVER_URL=${SERVER_URL} || exit 1

