#!/usr/bin/env bash
set -e

# Change to project root directory
cd "$(dirname "$0")"

# Locate Java binary (system PATH or VS Code bundled JRE)
if command -v java &>/dev/null; then
    JAVA_CMD="java"
else
    VSCODE_JAVA="$HOME/.vscode/extensions/redhat.java-1.58.0-linux-x64/jre/25.0.4.1-linux-x86_64/bin/java"
    if [ -x "$VSCODE_JAVA" ]; then
        JAVA_CMD="$VSCODE_JAVA"
    else
        echo "Error: Java runtime not found. Please install a JDK or ensure Java is in your PATH."
        exit 1
    fi
fi

# Locate Java compiler
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

# Ensure bin directory exists and recompile if needed
mkdir -p bin
if [ -n "$JAVAC_CMD" ]; then
    "$JAVAC_CMD" -cp "lib/mariadb-java-client-3.5.6.jar:backend/src" -d bin \
        backend/src/DatabaseConnection.java \
        backend/src/CryptoEngine.java \
        backend/src/UserDao.java \
        backend/src/VaultDao.java \
        backend/src/SecureVaultApp.java 2>/dev/null || true
fi

# Launch the SecureVault Swing Desktop UI
exec "$JAVA_CMD" -cp "bin:lib/*:backend/src" SecureVaultApp "$@"
