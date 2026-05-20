param(
    [string]$QtRoot = "D:\Qt\5.9.3\msvc2017_64",
    [string]$VsToolsRoot = "D:\BuildTools\VS2017",
    [string]$BuildDir = "build\qt-client-vs2017",
    [string]$Configuration = "release"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$projectDir = Join-Path $root "qt-client"
$outputDir = Join-Path $root $BuildDir
$distDir = Join-Path $root "dist\EnterpriseIMQtClient-vs2017"
$distZip = Join-Path $root "dist\EnterpriseIMQtClient-vs2017.zip"
$toolRunnerDir = Join-Path $root "build\qt-tool-runner"

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

function Import-VsDevEnv {
    param([string]$InstallRoot)

    $vcvarsCandidates = @(
        (Join-Path $InstallRoot "VC\Auxiliary\Build\vcvars64.bat"),
        "C:\Program Files (x86)\Microsoft Visual Studio\2017\BuildTools\VC\Auxiliary\Build\vcvars64.bat",
        "C:\Program Files (x86)\Microsoft Visual Studio\2017\Community\VC\Auxiliary\Build\vcvars64.bat"
    )
    $vcvars = $vcvarsCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
    if (-not $vcvars) {
        return $false
    }

    $envLines = cmd /c "`"$vcvars`" >nul && set"
    if ($LASTEXITCODE -ne 0) {
        return $false
    }
    foreach ($line in $envLines) {
        $idx = $line.IndexOf("=")
        if ($idx -gt 0) {
            [Environment]::SetEnvironmentVariable($line.Substring(0, $idx), $line.Substring($idx + 1), "Process")
        }
    }
    return $true
}

$qmake = Join-Path $QtRoot "bin\qmake.exe"
$windeployqt = Join-Path $QtRoot "bin\windeployqt.exe"
$webEngineDll = Join-Path $QtRoot "bin\Qt5WebEngineWidgets.dll"

if (-not (Test-Path $qmake)) {
    throw "Missing qmake: $qmake. Install Qt 5.9.3 MSVC2017 kit."
}
if (-not (Test-Path $webEngineDll)) {
    throw "Missing Qt WebEngineWidgets: $webEngineDll. Use Qt 5.9.3 MSVC2017 WebEngine kit, not MinGW kit."
}

New-Item -ItemType Directory -Force -Path $toolRunnerDir | Out-Null
$qmakeRunner = Join-Path $toolRunnerDir "qmake.exe"
Copy-Item -Force $qmake $qmakeRunner

$nmake = Get-Command nmake.exe -ErrorAction SilentlyContinue
if (-not $nmake) {
    if (Import-VsDevEnv $VsToolsRoot) {
        $nmake = Get-Command nmake.exe -ErrorAction SilentlyContinue
    }
}
if (-not $nmake) {
    throw "nmake.exe not found. Install VS2017 Build Tools or pass -VsToolsRoot with VC\Auxiliary\Build\vcvars64.bat."
}

if (Test-Path $outputDir) {
    Remove-Item -Recurse -Force $outputDir
}
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

Push-Location $outputDir
try {
    Invoke-Native $qmakeRunner @((Join-Path $projectDir "qt-client.pro"), "CONFIG+=$Configuration")
    Invoke-Native $nmake.Source @()
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

Invoke-Native $windeployqt @("--release", "--webengine", "--no-translations", (Join-Path $distDir "EnterpriseIMQtClient.exe"))

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

Write-Host "VS2017 Qt WebEngine build finished: $distDir" -ForegroundColor Green
Write-Host "Package zip: $distZip" -ForegroundColor Green
