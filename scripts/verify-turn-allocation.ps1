param(
    [string]$Container = "work-coturn-1",
    [string]$Server = "127.0.0.1",
    [string]$Username = "enterprise-im",
    [string]$Password = "enterprise-im-secret"
)

$ErrorActionPreference = "Stop"

function Invoke-Checked {
    param([string[]]$Arguments)

    & docker @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker $($Arguments -join ' ') exited with code $LASTEXITCODE"
    }
}

Write-Host "TURN allocation check" -ForegroundColor Cyan
Write-Host "Container: $Container"
Write-Host "Server: $Server"

Invoke-Checked @("exec", $Container, "sh", "-lc", "command -v turnutils_uclient >/dev/null")

$command = "turnutils_uclient -v -y -u '$Username' -w '$Password' -n 1 -m 2 '$Server' 2>&1"
$output = & docker exec $Container sh -lc $command
$exitCode = $LASTEXITCODE
$output | ForEach-Object { Write-Host $_ }

$text = $output -join "`n"
if ($text -notmatch "allocate response received" -or
    $text -notmatch "INFO: success" -or
    $text -notmatch "Received relay addr") {
    if ($exitCode -ne 0) {
        Write-Host "turnutils_uclient exit code: $exitCode" -ForegroundColor Yellow
    }
    throw "TURN allocation did not produce a successful relay allocation"
}

if ($exitCode -ne 0) {
    Write-Host "turnutils_uclient ended with code $exitCode after successful allocation; ignoring client-to-client tail error." -ForegroundColor Yellow
}
Write-Host "PASS TURN allocation" -ForegroundColor Green
