param(
    [string]$PjsipVersion = "2.14",
    [string]$MingwRoot = "D:\Qt\Tools\mingw530_32",
    [switch]$EnableVideo,
    [switch]$Clean
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$buildDir = Join-Path $root "build\pjsip-windows-mingw"
$runtimeDir = Join-Path $root "qt-client\third_party\pjsip\windows"
$tarball = Join-Path $root "build\pjsip-android\pjproject-$PjsipVersion.tar.gz"
$bash = "D:\git\Git\bin\bash.exe"

if (-not (Test-Path $bash)) {
    throw "Git Bash not found: $bash"
}
if (-not (Test-Path (Join-Path $MingwRoot "bin\gcc.exe"))) {
    throw "MinGW gcc not found under $MingwRoot"
}
if (-not (Test-Path $tarball)) {
    New-Item -ItemType Directory -Force -Path (Split-Path $tarball) | Out-Null
    $url = "https://github.com/pjsip/pjproject/archive/refs/tags/$PjsipVersion.tar.gz"
    Invoke-WebRequest -Uri $url -OutFile $tarball
}
if ($Clean -and (Test-Path $buildDir)) {
    Remove-Item -Recurse -Force $buildDir
}
New-Item -ItemType Directory -Force -Path $buildDir,$runtimeDir | Out-Null

function Convert-ToGitBashPath {
    param([string]$Path)
    $resolved = Resolve-Path $Path
    $drive = $resolved.Path.Substring(0, 1).ToLowerInvariant()
    $rest = $resolved.Path.Substring(2).Replace("\", "/")
    return "/$drive$rest"
}

$bashBuild = Convert-ToGitBashPath $buildDir
$bashTarball = Convert-ToGitBashPath $tarball
$bashRuntime = Convert-ToGitBashPath $runtimeDir
$bashMingwBin = Convert-ToGitBashPath (Join-Path $MingwRoot "bin")
$videoFlag = if ($EnableVideo) { "1" } else { "0" }
$configureVideoArgs = if ($EnableVideo) {
    "--disable-opencore-amr --disable-libwebrtc --disable-libsrtp"
} else {
    "--disable-video --disable-opencore-amr --disable-sdl --disable-ffmpeg --disable-libwebrtc --disable-libsrtp"
}

$script = @'
set -euo pipefail
export PATH="__MINGW_BIN__:/usr/bin:$PATH"
cd "__BUILD__"
rm -rf pjproject
mkdir -p pjproject
tar -xzf "__TARBALL__" -C pjproject --strip-components=1
cd pjproject
cat > pjlib/include/pj/config_site.h <<'EOF'
#include <pj/config_site_sample.h>
#define PJMEDIA_HAS_VIDEO __VIDEO_FLAG__
EOF
./configure --host=i686-w64-mingw32 __CONFIGURE_VIDEO_ARGS__
(mingw32-make dep || echo "WARN: make dep failed under Git Bash; continue with direct make")
(mingw32-make -j2 || true)
cd pjsip-apps/build
mingw32-make pjsua -j2
cd ../..
mkdir -p "__RUNTIME__"
PJSUA=$(find pjsip-apps/bin -type f -name 'pjsua*.exe' | head -1)
if [ -z "$PJSUA" ]; then
  echo "pjsua.exe not found"
  exit 1
fi
cp "$PJSUA" "__RUNTIME__/pjsua.exe"
for dll in libgcc_s_dw2-1.dll libstdc++-6.dll libwinpthread-1.dll; do
  if [ -f "__MINGW_BIN__/$dll" ]; then
    cp "__MINGW_BIN__/$dll" "__RUNTIME__/$dll"
  fi
done
"__RUNTIME__/pjsua.exe" --version || true
'@

$script = $script.Replace("__MINGW_BIN__", $bashMingwBin).
    Replace("__BUILD__", $bashBuild).
    Replace("__TARBALL__", $bashTarball).
    Replace("__RUNTIME__", $bashRuntime).
    Replace("__VIDEO_FLAG__", $videoFlag).
    Replace("__CONFIGURE_VIDEO_ARGS__", $configureVideoArgs) -replace "`r`n", "`n"

$scriptPath = Join-Path $buildDir "build-pjsip-windows-mingw.sh"
[System.IO.File]::WriteAllText($scriptPath, $script, [System.Text.Encoding]::ASCII)
& $bash (Convert-ToGitBashPath $scriptPath)
if ($LASTEXITCODE -ne 0) {
    throw "PJSIP Windows MinGW build failed with code $LASTEXITCODE"
}

Get-ChildItem $runtimeDir | Select-Object Name,Length,LastWriteTime
