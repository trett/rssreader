#!/bin/sh

echo "Building app"
bazel run //server:image || exit 1
bazel run //client:image --define SERVER_URL=${SERVER_URL} --define CLIENT_ID=${CLIENT_ID} --define REDIRECT_URI=${REDIRECT_URI} || exit 1

APP_VERSION=$(git rev-parse --short HEAD)
echo "Buiding image: $APP_VERSION"
docker tag bazel/client:image $CONTAINER_REGISTRY/bazel/client:$APP_VERSION
docker push $CONTAINER_REGISTRY/bazel/client:$APP_VERSION
docker tag bazel/server:image $CONTAINER_REGISTRY/bazel/server:$APP_VERSION
docker push $CONTAINER_REGISTRY/bazel/server:$APP_VERSION
