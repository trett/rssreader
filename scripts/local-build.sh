#!/bin/bash

# This script replicates the build process from cloudbuild.yaml locally on MacOS.
# It uses a Linux container to build the native binary to ensure compatibility 
# with the final Docker container.

set -e

# Record start time
START_TIME=$(date +%s)

echo "Starting local build..."

# Use a variable for the current directory to avoid path resolution issues
PROJECT_ROOT="$(pwd)"

# Step 1: Build the native binary and stage the Docker context
# We mount the project into /app and ensure the target directory exists.
# We use --entrypoint bash because the image may have native-image as its default entrypoint.

docker run --rm --entrypoint bash -v "$PROJECT_ROOT:/app" -w /app \
    -e "SBT_OPTS=-Xmx8G" \
    ghcr.io/graalvm/native-image-community:21 \
    -c '
        set -e
        
        echo "Step 1.1: Verifying mount..."
        if [ ! -f "build.sbt" ]; then
            echo "Error: build.sbt not found. Check Docker File Sharing settings."
            exit 1
        fi
        
        echo "Step 1.2: Installing build dependencies (nodejs, sbt)..."
        curl -fsSL https://rpm.nodesource.com/setup_20.x | bash - > /dev/null
        microdnf install -y nodejs tar gzip > /dev/null
        
        if ! command -v sbt &> /dev/null; then
            echo "Downloading sbt..."
            curl -L https://github.com/sbt/sbt/releases/download/v1.10.11/sbt-1.10.11.tgz | tar -xz -C /usr/local
            ln -sf /usr/local/sbt/bin/sbt /usr/local/bin/sbt
        fi

        echo "Step 1.3: Preparing output directories..."
        mkdir -p server/target/graalvm-native-image
        mkdir -p server/target/docker/stage
        
        echo "Step 1.4: Running sbt server/Docker/stage..."
        sbt "server/Docker/stage"
    '

# Step 2: Build the final Docker image from the staged directory
echo "Building final Docker image..."
docker build -t server:local server/target/docker/stage

# Calculate elapsed time
END_TIME=$(date +%s)
ELAPSED=$((END_TIME - START_TIME))
MINUTES=$((ELAPSED / 60))
SECONDS=$((ELAPSED % 60))

echo "Build complete! Image 'server:local' is ready."
echo "Total build time: ${MINUTES}m ${SECONDS}s"
echo "You can run it using: docker run -p 8080:8080 server:local"
