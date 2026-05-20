param(
    [string]$Output = "dist\EnterpriseIM-Delivery-Package.zip"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$stage = Join-Path $root "build\delivery-package-staging"
$outputPath = Join-Path $root $Output

function Copy-Tree {
    param(
        [string]$Source,
        [string]$Destination
    )

    $sourcePath = Join-Path $root $Source
    $destPath = Join-Path $stage $Destination
    New-Item -ItemType Directory -Force -Path $destPath | Out-Null
    robocopy $sourcePath $destPath /E /NFL /NDL /NJH /NJS /NP `
        /XD ".git" ".gradle" ".dart_tool" "build" "node_modules" "dist" "target" "__pycache__" `
        /XF "*.log" | Out-Null
    if ($LASTEXITCODE -gt 7) {
        throw "robocopy failed for $Source with code $LASTEXITCODE"
    }
}

if (Test-Path $stage) {
    $resolvedStage = (Resolve-Path $stage).Path
    if (-not $resolvedStage.StartsWith((Join-Path $root "build"), [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to remove unexpected staging path: $resolvedStage"
    }
    Remove-Item -LiteralPath $resolvedStage -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $stage | Out-Null

Copy-Item -Force (Join-Path $root "README.md") $stage
Copy-Item -Force (Join-Path $root ".gitignore") $stage
Copy-Item -Force (Join-Path $root ".env.example") $stage
Copy-Item -Force (Join-Path $root "docker-compose.yml") $stage

foreach ($dir in @("asterisk", "flutter-client", "im-server", "im-ui", "pjsip-gateway", "project-docs", "qt-client", "scripts")) {
    Copy-Tree $dir $dir
}

$stageDist = Join-Path $stage "dist"
New-Item -ItemType Directory -Force -Path $stageDist | Out-Null
foreach ($file in @("DELIVERY_README.md", "EnterpriseIMQtClient-vs2017.zip", "enterprise-im-app-release.apk")) {
    Copy-Item -Force (Join-Path $root "dist\$file") $stageDist
}

$serverJar = Join-Path $root "im-server\target\im-server-0.1.0-SNAPSHOT.jar"
if (Test-Path $serverJar) {
    New-Item -ItemType Directory -Force -Path (Join-Path $stage "im-server\target") | Out-Null
    Copy-Item -Force $serverJar (Join-Path $stage "im-server\target")
}

if (Test-Path $outputPath) {
    Remove-Item -Force $outputPath
}
Compress-Archive -Path (Join-Path $stage "*") -DestinationPath $outputPath -Force -ErrorAction Stop
if ($? -ne $true) {
    throw "Compress-Archive failed"
}
if (-not (Test-Path $outputPath)) {
    throw "Delivery package was not created"
}

Write-Host "Delivery package: $outputPath" -ForegroundColor Green
