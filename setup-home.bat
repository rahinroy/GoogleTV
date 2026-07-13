@echo off
REM Re-enable the Home-button redirect after a reboot (TCL / autostart-restricted boxes
REM reset the AUTO_START permission on every boot, blocking the accessibility service from
REM auto-binding). Double-click this after a reboot if the Home button stops opening this
REM launcher. No-PC alternative: open the launcher, Settings -> "Set as default launcher",
REM then toggle this app's Accessibility entry off and back on.
cd /d "%~dp0"

set ADB=platform-tools\adb.exe
if not exist "%ADB%" set ADB=adb

set PKG=com.nihar.tvlauncher
set SVC=%PKG%/%PKG%.HomeRedirectService

echo Granting AUTO_START...
%ADB% shell cmd appops set %PKG% AUTO_START allow

echo Rebinding the accessibility service...
%ADB% shell settings delete secure enabled_accessibility_services
timeout /t 2 /nobreak >nul
%ADB% shell settings put secure enabled_accessibility_services %SVC%
%ADB% shell settings put secure accessibility_enabled 1
timeout /t 2 /nobreak >nul

echo.
%ADB% shell "dumpsys accessibility | grep \"Bound services\""
echo Done. Press the Home button on your remote to test.
pause
