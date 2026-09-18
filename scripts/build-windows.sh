#!/usr/bin/env bash
#
# Builds a Windows executable from a Game Boy ROM.
#
#   bash /path/to/test1/scripts/build-windows.sh [path/to/seasons.gbc]
#
# Run it from anywhere - it finds the repository from its own location. The
# ROM is optional: it looks in the usual places and remembers what it found,
# so after the first build the path is never needed again.
#
# Recompiles the ROM's code to C, compiles that together with the runtime and
# the Win32 frontend, and writes build/oracle.exe. The ROM is read, never
# copied into the executable: keep it beside the exe, or pass it on the
# command line.
#
set -euo pipefail

say()  { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
die()  { printf '\033[31merror:\033[0m %s\n' "$*" >&2; exit 1; }

# A new shell opens in the home directory, not here, so a relative path to
# this script finds nothing and the build appears to do nothing at all. Work
# from the repository regardless of where the command was typed.
REPO="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)" || die "cannot locate the repository"
cd "$REPO"

printf '\033[1mbuilding in %s\033[0m\n' "$REPO"

OUT_DIR="build"
SRC_DIR="$OUT_DIR/src"
EXE="$OUT_DIR/oracle.exe"
REMEMBERED="$OUT_DIR/rom-path"
SYMBOLS="${SYMBOLS:-}"

# Every Game Boy cartridge opens with the same logo bytes at $104, which is a
# far better test than the file's name or extension.
is_rom() {
    [ -f "$1" ] || return 1
    local magic
    magic=$(dd if="$1" bs=1 skip=260 count=4 2>/dev/null | od -An -tx1 | tr -d ' \n')
    [ "$magic" = "ceed6666" ]
}

ROM="${1:-}"

if [ -z "$ROM" ] && [ -f "$REMEMBERED" ]; then
    candidate="$(cat "$REMEMBERED")"
    if is_rom "$candidate"; then
        ROM="$candidate"
        echo "  using the ROM from last time: $ROM"
    fi
fi

# The title in the cartridge header, at $134, up to fifteen characters.
rom_title() {
    dd if="$1" bs=1 skip=308 count=15 2>/dev/null | tr -d '\000' | tr -cd '\40-\176'
}

if [ -z "$ROM" ]; then
    # The places a downloaded or dumped cartridge actually ends up. MSYS2
    # mounts the Windows drives under /c, so a Windows download is reachable.
    found=""
    for dir in . "$HOME" "$HOME/Downloads" "$HOME/Desktop" \
               /c/Users/*/Downloads /c/Users/*/Desktop /c/Users/*/Documents \
               ../oracles-disasm external/oracles-disasm; do
        [ -d "$dir" ] || continue
        for f in "$dir"/*.gbc "$dir"/*.gb; do
            if is_rom "$f"; then found="$found$f
"; fi
        done
    done

    # Everything found, minus files that are byte for byte the same cartridge
    # in two places, which is not a choice worth asking about.
    uniq_found=""
    seen=""
    while IFS= read -r f; do
        [ -n "$f" ] || continue
        key="$(wc -c < "$f" | tr -d ' ')-$(rom_title "$f")"
        case "$seen" in *"[$key]"*) continue ;; esac
        seen="$seen[$key]"
        uniq_found="$uniq_found$f
"
    done <<EOF
$found
EOF

    count=$(printf '%s' "$uniq_found" | grep -c . || true)
    if [ "$count" -eq 1 ]; then
        ROM="$(printf '%s' "$uniq_found" | head -1)"
        echo "  found a ROM: $ROM  [$(rom_title "$ROM")]"
    elif [ "$count" -gt 1 ]; then
        # Guessing here is how an executable ends up translated from one
        # cartridge and opened with another, which crashes with no clue why.
        printf '\033[31merror:\033[0m more than one Game Boy ROM found. Say which:\n\n' >&2
        while IFS= read -r f; do
            [ -n "$f" ] || continue
            printf '    bash %s/scripts/build-windows.sh "%s"\n' "$REPO" "$f" >&2
            printf '        %s, %s KiB\n\n' "$(rom_title "$f")" \
                   "$(( $(wc -c < "$f") / 1024 ))" >&2
        done <<EOF2
$uniq_found
EOF2
        exit 1
    fi
fi

if [ -z "$ROM" ]; then
    die "no Game Boy ROM found, and none given.

  Looked in this folder, your home folder, Downloads, Desktop and Documents.
  Pass the path instead:

    bash $REPO/scripts/build-windows.sh /c/Users/YOU/Downloads/seasons.gbc"
fi

[ -f "$ROM" ] || die "no such file: $ROM"
is_rom "$ROM" || die "$ROM does not look like a Game Boy ROM
  (its header is missing the logo bytes every cartridge starts with)."

# Absolute, so the remembered path still works from a different folder.
ROM="$(CDPATH= cd -- "$(dirname -- "$ROM")" && pwd)/$(basename -- "$ROM")"
mkdir -p "$OUT_DIR"
printf '%s\n' "$ROM" > "$REMEMBERED"
printf '  ROM: %s\n       %s, %s KiB\n' "$ROM" "$(rom_title "$ROM")" \
       "$(( $(wc -c < "$ROM") / 1024 ))"

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
    cat > "$OUT_DIR/world_data.c" <<'STUB'
#include "worldmap.h"
const int gb_world_group = 0;
const uint8_t gb_world_rooms[GB_WORLD_ROOMS][GB_ROOM_TILES] = {{0}};
const uint8_t gb_world_room_tileset[GB_WORLD_ROOMS] = {0};
const uint8_t gb_world_tileset_layout[128] = {0};
const uint8_t gb_world_tileset_asset[128] = {[0 ... 127] = GB_TILESET_NONE};
const uint8_t gb_world_mappings[1][GB_MAPPING_BYTES] = {{0}};
const int gb_world_mapping_count = 1;
const uint8_t gb_world_tileset_vram[1][GB_TILESET_VRAM] = {{0}};
const uint8_t gb_world_tileset_palette[1][GB_TILESET_PALETTE] = {{0}};
const uint8_t gb_world_tileset_palette_mask[1] = {0};
const int gb_world_tileset_count = 0;
STUB
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

# Which ROM this executable was translated from. It only runs with that exact
# file, so it checks at startup and says so rather than crashing.
ROM_FINGERPRINT="$("$PY" - "$ROM" <<'FP'
import sys
h = 0xcbf29ce484222325
for b in open(sys.argv[1], "rb").read():
    h = ((h ^ b) * 0x100000001b3) & 0xFFFFFFFFFFFFFFFF
print("0x%016xULL" % h)
FP
)"
ROM_SIZE="$(wc -c < "$ROM" | tr -d ' ')"
# -mwindows suppresses the console window. Static linking means the exe runs
# on a machine with no toolchain installed.
"$CC_WIN" -O2 -Wall $TRACE_FLAG -Iruntime -I"$SRC_DIR" \
    -DGB_BUILD_STAMP="\"$BUILD_STAMP\"" \
    -DGB_BUILD_REV="\"$BUILD_REV\"" \
    -DGB_ROM_FINGERPRINT="$ROM_FINGERPRINT" \
    -DGB_ROM_SIZE="$ROM_SIZE" \
    -DGB_ROM_PATH="\"$(printf '%s' "$ROM" | sed 's/\\/\\\\/g')\"" \
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

  Run it (from anywhere):
    "$REPO/$EXE" "$ROM"

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
