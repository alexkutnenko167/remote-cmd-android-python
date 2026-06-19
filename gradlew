#!/bin/bash
# Gradle Wrapper
GRADLE_OPTS=""
JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}
JAVA_CMD="$JAVA_HOME/bin/java"

if [ ! -f "$JAVA_CMD" ]; then
    echo "Java not found. Installing..."
    sudo apt update
    sudo apt install openjdk-17-jdk -y
    JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
    JAVA_CMD="$JAVA_HOME/bin/java"
fi

exec "$JAVA_CMD" -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain "$@"
