@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ============================================
echo EYA APK Build ve Yükleme
echo ============================================
echo.

:: Proje dizinine git
cd /d "%~dp0"
if not exist "apps\eya" (
    echo [HATA] apps\eya klasörü bulunamadı!
    echo Lütfen bu dosyayı proje kök dizininde çalıştırın.
    pause
    exit /b 1
)

cd apps\eya

:: Gradle wrapper kontrolü
if not exist "gradlew.bat" (
    echo [HATA] gradlew.bat bulunamadı!
    echo Lütfen önce setup.bat'ı çalıştırın.
    pause
    exit /b 1
)

:: ADB kontrolü
where adb >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [HATA] ADB bulunamadı!
    echo ADB'yi PATH'e ekleyin veya Android Studio'yu kurun.
    pause
    exit /b 1
)

:: Cihaz kontrolü
echo Cihaz kontrol ediliyor...
adb devices | findstr /C:"device" >nul
if %ERRORLEVEL% NEQ 0 (
    echo [UYARI] Bağlı cihaz bulunamadı!
    echo Lütfen:
    echo   1. Android cihazınızı USB ile bağlayın
    echo   2. USB debugging'i açın
    echo   3. Cihazda "Bu bilgisayara güven" onayını verin
    echo.
    echo Tekrar denemek için Enter'a basın...
    pause
    exit /b 1
)

echo [OK] Cihaz bağlı.
echo.

:: Eski APK'yı kaldır
echo Eski APK kaldırılıyor...
adb uninstall com.eya >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo [OK] Eski APK kaldırıldı.
) else (
    echo [BİLGİ] Eski APK bulunamadı (ilk kurulum olabilir).
)
echo.

:: Clean build
echo Clean build yapılıyor...
echo (Bu işlem birkaç dakika sürebilir...)
echo.

call gradlew.bat clean
if %ERRORLEVEL% NEQ 0 (
    echo [HATA] Clean işlemi başarısız!
    pause
    exit /b 1
)

:: Debug APK build
echo Debug APK derleniyor...
call gradlew.bat assembleDebug
if %ERRORLEVEL% NEQ 0 (
    echo [HATA] APK derleme başarısız!
    pause
    exit /b 1
)

:: APK dosyasını kontrol et
set "APK_PATH=build\outputs\apk\debug\eya-debug.apk"
if not exist "%APK_PATH%" (
    echo [HATA] APK dosyası bulunamadı: %APK_PATH%
    pause
    exit /b 1
)

echo [OK] APK başarıyla derlendi: %APK_PATH%
echo.

:: APK'yı yükle
echo APK yükleniyor...
adb install -r "%APK_PATH%"
if %ERRORLEVEL% NEQ 0 (
    echo [HATA] APK yükleme başarısız!
    pause
    exit /b 1
)

echo [OK] APK başarıyla yüklendi!
echo.

:: Uygulamayı başlat (opsiyonel)
echo Uygulamayı başlatmak ister misiniz? (E/H)
set /p START_APP=
if /i "%START_APP%"=="E" (
    echo Uygulama başlatılıyor...
    adb shell am start -n com.eya/.MainActivity
    if %ERRORLEVEL% EQU 0 (
        echo [OK] Uygulama başlatıldı.
    ) else (
        echo [UYARI] Uygulama başlatılamadı.
    )
)

echo.
echo ============================================
echo İşlem tamamlandı!
echo ============================================
echo.
pause

