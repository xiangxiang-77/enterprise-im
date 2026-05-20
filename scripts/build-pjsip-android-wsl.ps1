param(
    [string]$PjsipVersion = "2.14",
    [string]$NdkVersion = "r25c",
    [string[]]$Abis = @("arm64-v8a"),
    [switch]$Clean
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$buildDir = Join-Path $root "build\pjsip-android"
$outJni = Join-Path $root "flutter-client\android\app\src\main\jniLibs"
$outLibs = Join-Path $root "flutter-client\android\app\libs"

if ($Clean -and (Test-Path $buildDir)) {
    Remove-Item -Recurse -Force $buildDir
}
New-Item -ItemType Directory -Force -Path $buildDir,$outJni,$outLibs | Out-Null

function Convert-ToWslPath {
    param([string]$Path)
    $resolved = Resolve-Path $Path
    $drive = $resolved.Path.Substring(0, 1).ToLowerInvariant()
    $rest = $resolved.Path.Substring(2).Replace("\", "/")
    return "/mnt/$drive$rest"
}

$wslRoot = Convert-ToWslPath $root
$wslBuild = Convert-ToWslPath $buildDir
$abiList = $Abis -join " "

$script = @'
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y --no-install-recommends ca-certificates wget unzip build-essential autoconf automake libtool pkg-config swig openjdk-17-jdk

cd "__WSL_BUILD__"
NDK_ZIP="android-ndk-__NDK_VERSION__-linux.zip"
NDK_DIR="android-ndk-__NDK_VERSION__"
if [ ! -d "$NDK_DIR" ]; then
  if [ ! -f "$NDK_ZIP" ]; then
    wget -O "$NDK_ZIP" "https://dl.google.com/android/repository/android-ndk-__NDK_VERSION__-linux.zip"
  fi
  unzip -q "$NDK_ZIP"
fi

PJ_TAR="pjproject-__PJSIP_VERSION__.tar.gz"
if [ ! -f "$PJ_TAR" ]; then
  wget -O "$PJ_TAR" "https://github.com/pjsip/pjproject/archive/refs/tags/__PJSIP_VERSION__.tar.gz"
fi

for ABI in __ABI_LIST__; do
  SRC="pjproject-__PJSIP_VERSION__-$ABI"
  rm -rf "$SRC"
  mkdir -p "$SRC"
  tar -xzf "$PJ_TAR" -C "$SRC" --strip-components=1
  cd "$SRC"
  cat > pjlib/include/pj/config_site.h <<'EOF'
#define PJ_CONFIG_ANDROID 1
#define PJMEDIA_HAS_VIDEO 1
#include <pj/config_site_sample.h>
EOF
  export ANDROID_NDK_ROOT="__WSL_BUILD__/$NDK_DIR"
  export APP_PLATFORM=android-23
  export TARGET_ABI="$ABI"
  ./configure-android --use-ndk-cflags
  make dep
  make -j"$(nproc)"
  cd pjsip-apps/src/swig
  make
  cd ../../..
  mkdir -p "__WSL_ROOT__/flutter-client/android/app/src/main/jniLibs/$ABI"
  find . -name 'libpjsua2.so' -type f -print -exec cp {} "__WSL_ROOT__/flutter-client/android/app/src/main/jniLibs/$ABI/libpjsua2.so" \; | tail -20
  LIBCPP="pjsip-apps/src/swig/java/android/pjsua2/src/main/jniLibs/$ABI/libc++_shared.so"
  if [ -f "$LIBCPP" ]; then
    cp "$LIBCPP" "__WSL_ROOT__/flutter-client/android/app/src/main/jniLibs/$ABI/libc++_shared.so"
  fi
  JAR=$(find . -name 'pjsua2.jar' -type f | head -1 || true)
  if [ -n "$JAR" ]; then
    cp "$JAR" "__WSL_ROOT__/flutter-client/android/app/libs/pjsua2.jar"
  elif [ -d "pjsip-apps/src/swig/java/output/org/pjsip/pjsua2" ]; then
    (cd pjsip-apps/src/swig/java/output && jar cf "__WSL_ROOT__/flutter-client/android/app/libs/pjsua2.jar" org/pjsip/pjsua2)
  fi
  cd "__WSL_BUILD__"
done
'@

$script = $script.Replace("__WSL_BUILD__", $wslBuild).
    Replace("__WSL_ROOT__", $wslRoot).
    Replace("__NDK_VERSION__", $NdkVersion).
    Replace("__PJSIP_VERSION__", $PjsipVersion).
    Replace("__ABI_LIST__", $abiList)

$tmp = Join-Path $buildDir "build-pjsip-android.sh"
$script = $script -replace "`r`n", "`n"
[System.IO.File]::WriteAllText($tmp, $script, [System.Text.Encoding]::ASCII)
$wslTmp = Convert-ToWslPath $tmp
wsl sed -i 's/\r$//' $wslTmp

wsl bash $wslTmp
if ($LASTEXITCODE -ne 0) {
    throw "PJSIP Android WSL build failed with code $LASTEXITCODE"
}

& (Join-Path $PSScriptRoot "verify-native-media-runtime.ps1")
