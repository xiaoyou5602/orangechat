@echo off
setlocal

REM Safe debug install helper: it preserves existing debug-app data.
REM Never replace install -r with adb uninstall; uninstall wipes local data.
set "ADB="
if defined ANDROID_SDK_ROOT if exist "%ANDROID_SDK_ROOT%\platform-tools\adb.exe" set "ADB=%ANDROID_SDK_ROOT%\platform-tools\adb.exe"
if not defined ADB if defined ANDROID_HOME if exist "%ANDROID_HOME%\platform-tools\adb.exe" set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
if not defined ADB if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not defined ADB for /f "delims=" %%I in ('where adb 2^>nul') do if not defined ADB set "ADB=%%I"
if not defined ADB (
    echo ADB was not found. Set ANDROID_SDK_ROOT or add platform-tools to PATH.
    exit /b 1
)

set "PKG=me.rerere.rikkahub.debug"
set "APK=app\build\outputs\apk\debug\app-arm64-v8a-debug.apk"

echo === Devices ===
"%ADB%" devices
echo.

echo === Installed debug package ===
"%ADB%" shell pm path %PKG%
"%ADB%" shell dumpsys package %PKG% | findstr /C:"versionCode" /C:"versionName" /C:"lastUpdateTime" /C:"firstInstallTime" /C:"codePath"
echo.

if not exist "%APK%" (
    echo APK not found: %APK%
    exit /b 1
)

echo === Install debug APK and preserve local data ===
"%ADB%" install -r "%APK%"
if errorlevel 1 (
    echo Install FAILED. If the signature differs, fix signing; do not uninstall.
    exit /b 1
)
echo.

echo === Installed package after update ===
"%ADB%" shell pm path %PKG%
"%ADB%" shell dumpsys package %PKG% | findstr /C:"versionCode" /C:"lastUpdateTime"
echo.
echo Done.
endlocal
pause
