#!/usr/bin/env bash
# Wraps the game into an Android APK using Cordova.
#
# This cannot run in the Claude Code sandbox: the Android SDK and the
# Android Gradle Plugin are served from dl.google.com, which the sandbox
# proxy blocks. Run it on your own machine, where it is reachable.
#
# Needs: Node 18+, a JDK (17 or 21), and the Android SDK with
#        ANDROID_HOME (or ANDROID_SDK_ROOT) set. Android Studio
#        installs all of that for you.
#
# Usage:  bash tools/make-apk.sh          -> debug APK, ready to sideload
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
BUILD="$ROOT/build-android"
APPID="com.blacklabblade.game"
APPNAME="Black Lab Blade"

command -v node >/dev/null || { echo "node is required"; exit 1; }
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
if [ -z "$ANDROID_HOME" ]; then
  echo "ANDROID_HOME (or ANDROID_SDK_ROOT) is not set."
  echo "Install Android Studio, then set it, e.g.:"
  echo '  export ANDROID_HOME="$HOME/Library/Android/sdk"   # macOS'
  echo '  export ANDROID_HOME="$HOME/Android/Sdk"           # Linux'
  exit 1
fi

echo "==> regenerating the single-file build"
python3 "$HERE/bundle.py"

echo "==> creating the Cordova project in $BUILD"
rm -rf "$BUILD"
npx --yes cordova create "$BUILD" "$APPID" "$APPNAME"

echo "==> installing the game as the app payload"
rm -rf "$BUILD/www"/*
cp "$ROOT/black-lab-blade.html" "$BUILD/www/index.html"

# Lock to landscape and fullscreen - it is a side-scroller.
python3 - "$BUILD/config.xml" <<'PY'
import sys, re
p = sys.argv[1]
s = open(p).read()
prefs = """    <preference name="Orientation" value="landscape" />
    <preference name="Fullscreen" value="true" />
    <preference name="BackgroundColor" value="0xff07060a" />
    <preference name="DisallowOverscroll" value="true" />
    <preference name="AndroidWindowSplashScreenBackground" value="#07060a" />
"""
s = s.replace("</widget>", prefs + "</widget>")
open(p, "w").write(s)
PY

echo "==> adding the android platform"
( cd "$BUILD" && npx --yes cordova platform add android )

echo "==> building"
( cd "$BUILD" && npx --yes cordova build android )

APK="$(find "$BUILD/platforms/android" -name '*-debug.apk' | head -1 || true)"
if [ -n "$APK" ]; then
  cp "$APK" "$ROOT/black-lab-blade.apk"
  echo
  echo "APK written to: $ROOT/black-lab-blade.apk"
  echo "Install it with:  adb install -r black-lab-blade.apk"
  echo "Or copy it to the phone and open it (allow install from unknown sources)."
else
  echo "Build finished but no APK was found; check the Cordova output above."
  exit 1
fi
