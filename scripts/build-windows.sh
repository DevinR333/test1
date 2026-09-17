#!/usr/bin/env bash
#
# Builds a Windows executable from a Game Boy ROM.
#
#   ./scripts/build-windows.sh path/to/seasons.gbc
#
# Recompiles the ROM's code to C, compiles that together with the runtime and
# the Win32 frontend, and writes build/oracle.exe. The ROM is read, never
# copied into the executable: keep it beside the exe, or pass it on the
# command line.
#
set -euo pipefail

ROM="${1:-}"
OUT_DIR="build"
SRC_DIR="$OUT_DIR/src"
EXE="$OUT_DIR/oracle.exe"
SYMBOLS="${SYMBOLS:-}"

say()  { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
die()  { printf '\033[31merror:\033[0m %s\n' "$*" >&2; exit 1; }

[ -n "$ROM" ] || die "give the path to your ROM:
  ./scripts/build-windows.sh /c/Users/YOU/Downloads/seasons.gbc"
[ -f "$ROM" ] || die "no such file: $ROM"

# Find a Python 3 under whatever name this environment uses.
PY=""
for c in python3 python; do
    if command -v "$c" >/dev/null && "$c" -c 'import sys; sys.exit(0 if sys.version_info[0]==3 else 1)' 2>/dev/null; then
        PY="$c"; break
    fi
done
[ -n "$PY" ] || die "no Python 3 found on PATH"

# Prefer a native compiler; fall back to a cross-compiler when building on
# Linux for Windows.
CC_WIN=""
for c in "${CC:-}" gcc x86_64-w64-mingw32-gcc; do
    [ -n "$c" ] && command -v "$c" >/dev/null && { CC_WIN="$c"; break; }
done
[ -n "$CC_WIN" ] || die "no C compiler found. In MSYS2: pacman -S mingw-w64-x86_64-toolchain"

say "recompiling $(basename "$ROM")"
RECOMP_ARGS=("$ROM" -o "$SRC_DIR")
[ -n "$SYMBOLS" ] && RECOMP_ARGS+=(--symbols "$SYMBOLS")
"$PY" tools/gbrecomp.py "${RECOMP_ARGS[@]}"

say "generating the interpreter fallback"
"$PY" tools/gen_interp.py runtime/interp_gen.c

say "compiling"
cp runtime/gb.h "$SRC_DIR/"
mkdir -p "$OUT_DIR"

# -mwindows suppresses the console window. Static linking means the exe runs
# on a machine with no toolchain installed.
# shellcheck disable=SC2086
"$CC_WIN" -O2 -Wall -Iruntime -I"$SRC_DIR" \
    -o "$EXE" \
    frontend/win32.c \
    runtime/alu.c runtime/memory.c runtime/ppu.c runtime/machine.c \
    runtime/interp.c runtime/interp_gen.c runtime/present.c \
    "$SRC_DIR"/bank_*.c "$SRC_DIR"/dispatch.c \
    -lgdi32 -luser32 -lm -static -mwindows

say "done"
SIZE=$(( $(wc -c < "$EXE") / 1024 ))
cat <<NOTE
  $EXE  (${SIZE} KiB)

  Run it:
    ./$EXE "$ROM"

  Controls
    arrows        move
    X or Enter    A
    Z or Backspace B
    Space         Start
    Shift         Select
    F1            switch between integer and fill scaling
    Esc           quit
NOTE
