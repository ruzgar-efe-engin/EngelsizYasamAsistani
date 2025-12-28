#!/bin/bash

# Engelsiz Yaşam Asistanı - Android Run Script (Mac/Linux)
# Bu script APK'yı emulator'de çalıştırır, emulator yoksa kurar

set -e  # Hata durumunda dur

echo "=========================================="
echo "Engelsiz Yaşam Asistanı - Android Run"
echo "=========================================="

# Proje dizinine git
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

# ==========================================
# 1. Java Kontrolü
# ==========================================
echo ""
echo "📦 Java kontrolü yapılıyor..."

if ! command -v java &> /dev/null; then
    echo "❌ Java bulunamadı!"
    echo "📥 Java 17 kuruluyor..."
    
    if [[ "$OSTYPE" == "darwin"* ]]; then
        if ! command -v brew &> /dev/null; then
            echo "❌ Homebrew bulunamadı!"
            exit 1
        fi
        brew install openjdk@17
        export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"
        export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
    else
        echo "Linux için Java kurulumu gerekli"
        exit 1
    fi
else
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -lt 17 ]; then
        echo "⚠️  Java 17 gerekli, kuruluyor..."
        if [[ "$OSTYPE" == "darwin"* ]] && command -v brew &> /dev/null; then
            brew install openjdk@17
            export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"
            export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
        fi
    else
        echo "✅ Java $JAVA_VERSION bulundu"
        if [[ "$OSTYPE" == "darwin"* ]]; then
            JAVA_HOME_PATH=$(/usr/libexec/java_home -v 17 2>/dev/null || echo "")
            if [ -z "$JAVA_HOME_PATH" ]; then
                if [ -d "/opt/homebrew/opt/openjdk@17" ]; then
                    export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
                    export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"
                fi
            else
                export JAVA_HOME="$JAVA_HOME_PATH"
            fi
        fi
    fi
fi

# ==========================================
# 2. Android SDK ve Emulator Kontrolü
# ==========================================
echo ""
echo "📦 Android SDK kontrolü yapılıyor..."

if [ -z "$ANDROID_HOME" ]; then
    if [ -d "$HOME/Library/Android/sdk" ]; then
        export ANDROID_HOME="$HOME/Library/Android/sdk"
    elif [ -d "$HOME/Android/Sdk" ]; then
        export ANDROID_HOME="$HOME/Android/Sdk"
    else
        echo "❌ ANDROID_HOME bulunamadı!"
        echo "Lütfen Android SDK'yı kurun: https://developer.android.com/studio"
        exit 1
    fi
fi

# Android SDK platform-tools PATH'ini ekle (adb için)
export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/tools:$ANDROID_HOME/tools/bin:$ANDROID_HOME/platform-tools:$PATH"

echo "✅ ANDROID_HOME: $ANDROID_HOME"
echo "✅ PATH'e platform-tools eklendi: $ANDROID_HOME/platform-tools"

# Emulator kontrolü
if [ ! -f "$ANDROID_HOME/emulator/emulator" ]; then
    echo "❌ Android Emulator bulunamadı!"
    echo "📥 Emulator kuruluyor..."
    
    # SDK Manager ile emulator kur
    if [ -f "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
        "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "emulator" "platform-tools" "platforms;android-34" "system-images;android-34;google_apis;x86_64"
    elif [ -f "$ANDROID_HOME/tools/bin/sdkmanager" ]; then
        "$ANDROID_HOME/tools/bin/sdkmanager" "emulator" "platform-tools" "platforms;android-34" "system-images;android-34;google_apis;x86_64"
    else
        echo "❌ SDK Manager bulunamadı!"
        echo "Lütfen Android Studio'yu kurun ve SDK'yı yükleyin"
        exit 1
    fi
fi

# ==========================================
# 3. AVD (Android Virtual Device) Kontrolü
# ==========================================
echo ""
echo "📱 AVD kontrolü yapılıyor..."

# AVD listesini kontrol et
AVD_LIST=$(emulator -list-avds 2>/dev/null || echo "")

if [ -z "$AVD_LIST" ]; then
    echo "❌ AVD bulunamadı!"
    echo "📥 AVD oluşturuluyor..."
    
    # AVD oluştur
    AVD_NAME="EngelsizYasamAsistani_AVD"
    
    # System image kontrolü
    SYSTEM_IMAGE="system-images;android-34;google_apis;x86_64"
    
    # AVD oluştur
    echo "no" | avdmanager create avd -n "$AVD_NAME" -k "$SYSTEM_IMAGE" -d "pixel_5" || {
        echo "⚠️  AVD oluşturma hatası, manuel oluşturma gerekebilir"
        echo "Android Studio'da AVD Manager'ı açın ve bir AVD oluşturun"
        exit 1
    }
    
    echo "✅ AVD oluşturuldu: $AVD_NAME"
    AVD_TO_USE="$AVD_NAME"
else
    # İlk AVD'yi kullan
    AVD_TO_USE=$(echo "$AVD_LIST" | head -n 1)
    echo "✅ AVD bulundu: $AVD_TO_USE"
fi

# ==========================================
# 4. APK Kontrolü
# ==========================================
echo ""
echo "📦 APK kontrolü yapılıyor..."

APK_PATH="build/outputs/apk/debug/EngelsizYasamAsistani-debug.apk"

if [ ! -f "$APK_PATH" ]; then
    echo "❌ APK bulunamadı!"
    echo "📥 APK build ediliyor..."
    ./build.sh || {
        echo "❌ Build hatası!"
        exit 1
    }
fi

echo "✅ APK bulundu: $APK_PATH"

# ==========================================
# 5. Emulator Başlatma
# ==========================================
echo ""
echo "🚀 Emulator başlatılıyor..."

# Çalışan emulator var mı kontrol et
RUNNING_EMULATOR=$(adb devices | grep "emulator" | awk '{print $1}' | head -n 1)

if [ -z "$RUNNING_EMULATOR" ]; then
    echo "📱 Emulator başlatılıyor: $AVD_TO_USE"
    
    # Emulator'ü arka planda başlat
    emulator -avd "$AVD_TO_USE" -no-snapshot-load > /dev/null 2>&1 &
    EMULATOR_PID=$!
    
    echo "⏳ Emulator başlatılıyor, lütfen bekleyin..."
    
    # Emulator'ün hazır olmasını bekle (maksimum 180 saniye)
    TIMEOUT=180
    ELAPSED=0
    echo -n "⏳ Emulator başlatılıyor"
    while [ $ELAPSED -lt $TIMEOUT ]; do
        if adb devices | grep -q "device$"; then
            # Package service'in hazır olmasını bekle
            if adb shell "getprop sys.boot_completed" 2>/dev/null | grep -q "1"; then
                echo ""
                echo "✅ Emulator hazır!"
                break
            fi
        fi
        sleep 3
        ELAPSED=$((ELAPSED + 3))
        echo -n "."
    done
    
    if [ $ELAPSED -ge $TIMEOUT ]; then
        echo ""
        echo "❌ Emulator başlatılamadı (timeout)"
        kill $EMULATOR_PID 2>/dev/null || true
        exit 1
    fi
    
    # Ek bekleme (boot tamamlansın ve servisler hazır olsun)
    echo "⏳ Emulator servisleri hazırlanıyor..."
    adb wait-for-device
    sleep 10
    
    # Package service'in hazır olmasını kontrol et
    RETRY=0
    while [ $RETRY -lt 30 ]; do
        if adb shell "pm list packages" >/dev/null 2>&1; then
            echo "✅ Package service hazır!"
            break
        fi
        sleep 2
        RETRY=$((RETRY + 1))
    done
else
    echo "✅ Emulator zaten çalışıyor: $RUNNING_EMULATOR"
fi

# ==========================================
# 6. APK Yükleme ve Çalıştırma
# ==========================================
echo ""
echo "📲 APK yükleniyor ve çalıştırılıyor..."

# APK'yı yükle
echo "📲 APK yükleniyor..."
adb install -r "$APK_PATH"
INSTALL_RESULT=$?

if [ $INSTALL_RESULT -eq 0 ]; then
    echo "✅ APK başarıyla yüklendi"
else
    # Zaten yüklü olabilir, kontrol et
    if adb shell pm list packages | grep -q "$PACKAGE_NAME"; then
        echo "✅ APK zaten yüklü"
    else
        echo "⚠️  APK yükleme hatası, tekrar deneniyor..."
        sleep 5
        adb install -r "$APK_PATH" || {
            echo "❌ APK yüklenemedi!"
            exit 1
        }
    fi
fi

# Uygulamayı başlat
PACKAGE_NAME="com.gormeengelliler.android"
MAIN_ACTIVITY="com.gormeengelliler.android.MainActivity"

echo "🚀 Uygulama başlatılıyor..."
adb shell am start -n "$PACKAGE_NAME/$MAIN_ACTIVITY" || {
    echo "⚠️  Uygulama başlatma hatası, tekrar deneniyor..."
    sleep 3
    adb shell am start -n "$PACKAGE_NAME/$MAIN_ACTIVITY" || {
        echo "⚠️  Uygulama başlatılamadı, manuel olarak başlatabilirsiniz"
    }
}

echo ""
echo "=========================================="
echo "✅ Uygulama emulator'de çalıştırıldı!"
echo "=========================================="
echo ""
echo "Emulator'ü kapatmak için:"
echo "  adb emu kill"
echo ""
echo "Log'ları görmek için:"
echo "  adb logcat | grep EngelsizYasamAsistani"
echo ""

