#!/bin/bash

# TTS Debug Script
# Kullanım: ./debug_tts.sh

echo "🔍 TTS Debug Logları Başlatılıyor..."
echo "Çıkmak için Ctrl+C basın"
echo ""
echo "Filtrelenen Tag'ler:"
echo "  - TTSManager"
echo "  - DeviceEventService"
echo "  - EventLogManager"
echo "  - MenuManager"
echo ""
echo "═══════════════════════════════════════════════════════════"
echo ""

# Logcat'i temizle
adb logcat -c

# Önemli logları göster
adb logcat -s TTSManager:D DeviceEventService:D EventLogManager:D MenuManager:D | grep -E --color=always "speak|stop|playAudio|isSpeaking|Handler delay|speakAsync|Menu adı|TTS başlatılıyor|playAudio|AudioTrack|isSpeaking|stop\(\)|speak\(\)|speakAsync\(\)" || adb logcat -s TTSManager:D DeviceEventService:D EventLogManager:D MenuManager:D

