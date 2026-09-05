# EMERGENCY ROLLBACK: re-enable the stock Google TV launcher.
#
# Run this if the Home button stops working after hard-disable-stock-home.ps1 -- e.g. it
# lands on a black screen, or you want the stock launcher back. Re-enables everything
# that script disabled and hands the HOME role back to the stock launcher.
#
#   .\restore-stock-home.ps1                  # device already connected
#   .\restore-stock-home.ps1 10.0.0.90:5555   # connect to that address first
param([string]$Device = "")

$ErrorActionPreference = "Continue"
$adb = Join-Path $PSScriptRoot "platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }

if ($Device -ne "") { & $adb connect $Device }

Write-Host "Re-enabling the stock launcher..."
& $adb shell pm enable com.google.android.apps.tv.launcherx

Write-Host "Re-enabling the recovery home activity..."
& $adb shell pm enable com.google.android.tungsten.setupwraith/com.google.android.tungsten.setupwraith.RecoveryActivity

Write-Host "Handing the HOME role back to the stock launcher..."
& $adb shell cmd package set-home-activity com.google.android.apps.tv.launcherx/com.google.android.apps.tv.launcherx.home.HomeActivity

Write-Host ""
Write-Host "HOME role holder:"
& $adb shell cmd role get-role-holders android.app.role.HOME
Write-Host "Done. Press Home on the remote -- the stock launcher should be back."
