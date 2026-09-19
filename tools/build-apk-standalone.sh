#!/usr/bin/env bash
# Builds Buddy Blade into an Android APK.
#
# SELF-CONTAINED: put this script next to buddy-blade.html and run it.
# You do NOT need the git repo.
#
#   bash build-apk-standalone.sh
#   bash build-apk-standalone.sh /path/to/buddy-blade.html
#
# Requires: Node 18+, a JDK (17 or 21), and the Android SDK.
# Installing Android Studio gives you all three.

set -euo pipefail

say()  { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
die()  { printf '\n\033[31mERROR: %s\033[0m\n\n' "$*" >&2; exit 1; }

# ---- find the game file -------------------------------------------------
HTML="${1:-}"
if [ -z "$HTML" ]; then
  HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  for guess in "$HERE/buddy-blade.html" "$PWD/buddy-blade.html" \
               "$HOME/Downloads/buddy-blade.html" "$HOME/Desktop/buddy-blade.html"; do
    [ -f "$guess" ] && HTML="$guess" && break
  done
fi
[ -n "$HTML" ] && [ -f "$HTML" ] || die "Can't find buddy-blade.html.
Put this script in the same folder as the game, or pass the path:
    bash $(basename "$0") ~/Downloads/buddy-blade.html"
HTML="$(cd "$(dirname "$HTML")" && pwd)/$(basename "$HTML")"
OUTDIR="$(dirname "$HTML")"
say "game file: $HTML"

# ---- prerequisites ------------------------------------------------------
command -v node >/dev/null || die "Node.js is not installed. Get it from https://nodejs.org (LTS)."
command -v java >/dev/null || die "A JDK is not installed. Installing Android Studio provides one."

: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
if [ -z "${ANDROID_HOME:-}" ]; then
  for guess in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk" "$LOCALAPPDATA/Android/Sdk"; do
    [ -d "$guess" ] && export ANDROID_HOME="$guess" && break
  done
fi
[ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME" ] || die "Android SDK not found.
Install Android Studio, open it once so it downloads the SDK, then set:
    export ANDROID_HOME=\"\$HOME/Library/Android/sdk\"   # macOS
    export ANDROID_HOME=\"\$HOME/Android/Sdk\"           # Linux
and run this script again."
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
say "android sdk: $ANDROID_HOME"

# ---- build --------------------------------------------------------------
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

say "creating the Android project (first run downloads Cordova, ~1 min)"
npx --yes cordova@12 create "$WORK/app" com.buddyblade.game "Buddy Blade" >/dev/null

rm -rf "$WORK/app/www"/*
cp "$HTML" "$WORK/app/www/index.html"

# landscape, fullscreen, no rubber-band scrolling
node -e '
const fs = require("fs"), p = process.argv[1];
let s = fs.readFileSync(p, "utf8");
s = s.replace("</widget>", `    <preference name="Orientation" value="landscape" />
    <preference name="Fullscreen" value="true" />
    <preference name="DisallowOverscroll" value="true" />
    <preference name="BackgroundColor" value="0xff07060a" />
</widget>`);
fs.writeFileSync(p, s);
' "$WORK/app/config.xml"

say "adding the android platform (downloads Gradle deps, a few minutes)"
( cd "$WORK/app" && npx --yes cordova@12 platform add android >/dev/null )

say "building the APK"
( cd "$WORK/app" && npx --yes cordova@12 build android )

APK="$(find "$WORK/app/platforms/android" -name '*.apk' | head -1 || true)"
[ -n "$APK" ] || die "Build finished but produced no APK. Scroll up for the Gradle error."

cp "$APK" "$OUTDIR/buddy-blade.apk"
say "done"
printf '\nAPK: %s\n\n' "$OUTDIR/buddy-blade.apk"
echo "Install it one of these ways:"
echo "  1. Plug the phone in with USB debugging on:  adb install -r \"$OUTDIR/buddy-blade.apk\""
echo "  2. Copy the .apk to the phone and tap it (allow installs from unknown sources)."
echo
