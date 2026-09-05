@echo off
REM Installs the RELEASE APK onto your Android TV / Google TV over adb.
REM
REM Release on purpose: a debuggable build is pinned by ART at `status=verify` and never
REM gets AOT-compiled, so Compose's scroll code runs interpreted until the JIT catches up.
REM That is what made fast scrolling choppy for the first few seconds. Measured here:
REM debug 57ms p50 / 63 missed vsync vs release 22ms p50 / 0 missed vsync on first scroll.
REM
REM Build first:  gradlew.bat assembleRelease
cd /d "%~dp0"

set ADB=platform-tools\adb.exe
if not exist "%ADB%" set ADB=adb
set PKG=com.nihar.tvlauncher
set APK=app\build\outputs\apk\release\app-release.apk

if not exist "%APK%" (
  echo APK not found at %APK% -- build first: gradlew.bat assembleRelease
  pause
  exit /b 1
)

echo Connected devices:
%ADB% devices

echo Installing %APK% ...
%ADB% install -r "%APK%"

REM Launch once so ProfileInstaller writes the baseline profile, then AOT compile it in
REM immediately -- otherwise this waits for background dexopt (idle + charging only).
echo Warming ART (baseline profile + AOT compile)...
%ADB% shell am start -n %PKG%/.MainActivity >nul
timeout /t 8 /nobreak >nul
%ADB% shell cmd package compile -m speed-profile -f %PKG%

echo Dexopt state (want speed-profile, NOT verify):
%ADB% shell "dumpsys package %PKG% | grep -m1 status="

REM Reinstalling ALWAYS unbinds the accessibility service, and TclAppBoot blocks it from
REM rebinding on its own, so the :home process stays dead until toggled. Setting the same
REM value is a no-op, hence delete-then-set.
echo Rebinding the Home-redirect accessibility service...
%ADB% shell cmd appops set %PKG% AUTO_START allow
%ADB% shell settings delete secure enabled_accessibility_services
timeout /t 2 /nobreak >nul
%ADB% shell settings put secure enabled_accessibility_services %PKG%/%PKG%.HomeRedirectService
%ADB% shell settings put secure accessibility_enabled 1
timeout /t 3 /nobreak >nul
%ADB% shell "dumpsys accessibility | grep \"Bound services\""
pause
