param(
    [int]$Port = 7070
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$gatewayDir = Join-Path $root "pjsip-gateway"

if (-not (Test-Path (Join-Path $gatewayDir "app.py"))) {
    throw "Missing pjsip-gateway\app.py"
}

$listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if ($listener) {
    Write-Host "PJSIP gateway already listening: http://127.0.0.1:$Port"
    exit 0
}

$envBlock = "import os, runpy; os.environ['PJSIP_GATEWAY_PORT']='$Port'; runpy.run_path('app.py', run_name='__main__')"
Start-Process -FilePath "python" `
    -ArgumentList @("-c", $envBlock) `
    -WorkingDirectory $gatewayDir `
    -WindowStyle Hidden

$healthUrl = "http://127.0.0.1:$Port/health"
for ($i = 0; $i -lt 15; $i++) {
    try {
        $health = Invoke-RestMethod -Uri $healthUrl -Method Get -TimeoutSec 2
        Write-Host "PJSIP gateway started: $healthUrl"
        $health | ConvertTo-Json -Depth 5
        exit 0
    } catch {
        Start-Sleep -Milliseconds 500
    }
}

throw "PJSIP gateway start failed: health check timed out"
