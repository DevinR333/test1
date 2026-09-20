#!/usr/bin/env bash
#
# Translates a ROM to C and builds the world data, and stops there.
#
#   bash scripts/generate-sources.sh [path/to/seasons.gbc]
#
# The Windows build does this on its way to an executable; Android Studio
# needs the same files but brings its own compiler, so this produces them on
# their own - no MinGW required.
#
# Writes build/src/*.c, build/world_data.c and runtime/interp_gen.c, which is
# everything android/ asks for.
#
set -euo pipefail

say() { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
die() { printf '\033[31merror:\033[0m %s\n' "$*" >&2; exit 1; }

REPO="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)" || die "cannot locate the repository"
cd "$REPO"
printf '\033[1mgenerating in %s\033[0m\n' "$REPO"

OUT_DIR="build"
SRC_DIR="$OUT_DIR/src"
REMEMBERED="$OUT_DIR/rom-path"

PY=""
for c in python3 python; do
    if command -v "$c" >/dev/null && "$c" -c 'import sys; sys.exit(0 if sys.version_info[0]==3 else 1)' 2>/dev/null; then
        PY="$c"; break
    fi
done
[ -n "$PY" ] || die "no Python 3 found on PATH"

is_rom() {
    [ -f "$1" ] || return 1
    local magic
    magic=$(dd if="$1" bs=1 skip=260 count=4 2>/dev/null | od -An -tx1 | tr -d ' \n')
    [ "$magic" = "ceed6666" ]
}

ROM="${1:-}"
if [ -z "$ROM" ] && [ -f "$REMEMBERED" ]; then
    candidate="$(cat "$REMEMBERED")"
    is_rom "$candidate" && ROM="$candidate"
fi
for d in external/oracles-disasm ../oracles-disasm; do
    [ -n "$ROM" ] && break
    is_rom "$d/seasons.gbc" && ROM="$d/seasons.gbc"
done
[ -n "$ROM" ] || die "no ROM given and none found. Pass the path:

    bash scripts/generate-sources.sh /path/to/seasons.gbc"
is_rom "$ROM" || die "$ROM does not look like a Game Boy ROM."

ROM="$(CDPATH= cd -- "$(dirname -- "$ROM")" && pwd)/$(basename -- "$ROM")"
mkdir -p "$OUT_DIR" "$SRC_DIR"
printf '%s\n' "$ROM" > "$REMEMBERED"
echo "  ROM: $ROM ($(( $(wc -c < "$ROM") / 1024 )) KiB)"

SYMBOLS="${SYMBOLS:-}"
if [ -z "$SYMBOLS" ]; then
    guess="${ROM%.*}.sym"
    [ -f "$guess" ] && SYMBOLS="$guess"
fi

say "translating the ROM to C"
RECOMP_ARGS=("$ROM" -o "$SRC_DIR")
[ -n "$SYMBOLS" ] && RECOMP_ARGS+=(--symbols "$SYMBOLS")
"$PY" tools/gbrecomp.py "${RECOMP_ARGS[@]}"
cp runtime/gb.h "$SRC_DIR/"

say "building the world data"
WORLD=""
for d in external/oracles-disasm ../oracles-disasm; do
    if [ -d "$d/rooms" ]; then
        "$PY" tools/worldmap.py "$d" -o "$OUT_DIR/world_data.c"
        WORLD="$d"
        break
    fi
done
[ -n "$WORLD" ] || die "no disassembly checkout found, so there is no world data.
  Looked for a rooms/ directory in external/oracles-disasm and ../oracles-disasm."

say "generating the interpreter fallback"
"$PY" tools/gen_interp.py runtime/interp_gen.c

say "done"
echo "  $(ls "$SRC_DIR"/bank_*.c 2>/dev/null | wc -l) translated banks in $SRC_DIR"
echo "  world data $(wc -c < "$OUT_DIR/world_data.c") bytes"
echo
echo "  Android Studio can now open the android/ folder and build."
echo "  Put this same ROM at android/app/src/main/assets/$(basename "$ROM")"
