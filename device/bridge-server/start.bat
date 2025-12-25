@echo off
REM Wokwi BLE Bridge Server - Start Script (Windows)
REM Bu script bridge server'ı başlatır ve gerekli kontrolleri yapar

echo ==========================================
echo Wokwi BLE Bridge Server - Baslatiliyor
echo ==========================================
echo.

REM Script'in bulunduğu dizine git
cd /d "%~dp0"

REM Node.js kontrolü
echo [91mNode.js kontrolu yapiliyor...[0m
where node >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [91mNode.js bulunamadi![0m
    echo.
    echo Node.js kurulumu icin:
    echo   https://nodejs.org/ adresinden indirin
    echo.
    exit /b 1
)

for /f "tokens=*" %%i in ('node -v') do set NODE_VERSION=%%i
echo [92mNode.js bulundu: %NODE_VERSION%[0m

REM npm kontrolü
echo [91mnpm kontrolu yapiliyor...[0m
where npm >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [91mnpm bulunamadi![0m
    exit /b 1
)

for /f "tokens=*" %%i in ('npm -v') do set NPM_VERSION=%%i
echo [92mnpm bulundu: %NPM_VERSION%[0m
echo.

REM Dependencies kontrolü
if not exist "node_modules" (
    echo [91mDependencies yukleniyor...[0m
    call npm install
    if %ERRORLEVEL% NEQ 0 (
        echo [91mDependencies yuklenirken hata![0m
        exit /b 1
    )
    echo [92mDependencies yuklendi[0m
    echo.
) else (
    echo [92mDependencies mevcut[0m
    echo.
)

REM .env dosyası kontrolü
if not exist ".env" (
    echo [93m.env dosyasi bulunamadi[0m
    if exist ".env.example" (
        echo [96m.env.example dosyasindan kopyalayabilirsiniz:[0m
        echo    copy .env.example .env
        echo.
        echo Veya environment variable'lari manuel olarak belirtebilirsiniz:
        echo    set SERIAL_PORT=COM3
        echo    npm start
        echo.
    )
) else (
    echo [92m.env dosyasi bulundu[0m
    echo.
)

REM Bridge server'ı başlat
echo [91mBridge server baslatiliyor...[0m
echo.
echo ==========================================
echo.

call npm start

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [91mBridge server baslatilamadi![0m
    exit /b 1
)

