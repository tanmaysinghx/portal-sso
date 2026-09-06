#!/usr/bin/env bash
set -euo pipefail

# Portal SSO - Root Build Script
# Builds the Angular frontend console and Spring Boot backend into a single executable JAR:
# portal-server/target/portal-sso.jar

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Building Portal SSO ==="

# Check Java 25
if ! command -v java >/dev/null 2>&1; then
    echo "Error: java is not installed or not in PATH."
    echo "Portal SSO requires Java 25+."
    exit 1
fi

JAVA_MAJOR_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "${JAVA_MAJOR_VERSION:-0}" -lt 25 ]; then
    echo "Warning: Detected Java version $JAVA_MAJOR_VERSION. Portal SSO requires Java 25+."
fi

# 1. Build Angular console
echo "==> Building Angular Admin Console (portal-client)..."
cd "$SCRIPT_DIR/portal-client"
if [ ! -d "node_modules" ]; then
    npm ci
fi
npm run build

# 2. Build and package Spring Boot JAR
echo "==> Packaging Spring Boot executable (portal-server)..."
cd "$SCRIPT_DIR/portal-server"
./mvnw -B -DskipTests package

echo ""
echo "=== Build Complete! ==="
echo "Artifact: $SCRIPT_DIR/portal-server/target/portal-sso.jar"
echo ""
echo "Quick run:"
echo "  java -jar portal-server/target/portal-sso.jar"
echo "  java -jar portal-server/target/portal-sso.jar --httpPort=8080"
