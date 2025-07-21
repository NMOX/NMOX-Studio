#!/bin/bash

# NMOX Studio Build Script
# Professional build script for the NetBeans RCP application

set -e

echo "========================================"
echo "Building NMOX Studio"
echo "========================================"

# Check Maven installation
if ! command -v mvn &> /dev/null; then
    echo "ERROR: Maven is not installed or not in PATH"
    exit 1
fi

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "ERROR: Java 17 or higher is required"
    exit 1
fi

echo "Java version: $(java -version 2>&1 | head -1)"
echo "Maven version: $(mvn -version | head -1)"
echo ""

# Clean previous builds
echo "Cleaning previous builds..."
mvn clean

# Run tests
echo "Running tests..."
mvn test

# Build the application
echo "Building application..."
mvn package

# Create distribution
echo "Creating distribution packages..."
mvn package -Pdeployment

echo ""
echo "========================================"
echo "Build completed successfully!"
echo "========================================"
echo "Application package: application/target/nmoxstudio/"
echo "Installers: application/target/"
echo ""

# Check if application was built successfully
if [ -d "application/target/nmoxstudio" ]; then
    echo "✅ Application built successfully"
    echo "To run the application:"
    echo "  cd application/target/nmoxstudio/bin"
    echo "  ./nmoxstudio"
else
    echo "❌ Build failed - application directory not found"
    exit 1
fi