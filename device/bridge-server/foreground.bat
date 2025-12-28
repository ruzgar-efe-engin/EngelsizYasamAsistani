@echo off
REM Wokwi BLE Bridge Server - Foreground Start Script (Windows)
REM Bu script bridge server'ı foreground'da (terminal'de) başlatır

REM Android SDK platform-tools PATH'ini ekle (adb için)
if "%ANDROID_HOME%"=="" (
    if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools" (
        set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
    ) else if exist "%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools" (
        set ANDROID_HOME=%USERPROFILE%\AppData\Local\Android\Sdk
    )
)
if not "%ANDROID_HOME%"=="" (
    set PATH=%ANDROID_HOME%\platform-tools;%PATH%
)

echo ==========================================
echo Wokwi BLE Bridge Server - Foreground Mode
echo ==========================================
echo.

REM Script'in bulunduğu dizine git
cd /d "%~dp0"

REM Node.js kontrolü
where node >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Node.js bulunamadı!
    echo    Kurulum: https://nodejs.org/ adresinden indirin
    pause
    exit /b 1
)

REM npm kontrolü
where npm >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] npm bulunamadı!
    pause
    exit /b 1
)

REM Dependencies kontrolü
if not exist "node_modules" (
    echo [INFO] Dependencies yükleniyor...
    call npm install
    if %ERRORLEVEL% NEQ 0 (
        echo [ERROR] Dependencies yüklenirken hata!
        pause
        exit /b 1
    )
    echo [OK] Dependencies yüklendi
    echo.
)

REM RFC2217 kullanımını aktif et (Wokwi için)
set USE_RFC2217=true

REM Bridge server'ı foreground'da başlat
echo [INFO] Bridge server foreground'da başlatılıyor...
echo    (Çıkmak için: Ctrl+C)
echo.
echo ==========================================
echo.

REM Foreground'da başlat (npm start direkt çalışır, background'a göndermez)
call npm start

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Bridge server başlatılamadı!
    pause
    exit /b 1
)

