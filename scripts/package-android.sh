#!/usr/bin/env bash
# Builds the self-contained Android Studio project as one zip.
#
# The project under android/ reads the translated game and the world data from
# the repository, which means opening it needs the whole checkout, Python and a
# ROM in the right place. This packs a copy that needs none of that: the
# generated C, the runtime and the cartridge are copied in beside the sources,
# so unzipping and pressing Run is the whole procedure.
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
out=${1:-$root/build/OraclePort.zip}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
proj=$work/OraclePort

if [ ! -f "$root/build/world_data.c" ] || [ ! -f "$root/build/src/dispatch.c" ]; then
    echo "The game has not been generated yet. Run:" >&2
    echo "    bash scripts/generate-sources.sh /path/to/seasons.gbc" >&2
    exit 1
fi
rom=$(cat "$root/build/rom-path")

mkdir -p "$proj"/app/src/main/{cpp/runtime,cpp/game,assets,res/values} "$proj"/gradle/wrapper
cp "$root"/android/{settings.gradle,build.gradle,gradle.properties} "$proj"/
cp "$root"/android/app/src/main/AndroidManifest.xml "$proj"/app/src/main/
cp "$root"/android/app/src/main/res/values/strings.xml "$proj"/app/src/main/res/values/
cp "$root"/android/app/src/main/cpp/android_main.c "$proj"/app/src/main/cpp/
cp "$root"/android/packaged/build.gradle.app "$proj"/app/build.gradle
cp "$root"/android/packaged/CMakeLists.txt "$proj"/app/src/main/cpp/CMakeLists.txt
cp "$root"/android/packaged/README.md "$proj"/README.md
cp "$root"/runtime/*.c "$root"/runtime/*.h "$proj"/app/src/main/cpp/runtime/
cp "$root"/build/src/*.c "$proj"/app/src/main/cpp/game/
cp "$root"/build/src/banks.h "$proj"/app/src/main/cpp/game/
cp "$root"/build/world_data.c "$proj"/app/src/main/cpp/game/
cp "$rom" "$proj"/app/src/main/assets/seasons.gbc

# The wrapper, generated somewhere the Android plugin is not being resolved -
# "gradle wrapper" in the project itself would try to fetch the plugin first.
wrap=$work/wrap
mkdir -p "$wrap" && printf 'rootProject.name="w"\n' > "$wrap"/settings.gradle
( cd "$wrap" && gradle wrapper --gradle-version 8.7 --no-daemon -q )
cp "$wrap"/gradlew "$wrap"/gradlew.bat "$proj"/
cp "$wrap"/gradle/wrapper/gradle-wrapper.jar "$proj"/gradle/wrapper/
cp "$wrap"/gradle/wrapper/gradle-wrapper.properties "$proj"/gradle/wrapper/
chmod +x "$proj"/gradlew

printf 'local.properties\n.gradle/\nbuild/\n.idea/\n*.iml\n' > "$proj"/.gitignore

mkdir -p "$(dirname "$out")"
rm -f "$out"
( cd "$work" && zip -qr "$out" OraclePort )
echo "wrote $out ($(du -h "$out" | cut -f1))"
