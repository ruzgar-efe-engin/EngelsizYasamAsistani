@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ============================================
echo ESP32-S3 Zero Firmware Build ve Yükleme
echo ============================================
echo.

:: Proje dizinine git
cd /d "%~dp0"
if not exist "device" (
    echo [HATA] device klasörü bulunamadı!
    echo Lütfen bu dosyayı proje kök dizininde çalıştırın.
    pause
    exit /b 1
)

cd device

:: PlatformIO kontrolü
where pio >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [HATA] PlatformIO bulunamadı!
    echo PlatformIO'yu şuradan indirin: https://platformio.org/install/cli
    echo Veya VS Code extension olarak: https://marketplace.visualstudio.com/items?itemName=platformio.platformio-ide
    pause
    exit /b 1
)

:: PlatformIO.ini kontrolü
if not exist "platformio.ini" (
    echo [HATA] platformio.ini bulunamadı!
    pause
    exit /b 1
)

:: Board seçimi
echo Hangi board için derleme yapmak istersiniz?
echo   1. ESP32-S3 Zero (esp32-s3-zero) - Fiziksel cihaz
echo   2. Seeed XIAO ESP32S3 (seeed_xiao_esp32s3) - Wokwi simülasyon
echo.
set /p BOARD_CHOICE="Seçiminiz (1 veya 2): "

if "%BOARD_CHOICE%"=="1" (
    set "ENV_NAME=esp32-s3-zero"
    set "BOARD_DESC=ESP32-S3 Zero"
) else if "%BOARD_CHOICE%"=="2" (
    set "ENV_NAME=seeed_xiao_esp32s3"
    set "BOARD_DESC=Seeed XIAO ESP32S3"
) else (
    echo [HATA] Geçersiz seçim!
    pause
    exit /b 1
)

echo.
echo Seçilen board: %BOARD_DESC%
echo Environment: %ENV_NAME%
echo.

:: USB port kontrolü
echo USB portları kontrol ediliyor...
pio device list >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [UYARI] PlatformIO cihazları listeleyemedi.
) else (
    echo Bağlı cihazlar:
    pio device list
    echo.
)

:: Clean build
echo Clean build yapılıyor...
pio run -e %ENV_NAME% -t clean
if %ERRORLEVEL% NEQ 0 (
    echo [UYARI] Clean işlemi başarısız olabilir (normal olabilir).
)
echo.

:: Build
echo Firmware derleniyor...
echo (Bu işlem birkaç dakika sürebilir...)
echo.

pio run -e %ENV_NAME%
if %ERRORLEVEL% NEQ 0 (
    echo [HATA] Firmware derleme başarısız!
    pause
    exit /b 1
)

echo [OK] Firmware başarıyla derlendi!
echo.

:: Upload seçimi
if "%BOARD_CHOICE%"=="1" (
    echo Firmware'i ESP32-S3 Zero'ya yüklemek ister misiniz? (E/H)
    set /p UPLOAD_CHOICE=
    
    if /i "%UPLOAD_CHOICE%"=="E" (
        echo.
        echo Firmware yükleniyor...
        echo (Cihazın USB ile bağlı olduğundan emin olun)
        echo.
        
        pio run -e %ENV_NAME% -t upload
        if %ERRORLEVEL% NEQ 0 (
            echo [HATA] Firmware yükleme başarısız!
            echo.
            echo Sorun giderme:
            echo   1. Cihazın USB ile bağlı olduğundan emin olun
            echo   2. USB sürücülerinin yüklü olduğundan emin olun
            echo   3. Cihazın boot modunda olduğundan emin olun (BOOT butonuna basılı tutun)
            echo   4. Farklı bir USB portu deneyin
            echo   5. USB kablosunun data transfer desteklediğinden emin olun
            pause
            exit /b 1
        )
        
        echo [OK] Firmware başarıyla yüklendi!
        echo.
        
        :: Monitor başlat (opsiyonel)
        echo Serial monitor'ü başlatmak ister misiniz? (E/H)
        set /p MONITOR_CHOICE=
        
        if /i "%MONITOR_CHOICE%"=="E" (
            echo.
            echo Serial monitor başlatılıyor...
            echo (Çıkmak için Ctrl+C)
            echo.
            pio device monitor -e %ENV_NAME%
        )
    ) else (
        echo Firmware yükleme atlandı.
    )
) else (
    echo [BİLGİ] Wokwi simülasyon için firmware yükleme gerekmez.
    echo [BİLGİ] Firmware dosyası: .pio\build\%ENV_NAME%\firmware.bin
)

echo.
echo ============================================
echo İşlem tamamlandı!
echo ============================================
echo.
pause

