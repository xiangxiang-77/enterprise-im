param(
    [string]$BuildDir = "build\qt-client",
    [string]$Configuration = "release"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$projectDir = Join-Path $root "qt-client"
$outputDir = Join-Path $root $BuildDir
$distDir = Join-Path $root "dist\EnterpriseIMQtClient"
$distZip = Join-Path $root "dist\EnterpriseIMQtClient.zip"

function Invoke-Native {
    param(
        [string]$FilePath,
        [string[]]$Arguments
    )

    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$FilePath exited with code $LASTEXITCODE"
    }
}

$preferredQmake = "D:\Qt\5.9.3\mingw53_32\bin\qmake.exe"
$preferredMake = "D:\Qt\Tools\mingw530_32\bin\mingw32-make.exe"
$preferredMingwBin = "D:\Qt\Tools\mingw530_32\bin"
$preferredQtBin = "D:\Qt\5.9.3\mingw53_32\bin"

$qmake = if (Test-Path $preferredQmake) { Get-Item $preferredQmake } else { Get-Command qmake -ErrorAction Stop }
$make = if (Test-Path $preferredMake) { Get-Item $preferredMake } else { Get-Command nmake, mingw32-make -ErrorAction SilentlyContinue | Select-Object -First 1 }
if (-not $make) {
    throw "qmake found at $($qmake.FullName), but no nmake or mingw32-make found. Install Qt 5.9.3 MinGW or run this from the matching Qt command prompt."
}

if (Test-Path $preferredMingwBin) {
    $env:PATH = "$preferredMingwBin;$preferredQtBin;$env:PATH"
}

if (-not (Test-Path $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir | Out-Null
}

Push-Location $outputDir
try {
    Invoke-Native $qmake.FullName @((Join-Path $projectDir "qt-client.pro"), "CONFIG+=$Configuration")
    Invoke-Native $make.FullName @()
} finally {
    Pop-Location
}

$exe = Join-Path $outputDir "$Configuration\EnterpriseIMQtClient.exe"
if (-not (Test-Path $exe)) {
    throw "missing Qt exe: $exe"
}

if (Test-Path $distDir) {
    Remove-Item -Recurse -Force $distDir
}
New-Item -ItemType Directory -Force -Path $distDir | Out-Null
Copy-Item -Force $exe $distDir

$windeployqt = Join-Path $preferredQtBin "windeployqt.exe"
if (Test-Path $windeployqt) {
    Invoke-Native $windeployqt @("--release", "--no-translations", (Join-Path $distDir "EnterpriseIMQtClient.exe"))
}

$pjsipDir = Join-Path $projectDir "third_party\pjsip\windows"
$pjsua = Join-Path $pjsipDir "pjsua.exe"
if (Test-Path $pjsua) {
    $pjsuaHelp = & $pjsua --help 2>&1 | Out-String
    if ($pjsuaHelp -notmatch "--video" -or $pjsuaHelp -notmatch "--vcapture-dev" -or $pjsuaHelp -notmatch "--vrender-dev") {
        throw "PJSIP Windows runtime lacks video options. Rebuild/copy pjsua.exe with video support before final media delivery."
    }
    Copy-Item -Recurse -Force (Join-Path $pjsipDir "*") $distDir
} else {
    throw "PJSIP Windows runtime not found: $pjsipDir. Put pjsua.exe and required DLLs there before final media delivery."
}

if (Test-Path $distZip) {
    Remove-Item -Force $distZip
}
Compress-Archive -Path (Join-Path $distDir "*") -DestinationPath $distZip -Force

Write-Host "Qt build finished in $outputDir" -ForegroundColor Green
Write-Host "Qt package copied to: $distDir" -ForegroundColor Green
Write-Host "Qt package zipped to: $distZip" -ForegroundColor Green
