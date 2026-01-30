#!/bin/bash
# Gradle wrapper script

GRADLE_VERSION=8.2

# Download gradle if not present
if [ ! -d "gradle/wrapper" ]; then
    mkdir -p gradle/wrapper
fi

# Use system gradle or download
exec gradle "$@"
