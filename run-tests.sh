#!/usr/bin/env bash
set -e

cd "$(dirname "$0")"

# Locate Java
if command -v java &>/dev/null; then
    JAVA_CMD="java"
else
    VSCODE_JAVA="$HOME/.vscode/extensions/redhat.java-1.58.0-linux-x64/jre/25.0.4.1-linux-x86_64/bin/java"
    if [ -x "$VSCODE_JAVA" ]; then
        JAVA_CMD="$VSCODE_JAVA"
    else
        echo "Error: Java runtime not found."
        exit 1
    fi
fi

# Locate Javac
if command -v javac &>/dev/null; then
    JAVAC_CMD="javac"
else
    VSCODE_JAVAC="$HOME/.vscode/extensions/redhat.java-1.58.0-linux-x64/jre/25.0.4.1-linux-x86_64/bin/javac"
    if [ -x "$VSCODE_JAVAC" ]; then
        JAVAC_CMD="$VSCODE_JAVAC"
    else
        JAVAC_CMD=""
    fi
fi

mkdir -p bin
if [ -n "$JAVAC_CMD" ]; then
    "$JAVAC_CMD" -cp "lib/*:backend/src" -d bin \
        backend/src/*.java \
        backend/test/*.java 2>/dev/null || true
fi

echo "Running SecureVault JUnit 5 Test Suite..."
exec "$JAVA_CMD" -jar lib/junit-platform-console-standalone-1.10.2.jar execute \
    --class-path "bin:lib/mariadb-java-client-3.5.6.jar" \
    --scan-class-path
