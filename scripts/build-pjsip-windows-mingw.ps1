param(
    [string]$PjsipVersion = "2.14",
    [string]$MingwRoot = "D:\Qt\Tools\mingw530_32",
    [string]$OpenH264Prefix = "",
    [string]$SdlPrefix = "",
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
$bashOpenH264Prefix = if ($OpenH264Prefix.Trim()) { Convert-ToGitBashPath $OpenH264Prefix } else { "" }
$defaultSdlPrefix = Join-Path $root "build\SDL2-2.28.5\i686-w64-mingw32"
if (-not $SdlPrefix.Trim() -and (Test-Path (Join-Path $defaultSdlPrefix "bin\sdl2-config"))) {
    $SdlPrefix = $defaultSdlPrefix
}
$bashSdlPrefix = if ($SdlPrefix.Trim()) { Convert-ToGitBashPath $SdlPrefix } else { "" }
$videoFlag = if ($EnableVideo) { "1" } else { "0" }
$configureVideoArgs = if ($EnableVideo) {
    $args = "--enable-video --disable-opencore-amr --disable-libwebrtc --disable-libsrtp"
    if ($bashOpenH264Prefix) {
        $args += " --with-openh264=$bashOpenH264Prefix"
    }
    if ($bashSdlPrefix) {
        $args += " --with-sdl=$bashSdlPrefix"
    }
    $args
} else {
    "--disable-video --disable-opencore-amr --disable-sdl --disable-ffmpeg --disable-libwebrtc --disable-libsrtp"
}

$script = @'
set -euo pipefail
export PATH="__MINGW_BIN__:/usr/bin:$PATH"
if [ -n "__OPENH264_PREFIX__" ]; then
  export LIBRARY_PATH="__OPENH264_PREFIX__/lib:${LIBRARY_PATH:-}"
  export CPATH="__OPENH264_PREFIX__/include:${CPATH:-}"
  MINGW_ROOT="$(cd "__MINGW_BIN__/.." && pwd)"
  if [ -f "__OPENH264_PREFIX__/lib/libopenh264.a" ] && [ -d "$MINGW_ROOT/i686-w64-mingw32/lib" ]; then
    cp "__OPENH264_PREFIX__/lib/libopenh264.a" "$MINGW_ROOT/i686-w64-mingw32/lib/libopenh264.a"
  fi
fi
if [ -n "__SDL_PREFIX__" ]; then
  export PATH="__SDL_PREFIX__/bin:$PATH"
  export SDL_CONFIG="__SDL_PREFIX__/bin/sdl2-config"
  export PKG_CONFIG_PATH="__SDL_PREFIX__/lib/pkgconfig:${PKG_CONFIG_PATH:-}"
  export LIBRARY_PATH="__SDL_PREFIX__/lib:${LIBRARY_PATH:-}"
  export CPATH="__SDL_PREFIX__/include/SDL2:__SDL_PREFIX__/include:${CPATH:-}"
  MINGW_ROOT="$(cd "__MINGW_BIN__/.." && pwd)"
  if [ -d "$MINGW_ROOT/i686-w64-mingw32/lib" ]; then
    cp "__SDL_PREFIX__/lib/libSDL2.a" "$MINGW_ROOT/i686-w64-mingw32/lib/libSDL2.a"
    cp "__SDL_PREFIX__/lib/libSDL2main.a" "$MINGW_ROOT/i686-w64-mingw32/lib/libSDL2main.a"
    cp "__SDL_PREFIX__/lib/libSDL2.dll.a" "$MINGW_ROOT/i686-w64-mingw32/lib/libSDL2.dll.a"
  fi
  if [ -d "$MINGW_ROOT/i686-w64-mingw32/include" ]; then
    rm -rf "$MINGW_ROOT/i686-w64-mingw32/include/SDL2"
    cp -R "__SDL_PREFIX__/include/SDL2" "$MINGW_ROOT/i686-w64-mingw32/include/SDL2"
  fi
fi
cd "__BUILD__"
rm -rf pjproject
mkdir -p pjproject
tar -xzf "__TARBALL__" -C pjproject --strip-components=1
cd pjproject
if [ -n "__OPENH264_PREFIX__" ] && [ -d "__OPENH264_PREFIX__/include/wels" ]; then
  mkdir -p pjmedia/include/wels
  cp "__OPENH264_PREFIX__/include/wels/"*.h pjmedia/include/wels/
fi
cat > pjlib/include/pj/config_site.h <<'EOF'
#include <pj/config_site_sample.h>
#define PJMEDIA_HAS_VIDEO __VIDEO_FLAG__
EOF
if [ -f third_party/BaseClasses/sal2.h ]; then
  cat >> third_party/BaseClasses/sal2.h <<'EOF'
#ifndef __in_opt
#define __in_opt
#endif
#ifndef __inout
#define __inout
#endif
#ifndef __inout_opt
#define __inout_opt
#endif
#ifndef __out_opt
#define __out_opt
#endif
#ifndef __deref_out
#define __deref_out
#endif
#ifndef __deref_out_opt
#define __deref_out_opt
#endif
#ifndef __deref_inout_opt
#define __deref_inout_opt
#endif
#ifndef __in_bcount
#define __in_bcount(x)
#endif
#ifndef __in_ecount
#define __in_ecount(x)
#endif
#ifndef __out_bcount
#define __out_bcount(x)
#endif
#ifndef __out_ecount
#define __out_ecount(x)
#endif
#ifndef __out_ecount_part
#define __out_ecount_part(x,y)
#endif
EOF
fi
./configure --host=i686-w64-mingw32 __CONFIGURE_VIDEO_ARGS__
if [ -n "__OPENH264_PREFIX__" ]; then
  sed -i 's/$(ILBC_CFLAGS) $(IPP_CFLAGS) $(G7221_CFLAGS)/$(ILBC_CFLAGS) $(IPP_CFLAGS) $(G7221_CFLAGS) $(OPENH264_CFLAGS)/g' pjmedia/build/Makefile
fi
(mingw32-make dep || echo "WARN: make dep failed under Git Bash; continue with direct make")
(mingw32-make -j2 || true)
cd pjsip-apps/build
(mingw32-make pjsua -j2 || true)
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
if [ -n "__SDL_PREFIX__" ] && [ -f "__SDL_PREFIX__/bin/SDL2.dll" ]; then
  cp "__SDL_PREFIX__/bin/SDL2.dll" "__RUNTIME__/SDL2.dll"
fi
"__RUNTIME__/pjsua.exe" --version || true
if [ "__VIDEO_FLAG__" = "1" ]; then
  CODECS=$(printf 'vid codec list\nq\n' | "__RUNTIME__/pjsua.exe" --null-audio --video --local-port=5097 2>&1 || true)
  echo "$CODECS"
  if echo "$CODECS" | grep -qi 'Found 0 video codecs'; then
    echo "Windows pjsua was built with video UI but zero video codecs. Install/build H264 or VP8 dependencies before claiming desktop video."
    exit 1
  fi
  DEVICES=$(printf 'vid dev list\nq\n' | "__RUNTIME__/pjsua.exe" --null-audio --video --local-port=5096 2>&1 || true)
  echo "$DEVICES"
  if [ -n "__SDL_PREFIX__" ] && ! echo "$DEVICES" | grep -qi 'SDL.*render'; then
    echo "Windows pjsua was built without SDL video renderer."
    exit 1
  fi
fi
'@

$script = $script.Replace("__MINGW_BIN__", $bashMingwBin).
    Replace("__OPENH264_PREFIX__", $bashOpenH264Prefix).
    Replace("__SDL_PREFIX__", $bashSdlPrefix).
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
