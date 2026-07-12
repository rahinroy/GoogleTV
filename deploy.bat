@echo off
REM Double-click this in File Explorer to install the latest built APK onto your device.
REM (Runs from wherever this .bat lives.) Uses platform-tools\adb.exe if present,
REM otherwise adb from your PATH.
cd /d "%~dp0"

set ADB=platform-tools\adb.exe
if not exist "%ADB%" set ADB=adb

echo Connected devices:
%ADB% devices
echo.
echo Installing latest build...
%ADB% install -r "app\build\outputs\apk\debug\app-debug.apk"
echo.
pause
