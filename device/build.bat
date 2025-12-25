@echo off
REM Device Build Script for Windows
REM Builds ESP32 firmware and optionally launches Wokwi simulation

setlocal enabledelayedexpansion

cd /d "%~dp0"

echo 🔧 Device Build Script
echo ======================

REM Check for PlatformIO
where pio >nul 2>&1
if %errorlevel% neq 0 (
    where platformio >nul 2>&1
    if %errorlevel% neq 0 (
        echo ❌ PlatformIO not found!
        echo 📦 Installing PlatformIO...
        
        REM Try pip
        where pip >nul 2>&1
        if %errorlevel% equ 0 (
            pip install platformio
            if %errorlevel% equ 0 (
                echo ✅ PlatformIO installed successfully
                goto :verify_pio
            )
        )
        
        REM Try pip3
        where pip3 >nul 2>&1
        if %errorlevel% equ 0 (
            pip3 install platformio
            if %errorlevel% equ 0 (
                echo ✅ PlatformIO installed successfully
                goto :verify_pio
            )
        )
        
        echo ❌ PlatformIO installation failed!
        echo Please install manually: https://platformio.org/install/cli
        exit /b 1
    )
)

:verify_pio
REM Use pio or platformio command
set PIO_CMD=pio
where pio >nul 2>&1
if %errorlevel% neq 0 (
    set PIO_CMD=platformio
)

REM Build the project
echo.
echo 🔨 Building firmware...
%PIO_CMD% run

if %errorlevel% neq 0 (
    echo ❌ Build failed!
    exit /b 1
)

echo ✅ Build successful!

REM Try to launch Wokwi simulation locally
echo.
echo 🚀 Launching Wokwi simulation locally...

where wokwi-cli >nul 2>&1
if %errorlevel% equ 0 (
    REM Check for WOKWI_CLI_TOKEN
    if "%WOKWI_CLI_TOKEN%"=="" (
        echo ⚠️  WOKWI_CLI_TOKEN environment variable not set
        echo 📝 To get your token:
        echo    1. Visit https://wokwi.com/dashboard/ci
        echo    2. Copy your token
        echo    3. Set it: $env:WOKWI_CLI_TOKEN="your-token-here"
        echo.
        echo Skipping Wokwi simulation (build was successful)
        exit /b 0
    )
    echo Starting Wokwi CLI...
    wokwi-cli .
    if %errorlevel% neq 0 (
        echo ⚠️  Wokwi CLI failed to start
        echo Make sure you're in the device directory with wokwi.toml
        exit /b 1
    )
) else (
    echo ❌ Wokwi CLI not found!
    echo 📦 Installing Wokwi CLI...
    echo Running installation script...
    powershell -Command "iwr https://wokwi.com/ci/install.ps1 -useb | iex"
    if %errorlevel% equ 0 (
        REM Check if wokwi-cli is now available
        where wokwi-cli >nul 2>&1
        if %errorlevel% equ 0 (
            echo ✅ Wokwi CLI installed
            REM Check for WOKWI_CLI_TOKEN
            if "%WOKWI_CLI_TOKEN%"=="" (
                echo ⚠️  WOKWI_CLI_TOKEN environment variable not set
                echo 📝 To get your token:
                echo    1. Visit https://wokwi.com/dashboard/ci
                echo    2. Copy your token
                echo    3. Set it: $env:WOKWI_CLI_TOKEN="your-token-here"
                echo.
                echo Skipping Wokwi simulation (build was successful)
                exit /b 0
            )
            echo Starting Wokwi CLI...
            wokwi-cli .
            if %errorlevel% neq 0 (
                echo ❌ Failed to start Wokwi CLI
                exit /b 1
            )
        ) else (
            echo ⚠️  Wokwi CLI installed but not found in PATH
            echo Please restart your terminal or add Wokwi CLI to PATH manually
            echo Then run: wokwi-cli start
        )
    ) else (
        echo ❌ Failed to install Wokwi CLI
        echo Please install manually: powershell -Command "iwr https://wokwi.com/ci/install.ps1 -useb | iex"
        exit /b 1
    )
)

echo.
echo ✨ Done!

endlocal

