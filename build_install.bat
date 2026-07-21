@echo off
setlocal
REM Prefer configured tools and only use the standard Windows locations when present.
if defined JAVA_HOME if not exist "%JAVA_HOME%\bin\java.exe" set "JAVA_HOME="
if not defined JAVA_HOME if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"

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

echo === Build Debug APK ===
call gradlew.bat :app:assembleDebug
if errorlevel 1 (
    echo Build FAILED!
    pause
    exit /b 1
)
echo.
echo === Devices ===
"%ADB%" devices
echo.

REM IMPORTANT: only use install -r to preserve user data. NEVER uninstall.
REM If install -r fails with -99 / UPDATE_INCOMPATIBLE, signatures differ;
REM fix keystore config instead of uninstalling.
echo === Install (preserve data) ===
"%ADB%" install -r "%APK%"
if errorlevel 1 (
    echo.
    echo Install FAILED. If signature mismatch, unify keystore and retry.
    echo Do NOT uninstall - it wipes app data.
)
echo.
echo === Verify ===
"%ADB%" shell pm path %PKG%
echo.
echo Done.
endlocal
