#!/bin/bash

# Engelsiz Yaşam Asistanı - Android Build Script (Mac/Linux)
# Bu script Android projesini build eder ve gerekli kurulumları yapar

set -e  # Hata durumunda dur

echo "=========================================="
echo "Engelsiz Yaşam Asistanı - Android Build"
echo "=========================================="

# Proje dizinine git
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

# ==========================================
# 1. Java Kurulumu ve Kontrolü
# ==========================================
echo ""
echo "📦 Java kontrolü yapılıyor..."

if ! command -v java &> /dev/null; then
    echo "❌ Java bulunamadı!"
    echo "📥 Java 17 kuruluyor..."
    
    # macOS için Homebrew kontrolü
    if [[ "$OSTYPE" == "darwin"* ]]; then
        if ! command -v brew &> /dev/null; then
            echo "❌ Homebrew bulunamadı! Lütfen önce Homebrew'i kurun:"
            echo "   /bin/bash -c \"\$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)\""
            exit 1
        fi
        brew install openjdk@17
        export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"
        export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
    else
        # Linux için
        echo "Linux için Java kurulumu:"
        echo "  Ubuntu/Debian: sudo apt-get update && sudo apt-get install openjdk-17-jdk"
        echo "  Fedora: sudo dnf install java-17-openjdk-devel"
        exit 1
    fi
else
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -lt 17 ]; then
        echo "⚠️  Java 17 veya üzeri gerekli! Mevcut versiyon: $JAVA_VERSION"
        echo "📥 Java 17 kuruluyor..."
        
        if [[ "$OSTYPE" == "darwin"* ]]; then
            if command -v brew &> /dev/null; then
                brew install openjdk@17
                export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"
                export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
            else
                echo "❌ Homebrew bulunamadı!"
                exit 1
            fi
        else
            echo "Lütfen Java 17'yi manuel olarak kurun"
            exit 1
        fi
    else
        echo "✅ Java $JAVA_VERSION bulundu"
        # JAVA_HOME ayarla
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
# 2. Android SDK Kontrolü ve Kurulumu
# ==========================================
echo ""
echo "📦 Android SDK kontrolü yapılıyor..."

if [ -z "$ANDROID_HOME" ]; then
    # Standart konumları kontrol et
    if [ -d "$HOME/Library/Android/sdk" ]; then
        export ANDROID_HOME="$HOME/Library/Android/sdk"
    elif [ -d "$HOME/Android/Sdk" ]; then
        export ANDROID_HOME="$HOME/Android/Sdk"
    else
        echo "❌ ANDROID_HOME bulunamadı!"
        echo ""
        echo "Android SDK kurulumu için:"
        echo "  1. Android Studio'yu indirin: https://developer.android.com/studio"
        echo "  2. Android Studio'yu açın ve SDK'yı yükleyin"
        echo "  3. SDK konumu genellikle:"
        echo "     macOS: ~/Library/Android/sdk"
        echo "     Linux: ~/Android/Sdk"
        echo ""
        echo "Veya manuel olarak SDK'yı indirip kurun:"
        echo "  https://developer.android.com/studio#command-tools"
        echo ""
        read -p "Android SDK kurulu mu? (y/n): " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            read -p "SDK konumunu girin: " SDK_PATH
            if [ -d "$SDK_PATH" ]; then
                export ANDROID_HOME="$SDK_PATH"
                echo "export ANDROID_HOME=\"$SDK_PATH\"" >> ~/.zshrc 2>/dev/null || echo "export ANDROID_HOME=\"$SDK_PATH\"" >> ~/.bashrc
                echo "✅ ANDROID_HOME ayarlandı: $ANDROID_HOME"
            else
                echo "❌ Geçersiz SDK konumu!"
                exit 1
            fi
        else
            echo "❌ Android SDK gerekli!"
            exit 1
        fi
    fi
fi

echo "✅ ANDROID_HOME: $ANDROID_HOME"

# Android SDK platform-tools kontrolü
if [ ! -d "$ANDROID_HOME/platform-tools" ]; then
    echo "⚠️  Android SDK platform-tools bulunamadı!"
    echo "Lütfen Android Studio'da SDK'yı güncelleyin veya platform-tools'u kurun"
fi

# ==========================================
# 3. Gradle Wrapper Kontrolü
# ==========================================
echo ""
echo "📦 Gradle wrapper kontrolü yapılıyor..."

if [ ! -f "gradlew" ]; then
    echo "📥 Gradle wrapper oluşturuluyor..."
    
    # Gradle kurulumu kontrolü
    if ! command -v gradle &> /dev/null; then
        echo "📥 Gradle kuruluyor..."
        
        if [[ "$OSTYPE" == "darwin"* ]]; then
            if command -v brew &> /dev/null; then
                brew install gradle
            else
                echo "❌ Homebrew bulunamadı! Gradle'ı manuel kurun:"
                echo "   https://gradle.org/install/"
                exit 1
            fi
        else
            # Linux için
            if command -v sdkman &> /dev/null; then
                sdk install gradle 8.5
            else
                echo "Gradle'ı manuel kurun: https://gradle.org/install/"
                exit 1
            fi
        fi
    fi
    
    # Gradle wrapper oluştur
    gradle wrapper --gradle-version 8.10.2
    chmod +x gradlew
    echo "✅ Gradle wrapper oluşturuldu"
fi

# Gradle wrapper'ı çalıştırılabilir yap
chmod +x gradlew 2>/dev/null || true

# Gradle wrapper jar kontrolü
if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    echo "📥 Gradle wrapper jar indiriliyor..."
    mkdir -p gradle/wrapper
    curl -L https://raw.githubusercontent.com/gradle/gradle/v8.10.2/gradle/wrapper/gradle-wrapper.jar -o gradle/wrapper/gradle-wrapper.jar || {
        echo "⚠️  Gradle wrapper jar indirilemedi, build sırasında otomatik indirilecek"
    }
fi

# ==========================================
# 4. Resources Dosyalarını Kopyala
# ==========================================
echo ""
echo "📋 Resources dosyalarını kopyalıyor..."
mkdir -p src/main/res/raw

# Doğru yol: apps/android -> apps/resources
RESOURCES_DIR="../resources"
if [ ! -d "$RESOURCES_DIR" ]; then
    # Alternatif: root'tan apps/resources
    RESOURCES_DIR="../../apps/resources"
fi

if [ -f "$RESOURCES_DIR/menu-structure.json" ]; then
    cp "$RESOURCES_DIR/menu-structure.json" src/main/res/raw/menu_structure.json
    echo "✅ menu-structure.json kopyalandı"
elif [ -f "../../resources/menu-structure.json" ]; then
    cp ../../resources/menu-structure.json src/main/res/raw/menu_structure.json
    echo "✅ menu-structure.json kopyalandı"
else
    echo "⚠️  menu-structure.json bulunamadı! (Aranan konumlar: $RESOURCES_DIR, ../../resources)"
fi

if [ -f "$RESOURCES_DIR/tts-config.json" ]; then
    cp "$RESOURCES_DIR/tts-config.json" src/main/res/raw/tts_config.json
    echo "✅ tts-config.json kopyalandı"
elif [ -f "../../resources/tts-config.json" ]; then
    cp ../../resources/tts-config.json src/main/res/raw/tts_config.json
    echo "✅ tts-config.json kopyalandı"
else
    echo "⚠️  tts-config.json bulunamadı! (Aranan konumlar: $RESOURCES_DIR, ../../resources)"
fi

# ==========================================
# 5. local.properties Kontrolü
# ==========================================
echo ""
echo "📝 local.properties kontrolü yapılıyor..."

if [ ! -f "local.properties" ]; then
    if [ -f "local.properties.example" ]; then
        echo "📝 local.properties.example'dan local.properties oluşturuluyor..."
        cp local.properties.example local.properties
        
        # SDK yolunu ekle
        echo "" >> local.properties
        echo "sdk.dir=$ANDROID_HOME" >> local.properties
        
        echo "⚠️  Lütfen local.properties dosyasını düzenleyip GOOGLE_TTS_API_KEY'i ekleyin!"
    else
        echo "📝 local.properties oluşturuluyor..."
        cat > local.properties << EOF
# Android SDK location
sdk.dir=$ANDROID_HOME

# Google TTS API Key
# Bu key'i Google Cloud Console'dan alın: https://console.cloud.google.com/apis/credentials
GOOGLE_TTS_API_KEY=your-api-key-here
EOF
        echo "⚠️  Lütfen local.properties dosyasını düzenleyip GOOGLE_TTS_API_KEY'i ekleyin!"
    fi
else
    # SDK yolunu kontrol et ve güncelle
    if ! grep -q "sdk.dir" local.properties; then
        echo "sdk.dir=$ANDROID_HOME" >> local.properties
        echo "✅ SDK yolu local.properties'e eklendi"
    fi
fi

# ==========================================
# 6. gradle.properties Kontrolü
# ==========================================
echo ""
echo "📝 gradle.properties kontrolü yapılıyor..."

if [ ! -f "gradle.properties" ]; then
    echo "📝 gradle.properties oluşturuluyor..."
    cat > gradle.properties << EOF
# Project-wide Gradle settings.
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.enableJetifier=true
kotlin.code.style=official
EOF
    echo "✅ gradle.properties oluşturuldu"
fi

# ==========================================
# 7. Build İşlemi (Tek Komut)
# ==========================================
echo ""
echo "🔨 Proje build ediliyor (dependencies + compile + APK)..."

# Environment variable'ları export et
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$ANDROID_HOME"

# Tek komutla tüm işlemleri yap: dependencies indir, derle, APK oluştur
./gradlew assembleDebug --no-daemon || {
    echo "❌ Build hatası!"
    echo "Lütfen hata mesajlarını kontrol edin."
    exit 1
}

echo ""
echo "=========================================="
echo "✅ Build başarılı!"
echo "=========================================="
echo ""
echo "APK dosyası: build/outputs/apk/debug/EngelsizYasamAsistani-debug.apk"
echo ""
echo "Cihaza yüklemek için:"
echo "  ./gradlew installDebug"
echo ""
echo "Tam build (lint dahil) için:"
echo "  ./gradlew build"
echo ""

