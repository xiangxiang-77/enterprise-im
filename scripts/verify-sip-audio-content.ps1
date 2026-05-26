param(
    [int]$CallerDelaySeconds = 6,
    [int]$CallDurationSeconds = 14
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$outDir = Join-Path $root "build\sip-audio-content"

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

Push-Location $root
try {
    Invoke-Native "docker" @("compose", "up", "-d", "asterisk", "pjsip-gateway")

    docker compose exec -T asterisk asterisk -rx "database deltree registrar/contact" | Out-Null
    docker compose exec -T asterisk asterisk -rx "pjsip reload" | Out-Null

    $listenerQuit = $CallerDelaySeconds + $CallDurationSeconds + 10
    $totalWait = $CallerDelaySeconds + $CallDurationSeconds + 14

    $inner = @"
set -eu
rm -rf /tmp/sip-audio-content
mkdir -p /tmp/sip-audio-content
python3 - <<'PY'
import math
import struct
import wave

def write_tone(path, freq, seconds):
    rate = 8000
    with wave.open(path, "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(rate)
        for index in range(rate * seconds):
            sample = int(12000 * math.sin(2 * math.pi * freq * index / rate))
            out.writeframes(struct.pack("<h", sample))

write_tone("/tmp/sip-audio-content/qt_voice_440hz.wav", 440, 8)
write_tone("/tmp/sip-audio-content/flutter_voice_880hz.wav", 880, 8)
PY

COMMON="--null-audio --auto-conf --duration=30 --log-level=5 --app-log-level=4 --registrar=sip:asterisk:5060 --realm=* --password=enterprise-im-sip-secret --use-compact-form --add-codec PCMU/8000"
(
  sleep $listenerQuit
  printf 'q\n'
) | /opt/pjsip/bin/pjsua `$COMMON --id=sip:u_qt@enterprise-im.local --username=u_qt --local-port=5070 --rtp-port=4010 --auto-answer=200 --play-file=/tmp/sip-audio-content/qt_voice_440hz.wav --auto-play --rec-file=/tmp/sip-audio-content/qt_heard_flutter.wav --auto-rec --log-file=/tmp/sip-audio-content/qt.log >/tmp/sip-audio-content/qt.out 2>&1 &
Q=`$!

sleep $CallerDelaySeconds
(
  sleep 4
  printf 'm\n'
  sleep 1
  printf 'sip:u_qt@asterisk:5060\n'
  sleep $CallDurationSeconds
  printf 'q\n'
) | /opt/pjsip/bin/pjsua `$COMMON --id=sip:u_flutter@enterprise-im.local --username=u_flutter --local-port=5072 --rtp-port=4020 --play-file=/tmp/sip-audio-content/flutter_voice_880hz.wav --auto-play --rec-file=/tmp/sip-audio-content/flutter_heard_qt.wav --auto-rec --log-file=/tmp/sip-audio-content/flutter.log >/tmp/sip-audio-content/flutter.out 2>&1 &
F=`$!

sleep $totalWait
kill `$Q `$F 2>/dev/null || true

python3 - <<'PY'
import json
import math
import os
import struct
import sys
import wave

def tone_power(samples, rate, freq):
    step = max(1, rate // 8000)
    sliced = samples[::step]
    real_rate = rate / step
    if not sliced:
        return 0.0
    cos_sum = 0.0
    sin_sum = 0.0
    for index, sample in enumerate(sliced):
        angle = 2 * math.pi * freq * index / real_rate
        cos_sum += sample * math.cos(angle)
        sin_sum += sample * math.sin(angle)
    return (cos_sum * cos_sum + sin_sum * sin_sum) / len(sliced)

def read_samples(path):
    with wave.open(path, "rb") as inp:
        rate = inp.getframerate()
        frames = inp.readframes(inp.getnframes())
    samples = list(struct.unpack("<" + "h" * (len(frames) // 2), frames))
    return rate, samples[rate * 2:rate * 14]

def analyse(path):
    rate, samples = read_samples(path)
    rms = (sum(sample * sample for sample in samples) / len(samples)) ** 0.5 if samples else 0.0
    return {
        "path": path,
        "bytes": os.path.getsize(path),
        "rate": rate,
        "rms": rms,
        "p440": tone_power(samples, rate, 440),
        "p880": tone_power(samples, rate, 880),
    }

qt = analyse("/tmp/sip-audio-content/qt_heard_flutter.wav")
flutter = analyse("/tmp/sip-audio-content/flutter_heard_qt.wav")
qt_pass = qt["p880"] > qt["p440"] * 100 and qt["rms"] > 500
flutter_pass = flutter["p440"] > flutter["p880"] * 100 and flutter["rms"] > 500
result = {
    "qt_heard_flutter_880hz": qt_pass,
    "flutter_heard_qt_440hz": flutter_pass,
    "qt": qt,
    "flutter": flutter,
}
print(json.dumps(result, indent=2))
with open("/tmp/sip-audio-content/result.json", "w", encoding="utf-8") as out:
    json.dump(result, out, indent=2)
if not (qt_pass and flutter_pass):
    sys.exit(1)
PY
"@

    $inner | docker compose exec -T pjsip-gateway sh -lc "tr -d '\r' | sh -s"
    if ($LASTEXITCODE -ne 0) {
        throw "SIP audio content proof failed"
    }

    if (Test-Path $outDir) {
        Remove-Item -Recurse -Force $outDir
    }
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null

    $containerId = (docker compose ps -q pjsip-gateway).Trim()
    if (-not $containerId) {
        throw "pjsip-gateway container not found"
    }

    foreach ($name in @(
        "qt.log",
        "flutter.log",
        "qt.out",
        "flutter.out",
        "qt_voice_440hz.wav",
        "flutter_voice_880hz.wav",
        "qt_heard_flutter.wav",
        "flutter_heard_qt.wav",
        "result.json"
    )) {
        Invoke-Native "docker" @("cp", "$containerId`:/tmp/sip-audio-content/$name", (Join-Path $outDir $name))
    }

    Write-Host "PASS SIP audio content proof" -ForegroundColor Green
    Write-Host "Evidence: $outDir"
    Write-Host "  qt_heard_flutter.wav contains Flutter-side 880Hz voice fingerprint"
    Write-Host "  flutter_heard_qt.wav contains Qt-side 440Hz voice fingerprint"
} finally {
    Pop-Location
}
