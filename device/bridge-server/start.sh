#!/bin/bash

# Wokwi BLE Bridge Server - Start Script (Mac/Linux)
# Bu script bridge server'ı başlatır ve gerekli kontrolleri yapar

set -e

# Android SDK platform-tools PATH'ini ekle (adb için)
if [ -d "$HOME/Library/Android/sdk/platform-tools" ]; then
    export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
elif [ -d "$HOME/Android/Sdk/platform-tools" ]; then
    export PATH="$HOME/Android/Sdk/platform-tools:$PATH"
fi

echo "=========================================="
echo "Wokwi BLE Bridge Server - Başlatılıyor"
echo "=========================================="
echo ""

# Script'in bulunduğu dizine git
cd "$(dirname "$0")"

# Node.js kontrolü
echo "📦 Node.js kontrolü yapılıyor..."
if ! command -v node &> /dev/null; then
    echo "❌ Node.js bulunamadı!"
    echo ""
    echo "Node.js kurulumu için:"
    echo "  macOS: brew install node"
    echo "  Linux: sudo apt-get install nodejs npm"
    echo ""
    exit 1
fi

NODE_VERSION=$(node -v)
echo "✅ Node.js bulundu: $NODE_VERSION"

# Node.js versiyon kontrolü (18+ gerekli)
NODE_MAJOR_VERSION=$(node -v | cut -d'v' -f2 | cut -d'.' -f1)
if [ "$NODE_MAJOR_VERSION" -lt 18 ]; then
    echo "⚠️  Uyarı: Node.js 18+ önerilir (mevcut: $NODE_VERSION)"
    echo ""
fi

# npm kontrolü
echo "📦 npm kontrolü yapılıyor..."
if ! command -v npm &> /dev/null; then
    echo "❌ npm bulunamadı!"
    exit 1
fi

NPM_VERSION=$(npm -v)
echo "✅ npm bulundu: $NPM_VERSION"
echo ""

# Dependencies kontrolü
if [ ! -d "node_modules" ]; then
    echo "📦 Dependencies yükleniyor..."
    npm install
    echo "✅ Dependencies yüklendi"
    echo ""
else
    echo "✅ Dependencies mevcut"
    echo ""
fi

# .env dosyası kontrolü
if [ ! -f ".env" ]; then
    echo "⚠️  .env dosyası bulunamadı"
    if [ -f ".env.example" ]; then
        echo "💡 .env.example dosyasından kopyalayabilirsiniz:"
        echo "   cp .env.example .env"
        echo ""
        echo "Veya environment variable'ları manuel olarak belirtebilirsiniz:"
        echo "   SERIAL_PORT=/dev/tty.usbserial-XXXX npm start"
        echo ""
    fi
else
    echo "✅ .env dosyası bulundu"
    echo ""
fi

# Bluetooth kontrolü (macOS için)
if [[ "$OSTYPE" == "darwin"* ]]; then
    echo "📡 Bluetooth kontrolü yapılıyor..."
    BLUETOOTH_STATUS=$(system_profiler SPBluetoothDataType 2>/dev/null | grep -i "State:.*On" || echo "")
    if [ -z "$BLUETOOTH_STATUS" ]; then
        echo "⚠️  Bluetooth durumu kontrol edilemedi (açık olduğundan emin olun)"
    else
        echo "✅ Bluetooth açık"
    fi
    echo ""
fi

# Bridge server'ı başlat
echo "🚀 Bridge server başlatılıyor..."
echo ""
echo "=========================================="
echo ""

npm start

