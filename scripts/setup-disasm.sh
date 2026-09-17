#!/usr/bin/env bash
#
# Sets up a working oracles-disasm build: installs the toolchain, clones the
# disassembly, and builds Oracle of Seasons.
#
# Run it and answer nothing. It is safe to re-run; anything already present is
# left alone.
#
#   ./scripts/setup-disasm.sh              build into ./external
#   ./scripts/setup-disasm.sh --hack-base  modifiable build (editable assets)
#   ./scripts/setup-disasm.sh --dir PATH   build somewhere else
#
set -euo pipefail

WLA_VERSION="10.6"
DISASM_REPO="https://github.com/Stewmath/oracles-disasm.git"
WLA_REPO="https://github.com/vhelin/wla-dx.git"
TARGET_DIR="external"
HACK_BASE=0

while [ $# -gt 0 ]; do
    case "$1" in
        --hack-base) HACK_BASE=1; shift ;;
        --dir) TARGET_DIR="$2"; shift 2 ;;
        -h|--help) sed -n '2,12p' "$0" | sed 's/^# \?//'; exit 0 ;;
        *) echo "unknown option: $1" >&2; exit 1 ;;
    esac
done

say()  { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[33mwarning:\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[31merror:\033[0m %s\n' "$*" >&2; exit 1; }

# --- platform -------------------------------------------------------------
case "$(uname -s)" in
    Linux)  PLATFORM=linux ;;
    Darwin) PLATFORM=macos ;;
    MINGW*|MSYS*|CYGWIN*) PLATFORM=msys ;;
    *) die "unsupported platform: $(uname -s). On Windows, install MSYS2 and run this inside it." ;;
esac
say "platform: $PLATFORM"

need_sudo() { [ "$(id -u)" -eq 0 ] && echo "" || echo "sudo"; }
SUDO=$(need_sudo)

# --- dependencies ---------------------------------------------------------
say "installing build dependencies"
case "$PLATFORM" in
    linux)
        if command -v apt-get >/dev/null; then
            $SUDO apt-get update -qq
            $SUDO apt-get install -y build-essential cmake git python3 python3-yaml
        elif command -v dnf >/dev/null; then
            $SUDO dnf install -y gcc gcc-c++ make cmake git python3 python3-pyyaml
        elif command -v pacman >/dev/null; then
            $SUDO pacman -S --needed --noconfirm base-devel cmake git python python-yaml
        else
            warn "unrecognised package manager; install manually: cmake, git, python3, python3-yaml"
        fi
        ;;
    macos)
        command -v brew >/dev/null || die "Homebrew required. Install from https://brew.sh then re-run."
        brew install cmake git python3
        python3 -m pip install --quiet --user pyyaml
        ;;
    msys)
        pacman -S --needed --noconfirm base-devel mingw-w64-x86_64-toolchain \
            mingw-w64-x86_64-cmake git python python-yaml
        ;;
esac

python3 -c 'import yaml' 2>/dev/null || {
    warn "python yaml module still missing; trying pip"
    python3 -m pip install --quiet --user pyyaml || die "could not install pyyaml"
}

# --- WLA-DX ---------------------------------------------------------------
# The disassembly needs v10.6 specifically. A distro package is usually older,
# so build it from source and install to /usr/local.
if command -v wla-gb >/dev/null && wla-gb -v 2>&1 | grep -q "$WLA_VERSION"; then
    say "WLA-DX $WLA_VERSION already installed"
else
    say "building WLA-DX $WLA_VERSION from source"
    BUILD_TMP=$(mktemp -d)
    trap 'rm -rf "$BUILD_TMP"' EXIT
    git clone --depth 1 --branch "v$WLA_VERSION" "$WLA_REPO" "$BUILD_TMP/wla-dx"
    cmake -S "$BUILD_TMP/wla-dx" -B "$BUILD_TMP/build" -DCMAKE_BUILD_TYPE=Release
    cmake --build "$BUILD_TMP/build" -j"$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 4)"
    $SUDO cmake --install "$BUILD_TMP/build"
    command -v wla-gb >/dev/null || die "wla-gb not on PATH after install"
fi
wla-gb -v 2>&1 | head -1

# --- disassembly ----------------------------------------------------------
mkdir -p "$TARGET_DIR"
DISASM="$TARGET_DIR/oracles-disasm"
if [ -d "$DISASM/.git" ]; then
    say "oracles-disasm already cloned at $DISASM"
else
    say "cloning oracles-disasm"
    git clone "$DISASM_REPO" "$DISASM"
fi

if [ "$HACK_BASE" -eq 1 ]; then
    say "switching to the modifiable build (editable text, graphics and assets)"
    ( cd "$DISASM" && git checkout hack-base 2>/dev/null \
        || { [ -x ./swapbuild.sh ] && ./swapbuild.sh; } \
        || warn "could not switch to a modifiable build; staying on the default branch" )
fi

# --- build ----------------------------------------------------------------
say "building Oracle of Seasons"
( cd "$DISASM" && make seasons -j"$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 4)" )

say "looking for build output"
find "$DISASM" -maxdepth 2 \( -name '*.gbc' -o -name '*.gb' -o -name '*.sym' \) \
    -newermt '-10 minutes' -print 2>/dev/null | sed 's/^/  /' || true

cat <<NOTE

Done.

  disassembly:  $DISASM
  built ROM:    look for seasons.gbc above

Note on verification: the project documents that WLA does not produce a
byte-exact Seasons ROM, because of how it handles empty space. A mismatch
against your own dump is expected here and does not affect how the game runs.
Ages does match.

Next:
  python3 tools/identify.py <your-dump.gbc>
  python3 tools/identify.py $DISASM/seasons.gbc
NOTE
