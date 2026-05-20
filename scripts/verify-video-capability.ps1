param(
    [switch]$Strict
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$warnings = @()
$failures = @()

function Add-Check {
    param(
        [string]$Name,
        [bool]$Ok,
        [string]$Pass,
        [string]$Warn,
        [switch]$Required
    )

    if ($Ok) {
        Write-Host "PASS $Name - $Pass" -ForegroundColor Green
    } elseif ($Required -or $Strict) {
        $script:failures += "$Name - $Warn"
        Write-Host "FAIL $Name - $Warn" -ForegroundColor Red
    } else {
        $script:warnings += "$Name - $Warn"
        Write-Host "WARN $Name - $Warn" -ForegroundColor Yellow
    }
}

function Read-TextSafe {
    param([string]$Path)
    if (-not (Test-Path $Path)) {
        return ""
    }
    return [System.IO.File]::ReadAllText($Path)
}

Write-Host "Video capability check" -ForegroundColor Cyan

$mainActivity = Join-Path $root "flutter-client\android\app\src\main\kotlin\com\example\enterprise_im_flutter_client\MainActivity.kt"
$mainActivityText = Read-TextSafe $mainActivity
Add-Check `
    -Name "Flutter bridge video args" `
    -Ok ($mainActivityText -match 'videoCount\s*=\s*if\s*\(\s*mediaType\s*==\s*"video"\s*\)\s*1\s*else\s*0' -and $mainActivityText -match 'videoCount\s*=\s*if\s*\(\s*activeMediaType\s*==\s*"video"\s*\)\s*1\s*else\s*0' -and $mainActivityText -match 'Manifest\.permission\.CAMERA') `
    -Pass "pjsua2 outbound and incoming video calls request videoCount=1 and camera permission" `
    -Warn "MainActivity.kt does not prove pjsua2 outbound/incoming videoCount=1 plus CAMERA permission" `
    -Required

Add-Check `
    -Name "Flutter SIP diagnostics" `
    -Ok ($mainActivityText -match 'invokeMethod\(\s*"sipEvent"' -and $mainActivityText -match 'remote video attach failed') `
    -Pass "native SIP bridge reports state/error/video attach events to Flutter" `
    -Warn "native SIP bridge does not expose detailed SIP/video errors to Flutter" `
    -Required

$flutterMain = Join-Path $root "flutter-client\lib\main.dart"
$flutterMainText = Read-TextSafe $flutterMain
Add-Check `
    -Name "Flutter SIP error detail UI" `
    -Ok ($flutterMainText -match 'setMethodCallHandler\(handleSipChannelCall\)' -and $flutterMainText -match 'SIP EVENT' -and $flutterMainText -match 'error: \$\{shortSipMessage') `
    -Pass "Flutter listens for native SIP events and displays concise error details" `
    -Warn "Flutter UI may still collapse native SIP failures to generic SIP error" `
    -Required

$manifest = Join-Path $root "flutter-client\android\app\src\main\AndroidManifest.xml"
$manifestText = Read-TextSafe $manifest
Add-Check `
    -Name "Flutter media permissions" `
    -Ok ($manifestText -match 'android\.permission\.RECORD_AUDIO' -and $manifestText -match 'android\.permission\.CAMERA' -and $manifestText -match 'android\.permission\.MODIFY_AUDIO_SETTINGS') `
    -Pass "source manifest declares microphone, camera, and audio routing permissions" `
    -Warn "AndroidManifest.xml missing required microphone/camera/audio permissions" `
    -Required

$asteriskConfig = Join-Path $root "asterisk\pjsip.conf"
$asteriskText = Read-TextSafe $asteriskConfig
Add-Check `
    -Name "Asterisk video codecs" `
    -Ok ($asteriskText -match '(?m)^allow=.*(h264|vp8)') `
    -Pass "PJSIP endpoint template allows at least one video codec" `
    -Warn "Asterisk PJSIP endpoint template allows audio only; remote video negotiation will fail" `
    -Required

$jniRoot = Join-Path $root "flutter-client\android\app\src\main\jniLibs"
$flutterVideoRuntime = @(
    (Join-Path $jniRoot "arm64-v8a\libpjsua2.so"),
    (Join-Path $root "flutter-client\android\app\libs\pjsua2.jar")
)
$missingFlutterRuntime = @($flutterVideoRuntime | Where-Object { -not (Test-Path $_) })
Add-Check `
    -Name "Flutter pjsua2 runtime" `
    -Ok ($missingFlutterRuntime.Count -eq 0) `
    -Pass "arm64 libpjsua2.so and pjsua2.jar bundled" `
    -Warn ("missing " + ($missingFlutterRuntime -join ", ")) `
    -Required

$apkPath = Join-Path $root "dist\enterprise-im-app-release.apk"
$apkHasPjsua2 = $false
$apkHasSharedCpp = $false
$apkHasCameraPermission = $false
if (Test-Path $apkPath) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($apkPath)
    try {
        $apkHasPjsua2 = [bool]($zip.Entries | Where-Object { $_.FullName -eq "lib/arm64-v8a/libpjsua2.so" } | Select-Object -First 1)
        $apkHasSharedCpp = [bool]($zip.Entries | Where-Object { $_.FullName -eq "lib/arm64-v8a/libc++_shared.so" } | Select-Object -First 1)
        $apkHasCameraPermission = [bool]($zip.Entries | Where-Object { $_.FullName -eq "AndroidManifest.xml" } | Select-Object -First 1)
    } finally {
        $zip.Dispose()
    }
}
Add-Check `
    -Name "APK native pjsua2" `
    -Ok $apkHasPjsua2 `
    -Pass "release APK contains lib/arm64-v8a/libpjsua2.so" `
    -Warn "release APK missing arm64 pjsua2 library" `
    -Required
Add-Check `
    -Name "APK native C++ runtime" `
    -Ok $apkHasSharedCpp `
    -Pass "release APK contains lib/arm64-v8a/libc++_shared.so" `
    -Warn "release APK missing arm64 libc++_shared.so" `
    -Required
Add-Check `
    -Name "APK manifest present" `
    -Ok $apkHasCameraPermission `
    -Pass "release APK has compiled AndroidManifest.xml; source declares CAMERA for video" `
    -Warn "release APK missing compiled manifest" `
    -Required

$sipMediaClient = Join-Path $root "qt-client\src\SipMediaClient.cpp"
$sipMediaText = Read-TextSafe $sipMediaClient
Add-Check `
    -Name "Qt video launch path" `
    -Ok ($sipMediaText -match 'mediaType\s*==\s*"video"' -and $sipMediaText -match '--video' -and $sipMediaText -match '--auto-conf' -and $sipMediaText -match '--null-video') `
    -Pass "desktop uses --video for video calls, --null-video for audio calls, and auto-conf for media wiring" `
    -Warn "desktop pjsua launch path does not prove explicit video plus audio media wiring" `
    -Required

$pjsua = Join-Path $root "qt-client\third_party\pjsip\windows\pjsua.exe"
$pjsuaVideoHelp = $false
if (Test-Path $pjsua) {
    $help = & $pjsua --help 2>&1 | Out-String
    $pjsuaVideoHelp = $help -match '(?i)(--video|--vid|video|camera|render|capture-dev=.*video)'
}
Add-Check `
    -Name "Qt bundled pjsua video support" `
    -Ok $pjsuaVideoHelp `
    -Pass "pjsua help exposes video/camera options" `
    -Warn "bundled Windows pjsua help does not expose video options; desktop video rendering may be audio-only runtime" `
    -Required

Write-Host ""
if ($failures.Count -gt 0) {
    Write-Host "Video capability failures:" -ForegroundColor Red
    $failures | ForEach-Object { Write-Host "- $_" -ForegroundColor Red }
    exit 1
}

if ($warnings.Count -gt 0) {
    Write-Host "Video capability warnings:" -ForegroundColor Yellow
    $warnings | ForEach-Object { Write-Host "- $_" -ForegroundColor Yellow }
    exit 0
}

Write-Host "PASS video capability" -ForegroundColor Green
