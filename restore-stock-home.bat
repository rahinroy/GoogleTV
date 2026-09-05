@echo off
REM EMERGENCY ROLLBACK: re-enable the stock Google TV launcher. Double-click this if the
REM Home button stops working after running hard-disable-stock-home.bat (e.g. it lands on
REM a black screen), or whenever you want the stock launcher back.
cd /d "%~dp0"

set ADB=platform-tools\adb.exe
if not exist "%ADB%" set ADB=adb

echo Re-enabling the stock launcher...
%ADB% shell pm enable com.google.android.apps.tv.launcherx

echo Re-enabling the recovery home activity...
%ADB% shell pm enable com.google.android.tungsten.setupwraith/com.google.android.tungsten.setupwraith.RecoveryActivity

echo Handing the HOME role back to the stock launcher...
%ADB% shell cmd package set-home-activity com.google.android.apps.tv.launcherx/com.google.android.apps.tv.launcherx.home.HomeActivity

echo.
%ADB% shell cmd role get-role-holders android.app.role.HOME
echo Done. Press Home on the remote - the stock launcher should be back.
pause
