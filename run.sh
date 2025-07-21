#!/bin/bash

# NMOX Studio Run Script
# Development script to run the application

set -e

echo "========================================"
echo "Running NMOX Studio"
echo "========================================"

# Check if application is built
if [ ! -d "application/target/nmoxstudio" ]; then
    echo "Application not built. Building now..."
    ./build.sh
fi

# Run the application
echo "Starting NMOX Studio..."
cd application/target/nmoxstudio/bin
./nmoxstudio --userdir "../../../../userdir" --jdkhome "$JAVA_HOME"