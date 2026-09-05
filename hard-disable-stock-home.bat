@echo off
REM Make this launcher the REAL home by removing every higher-priority home activity.
REM On this TCL box the Home button resolves by intent-filter PRIORITY and ignores
REM ROLE_HOME, so the stock launcher opened first and the accessibility redirect could
REM only react afterwards -- that round trip is the visible "flash". Disabling everything
REM above us is the only way to stop it.
REM
REM   priority  2  launcherx                     <- stock launcher, disabled here
REM   priority  1  setupwraith/.RecoveryActivity <- black dead-end, disabled here
REM   priority  0  com.nihar.tvlauncher          <- us
REM
REM setupwraith is the setup wizard -- re-enable it before a factory reset.
REM `pm disable-user` persists across reboots. Undo with restore-stock-home.bat
REM Disabling setupwraith may knock the device off the network for ~2 min. That is normal.
cd /d "%~dp0"

set ADB=platform-tools\adb.exe
if not exist "%ADB%" set ADB=adb
set PKG=com.nihar.tvlauncher

echo Claiming the HOME role...
%ADB% shell cmd package set-home-activity %PKG%/.MainActivity

echo Disabling the stock launcher (priority 2)...
%ADB% shell pm disable-user --user 0 com.google.android.apps.tv.launcherx

echo Disabling setupwraith / RecoveryActivity (priority 1)...
%ADB% shell pm disable-user --user 0 com.google.android.tungsten.setupwraith

echo.
echo Done. Press Home on the remote. If anything is wrong, run restore-stock-home.bat
pause
