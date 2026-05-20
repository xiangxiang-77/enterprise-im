param(
    [int]$CallerDelaySeconds = 8,
    [int]$CallDurationSeconds = 16
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$outDir = Join-Path $root "build\sip-media-loop"

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

function Assert-Contains {
    param(
        [string]$Path,
        [string]$Pattern,
        [string]$Message
    )

    if (-not (Select-String -Path $Path -Pattern $Pattern -Quiet)) {
        throw "$Message. See $Path"
    }
}

Push-Location $root
try {
    Invoke-Native "docker" @("compose", "up", "-d", "asterisk", "coturn", "pjsip-gateway")

    $pjsuaVersion = docker compose exec -T pjsip-gateway sh -lc "/opt/pjsip/bin/pjsua --version 2>&1 | grep 'PJ_VERSION' | head -1"
    if ($LASTEXITCODE -ne 0 -or $pjsuaVersion -notmatch "2\.1[0-4]") {
        throw "pjsua 2.10-2.14 not verified in pjsip-gateway: $pjsuaVersion"
    }

    docker compose exec -T asterisk asterisk -rx "database deltree registrar/contact" | Out-Null
    docker compose exec -T asterisk asterisk -rx "pjsip reload" | Out-Null

    $listenerQuit = $CallerDelaySeconds + $CallDurationSeconds + 14
    $callerQuit = $CallDurationSeconds + 10
    $totalWait = $CallerDelaySeconds + $CallDurationSeconds + 16

    $inner = @"
set -u
rm -rf /tmp/sip-smoke
mkdir -p /tmp/sip-smoke
COMMON="--null-audio --auto-conf --duration=60 --log-level=5 --app-log-level=4 --registrar=sip:asterisk:5060 --realm=* --password=enterprise-im-sip-secret --use-compact-form --add-codec PCMU/8000"
(
  sleep $listenerQuit
  printf 'q\n'
) | /opt/pjsip/bin/pjsua `$COMMON --id=sip:u_qt@enterprise-im.local --username=u_qt --local-port=5070 --rtp-port=4010 --auto-answer=200 --log-file=/tmp/sip-smoke/qt.log >/tmp/sip-smoke/qt.out 2>&1 &
Q=`$!
sleep $CallerDelaySeconds
(
  sleep 8
  printf 'm\n'
  sleep 1
  printf 'sip:u_qt@asterisk:5060\n'
  sleep $CallDurationSeconds
  printf 'q\n'
) | /opt/pjsip/bin/pjsua `$COMMON --duration=50 --id=sip:u_flutter@enterprise-im.local --username=u_flutter --local-port=5072 --rtp-port=4020 --log-file=/tmp/sip-smoke/flutter.log >/tmp/sip-smoke/flutter.out 2>&1 &
F=`$!
sleep $totalWait
kill `$Q `$F 2>/dev/null || true
"@

    $inner | docker compose exec -T pjsip-gateway sh -lc "tr -d '\r' | sh -s"
    if ($LASTEXITCODE -ne 0) {
        throw "container SIP media loop command failed"
    }

    if (Test-Path $outDir) {
        Remove-Item -Recurse -Force $outDir
    }
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null

    $containerId = (docker compose ps -q pjsip-gateway).Trim()
    if (-not $containerId) {
        throw "pjsip-gateway container not found"
    }
    Invoke-Native "docker" @("cp", "$containerId`:/tmp/sip-smoke/qt.log", (Join-Path $outDir "qt.log"))
    Invoke-Native "docker" @("cp", "$containerId`:/tmp/sip-smoke/flutter.log", (Join-Path $outDir "flutter.log"))

    $qtLog = Join-Path $outDir "qt.log"
    $flutterLog = Join-Path $outDir "flutter.log"

    Assert-Contains $qtLog "registration success, status=200" "Qt-side SIP registration did not succeed"
    Assert-Contains $flutterLog "registration success, status=200" "Flutter-side SIP registration did not succeed"
    Assert-Contains $qtLog "Incoming Request msg INVITE" "Qt-side pjsua did not receive INVITE"
    Assert-Contains $qtLog "Answering call 0: code=200" "Qt-side pjsua did not auto-answer"
    Assert-Contains $flutterLog "Response msg 200/INVITE" "Caller did not receive 200 OK for INVITE"
    Assert-Contains $qtLog "Call 0 state changed to CONFIRMED" "Qt-side call was not confirmed"
    Assert-Contains $flutterLog "Call 0 state changed to CONFIRMED" "Flutter-side call was not confirmed"
    Assert-Contains $qtLog "Audio updated, stream #0: PCMU \(sendrecv\)" "Qt-side audio stream was not active"
    Assert-Contains $flutterLog "Audio updated, stream #0: PCMU \(sendrecv\)" "Flutter-side audio stream was not active"
    Assert-Contains $qtLog "RTP status:" "Qt-side RTP status was not observed"
    Assert-Contains $flutterLog "RTP status:" "Flutter-side RTP status was not observed"

    Write-Host "PASS SIP media loop" -ForegroundColor Green
    Write-Host "Evidence: $outDir"
} finally {
    Pop-Location
}
