# Installs the latest built APK onto your Android TV / Google TV over adb.
# Run this after building (.\gradlew.bat assembleDebug). Works from any directory.
#
#   .\deploy.ps1                       # install onto an already-connected device
#   .\deploy.ps1 192.168.1.50:37065    # (re)connect to that address first, then install
#
# adb is taken from .\platform-tools\adb.exe if present, otherwise from your PATH.
param([string]$Device = "")

$ErrorActionPreference = "Stop"

$adb = Join-Path $PSScriptRoot "platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }   # fall back to adb on PATH

$apk = Join-Path $PSScriptRoot "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apk)) { throw "APK not found at $apk — build first: .\gradlew.bat assembleDebug" }

if ($Device -ne "") { & $adb connect $Device }

Write-Host "Connected devices:"
& $adb devices

Write-Host "Installing $apk ..."
& $adb install -r $apk
