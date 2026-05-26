param(
    [switch]$SkipSipLoop,
    [switch]$SkipAudioContent,
    [switch]$SkipTurn
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

function Invoke-Step {
    param(
        [string]$Name,
        [string]$Script,
        [string[]]$Arguments = @()
    )

    Write-Host ""
    Write-Host "== $Name ==" -ForegroundColor Cyan
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root $Script) @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Name failed"
    }
}

Invoke-Step "Native runtime bundle" "scripts\verify-native-media-runtime.ps1" @("-RequireQt", "-RequireFlutter")
Invoke-Step "Video capability" "scripts\verify-video-capability.ps1"

if (-not $SkipTurn) {
    Invoke-Step "TURN allocation" "scripts\verify-turn-allocation.ps1"
}

if (-not $SkipSipLoop) {
    Invoke-Step "SIP audio media loop" "scripts\verify-sip-media-loop.ps1"
}

if (-not $SkipAudioContent) {
    Invoke-Step "SIP audio content proof" "scripts\verify-sip-audio-content.ps1"
}

Write-Host ""
Write-Host "PASS final media preflight" -ForegroundColor Green
Write-Host "Remaining acceptance needs real Qt/Android devices only: physical microphone/speaker hearing, camera preview/render, and cross-network TURN behavior."
