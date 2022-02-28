#!/bin/sh

bazel run //server:image
bazel run //client:image
docker tag bazel/client:image $CONTAINER_REGISTRY/bazel/client:latest
docker push $CONTAINER_REGISTRY/bazel/client:latest
docker tag bazel/server:image $CONTAINER_REGISTRY/bazel/server:latest
docker push $CONTAINER_REGISTRY/bazel/server:latest
