# Make this launcher the REAL home by removing every higher-priority home activity.
#
# Why this and not just the HOME role: on this TCL box the physical Home button resolves
# by intent-filter PRIORITY and ignores ROLE_HOME (verified -- holding the role still let
# the stock launcher open first). The accessibility redirect can only react *after* the
# stock launcher is already on screen, which is the visible "flash". The only way to stop
# the flash is to make sure nothing above us can resolve as home.
#
# Home candidates on this device, highest first:
#   priority  2  com.google.android.apps.tv.launcherx        <- stock launcher, disabled here
#   priority  1  ...setupwraith/.RecoveryActivity            <- black dead-end, disabled here
#   priority  0  com.nihar.tvlauncher  (+ Projectivy, if installed)
#   priority -1000 com.android.tv.settings/...FallbackHome   <- always-present backstop
#
# The RecoveryActivity COMPONENT cannot be disabled ("SecurityException: Shell cannot
# change component state"), so the whole setupwraith package has to go. That package is
# the setup wizard -- re-enable it before a factory reset.
#
# Unlike the AUTO_START appop, `pm disable-user` PERSISTS across reboots, so this survives
# a restart. Undo with .\restore-stock-home.ps1
#
# NOTE: disabling setupwraith briefly knocked the device off the network (~2 min, adb
# included) without rebooting it. That is expected; wait for it to come back.
param([string]$Device = "")

$ErrorActionPreference = "Continue"
$adb = Join-Path $PSScriptRoot "platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }

$pkg = "com.nihar.tvlauncher"
if ($Device -ne "") { & $adb connect $Device }

Write-Host "Claiming the HOME role (breaks the priority-0 tie vs other launchers)..."
& $adb shell cmd package set-home-activity "$pkg/.MainActivity"

Write-Host "Disabling the stock launcher (priority 2)..."
& $adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx

Write-Host "Disabling setupwraith / RecoveryActivity (priority 1)..."
& $adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith

Write-Host ""
Write-Host "Remaining HOME candidates:"
& $adb shell "cmd package query-activities -c android.intent.category.HOME -a android.intent.action.MAIN" |
    Select-String -Pattern 'priority=|packageName='
Write-Host ""
Write-Host "Done. Press Home on the remote. If anything is wrong, run .\restore-stock-home.ps1"
