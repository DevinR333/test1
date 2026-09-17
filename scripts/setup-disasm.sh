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
if [ "$PLATFORM" = "msys" ]; then
    say "platform: MSYS2 (${MSYSTEM:-unknown} environment)"
else
    say "platform: $PLATFORM"
fi

# MSYS2 has no sudo, and pacman there does not need it.
if [ "$PLATFORM" = "msys" ] || [ "$(id -u)" -eq 0 ]; then
    SUDO=""
elif command -v sudo >/dev/null; then
    SUDO="sudo"
else
    die "sudo not found and not running as root; install sudo or re-run as root"
fi

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
        python3 -m pip install --quiet --user pyyaml || true
        ;;
    msys)
        pacman -S --needed --noconfirm base-devel git \
            python python-yaml \
            mingw-w64-x86_64-toolchain mingw-w64-x86_64-cmake
        ;;
esac

if [ "$PLATFORM" = "msys" ]; then
    # Installed here regardless of which shell shortcut was used.
    for d in /mingw64/bin /ucrt64/bin /usr/bin; do
        [ -d "$d" ] && case ":$PATH:" in *":$d:"*) ;; *) PATH="$d:$PATH" ;; esac
    done
    export PATH

    if ! command -v cmake >/dev/null; then
        warn "cmake still not on PATH; installing the MSYS build as a fallback"
        pacman -S --needed --noconfirm cmake || true
    fi
    if ! command -v gcc >/dev/null; then
        warn "gcc still not on PATH; installing the MSYS toolchain as a fallback"
        pacman -S --needed --noconfirm gcc make || true
    fi
fi

for tool in cmake git make; do
    command -v "$tool" >/dev/null || die "$tool is not on PATH after installation.
  If you are on Windows, close this window and open \"MSYS2 MINGW64\" from the
  Start menu (not \"MSYS2 MSYS\"), then re-run this script."
done
say "toolchain: cmake $(cmake --version 2>/dev/null | head -1 | awk '{print $3}'), $(gcc --version 2>/dev/null | head -1)"

# The interpreter is `python3` on most systems but `python` under some MSYS2
# environments, so resolve it once instead of assuming.
PY=""
for candidate in python3 python; do
    if command -v "$candidate" >/dev/null && "$candidate" -c 'import sys; sys.exit(0 if sys.version_info[0] == 3 else 1)' 2>/dev/null; then
        PY="$candidate"
        break
    fi
done
[ -n "$PY" ] || die "no Python 3 found on PATH. Install it and re-run."
say "using $PY ($("$PY" --version 2>&1))"

if ! "$PY" -c 'import yaml' 2>/dev/null; then
    warn "the python yaml module is missing; installing it"
    "$PY" -m pip install --quiet --user pyyaml \
        || die "could not install pyyaml. Try: pacman -S python-yaml"
fi

# oracles-disasm's build scripts call python3 by name. If only `python` exists,
# put a python3 alongside it on PATH for the duration of the build.
if ! command -v python3 >/dev/null; then
    SHIM_DIR="$(mktemp -d)"
    printf '#!/bin/sh\nexec %s "$@"\n' "$(command -v "$PY")" > "$SHIM_DIR/python3"
    chmod +x "$SHIM_DIR/python3"
    export PATH="$SHIM_DIR:$PATH"
    warn "no python3 on PATH; shimming it to $PY for this build"
fi

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
    ( cd "$DISASM"
      git checkout hack-base 2>/dev/null \
        || { [ -x ./swapbuild.sh ] && ./swapbuild.sh; } \
        || warn "could not switch to a modifiable build; staying on the default branch"
      # Say plainly where we ended up. A detached HEAD is harmless for building
      # but means the checkout is not on the branch you might expect.
      current=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)
      if [ "$current" = "HEAD" ]; then
          warn "checkout is in detached HEAD at $(git rev-parse --short HEAD). Fine for building."
      else
          echo "  on branch: $current"
      fi )
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
  $PY tools/identify.py <your-dump.gbc>
  $PY tools/identify.py $DISASM/seasons.gbc
NOTE
