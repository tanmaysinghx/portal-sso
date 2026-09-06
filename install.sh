#!/usr/bin/env bash
set -euo pipefail

# Portal SSO - One-Line Installer (Jenkins LTS Style)
# Usage:
#   curl -fsSL https://raw.githubusercontent.com/tanmaysinghx/portal-sso/main/install.sh | bash
# Or run locally:
#   ./install.sh

PORTAL_VERSION="${PORTAL_VERSION:-latest}"
INSTALL_DIR="${INSTALL_DIR:-/opt/portal-sso}"
PORTAL_HOME="${PORTAL_HOME:-$HOME/.portal-sso}"
PORTAL_PORT="${PORTAL_PORT:-8080}"
GITHUB_REPO="tanmaysinghx/portal-sso"

echo "==============================================================="
echo "                Installing Portal SSO (LTS 25)                "
echo "==============================================================="

# 1. Detect Operating System
OS="$(uname -s)"
case "$OS" in
    Linux*)     PLATFORM="Linux";;
    Darwin*)    PLATFORM="macOS";;
    *)          PLATFORM="UNKNOWN:$OS";;
esac

echo "Detected OS: $PLATFORM"

# 2. Check Java 25
JAVA_CMD=""
if command -v java >/dev/null 2>&1; then
    JAVA_CMD="java"
elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
fi

check_java_version() {
    if [ -z "$JAVA_CMD" ]; then
        return 1
    fi
    local ver
    ver=$("$JAVA_CMD" -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
    if [ "${ver:-0}" -ge 25 ]; then
        return 0
    else
        return 2
    fi
}

JAVA_STATUS=0
check_java_version || JAVA_STATUS=$?

if [ "$JAVA_STATUS" -ne 0 ]; then
    echo ""
    echo "⚠️  Java 25 is required to run Portal SSO."
    if [ "$JAVA_STATUS" -eq 2 ]; then
        CURRENT_VER=$("$JAVA_CMD" -version 2>&1 | awk -F '"' '/version/ {print $2}')
        echo "   Found Java version $CURRENT_VER, but version 25 or higher is required."
    else
        echo "   No Java runtime was found in your PATH."
    fi
    echo ""
    echo "To install Java 25 (Eclipse Temurin / OpenJDK 25):"
    if [ "$PLATFORM" = "macOS" ]; then
        echo "   brew install --cask temurin@25"
    elif [ "$PLATFORM" = "Linux" ]; then
        echo "   # Ubuntu / Debian:"
        echo "   sudo apt install -y openjdk-25-jdk"
        echo ""
        echo "   # Or using SDKMAN! (any Linux / macOS):"
        echo "   sdk install java 25-tem"
    fi
    echo ""
    read -r -p "Do you want to continue downloading Portal SSO anyway? [y/N] " CONTINUE_ANYWAY
    if [[ ! "$CONTINUE_ANYWAY" =~ ^[Yy]$ ]]; then
        echo "Aborted. Please install Java 25 and re-run this installer."
        exit 1
    fi
fi

# 3. Create Home Directory
mkdir -p "$PORTAL_HOME/data"
mkdir -p "$PORTAL_HOME/secrets"
chmod 700 "$PORTAL_HOME/secrets"

TARGET_JAR="$PORTAL_HOME/portal-sso.jar"

# 4. Download or Copy portal-sso.jar
if [ -f "portal-server/target/portal-sso.jar" ]; then
    echo "Found local build artifact. Copying to $TARGET_JAR..."
    cp "portal-server/target/portal-sso.jar" "$TARGET_JAR"
else
    echo "Downloading Portal SSO from GitHub releases ($PORTAL_VERSION)..."
    if [ "$PORTAL_VERSION" = "latest" ]; then
        DOWNLOAD_URL="https://github.com/$GITHUB_REPO/releases/latest/download/portal-sso.jar"
    else
        DOWNLOAD_URL="https://github.com/$GITHUB_REPO/releases/download/$PORTAL_VERSION/portal-sso.jar"
    fi

    if command -v curl >/dev/null 2>&1; then
        curl -fsSL -o "$TARGET_JAR" "$DOWNLOAD_URL" || {
            echo "Direct download failed or release not yet uploaded."
            echo "Falling back to building from source..."
            ./build.sh
            cp "portal-server/target/portal-sso.jar" "$TARGET_JAR"
        }
    elif command -v wget >/dev/null 2>&1; then
        wget -q -O "$TARGET_JAR" "$DOWNLOAD_URL" || {
            echo "Direct download failed or release not yet uploaded."
            echo "Falling back to building from source..."
            ./build.sh
            cp "portal-server/target/portal-sso.jar" "$TARGET_JAR"
        }
    fi
fi

echo ""
echo "==============================================================="
echo "       Portal SSO has been successfully installed!            "
echo "==============================================================="
echo "JAR Location: $TARGET_JAR"
echo "Home Data:    $PORTAL_HOME"
echo ""
echo "To start Portal SSO right now, run:"
echo "  java -jar $TARGET_JAR --httpPort=$PORTAL_PORT"
echo ""
echo "On first startup, an administrator account and initialAdminPassword"
echo "will be automatically created and displayed on your screen."
echo "==============================================================="
