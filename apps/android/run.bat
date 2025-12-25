@echo off
REM Engelsiz Yaşam Asistanı - Android Run Script (Windows)
REM Bu script APK'yı emulator'de çalıştırır, emulator yoksa kurar

echo ==========================================
echo Engelsiz Yaşam Asistanı - Android Run
echo ==========================================
echo.

REM Proje dizinine git
cd /d "%~dp0"

REM ==========================================
REM 1. Java Kontrolü
REM ==========================================
echo.
echo [INFO] Java kontrolü yapılıyor...

where java >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Java bulunamadı!
    echo Lütfen Java 17 kurun: https://adoptium.net/
    pause
    exit /b 1
)

echo [OK] Java bulundu

REM ==========================================
REM 2. Android SDK ve Emulator Kontrolü
REM ==========================================
echo.
echo [INFO] Android SDK kontrolü yapılıyor...

if "%ANDROID_HOME%"=="" (
    if exist "%LOCALAPPDATA%\Android\Sdk" (
        set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
    ) else if exist "%USERPROFILE%\AppData\Local\Android\Sdk" (
        set ANDROID_HOME=%USERPROFILE%\AppData\Local\Android\Sdk
    ) else (
        echo [ERROR] ANDROID_HOME bulunamadı!
        echo Lütfen Android SDK'yı kurun: https://developer.android.com/studio
        pause
        exit /b 1
    )
)

set PATH=%ANDROID_HOME%\emulator;%ANDROID_HOME%\tools;%ANDROID_HOME%\tools\bin;%ANDROID_HOME%\platform-tools;%PATH%

echo [OK] ANDROID_HOME: %ANDROID_HOME%

REM Emulator kontrolü
if not exist "%ANDROID_HOME%\emulator\emulator.exe" (
    echo [ERROR] Android Emulator bulunamadı!
    echo [INFO] Emulator kuruluyor...
    
    if exist "%ANDROID_HOME%\cmdline-tools\latest\bin\sdkmanager.bat" (
        call "%ANDROID_HOME%\cmdline-tools\latest\bin\sdkmanager.bat" emulator platform-tools "platforms;android-34" "system-images;android-34;google_apis;x86_64"
    ) else if exist "%ANDROID_HOME%\tools\bin\sdkmanager.bat" (
        call "%ANDROID_HOME%\tools\bin\sdkmanager.bat" emulator platform-tools "platforms;android-34" "system-images;android-34;google_apis;x86_64"
    ) else (
        echo [ERROR] SDK Manager bulunamadı!
        echo Lütfen Android Studio'yu kurun ve SDK'yı yükleyin
        pause
        exit /b 1
    )
)

REM ==========================================
REM 3. AVD (Android Virtual Device) Kontrolü
REM ==========================================
echo.
echo [INFO] AVD kontrolü yapılıyor...

REM AVD listesini kontrol et
for /f "tokens=*" %%i in ('emulator -list-avds 2^>nul') do set AVD_LIST=%%i

if "%AVD_LIST%"=="" (
    echo [ERROR] AVD bulunamadı!
    echo [INFO] AVD oluşturuluyor...
    
    set AVD_NAME=EngelsizYasamAsistani_AVD
    set SYSTEM_IMAGE=system-images;android-34;google_apis;x86_64
    
    echo no | avdmanager create avd -n %AVD_NAME% -k %SYSTEM_IMAGE% -d pixel_5
    if %ERRORLEVEL% NEQ 0 (
        echo [WARNING] AVD oluşturma hatası, manuel oluşturma gerekebilir
        echo Android Studio'da AVD Manager'ı açın ve bir AVD oluşturun
        pause
        exit /b 1
    )
    
    echo [OK] AVD oluşturuldu: %AVD_NAME%
    set AVD_TO_USE=%AVD_NAME%
) else (
    for /f "tokens=*" %%i in ('emulator -list-avds 2^>nul') do (
        set AVD_TO_USE=%%i
        goto :avd_found
    )
    :avd_found
    echo [OK] AVD bulundu: %AVD_TO_USE%
)

REM ==========================================
REM 4. APK Kontrolü
REM ==========================================
echo.
echo [INFO] APK kontrolü yapılıyor...

set APK_PATH=build\outputs\apk\debug\EngelsizYasamAsistani-debug.apk

if not exist "%APK_PATH%" (
    echo [ERROR] APK bulunamadı!
    echo [INFO] APK build ediliyor...
    call build.bat
    if %ERRORLEVEL% NEQ 0 (
        echo [ERROR] Build hatası!
        pause
        exit /b 1
    )
)

echo [OK] APK bulundu: %APK_PATH%

REM ==========================================
REM 5. Emulator Başlatma
REM ==========================================
echo.
echo [INFO] Emulator başlatılıyor...

REM Çalışan emulator var mı kontrol et
adb devices | findstr "emulator" >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [INFO] Emulator başlatılıyor: %AVD_TO_USE%
    
    start /B emulator -avd %AVD_TO_USE% -no-snapshot-load
    
    echo [INFO] Emulator başlatılıyor, lütfen bekleyin...
    
    REM Emulator'ün hazır olmasını bekle (maksimum 180 saniye)
    set TIMEOUT=180
    set ELAPSED=0
    echo [INFO] Emulator başlatılıyor
    :wait_emulator
    timeout /t 3 /nobreak >nul
    set /a ELAPSED+=3
    adb devices | findstr "device$" >nul 2>&1
    if %ERRORLEVEL% EQU 0 (
        REM Package service'in hazır olmasını kontrol et
        adb shell "getprop sys.boot_completed" | findstr "1" >nul 2>&1
        if %ERRORLEVEL% EQU 0 (
            echo [OK] Emulator hazır!
            goto :emulator_ready
        )
    )
    if %ELAPSED% GEQ %TIMEOUT% (
        echo [ERROR] Emulator başlatılamadı (timeout)
        pause
        exit /b 1
    )
    echo|set /p="."
    goto :wait_emulator
    
    :emulator_ready
    REM Ek bekleme (boot tamamlansın ve servisler hazır olsun)
    echo [INFO] Emulator servisleri hazırlanıyor...
    adb wait-for-device
    timeout /t 10 /nobreak >nul
    
    REM Package service'in hazır olmasını kontrol et
    set RETRY=0
    :wait_package
    if %RETRY% GEQ 30 goto :package_timeout
    adb shell "pm list packages" >nul 2>&1
    if %ERRORLEVEL% EQU 0 (
        echo [OK] Package service hazır!
        goto :package_ready
    )
    timeout /t 2 /nobreak >nul
    set /a RETRY+=1
    goto :wait_package
    
    :package_timeout
    echo [WARNING] Package service hazır olmayabilir, devam ediliyor...
    
    :package_ready
) else (
    echo [OK] Emulator zaten çalışıyor
)

REM ==========================================
REM 6. APK Yükleme ve Çalıştırma
REM ==========================================
echo.
echo [INFO] APK yükleniyor ve çalıştırılıyor...

REM APK'yı yükle
set PACKAGE_NAME=com.gormeengelliler.android
set MAIN_ACTIVITY=com.gormeengelliler.android.MainActivity

echo [INFO] APK yükleniyor...
adb install -r "%APK_PATH%"
if %ERRORLEVEL% EQU 0 (
    echo [OK] APK başarıyla yüklendi
) else (
    REM Zaten yüklü olabilir, kontrol et
    adb shell pm list packages | findstr "%PACKAGE_NAME%" >nul 2>&1
    if %ERRORLEVEL% EQU 0 (
        echo [OK] APK zaten yüklü
    ) else (
        echo [WARNING] APK yükleme hatası, tekrar deneniyor...
        timeout /t 5 /nobreak >nul
        adb install -r "%APK_PATH%"
        if %ERRORLEVEL% NEQ 0 (
            echo [ERROR] APK yüklenemedi!
            pause
            exit /b 1
        )
    )
)

REM Uygulamayı başlat
echo [INFO] Uygulama başlatılıyor...
adb shell am start -n %PACKAGE_NAME%/%MAIN_ACTIVITY%
if %ERRORLEVEL% NEQ 0 (
    echo [WARNING] Uygulama başlatma hatası, tekrar deneniyor...
    timeout /t 3 /nobreak >nul
    adb shell am start -n %PACKAGE_NAME%/%MAIN_ACTIVITY%
    if %ERRORLEVEL% NEQ 0 (
        echo [WARNING] Uygulama başlatılamadı, manuel olarak başlatabilirsiniz
    )
)

echo.
echo ==========================================
echo [OK] Uygulama emulator'de çalıştırıldı!
echo ==========================================
echo.
echo Emulator'ü kapatmak için:
echo   adb emu kill
echo.
echo Log'ları görmek için:
echo   adb logcat ^| findstr EngelsizYasamAsistani
echo.

pause

