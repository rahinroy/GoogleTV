# Installs the latest built APK onto your Android TV / Google TV over adb.
#
#   .\deploy.ps1                       # install onto an already-connected device
#   .\deploy.ps1 10.0.0.90:5555        # (re)connect to that address first, then install
#   .\deploy.ps1 -Debug                # install the debug build instead (see below)
#
# Defaults to the RELEASE build on purpose. A debuggable build is pinned by ART at
# `status=verify` and is never AOT-compiled, so Compose's scroll code runs interpreted
# until the JIT catches up -- that is what made fast scrolling choppy for the first few
# seconds and smooth afterwards. Measured on this launcher, first scroll after a cold
# start: debug 57ms p50 / 63 missed vsync, release 22ms p50 / 0 missed vsync.
#
# Build first:  .\gradlew.bat assembleRelease
# adb is taken from .\platform-tools\adb.exe if present, otherwise from your PATH.
param([string]$Device = "", [switch]$Debug)

$ErrorActionPreference = "Stop"

$adb = Join-Path $PSScriptRoot "platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }   # fall back to adb on PATH

$pkg = "com.nihar.tvlauncher"
if ($Debug) {
    $apk = Join-Path $PSScriptRoot "app\build\outputs\apk\debug\app-debug.apk"
    $what = "debug (NOT AOT-compiled -- scrolling will be choppy until the JIT warms up)"
} else {
    $apk = Join-Path $PSScriptRoot "app\build\outputs\apk\release\app-release.apk"
    $what = "release"
}
if (-not (Test-Path $apk)) {
    throw "APK not found at $apk -- build first: .\gradlew.bat $(if ($Debug) {'assembleDebug'} else {'assembleRelease'})"
}

if ($Device -ne "") { & $adb connect $Device }

Write-Host "Connected devices:"
& $adb devices

Write-Host "Installing $what ..."
& $adb install -r $apk

if (-not $Debug) {
    # Launch once so ProfileInstaller writes the bundled baseline profile, then compile it
    # in immediately. Without this the AOT compile waits for background dexopt, which only
    # runs when the device is idle and charging -- so the first day of use would still be
    # choppy. This makes the smoothness available from the very first scroll.
    Write-Host "Warming ART (installing baseline profile + AOT compiling)..."
    & $adb shell am start -n "$pkg/.MainActivity" | Out-Null
    Start-Sleep -Seconds 8
    & $adb shell cmd package compile -m speed-profile -f $pkg
    Write-Host "Dexopt state (want speed-profile, NOT verify):"
    & $adb shell "dumpsys package $pkg | grep -m1 status="
}

# Reinstalling ALWAYS unbinds the accessibility service, and TclAppBoot blocks it from
# rebinding on its own -- so the :home process (and the Home-button safety net) stays dead
# until it is toggled. Re-toggle it here; setting the same value is a no-op, hence delete-
# then-set.
Write-Host "Rebinding the Home-redirect accessibility service..."
& $adb shell cmd appops set $pkg AUTO_START allow
& $adb shell settings delete secure enabled_accessibility_services
Start-Sleep -Seconds 2
& $adb shell settings put secure enabled_accessibility_services "$pkg/$pkg.HomeRedirectService"
& $adb shell settings put secure accessibility_enabled 1
Start-Sleep -Seconds 3
& $adb shell "dumpsys accessibility | grep 'Bound services'"
