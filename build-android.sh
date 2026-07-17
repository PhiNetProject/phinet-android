#!/usr/bin/env bash
#
# build-android.sh — cross-compile the ΦNET daemon for Android and install
# it into the app's jniLibs as libphinetdaemon.so (the one place Android
# lets you execute a packaged binary). This is what makes the phone a node.
#
# One-time setup:
#   rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
#   cargo install cargo-ndk
#   # Android NDK r26+ (older NDKs miss libc symbols `ring` needs):
#   #   Android Studio > SDK Manager > SDK Tools > NDK (Side by side)
#
# Usage:
#   ANDROID_NDK_HOME=~/Android/Sdk/ndk/26.3.11579264 ./build-android.sh /path/to/phinet-main
#
set -euo pipefail

SRC="${1:-}"
[ -z "$SRC" ] && { echo "usage: ./build-android.sh /path/to/phinet-main"; exit 1; }
[ -d "$SRC/phinet-daemon" ] || { echo "!! $SRC is not the phinet-main workspace"; exit 1; }
[ -n "${ANDROID_NDK_HOME:-}" ] || { echo "!! set ANDROID_NDK_HOME to your NDK (r26+) path"; exit 1; }
command -v cargo-ndk >/dev/null || { echo "!! cargo install cargo-ndk"; exit 1; }

# ── ring / *-sys crates need the NDK's clang as the C compiler + sysroot.
#    cargo-ndk sets the linkers; we also export CC/AR/ranlib so C deps build.
HOST_TAG="linux-x86_64"; case "$(uname -s)" in Darwin) HOST_TAG="darwin-x86_64";; esac
TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$HOST_TAG/bin"
[ -d "$TOOLCHAIN" ] || { echo "!! NDK toolchain not found at $TOOLCHAIN"; exit 1; }
API=26
export CC_aarch64_linux_android="$TOOLCHAIN/aarch64-linux-android$API-clang"
export CC_x86_64_linux_android="$TOOLCHAIN/x86_64-linux-android$API-clang"
export CC_armv7_linux_androideabi="$TOOLCHAIN/armv7a-linux-androideabi$API-clang"
export AR="$TOOLCHAIN/llvm-ar"
export RANLIB="$TOOLCHAIN/llvm-ranlib"
# ring: use the NDK toolchain and disable assembly issues on some NDKs.
export TARGET_CC_aarch64_linux_android="$CC_aarch64_linux_android"
export TARGET_CC_x86_64_linux_android="$CC_x86_64_linux_android"

JNILIBS="$(cd "$(dirname "$0")" && pwd)/app/src/main/jniLibs"
mkdir -p "$JNILIBS/arm64-v8a" "$JNILIBS/armeabi-v7a" "$JNILIBS/x86_64"

# 16 KB page-size alignment (Android 15 / devices like the Galaxy S25).
# Appends to any existing RUSTFLAGS so we don't clobber the caller's.
export RUSTFLAGS="${RUSTFLAGS:-} -C link-arg=-Wl,-z,max-page-size=16384"

echo "== building phinet-daemon for Android (arm64-v8a, armeabi-v7a, x86_64) =="
( cd "$SRC" && cargo ndk \
    -t arm64-v8a -t armeabi-v7a -t x86_64 \
    --platform "$API" \
    build --release -p phinet-daemon )

copy() { # <triple> <abi>
  local bin="$SRC/target/$1/release/phinet-daemon"
  [ -f "$bin" ] || { echo "!! missing $bin"; exit 1; }
  cp "$bin" "$JNILIBS/$2/libphinetdaemon.so"
  echo "   installed $2/libphinetdaemon.so ($(du -h "$JNILIBS/$2/libphinetdaemon.so" | cut -f1))"
}
copy aarch64-linux-android      arm64-v8a
copy armv7-linux-androideabi    armeabi-v7a
copy x86_64-linux-android       x86_64

echo
echo "== done. Now build the app (Android Studio, or ./gradlew :app:assembleDebug). =="
echo "   A Galaxy S25 uses arm64-v8a; the emulator uses x86_64."
