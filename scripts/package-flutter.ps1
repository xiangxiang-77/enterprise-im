param(
    [switch]$GenerateAndroidProject
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$projectDir = Join-Path $root "flutter-client"
$localProperties = Join-Path $projectDir "android\local.properties"

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

$flutter = "flutter"
$flutterCommand = Get-Command flutter -ErrorAction SilentlyContinue
if ($flutterCommand) {
    $flutter = $flutterCommand.Source
} else {
    $localProperties = Join-Path $projectDir "android\local.properties"
    if (Test-Path $localProperties) {
        $flutterSdk = (Get-Content $localProperties | Where-Object { $_ -like "flutter.sdk=*" } | Select-Object -First 1) -replace "^flutter.sdk=", ""
        if ($flutterSdk) {
            $candidate = Join-Path $flutterSdk "bin\flutter.bat"
            if (Test-Path $candidate) {
                $flutter = $candidate
            }
        }
    }
    if ($flutter -eq "flutter") {
        throw "flutter not found in PATH and android\local.properties flutter.sdk is missing or invalid"
    }
}

if (-not $env:PUB_HOSTED_URL) {
    $env:PUB_HOSTED_URL = "https://pub.flutter-io.cn"
}
if (-not $env:FLUTTER_STORAGE_BASE_URL) {
    $env:FLUTTER_STORAGE_BASE_URL = "https://storage.flutter-io.cn"
}
if (-not $env:ANDROID_HOME -and (Test-Path $localProperties)) {
    $androidSdk = (Get-Content $localProperties | Where-Object { $_ -like "sdk.dir=*" } | Select-Object -First 1) -replace "^sdk.dir=", ""
    $androidSdk = $androidSdk -replace "\\\\", "\"
    if ($androidSdk -and (Test-Path $androidSdk)) {
        $env:ANDROID_HOME = $androidSdk
        $env:ANDROID_SDK_ROOT = $androidSdk
    }
}

Push-Location $projectDir
try {
    if ($GenerateAndroidProject -or -not (Test-Path "android")) {
        Invoke-Native $flutter @("create", "--platforms=android", ".")
    }
    Invoke-Native $flutter @("pub", "get")
    $gradlew = Join-Path $projectDir "android\gradlew.bat"
    if (Test-Path $gradlew) {
        & $gradlew "--stop" | Out-Null
    }
    Invoke-Native $flutter @("build", "apk")
} finally {
    Pop-Location
}

$jniLibs = Join-Path $projectDir "android\app\src\main\jniLibs"
$pjsua2 = Get-ChildItem $jniLibs -Recurse -Filter "libpjsua2.so" -ErrorAction SilentlyContinue | Select-Object -First 1
$androidLibs = Join-Path $projectDir "android\app\libs"
$bindingCandidates = @((Join-Path $androidLibs "pjsua2.jar"), (Join-Path $androidLibs "pjsua2.aar"))
$binding = $bindingCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $pjsua2 -or -not $binding) {
    throw "PJSIP Android runtime not complete. Put libpjsua2.so under android\app\src\main\jniLibs\<abi> and pjsua2.jar/aar under android\app\libs before final native media packaging."
}

$distDir = Join-Path $root "dist"
New-Item -ItemType Directory -Force -Path $distDir | Out-Null
$builtApk = Join-Path $projectDir "build\app\outputs\flutter-apk\app-release.apk"
$distApk = Join-Path $distDir "enterprise-im-app-release.apk"
Copy-Item -Force $builtApk $distApk

Write-Host "Flutter APK build finished: $projectDir\build\app\outputs\flutter-apk\app-release.apk" -ForegroundColor Green
Write-Host "Flutter APK copied to: $distApk" -ForegroundColor Green
