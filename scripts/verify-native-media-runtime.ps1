param(
    [switch]$RequireQt,
    [switch]$RequireFlutter
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

function Assert-File {
    param(
        [string]$Path,
        [string]$Message
    )
    if (-not (Test-Path $Path)) {
        throw $Message
    }
}

function Test-Any {
    param([string[]]$Paths)
    foreach ($path in $Paths) {
        if (Test-Path $path) {
            return $true
        }
    }
    return $false
}

$qtRuntime = Join-Path $root "qt-client\third_party\pjsip\windows"
$qtPjsua = Join-Path $qtRuntime "pjsua.exe"
$qtOk = Test-Path $qtPjsua

$flutterJni = Join-Path $root "flutter-client\android\app\src\main\jniLibs"
$flutterLibs = Join-Path $root "flutter-client\android\app\libs"
$flutterAbis = @("arm64-v8a", "armeabi-v7a", "x86_64")
$flutterFound = @()
foreach ($abi in $flutterAbis) {
    $lib = Join-Path $flutterJni "$abi\libpjsua2.so"
    if (Test-Path $lib) {
        $flutterFound += $abi
    }
}
$flutterOk = $flutterFound.Count -gt 0
$flutterBindingOk = Test-Any @((Join-Path $flutterLibs "pjsua2.jar"), (Join-Path $flutterLibs "pjsua2.aar"))

Write-Host "Native media runtime check" -ForegroundColor Cyan
Write-Host "Qt PJSIP runtime: $qtRuntime"
Write-Host ("  pjsua.exe: " + ($(if ($qtOk) { "FOUND" } else { "MISSING" })))
Write-Host "Flutter PJSIP runtime: $flutterJni"
Write-Host ("  libpjsua2.so ABI: " + ($(if ($flutterOk) { $flutterFound -join ", " } else { "MISSING" })))
Write-Host ("  pjsua2 Java binding: " + ($(if ($flutterBindingOk) { "FOUND" } else { "MISSING" })))

$missing = @()
if ($RequireQt -and -not $qtOk) {
    $missing += "Qt native media runtime missing. Put PJSIP 2.10-2.14 Windows pjsua.exe and DLLs in $qtRuntime."
}
if ($RequireFlutter -and (-not $flutterOk -or -not $flutterBindingOk)) {
    $missing += "Flutter native media runtime missing. Put PJSIP 2.10-2.14 Android libpjsua2.so under $flutterJni\<abi> and pjsua2.jar/aar under $flutterLibs."
}
if ($missing.Count -gt 0) {
    throw ($missing -join [Environment]::NewLine)
}

if ($qtOk -and $flutterOk -and $flutterBindingOk) {
    Write-Host "PASS native media runtime" -ForegroundColor Green
} else {
    Write-Host "WARN native media runtime incomplete" -ForegroundColor Yellow
}
