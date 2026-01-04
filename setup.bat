@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ============================================
echo Engelsiz Yaşam Asistanı - İlk Kurulum
echo ============================================
echo.

set "ERRORS=0"

:: Git kontrolü
echo [1/6] Git kontrol ediliyor...
where git >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [HATA] Git bulunamadı!
    echo Git'i şuradan indirin: https://git-scm.com/download/win
    set /a ERRORS+=1
) else (
    for /f "tokens=*" %%i in ('git --version') do set GIT_VERSION=%%i
    echo [OK] Git bulundu: !GIT_VERSION!
)
echo.

:: Java JDK 17 kontrolü
echo [2/6] Java JDK 17 kontrol ediliyor...
where java >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [HATA] Java bulunamadı!
    echo Java JDK 17'yi şuradan indirin: https://adoptium.net/
    set /a ERRORS+=1
) else (
    for /f "tokens=*" %%i in ('java -version 2^>^&1') do set JAVA_VERSION=%%i
    echo [OK] Java bulundu: !JAVA_VERSION!
    
    :: Java versiyonunu kontrol et
    java -version 2>&1 | findstr /C:"17" >nul
    if %ERRORLEVEL% NEQ 0 (
        echo [UYARI] Java 17 olmayabilir. Java 17 kullanmanız önerilir.
    )
)
echo.

:: PlatformIO kontrolü
echo [3/6] PlatformIO kontrol ediliyor...
where pio >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [HATA] PlatformIO bulunamadı!
    echo PlatformIO'yu şuradan indirin: https://platformio.org/install/cli
    echo Veya VS Code extension olarak: https://marketplace.visualstudio.com/items?itemName=platformio.platformio-ide
    set /a ERRORS+=1
) else (
    for /f "tokens=*" %%i in ('pio --version 2^>^&1') do set PIO_VERSION=%%i
    echo [OK] PlatformIO bulundu: !PIO_VERSION!
)
echo.

:: ADB kontrolü
echo [4/6] Android Debug Bridge (ADB) kontrol ediliyor...
where adb >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [UYARI] ADB bulunamadı!
    echo ADB genellikle Android Studio ile birlikte gelir.
    echo Android Studio'yu şuradan indirin: https://developer.android.com/studio
    echo Veya sadece Command Line Tools: https://developer.android.com/studio#command-tools
    echo.
    echo ADB'yi PATH'e eklemeniz gerekecek.
    set /a ERRORS+=1
) else (
    for /f "tokens=*" %%i in ('adb version') do set ADB_VERSION=%%i
    echo [OK] ADB bulundu: !ADB_VERSION!
)
echo.

:: Android SDK kontrolü (ANDROID_HOME veya ANDROID_SDK_ROOT)
echo [5/6] Android SDK kontrol ediliyor...
if defined ANDROID_HOME (
    echo [OK] ANDROID_HOME tanımlı: %ANDROID_HOME%
) else if defined ANDROID_SDK_ROOT (
    echo [OK] ANDROID_SDK_ROOT tanımlı: %ANDROID_SDK_ROOT%
) else (
    echo [UYARI] ANDROID_HOME veya ANDROID_SDK_ROOT tanımlı değil.
    echo Android Studio kurulumu sırasında genellikle otomatik tanımlanır.
    echo Manuel olarak tanımlamak için:
    echo   setx ANDROID_HOME "C:\Users\%USERNAME%\AppData\Local\Android\Sdk"
    echo   setx ANDROID_SDK_ROOT "C:\Users\%USERNAME%\AppData\Local\Android\Sdk"
)
echo.

:: USB sürücüleri kontrolü (ESP32 için)
echo [6/6] USB sürücüleri kontrol ediliyor...
echo [BİLGİ] ESP32-S3 Zero için USB sürücüleri gerekebilir.
echo [BİLGİ] CP210x veya CH340 sürücüleri gerekebilir.
echo [BİLGİ] Sürücüleri şuradan indirin:
echo   - CP210x: https://www.silabs.com/developers/usb-to-uart-bridge-vcp-drivers
echo   - CH340: https://github.com/WCHSoftGroup/ch34xser_linux
echo [BİLGİ] Cihazı USB'ye taktığınızda Device Manager'da görünmelidir.
echo.

:: Hata özeti
echo ============================================
if %ERRORS% EQU 0 (
    echo [BAŞARILI] Tüm temel gereksinimler mevcut!
) else (
    echo [HATA] %ERRORS% eksik gereksinim bulundu.
    echo Lütfen yukarıdaki hataları düzeltin ve tekrar çalıştırın.
)
echo ============================================
echo.

:: Bağımlılıkları kur
echo Bağımlılıklar kuruluyor...
echo.

:: PlatformIO bağımlılıkları
if exist "device\platformio.ini" (
    echo [Device] PlatformIO bağımlılıkları kuruluyor...
    cd device
    pio lib install 2>nul
    if %ERRORLEVEL% EQU 0 (
        echo [OK] PlatformIO bağımlılıkları kuruldu.
    ) else (
        echo [HATA] PlatformIO bağımlılıkları kurulamadı.
    )
    cd ..
    echo.
)

:: Gradle bağımlılıkları
if exist "apps\eya\build.gradle.kts" (
    echo [EYA] Gradle bağımlılıkları kuruluyor...
    cd apps\eya
    
    :: Gradle wrapper kontrolü
    if not exist "gradlew.bat" (
        echo [HATA] gradlew.bat bulunamadı!
        echo Gradle wrapper'ı manuel olarak oluşturmanız gerekebilir.
    ) else (
        echo Gradle wrapper çalıştırılıyor...
        call gradlew.bat --version >nul 2>&1
        if %ERRORLEVEL% EQU 0 (
            echo [OK] Gradle wrapper çalışıyor.
            echo Bağımlılıklar indiriliyor (ilk sefer uzun sürebilir)...
            call gradlew.bat dependencies --configuration implementation >nul 2>&1
            if %ERRORLEVEL% EQU 0 (
                echo [OK] Gradle bağımlılıkları hazır.
            ) else (
                echo [UYARI] Gradle bağımlılıkları kontrol edilemedi.
            )
        ) else (
            echo [HATA] Gradle wrapper çalıştırılamadı.
        )
    )
    cd ..\..
    echo.
)

echo ============================================
echo Kurulum tamamlandı!
echo ============================================
echo.
echo Sonraki adımlar:
echo   1. Android cihazınızı USB ile bağlayın ve USB debugging'i açın
echo   2. ESP32-S3 Zero cihazınızı USB ile bağlayın
echo   3. build-and-install-apk.bat ile APK'yı derleyip yükleyin
echo   4. build-and-upload-device.bat ile firmware'i derleyip yükleyin
echo.
pause

