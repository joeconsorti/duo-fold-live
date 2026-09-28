# Read-only Samsung Camera diagnostics. Run with authorized USB debugging.
# No display overrides, setting writes, screenshots, or log clearing.
param([string]$Adb = ".\adb.exe")
$ErrorActionPreference = "Stop"
if (!(Test-Path $Adb) -and !(Get-Command $Adb -ErrorAction SilentlyContinue)) {
    throw "Run from your platform-tools folder or pass -Adb C:\path\to\adb.exe"
}
$devices = @(& $Adb devices | Where-Object { $_ -match '^\S+\s+device$' })
if ($devices.Count -ne 1) { throw "Connect exactly one USB-debugging-authorized phone." }
$serial = ($devices[0] -split '\s+')[0]
$folder = Join-Path (Get-Location) ("Duo-Camera-Handoff-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
New-Item -ItemType Directory -Path $folder | Out-Null
function Save-Adb([string]$Name, [string[]]$Arguments) {
    $result = & $Adb -s $serial @Arguments 2>&1
    $result | Out-File (Join-Path $folder $Name) -Encoding utf8
    if ($LASTEXITCODE -ne 0) { Write-Warning "$Name could not be collected completely." }
}
function Snapshot([string]$Phase) {
    Save-Adb "$Phase-state.txt" @("shell","dumpsys","device_state")
    Save-Adb "$Phase-display.txt" @("shell","dumpsys","display")
    Save-Adb "$Phase-layers.txt" @("shell","dumpsys","SurfaceFlinger","--list")
    "$(Get-Date -Format o) $Phase" | Add-Content (Join-Path $folder "markers.txt")
}
Write-Host "Turn Duo animation OFF and stop any continuity test. Keep the phone unlocked."
Read-Host "Unfold, open Samsung Camera, leave cover preview OFF, then press Enter" | Out-Null
Save-Adb "firmware.txt" @("shell","getprop","ro.build.fingerprint")
Save-Adb "camera-package.txt" @("shell","dumpsys","package","com.sec.android.app.camera")
Save-Adb "camera-apk-paths.txt" @("shell","pm","path","com.sec.android.app.camera")
Snapshot "01-preview-off"
Read-Host "Enable Camera's cover-screen preview, then press Enter" | Out-Null
Snapshot "02-preview-on"
Write-Host "For the next 30 seconds: slowly fold and unfold with Camera preview enabled."
Write-Host "Watch BOTH screens for blackouts; do not take photos. Optional: film with another phone."
# Sequential polling avoids overlapping dumps. This is sampled state, not optical proof.
$timer = [Diagnostics.Stopwatch]::StartNew()
$i = 0
while ($timer.Elapsed.TotalSeconds -lt 30) {
    "$(Get-Date -Format o) sample=$i elapsedMs=$($timer.ElapsedMilliseconds)" | Add-Content (Join-Path $folder "timeline.txt")
    & $Adb -s $serial shell dumpsys device_state 2>&1 | Add-Content (Join-Path $folder "timeline.txt")
    & $Adb -s $serial shell dumpsys display 2>&1 | Add-Content (Join-Path $folder "timeline.txt")
    $i++
    Start-Sleep -Milliseconds 150
}
Read-Host "Unfold again, disable cover preview, then press Enter" | Out-Null
Snapshot "03-preview-disabled"
$observation = Read-Host "Describe any black flash: enabling preview, folding, unfolding, disabling preview"
$observation | Out-File (Join-Path $folder "observations.txt") -Encoding utf8
# Restrict logs to relevant system tags and Camera; do not clear device logs.
Save-Adb "display-log.txt" @("logcat","-d","-v","threadtime","-t","2000","DeviceStateManagerService:V","LogicalDisplayMapper:V","DisplayManagerService:V","FoldDisplayController:V","Camera:V","*:S")
Compress-Archive -Path (Join-Path $folder "*") -DestinationPath "$folder.zip"
Write-Host "Send this ZIP: $folder.zip"
Write-Host "This contains device/package/display metadata and filtered logs, not camera images."
Write-Host "Camera APK paths are included; the APK itself is not bundled."
