@echo off
setlocal enabledelayedexpansion
REM Engelsiz Yaşam Asistanı - Android Build Script (Windows)
REM Bu script Android projesini build eder ve gerekli kurulumları yapar

echo ==========================================
echo Engelsiz Yaşam Asistanı - Android Build
echo ==========================================
echo.

REM Proje dizinine git
cd /d "%~dp0"

REM ==========================================
REM 1. Java Kurulumu ve Kontrolü
REM ==========================================
echo.
echo [INFO] Java kontrolü yapılıyor...

where java >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Java bulunamadı!
    echo [INFO] Java 17 kurulumu için:
    echo   1. Adoptium'dan Java 17 indirin: https://adoptium.net/
    echo   2. İndirilen .msi dosyasını çalıştırın
    echo   3. Kurulumdan sonra bu script'i tekrar çalıştırın
    echo.
    echo Veya Chocolatey ile:
    echo   choco install openjdk17
    pause
    exit /b 1
)

for /f "tokens=2" %%i in ('java -version 2^>^&1 ^| findstr /i "version"') do set JAVA_VERSION=%%i
echo [OK] Java bulundu: %JAVA_VERSION%

REM JAVA_HOME kontrolü
if "%JAVA_HOME%"=="" (
    echo [INFO] JAVA_HOME ayarlanıyor...
    for /f "tokens=*" %%i in ('where java') do set JAVA_PATH=%%i
    for %%i in ("%JAVA_PATH%") do set JAVA_HOME=%%~dpi..
    set "JAVA_HOME=%JAVA_HOME:~0,-1%"
    echo [OK] JAVA_HOME: %JAVA_HOME%
)

REM ==========================================
REM 2. Android SDK Kontrolü ve Kurulumu
REM ==========================================
echo.
echo [INFO] Android SDK kontrolü yapılıyor...

if "%ANDROID_HOME%"=="" (
    REM Standart konumları kontrol et
    if exist "%LOCALAPPDATA%\Android\Sdk" (
        set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
    ) else if exist "%USERPROFILE%\AppData\Local\Android\Sdk" (
        set ANDROID_HOME=%USERPROFILE%\AppData\Local\Android\Sdk
    ) else (
        echo [ERROR] ANDROID_HOME bulunamadı!
        echo.
        echo Android SDK kurulumu için:
        echo   1. Android Studio'yu indirin: https://developer.android.com/studio
        echo   2. Android Studio'yu açın ve SDK'yı yükleyin
        echo   3. SDK konumu genellikle: %LOCALAPPDATA%\Android\Sdk
        echo.
        set /p SDK_PATH="SDK konumunu girin (veya Enter ile çıkış): "
        if "%SDK_PATH%"=="" (
            echo [ERROR] Android SDK gerekli!
            pause
            exit /b 1
        )
        if exist "%SDK_PATH%" (
            set ANDROID_HOME=%SDK_PATH%
            setx ANDROID_HOME "%SDK_PATH%"
            echo [OK] ANDROID_HOME ayarlandı: %ANDROID_HOME%
        ) else (
            echo [ERROR] Geçersiz SDK konumu!
            pause
            exit /b 1
        )
    )
)

echo [OK] ANDROID_HOME: %ANDROID_HOME%

REM Android SDK platform-tools kontrolü
if not exist "%ANDROID_HOME%\platform-tools" (
    echo [WARNING] Android SDK platform-tools bulunamadı!
    echo Lütfen Android Studio'da SDK'yı güncelleyin
)

REM ==========================================
REM 3. Gradle Wrapper Kontrolü
REM ==========================================
echo.
echo [INFO] Gradle wrapper kontrolü yapılıyor...

if not exist "gradlew.bat" (
    echo [INFO] Gradle wrapper oluşturuluyor...
    
    REM Gradle kurulumu kontrolü
    where gradle >nul 2>&1
    if %ERRORLEVEL% NEQ 0 (
        echo [INFO] Gradle kuruluyor...
        echo.
        echo Gradle kurulumu için:
        echo   1. Chocolatey ile: choco install gradle
        echo   2. Veya manuel: https://gradle.org/install/
        echo.
        set /p INSTALL_GRADLE="Gradle kurulu mu? (y/n): "
        if /i not "%INSTALL_GRADLE%"=="y" (
            echo [ERROR] Gradle gerekli!
            pause
            exit /b 1
        )
    )
    
    REM Gradle wrapper oluştur
    gradle wrapper --gradle-version 8.10.2
    if %ERRORLEVEL% NEQ 0 (
        echo [ERROR] Gradle wrapper oluşturulamadı!
        pause
        exit /b 1
    )
    
    echo [OK] Gradle wrapper oluşturuldu
)

REM Gradle wrapper jar kontrolü
if not exist "gradle\wrapper\gradle-wrapper.jar" (
    echo [INFO] Gradle wrapper jar indiriliyor...
    mkdir gradle\wrapper 2>nul
    curl -L https://raw.githubusercontent.com/gradle/gradle/v8.10.2/gradle/wrapper/gradle-wrapper.jar -o gradle\wrapper\gradle-wrapper.jar
    if %ERRORLEVEL% NEQ 0 (
        echo [WARNING] Gradle wrapper jar indirilemedi, build sırasında otomatik indirilecek
    )
)

REM ==========================================
REM 4. Resources Dosyalarını Kopyala
REM ==========================================
echo.
echo [INFO] Resources dosyalarını kopyalıyor...
if not exist "src\main\res\raw" mkdir "src\main\res\raw"

REM Doğru yol: apps\android -> apps\resources
if exist "..\resources\menu-structure.json" (
    copy /Y "..\resources\menu-structure.json" "src\main\res\raw\menu_structure.json" >nul 2>&1
    echo [OK] menu-structure.json kopyalandı
) else if exist "..\..\apps\resources\menu-structure.json" (
    copy /Y "..\..\apps\resources\menu-structure.json" "src\main\res\raw\menu_structure.json" >nul 2>&1
    echo [OK] menu-structure.json kopyalandı
) else (
    echo [WARNING] menu-structure.json bulunamadı! (Aranan konumlar: ..\resources, ..\..\apps\resources)
)

if exist "..\resources\tts-config.json" (
    copy /Y "..\resources\tts-config.json" "src\main\res\raw\tts_config.json" >nul 2>&1
    echo [OK] tts-config.json kopyalandı
) else if exist "..\..\apps\resources\tts-config.json" (
    copy /Y "..\..\apps\resources\tts-config.json" "src\main\res\raw\tts_config.json" >nul 2>&1
    echo [OK] tts-config.json kopyalandı
) else (
    echo [WARNING] tts-config.json bulunamadı! (Aranan konumlar: ..\resources, ..\..\apps\resources)
)

REM ==========================================
REM 5. local.properties Kontrolü
REM ==========================================
echo.
echo [INFO] local.properties kontrolü yapılıyor...

if not exist "local.properties" (
    if exist "local.properties.example" (
        echo [INFO] local.properties.example'dan local.properties oluşturuluyor...
        copy /Y "local.properties.example" "local.properties" >nul
        
        REM SDK yolunu ekle
        echo. >> local.properties
        echo sdk.dir=%ANDROID_HOME% >> local.properties
        
        echo [WARNING] Lütfen local.properties dosyasını düzenleyip GOOGLE_TTS_API_KEY'i ekleyin!
    ) else (
        echo [INFO] local.properties oluşturuluyor...
        (
            echo # Android SDK location
            echo sdk.dir=%ANDROID_HOME%
            echo.
            echo # Google TTS API Key
            echo # Bu key'i Google Cloud Console'dan alın: https://console.cloud.google.com/apis/credentials
            echo GOOGLE_TTS_API_KEY=your-api-key-here
        ) > local.properties
        echo [WARNING] Lütfen local.properties dosyasını düzenleyip GOOGLE_TTS_API_KEY'i ekleyin!
    )
) else (
    REM SDK yolunu kontrol et ve güncelle
    findstr /C:"sdk.dir" local.properties >nul 2>&1
    if %ERRORLEVEL% NEQ 0 (
        echo sdk.dir=%ANDROID_HOME% >> local.properties
        echo [OK] SDK yolu local.properties'e eklendi
    )
)

REM ==========================================
REM 6. gradle.properties Kontrolü
REM ==========================================
echo.
echo [INFO] gradle.properties kontrolü yapılıyor...

if not exist "gradle.properties" (
    echo [INFO] gradle.properties oluşturuluyor...
    (
        echo # Project-wide Gradle settings.
        echo org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
        echo android.useAndroidX=true
        echo android.enableJetifier=true
        echo kotlin.code.style=official
    ) > gradle.properties
    echo [OK] gradle.properties oluşturuldu
)

REM ==========================================
REM 7. Clean Build İşlemi
REM ==========================================
echo.
echo [INFO] Clean build yapılıyor...

REM Android SDK platform-tools PATH'ini ekle (adb için)
set PATH=%ANDROID_HOME%\platform-tools;%PATH%

REM Clean işlemi
call gradlew.bat clean --no-daemon
if %ERRORLEVEL% NEQ 0 (
    echo [WARNING] Clean işlemi sırasında uyarı oluştu, devam ediliyor...
)

echo [OK] Clean tamamlandı

REM ==========================================
REM 8. Build İşlemi
REM ==========================================
echo.
echo [INFO] Proje build ediliyor (dependencies + compile + APK)...

REM Tek komutla tüm işlemleri yap: dependencies indir, derle, APK oluştur
call gradlew.bat assembleDebug --no-daemon
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Build hatası!
    echo Lütfen hata mesajlarını kontrol edin.
    pause
    exit /b 1
)

echo.
echo ==========================================
echo [OK] Build başarılı!
echo ==========================================
echo.
echo APK dosyası: build\outputs\apk\debug\EngelsizYasamAsistani-debug.apk
echo.

REM ==========================================
REM 9. Cihaza Yükleme (Opsiyonel)
REM ==========================================
echo [INFO] Cihaza yükleme işlemi...
echo.

REM ADB cihaz kontrolü
where adb >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [WARNING] ADB bulunamadı! (Android SDK platform-tools)
    echo    Cihaza manuel yüklemek için:
    echo    adb install -r build\outputs\apk\debug\EngelsizYasamAsistani-debug.apk
    goto :end_install
)

REM Cihaz sayısını kontrol et
set DEVICE_COUNT=0
set DEVICE_ID=
for /f "tokens=1,*" %%a in ('adb devices ^| findstr /C:"device"') do (
    set /a DEVICE_COUNT+=1
    if !DEVICE_COUNT!==1 (
        set DEVICE_ID=%%a
    )
)

if %DEVICE_COUNT%==0 (
    echo [WARNING] Bağlı cihaz bulunamadı!
    echo    USB ile bağlı bir cihaz veya emulator bekleniyor...
    echo.
    echo Cihaza manuel yüklemek için:
    echo   adb install -r build\outputs\apk\debug\EngelsizYasamAsistani-debug.apk
    goto :end_install
)

if %DEVICE_COUNT%==1 (
    REM Tek cihaz bulundu
    echo [INFO] Tek cihaz bulundu: %DEVICE_ID%
    echo [INFO] Eski APK kaldırılıyor...
    adb -s %DEVICE_ID% uninstall com.gormeengelliler.android >nul 2>&1
    echo [INFO] Yeni APK yükleniyor...
    adb -s %DEVICE_ID% install -r build\outputs\apk\debug\EngelsizYasamAsistani-debug.apk
    if %ERRORLEVEL% EQU 0 (
        echo [OK] APK başarıyla yüklendi!
    ) else (
        echo [ERROR] APK yükleme hatası!
    )
) else (
    REM Birden fazla cihaz
    echo [WARNING] Birden fazla cihaz bulundu!
    echo    Cihazlar:
    adb devices | findstr /C:"device"
    echo.
    echo Belirli bir cihaza yüklemek için:
    echo   adb -s ^<DEVICE_ID^> uninstall com.gormeengelliler.android
    echo   adb -s ^<DEVICE_ID^> install -r build\outputs\apk\debug\EngelsizYasamAsistani-debug.apk
)

:end_install
echo.
echo Tam build (lint dahil) için:
echo   gradlew.bat build
echo.

pause

