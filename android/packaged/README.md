# Oracle of Seasons — native Android port

A static recompilation, not an emulator: the cartridge's code was translated
to C ahead of time and is compiled into the app as ordinary native code.

## Building the APK

Nothing needs generating and nothing needs fetching. Open this folder in
Android Studio (**File → Open**, pick this folder, not its parent), let it
sync, and press Run.

The first build compiles sixty-one translated banks — about 50 MB of C — so
give it several minutes. After that it is incremental and quick.

Requirements Android Studio will install for you if they are missing:

* **NDK** and **CMake 3.22.1** (SDK Manager → SDK Tools)
* An arm64 device or emulator — `arm64-v8a` is the only ABI built, because
  building four would take four times as long and no handheld needs the rest.

If Run is greyed out or does nothing, the Gradle sync has not finished or has
failed; open the **Build** tool window and read the sync error there, and the
**Build → Build Output** pane for compile errors. Those two panes name the
actual problem — the Run button going quiet is only a symptom.

## Controls

Xbox, Retroid, 8BitDo and anything else Android reports as a gamepad work
with no setup.

| Pad | Does |
|---|---|
| D-pad | Move |
| A | A |
| B / X | B |
| **Start** | The game's own menu (save, inventory) |
| **Select** | The map |
| **L1** | Zoom out — the world keeps playing as you pull back |
| **R1** | Zoom in |
| Y | Open the display chooser |

In the display chooser: up/down to pick, A to confirm, B to back out. The
three modes are the open world drawn around the screen, the picture filling a
16:9 frame, and the picture filling a 4:3 frame.

## One thing that is not a bug

**Start and Select do nothing until the opening is finished.** That is the
cartridge's own rule, not this port's — the game keeps a flag the
disassembly comments as *"Once set, start/select are usable"*, and it is set
when Impa's scene ends. Walk east to where Din is dancing, watch the scene
through, and the menu and map turn on. Until then the game answers Start with
an error beep and nothing else.

## Speed

The machine is held to 59.73 frames a second — the hardware's own rate — by a
wall-clock deadline on the thread that runs the game. Before this the game
thread was not paced at all and ran as fast as the processor could carry it.
