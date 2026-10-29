#!/usr/bin/env bash
# Cross-compile the tiny PTY JNI bridge for the ABIs KingKang ships.
# Usage: ANDROID_NDK_HOME=/path/to/ndk ./scripts/build-native.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"

if [ -z "$NDK" ] || [ ! -d "$NDK" ]; then
  echo "error: set ANDROID_NDK_HOME to an NDK r27+ directory" >&2
  exit 1
fi

case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) HOST=windows-x86_64; EXE=.exe ;;
  Darwin) HOST=darwin-x86_64; EXE= ;;
  *) HOST=linux-x86_64; EXE= ;;
esac

TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/$HOST"
CC="$TOOLCHAIN/bin/clang$EXE"
SYSROOT="$TOOLCHAIN/sysroot"
STRIP="$TOOLCHAIN/bin/llvm-strip$EXE"

if [ ! -x "$CC" ]; then
  echo "error: clang not found at $CC" >&2
  exit 1
fi

build() {
  local abi="$1" target="$2"
  local out="$ROOT/app/src/main/jniLibs/$abi"
  mkdir -p "$out"
  "$CC" --target="$target" --sysroot="$SYSROOT" \
    -D_FILE_OFFSET_BITS=64 \
    -fPIC -shared -O2 -fstack-protector-strong \
    -Wall -Wextra \
    -Wl,-soname,libkingkang_pty.so \
    -Wl,-z,relro -Wl,-z,now -Wl,-z,max-page-size=16384 \
    -o "$out/libkingkang_pty.so" \
    "$ROOT/app/src/main/cpp/pty_bridge.c"
  "$STRIP" --strip-unneeded "$out/libkingkang_pty.so"
  echo "built $out/libkingkang_pty.so"
}

build x86_64 x86_64-linux-android26
build arm64-v8a aarch64-linux-android26
