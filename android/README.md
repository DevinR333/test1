# Android build

Open the `android` folder in Android Studio. It brings its own SDK and NDK, so
nothing else needs installing.

## Once, before the first build

The game's code is translated from your ROM, and the world outside the game's
own screen is read from the disassembly. Both have to exist before Android
Studio has anything to compile. From the repository root:

    bash scripts/generate-sources.sh

That writes `build/src/` and `build/world_data.c`, which the Android build
reads directly. It needs Python 3 and takes a couple of minutes. If you have
already built the Windows version, this is done.

Then put the same ROM in the package so the app is one self-contained file:

    cp external/oracles-disasm/seasons.gbc android/app/src/main/assets/

It must be the **same ROM** the sources were generated from. The translated
code only runs against the cartridge it was translated from.

## Building

Open `android/` in Android Studio, let it sync, and Run. The first build
compiles sixty translated files and takes several minutes; later builds only
recompile what changed.

Only `arm64-v8a` is built. Every current handheld - Retroid, Odin, Ayn, a
phone - is arm64, and adding the other architectures multiplies a long build
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

It is up when the game starts, and Y brings it back at any time.

- **Open world** - the world drawn around the game, which is what the desktop
  build does: the camera pulls back and the surrounding rooms are drawn from
  the game's own data.
- **Widescreen 16:9** - the hardware's screen alone, in the largest 16:9
  rectangle the display will hold.
- **Standard 4:3** - the same, in 4:3.

In both fixed shapes the screen sits at a whole multiple of its own size, so
no row of pixels is doubled while its neighbour is not.

## If it does not build

- *"The translated game has not been generated yet"* - run
  `scripts/generate-sources.sh` as above.
- *No ROM in the package* - the app logs this and exits. Put `seasons.gbc` in
  `app/src/main/assets`.
- A black screen with the menu over it means the game is running but has not
  reached the overworld yet; the world view falls back to the plain screen
  until it does.
