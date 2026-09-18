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

# MSYS2 has several environments and each puts a different directory on PATH,
# so a compiler installed for one is invisible from another. Add them all
# rather than requiring a particular Start-menu shortcut.
case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*)
        for d in /mingw64/bin /ucrt64/bin /clang64/bin /mingw32/bin; do
            if [ -d "$d" ]; then
                case ":$PATH:" in *":$d:"*) ;; *) PATH="$PATH:$d" ;; esac
            fi
        done
        export PATH
        ;;
esac

# A compiler's name says nothing about what it targets: under MSYS2 `gcc`
# builds Windows binaries, on Linux the same name does not. Probe each
# candidate instead of trusting it.
targets_windows() {
    local probe rc
    probe=$(mktemp -d)
    printf '#ifndef _WIN32\n#error not windows\n#endif\nint main(void){return 0;}\n' \
        > "$probe/t.c"
    "$1" -c "$probe/t.c" -o "$probe/t.o" >/dev/null 2>&1
    rc=$?
    rm -rf "$probe"
    return $rc
}

CC_WIN=""
candidates=("${CC:-}" gcc cc clang x86_64-w64-mingw32-gcc
            /mingw64/bin/gcc /ucrt64/bin/gcc /clang64/bin/gcc)
for c in "${candidates[@]}"; do
    [ -n "$c" ] || continue
    command -v "$c" >/dev/null 2>&1 || [ -x "$c" ] || continue
    if targets_windows "$c"; then CC_WIN="$c"; break; fi
done

if [ -z "$CC_WIN" ]; then
    # Name the package that matches the environment actually in use, since
    # installing the wrong one leaves the compiler just as unreachable.
    case "${MSYSTEM:-}" in
        UCRT64)  pkg="mingw-w64-ucrt-x86_64-toolchain" ;;
        CLANG64) pkg="mingw-w64-clang-x86_64-toolchain" ;;
        MINGW32) pkg="mingw-w64-i686-toolchain" ;;
        *)       pkg="mingw-w64-x86_64-toolchain" ;;
    esac
    die "no compiler that targets Windows was found.
  You are in the ${MSYSTEM:-unknown} environment. Install its toolchain:

      pacman -S --needed --noconfirm $pkg

  then run this script again."
fi
echo "  compiler: $CC_WIN ($("$CC_WIN" --version 2>/dev/null | head -1))"

# Static discovery alone cannot follow a real game's bank switching, so
# coverage without symbols is a small fraction of the ROM. A symbol file names
# every routine outright.
#
# It must belong to the same build as the ROM: a disassembly's own output and
# an original cartridge dump have entirely different addresses. Only a .sym
# sitting beside the ROM under the same name is treated as a match.
if [ -z "$SYMBOLS" ]; then
    candidate="${ROM%.*}.sym"
    [ -f "$candidate" ] && SYMBOLS="$candidate"
fi

if [ -n "$SYMBOLS" ]; then
    echo "  symbols: $SYMBOLS"
else
    cat <<'WARN'

  No symbol file found beside the ROM.

  Discovery will only find code it can reach by following branches from the
  hardware entry points, which on a real game is a small part of the ROM.
  Everything else falls back to the interpreter, so it will run, but far less
  of it will be native.

  For full coverage, build from the disassembly's own output, which has a
  matching .sym beside it:

      ./scripts/build-windows.sh external/oracles-disasm/seasons.gbc

WARN
fi

# Remove the previous executable before anything else. If a later step fails,
# there must be nothing left to run by mistake - an old binary producing old
# results is worse than no binary at all.
rm -f "$EXE"

say "recompiling $(basename "$ROM")"
RECOMP_ARGS=("$ROM" -o "$SRC_DIR")
[ -n "$SYMBOLS" ] && RECOMP_ARGS+=(--symbols "$SYMBOLS")
"$PY" tools/gbrecomp.py "${RECOMP_ARGS[@]}"

# A goto may only target a label in the same generated file. Checking here
# turns a class of emitter bug into a clear message instead of a compiler
# error about an undefined label several hundred lines into generated code.
"$PY" - "$SRC_DIR" <<'CHECK'
import glob, os, re, sys
bad = []
for path in glob.glob(os.path.join(sys.argv[1], "bank_*.c")):
    text = open(path).read()
    labels = set(re.findall(r"^(L_[0-9A-F]{4}):", text, re.M))
    for used in sorted(set(re.findall(r"goto (L_[0-9A-F]{4});", text))):
        if used not in labels:
            bad.append((os.path.basename(path), used))
if bad:
    print(f"  internal error: {len(bad)} goto(s) target a label in another file:")
    for f, l in bad[:10]:
        print(f"    {f}: {l}")
    sys.exit(1)
print("  generated code is internally consistent")
CHECK

# World map data, when a disassembly checkout is present. The hardware only
# renders one room, so a camera that shows more needs the room layouts.
WORLD_SRC=""
if [ -n "${NO_MAP:-}" ]; then
    say "building without the map view (NO_MAP set)"
    printf '#include "worldmap.h"\nconst int gb_world_group=0;\nconst uint8_t gb_world_rooms[GB_WORLD_ROOMS][GB_ROOM_TILES]={{0}};\nconst uint8_t gb_world_room_tileset[GB_WORLD_ROOMS]={0};\nconst uint8_t gb_world_mappings[1][GB_MAPPING_BYTES]={{0}};\nconst int gb_world_mapping_count=1;\nconst uint8_t gb_world_tileset_vram[1][GB_TILESET_VRAM]={{0}};\nconst uint8_t gb_world_tileset_palette[1][GB_TILESET_PALETTE]={{0}};\nconst int gb_world_tileset_count=0;\n' > "$OUT_DIR/world_data.c"
    WORLD_SRC="$OUT_DIR/world_data.c runtime/worldmap.c"
fi
for d in external/oracles-disasm ../oracles-disasm; do
    [ -n "$WORLD_SRC" ] && break
    if [ -d "$d/rooms" ]; then
        say "building world map data from $d"
        "$PY" tools/worldmap.py "$d" -o "$OUT_DIR/world_data.c"
        WORLD_SRC="$OUT_DIR/world_data.c runtime/worldmap.c"
        break
    fi
done
if [ -z "$WORLD_SRC" ]; then
    # An empty table renders as a blank screen, which is indistinguishable
    # from a bug. Refuse rather than build something that looks broken.
    die "no disassembly checkout found, so the map view has no world data.

  Looked for a rooms/ directory in:
    external/oracles-disasm
    ../oracles-disasm

  Run ./scripts/setup-disasm.sh first, or pass NO_MAP=1 to build without
  the map view."
fi

say "generating the interpreter fallback"
"$PY" tools/gen_interp.py runtime/interp_gen.c

say "compiling"
cp runtime/gb.h "$SRC_DIR/"
mkdir -p "$OUT_DIR" "$OUT_DIR/obj"

JOBS=$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 4)

# Block tracing costs a call per block entered. It is invaluable while
# bringing a game up and pure overhead once it runs, so it is opt-in:
#   TRACE=1 ./scripts/build-windows.sh ...
TRACE_FLAG=""
if [ -n "${TRACE:-}" ]; then
    TRACE_FLAG="-DGB_TRACE"
    echo "  block tracing enabled"
fi

# The generated bank files are enormous - a single function can run to a
# hundred thousand lines - and optimising them is both very slow and close to
# pointless: the code is already straight-line, and the machine being emulated
# runs at four megahertz. They compile at -O1, in parallel. The runtime and
# frontend are ordinary code and still get -O2.
echo "  $(ls "$SRC_DIR"/bank_*.c | wc -l) generated files at -O1, $JOBS at a time"

# Throttle to JOBS concurrent compilers, then confirm success by checking the
# object files themselves. Tracking individual process ids does not work here:
# the throttle reaps them, and waiting on an already-reaped id reports failure
# for a compile that actually succeeded.
expected=0
for src in "$SRC_DIR"/bank_*.c "$SRC_DIR"/dispatch.c; do
    expected=$((expected + 1))
    obj="$OUT_DIR/obj/$(basename "${src%.c}").o"

    # Reuse an object already built from an unchanged source.
    if [ -f "$obj" ] && [ "$obj" -nt "$src" ]; then
        continue
    fi

    # A partial object from an interrupted build must not be mistaken for a
    # finished one, so compile to a temporary name and move it into place.
    ( "$CC_WIN" -O1 $TRACE_FLAG -Iruntime -I"$SRC_DIR" -c "$src" -o "$obj.tmp" \
        && mv -f "$obj.tmp" "$obj" ) &

    while [ "$(jobs -rp | wc -l)" -ge "$JOBS" ]; do
        wait -n 2>/dev/null || true
    done
done
wait

built=$(ls -1 "$OUT_DIR"/obj/*.o 2>/dev/null | wc -l)
if [ "$built" -ne "$expected" ]; then
    rm -f "$OUT_DIR"/obj/*.tmp
    die "only $built of $expected generated files compiled.
  Re-run to retry; objects that succeeded are kept."
fi
echo "  $built objects ready"

echo "  runtime and frontend at -O2"
BUILD_STAMP="$(date -u '+%Y-%m-%d %H:%M:%S UTC')"
BUILD_REV="$(git rev-parse --short HEAD 2>/dev/null || echo unknown)"
# -mwindows suppresses the console window. Static linking means the exe runs
# on a machine with no toolchain installed.
"$CC_WIN" -O2 -Wall $TRACE_FLAG -Iruntime -I"$SRC_DIR" \
    -DGB_BUILD_STAMP="\"$BUILD_STAMP\"" \
    -DGB_BUILD_REV="\"$BUILD_REV\"" \
    -o "$EXE" \
    frontend/win32.c \
    runtime/alu.c runtime/memory.c runtime/ppu.c runtime/machine.c \
    runtime/interp.c runtime/interp_gen.c runtime/present.c runtime/io_masks.c \
    runtime/diag.c $WORLD_SRC \
    "$OUT_DIR"/obj/*.o \
    -lgdi32 -luser32 -lm -static -mwindows

[ -f "$EXE" ] || die "the link step produced no executable"

say "done"
SIZE=$(( $(wc -c < "$EXE") / 1024 ))
echo "  built $BUILD_STAMP from $BUILD_REV"
if [ -n "${NO_MAP:-}" ]; then
    echo "  map view:  DISABLED"
else
    echo "  map view:  world data compiled in ($(wc -c < "$OUT_DIR/world_data.c" | tr -d ' ') bytes)"
fi
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
    - and +       zoom out and in, while playing
                  (at normal size the world fills the window, no bars)
    Tab           snap between normal size and fully pulled back
    0             back to normal size
    F1            switch between integer and fill scaling
    F2            write a diagnostic snapshot
    Esc           quit
NOTE
