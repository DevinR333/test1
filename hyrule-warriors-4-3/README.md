# Hyrule Warriors: Definitive Edition -> 4:3 on Eden (Android)

Title ID: **`0100AE00096EA000`**

Two routes. Route A takes ten seconds and needs no patch; Route B is the one
that gives you *real* 4:3. Read Route A first — it is probably what you
actually want, and it is a required companion step for Route B either way.

---

## Route A — the built-in setting (no patch)

Eden already has this. Set it **per game** so it does not affect your whole
library:

1. Long-press **Hyrule Warriors: Definitive Edition** in the game list.
2. **Properties** (or **Game Settings**) → **Graphics**.
3. **Aspect Ratio** → **Force 4:3**.

What you get: the picture is pillarboxed into a 4:3 window. Be aware of what
this actually does — the game still renders a 16:9 frame internally, and the
emulator squeezes that frame into a 4:3 box. Everything ends up horizontally
compressed: Link looks tall and thin.

That is exactly right if you are feeding a 4:3 display or a CRT, which
stretches it back out. It is wrong if you wanted the picture *cropped* to 4:3
with correct proportions. For that, you need Route B.

## Route B — an exefs patch for true 4:3

True 4:3 means the game itself builds a 4:3 projection: correct geometry,
narrower field of view, sides cropped rather than squashed. Only the game can
do that, so the game's executable has to be patched.

### Why I can't hand you a finished .pchtxt

A `.pchtxt` is a list of byte offsets into one specific build of one specific
game. The offsets are meaningless without that executable, and they change with
every game update and every region. Nobody can write those numbers without the
file in front of them — and your dump should stay on your device.

So `make_43_patch.py` does the offset-finding on your device instead. It reads
your `main`, pulls the build ID straight out of it, locates the 16:9 constants,
and writes a `.pchtxt` that is already correct for your copy.

### Steps

**1. Get the game's `main` file.**

It is in the game's exefs. Either extract the XCI/NSP with hactoolnet or
nstool, or dump it from Eden: long-press the game → Properties → the ExeFS dump
option (wording moves around between builds).

**2. Run the script** (needs Python 3.8+; works in Termux on the phone, or on a
PC — nothing is uploaded anywhere):

```sh
python3 make_43_patch.py /path/to/main
```

The title ID defaults to Definitive Edition's, so there is nothing else to
pass. It prints the build ID and every aspect-ratio constant it found, then
writes:

```
out/0100AE00096EA000/Force 4-3/exefs/<BUILDID>.pchtxt
```

**3. Install it.** Copy the `0100AE00096EA000` folder into the `load` folder
inside your Eden user folder — the folder you picked the first time you opened
Eden. The final layout is:

```
<Eden user folder>/load/0100AE00096EA000/Force 4-3/exefs/<BUILDID>.pchtxt
```

**4. Enable it.** Long-press the game → **Add-ons** (or **Mods**) → tick
**Force 4-3**. If it does not appear, the folder layout is wrong — the
`exefs` level is the one people usually miss.

**5. Also do Route A.** The patch changes what the game draws; Eden still needs
telling to show it in a 4:3 window. Without the Force 4:3 display setting you
get a correct 4:3 image stretched across a 16:9 output, which looks worse than
either on its own.

### If the script finds several constants

Expect that. `1.7777778` shows up in menus, the minimap, cutscene letterboxing
and the 3D camera alike, and only some of those are the one you want. Patch
them all first and see what breaks, then narrow down:

```sh
# split the candidates into 4 mods, enable them one at a time
python3 make_43_patch.py /path/to/main --split 4

# once you know which hits you want, keep only those
python3 make_43_patch.py /path/to/main --only 2,5
```

A bad patch cannot damage anything permanently — untick the add-on and you are
back to stock. Worst case is a crash on boot or a mangled HUD.

### If the script finds nothing

Then the game computes its aspect from the framebuffer size instead of storing
a constant. This is a real possibility here: Definitive Edition is a Wii U port
on Koei Tecmo's engine that already scales between 720p handheld and 1080p
docked, so its aspect may well be derived rather than fixed. Take the other
road:

Grab an existing **resolution mod** for Definitive Edition — theboy181's
repository has pchtxt mods for this exact game, and they already contain the
correct build IDs and the offsets of the width/height values, which is the hard
part. Open the `.pchtxt` and change the resolution it sets to a 4:3 pair
instead of a 16:9 one:

| Instead of | Use |
| --- | --- |
| 1280 x 720 | 960 x 720 |
| 1600 x 900 | 1200 x 900 |
| 1920 x 1080 | 1440 x 1080 |

Values are little-endian 32-bit ints in the patch line, so 1440 (`0x5A0`) is
written `A0050000` and 1080 (`0x438`) is written `38040000`.
`templates/example.pchtxt` shows the exact formatting. A game that derives its
aspect from the framebuffer picks up true 4:3 from this automatically. On
Android, prefer the 960x720 pair — it is also the cheapest to render.

---

## Caveats worth knowing up front

- Definitive Edition was built for 16:9 (it is a port of a 16:9 Wii U game). The
  HUD is positioned for that frame, so expect the minimap, the KO counter and
  the mission banners to sit oddly or run off the edges. The patch changes the
  camera, not the UI layout.
- Cutscenes are often authored as fixed 16:9 compositions and can crop badly.
- Anything that widens your view of the battlefield also changes how much of a
  Keep you can see at once — 4:3 crops the sides, so you will spot flanking
  captains later than you are used to.
- A game update invalidates the patch: the build ID stops matching and Eden
  quietly skips it. Re-run the script after updating.
- Cropping the sides does not make the game cheaper to render, since the
  internal resolution is unchanged — unless you take the resolution-mod route
  above, which does.

## Files here

- `make_43_patch.py` — reads your `main`, writes a ready `.pchtxt`. No
  dependencies; uses python-lz4 if present, otherwise its own LZ4 decoder.
- `templates/example.pchtxt` — annotated format reference for hand-editing.

For **Age of Calamity** instead, everything above applies with title ID
`01002B00111A2000` (pass `--title-id 01002B00111A2000`), with one extra note:
that game glitches above roughly 810p, so stick to 960x720 on the resolution
route.

## Sources

- [ChanseyIsTheBest/NX-60FPS-RES-GFX-Cheats — Definitive Edition](https://github.com/ChanseyIsTheBest/NX-60FPS-RES-GFX-Cheats/blob/main/titles/0100AE00096EA000/Hyrule%20Warriors%20Definitive%20Edition.txt) — title ID
- [theboy181/switch-ptchtxt-mods — Hyrule Warriors: Definitive Edition](https://github.com/theboy181/switch-ptchtxt-mods/tree/main/Hyrule%20Warriors:%20Definitive%20Edition) — existing pchtxt mods to base the resolution route on
- [ChanseyIsTheBest/NX-60FPS-RES-GFX-Cheats — Age of Calamity](https://github.com/ChanseyIsTheBest/NX-60FPS-RES-GFX-Cheats/blob/main/titles/01002B00111A2000/Hyrule%20Warriors%20Age%20of%20Calamity.txt) — title ID and the ~810p ceiling note
- [eden-emulator/eden-overrides](https://github.com/eden-emulator/eden-overrides) — per-game setting overrides
