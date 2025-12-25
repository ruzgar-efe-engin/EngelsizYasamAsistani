#!/bin/bash

# Cursor Auto Accept - Hammerspoon Kurulum Scripti

echo "🚀 Cursor Auto Accept kurulumu başlatılıyor..."

# Hammerspoon'un yüklü olup olmadığını kontrol et
if ! command -v hammerspoon &> /dev/null && [ ! -d "/Applications/Hammerspoon.app" ]; then
    echo "📦 Hammerspoon yükleniyor..."
    brew install --cask hammerspoon
else
    echo "✅ Hammerspoon zaten yüklü"
fi

# Hammerspoon dizinini oluştur
mkdir -p ~/.hammerspoon

# Script dosyasını kopyala
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cp "$SCRIPT_DIR/cursor-auto-accept.lua" ~/.hammerspoon/cursor-auto-accept.lua

echo "✅ Script dosyası kopyalandı"

# init.lua dosyasını kontrol et ve güncelle
INIT_FILE="$HOME/.hammerspoon/init.lua"

if [ ! -f "$INIT_FILE" ]; then
    echo "-- Hammerspoon Configuration" > "$INIT_FILE"
    echo "-- Cursor Auto Accept" >> "$INIT_FILE"
    echo 'require("cursor-auto-accept").start()' >> "$INIT_FILE"
    echo "✅ init.lua dosyası oluşturuldu"
else
    # Dosyada zaten var mı kontrol et
    if ! grep -q "cursor-auto-accept" "$INIT_FILE"; then
        echo "" >> "$INIT_FILE"
        echo "-- Cursor Auto Accept" >> "$INIT_FILE"
        echo 'require("cursor-auto-accept").start()' >> "$INIT_FILE"
        echo "✅ init.lua dosyasına eklendi"
    else
        echo "ℹ️  init.lua dosyasında zaten mevcut"
    fi
fi

echo ""
echo "✨ Kurulum tamamlandı!"
echo ""
echo "📋 Sonraki adımlar:"
echo "1. Hammerspoon menü çubuğundaki ikona tıklayın"
echo "2. 'Reload Config' seçeneğini seçin"
echo "3. Cursor'u açın ve Agent modunu kullanın"
echo ""
echo "🎯 Kontrol: Cmd+Shift+A ile script'i başlatıp/durdururabilirsiniz"

