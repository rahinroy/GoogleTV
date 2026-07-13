# Re-enable the Home-button redirect after a reboot.
#
# On TCL / autostart-restricted boxes, the vendor autostart manager resets the AUTO_START
# permission to "deny" on every reboot, which blocks the accessibility HomeRedirectService
# from auto-binding at boot. This grants it back and rebinds the service so the Home button
# opens this launcher again. Run it once after a reboot if the Home button stops working.
#
# (No-PC alternative: open the launcher -> Settings -> "Set as default launcher", then
#  toggle this app's entry under Accessibility off and back on — a user toggle always works.)
#
#   .\setup-home.ps1                       # device already connected
#   .\setup-home.ps1 192.168.1.50:37065    # connect to that address first
param([string]$Device = "")

$ErrorActionPreference = "Stop"
$adb = Join-Path $PSScriptRoot "platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }   # fall back to adb on PATH

$pkg = "com.nihar.tvlauncher"
$svc = "$pkg/$pkg.HomeRedirectService"

if ($Device -ne "") { & $adb connect $Device }

Write-Host "Granting AUTO_START..."
& $adb shell cmd appops set $pkg AUTO_START allow

Write-Host "Rebinding the accessibility service..."
& $adb shell settings delete secure enabled_accessibility_services
Start-Sleep -Seconds 2
& $adb shell settings put secure enabled_accessibility_services $svc
& $adb shell settings put secure accessibility_enabled 1
Start-Sleep -Seconds 2

Write-Host "Bound services:"
& $adb shell "dumpsys accessibility | grep 'Bound services'"
Write-Host "Done. Press the Home button on your remote to test."
