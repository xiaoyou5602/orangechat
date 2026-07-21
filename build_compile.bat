@echo off
setlocal
REM Prefer an existing JAVA_HOME. Fall back to Android Studio's usual location
REM only when it is installed there; do not keep a stale path from another PC.
if defined JAVA_HOME if not exist "%JAVA_HOME%\bin\java.exe" set "JAVA_HOME="
if not defined JAVA_HOME if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"
call gradlew.bat :app:compileDebugKotlin
endlocal
