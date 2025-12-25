#!/bin/bash

# Device Build Script for Mac/Linux
# Builds ESP32 firmware and optionally launches Wokwi simulation

set -e  # Exit on error

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🔧 Device Build Script"
echo "======================"

# Check for PlatformIO
if ! command -v pio &> /dev/null && ! command -v platformio &> /dev/null; then
    echo "❌ PlatformIO not found!"
    echo "📦 Installing PlatformIO..."
    
    # Try pipx first (recommended)
    if command -v pipx &> /dev/null; then
        pipx install platformio
    # Try pip3
    elif command -v pip3 &> /dev/null; then
        pip3 install platformio
    # Try pip
    elif command -v pip &> /dev/null; then
        pip install platformio
    else
        echo "❌ Error: pip, pip3, or pipx not found!"
        echo "Please install Python and pip first."
        exit 1
    fi
    
    # Verify installation
    if ! command -v pio &> /dev/null && ! command -v platformio &> /dev/null; then
        echo "❌ PlatformIO installation failed!"
        echo "Please install manually: https://platformio.org/install/cli"
        exit 1
    fi
    
    echo "✅ PlatformIO installed successfully"
else
    echo "✅ PlatformIO found"
fi

# Use pio or platformio command
PIO_CMD="pio"
if ! command -v pio &> /dev/null; then
    PIO_CMD="platformio"
fi

# Build the project
echo ""
echo "🔨 Building firmware..."
$PIO_CMD run

if [ $? -eq 0 ]; then
    echo "✅ Build successful!"
else
    echo "❌ Build failed!"
    exit 1
fi

# Try to launch Wokwi simulation locally
echo ""
echo "🚀 Launching Wokwi simulation locally..."

if command -v wokwi-cli &> /dev/null; then
    # Check for WOKWI_CLI_TOKEN
    if [ -z "$WOKWI_CLI_TOKEN" ]; then
        echo "⚠️  WOKWI_CLI_TOKEN environment variable not set"
        echo "📝 To get your token:"
        echo "   1. Visit https://wokwi.com/dashboard/ci"
        echo "   2. Copy your token"
        echo "   3. Set it: export WOKWI_CLI_TOKEN='your-token-here'"
        echo ""
        echo "Skipping Wokwi simulation (build was successful)"
        exit 0
    fi
    echo "Starting Wokwi CLI..."
    wokwi-cli .
    if [ $? -ne 0 ]; then
        echo "⚠️  Wokwi CLI failed to start"
        echo "Make sure you're in the device directory with wokwi.toml"
        exit 1
    fi
else
    echo "❌ Wokwi CLI not found!"
    echo "📦 Installing Wokwi CLI..."
    echo "Running installation script..."
    curl -L https://wokwi.com/ci/install.sh | sh
    if [ $? -eq 0 ]; then
        # Try multiple possible locations for wokwi-cli
        WOKWI_CLI=""
        if [ -f "$HOME/bin/wokwi-cli" ]; then
            WOKWI_CLI="$HOME/bin/wokwi-cli"
        elif [ -f "$HOME/.wokwi/bin/wokwi-cli" ]; then
            WOKWI_CLI="$HOME/.wokwi/bin/wokwi-cli"
        elif [ -f "$HOME/.local/bin/wokwi-cli" ]; then
            WOKWI_CLI="$HOME/.local/bin/wokwi-cli"
        fi
        
        if [ -n "$WOKWI_CLI" ]; then
            echo "✅ Wokwi CLI installed"
            # Check for WOKWI_CLI_TOKEN
            if [ -z "$WOKWI_CLI_TOKEN" ]; then
                echo "⚠️  WOKWI_CLI_TOKEN environment variable not set"
                echo "📝 To get your token:"
                echo "   1. Visit https://wokwi.com/dashboard/ci"
                echo "   2. Copy your token"
                echo "   3. Set it: export WOKWI_CLI_TOKEN='your-token-here'"
                echo ""
                echo "Skipping Wokwi simulation (build was successful)"
                exit 0
            fi
            echo "Starting Wokwi CLI..."
            "$WOKWI_CLI" .
            if [ $? -ne 0 ]; then
                echo "⚠️  Wokwi CLI failed to start"
                echo "Make sure you're in the device directory with wokwi.toml"
                exit 1
            fi
        else
            echo "⚠️  Wokwi CLI installed but not found in expected locations"
            echo "Please restart your terminal or run: export PATH=\"\$HOME/bin:\$HOME/.wokwi/bin:\$HOME/.local/bin:\$PATH\""
            echo "Then run: wokwi-cli start"
            exit 1
        fi
    else
        echo "❌ Failed to install Wokwi CLI"
        echo "Please install manually: curl -L https://wokwi.com/ci/install.sh | sh"
        exit 1
    fi
fi

echo ""
echo "✨ Done!"

