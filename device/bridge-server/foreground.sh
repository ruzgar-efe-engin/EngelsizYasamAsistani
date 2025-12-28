#!/bin/bash

# Wokwi BLE Bridge Server - Foreground Start Script (Mac/Linux)
# Bu script bridge server'ı foreground'da (terminal'de) başlatır

set -e

# Android SDK platform-tools PATH'ini ekle (adb için)
if [ -d "$HOME/Library/Android/sdk/platform-tools" ]; then
    export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
elif [ -d "$HOME/Android/Sdk/platform-tools" ]; then
    export PATH="$HOME/Android/Sdk/platform-tools:$PATH"
fi

echo "=========================================="
echo "Wokwi BLE Bridge Server - Foreground Mode"
echo "=========================================="
echo ""

# Script'in bulunduğu dizine git
cd "$(dirname "$0")"

# Node.js kontrolü
if ! command -v node &> /dev/null; then
    echo "❌ Node.js bulunamadı!"
    echo "   Kurulum: brew install node (macOS) veya sudo apt-get install nodejs npm (Linux)"
    exit 1
fi

# npm kontrolü
if ! command -v npm &> /dev/null; then
    echo "❌ npm bulunamadı!"
    exit 1
fi

# Dependencies kontrolü
if [ ! -d "node_modules" ]; then
    echo "📦 Dependencies yükleniyor..."
    npm install
    echo "✅ Dependencies yüklendi"
    echo ""
fi

# RFC2217 kullanımını aktif et (Wokwi için)
export USE_RFC2217=true

# macOS CoreBluetooth native loglarını kapat (didReceiveReadRequest spam'ini önler)
# OS_ACTIVITY_MODE=disable macOS'un os_log sistemini devre dışı bırakır
export OS_ACTIVITY_MODE=disable

# Bridge server'ı foreground'da başlat
echo "🚀 Bridge server foreground'da başlatılıyor..."
echo "   (Çıkmak için: Ctrl+C)"
echo "   OS_ACTIVITY_MODE=disable aktif (macOS native loglar filtreleniyor)"
echo ""
echo "=========================================="
echo ""

# Foreground'da başlat (npm start direkt çalışır, background'a göndermez)
# OS_ACTIVITY_MODE'u direkt komut olarak da set et (daha garantili)
OS_ACTIVITY_MODE=disable npm start

