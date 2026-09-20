# Android build

Open the `android` folder in Android Studio and press Run. That is the whole
procedure — Studio brings its own SDK, NDK and Gradle.

## The one thing you must do first

Put your Oracle of Seasons cartridge here:

    android/app/src/main/assets/seasons.gbc

That file is both packaged into the app **and** translated into the code the
app runs, by the build itself. Because it is the same file for both, the game's
code and the cartridge it runs against cannot disagree — which is the failure
that cost us several rounds on the desktop build.

The build also needs the disassembly checkout that the scenery is read from. It
looks for a `rooms/` directory in `external/oracles-disasm` or
`../oracles-disasm`, relative to the repository. If you have built the Windows
version, this is already there.

Python 3 must be on PATH. The build uses it once, to translate the ROM. If it
is missing, Gradle says so by name rather than failing obscurely.

## What the first build does

1. Translates your ROM into about sixty C files (a minute or two).
2. Reads the room layouts, tilesets and palettes out of the disassembly.
3. Compiles all of it for `arm64-v8a` (several minutes the first time).

Later builds skip steps 1 and 2 unless the ROM changes, and only recompile
what you touched.

Only `arm64-v8a` is built. Every current handheld — Retroid, Odin, Ayn, a
phone — is arm64, and adding the other architectures multiplies a long build
by four. To add them, edit `abiFilters` in `app/build.gradle`.

## Controls

Laid out for an Xbox-style pad, which is what a Retroid, an 8BitDo, an Odin
and most handhelds report.

| Pad | Game |
| --- | --- |
| D-pad, or left stick | move |
| A | A |
| B or X | B |
| Start | Start |
| Select, or left stick click | Select |
| Y | open the display menu |
| L1 / R1 | pull the camera back / push it in |

A pad that reports its d-pad as an axis rather than as buttons is handled too,
which is common enough that ignoring it would look like the d-pad not working.

## Display menu

Up when the game starts, and Y brings it back at any time.

- **Open world** — the world drawn around the game: the camera pulls back and
  the surrounding rooms are drawn from the game's own data.
- **Widescreen 16:9** — the hardware's screen alone, in the largest 16:9
  rectangle the display will hold.
- **Standard 4:3** — the same, in 4:3.

In both fixed shapes the screen sits at a whole multiple of its own size, so
no row of pixels is doubled while its neighbour is not.

## If it does not build

- **"No ROM found"** — put the cartridge in `app/src/main/assets` as above.
- **"Python 3 … was not found on PATH"** — install it, or use the Python that
  comes with MSYS2, then build again.
- **"No disassembly checkout found"** — the scenery comes from it; clone it
  beside the repository or into `external/oracles-disasm`.
- A black screen with the menu over it means the game is running but has not
  reached the overworld yet; the world view falls back to the plain screen
  until it does.

## Honesty about this code

It has not been run. This machine cannot reach `dl.google.com`, so there is no
NDK here and no way to compile or test it. What it has had is a compile against
stub headers standing in for the NDK, which caught a real fault — the state
struct was called `A`, and `gb.h` defines `A` as the processor's accumulator,
so every reference to it had quietly become something else. Expect to send me
the first build error; the runtime underneath it is the same code the desktop
build has been exercising all along.
