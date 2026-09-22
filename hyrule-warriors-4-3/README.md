# Hyrule Warriors: Definitive Edition -> true 4:3 on Eden (Android)

Title ID: **`0100AE00096EA000`**

## Why "Force 4:3" alone looks squished

Eden's Aspect Ratio setting is a *display* setting. The game still draws a 16:9
scene; Force 4:3 just compresses that scene into a narrower box. Link gets tall
and thin. That is the squish you're seeing.

The fix is not to avoid that setting — it's to make **the game itself** draw a
4:3 scene. Then the same setting presents it at the right proportions.

| | Result |
| --- | --- |
| Force 4:3 only | squished (16:9 scene crushed into a 4:3 box) |
| Patch only | stretched (4:3 scene smeared across a 16:9 box) |
| **Patch + Force 4:3** | **true 4:3 — correct proportions, sides cropped** |

So you need both halves. The patch is the half that doesn't exist yet, and
that's what `make_43_patch.py` builds.

## Why I can't just hand you a finished .pchtxt

A `.pchtxt` is a list of byte offsets into one specific build of one specific
game. The offsets don't exist without that executable in front of you, and they
change with every game update and region. So the script does the
offset-finding on your device: it reads your `main`, pulls the build ID out of
it, finds the values that control the aspect, and writes a `.pchtxt` that is
already correct for your copy.

It's pure Python with no dependencies, so it runs in Termux on the phone. It
uploads nothing.

---

## Step 1 — get the game's `main` file

It lives in the game's exefs. Either extract the XCI/NSP with hactoolnet or
nstool, or dump it from Eden: long-press the game → Properties → the ExeFS dump
option (wording moves between builds).

## Step 2 — run the script

```sh
python3 make_43_patch.py /path/to/main
```

The title ID defaults to Definitive Edition's, so there's nothing else to pass.
It prints what it found and writes **two** mods — one per route:

```
out/0100AE00096EA000/Force 4-3 (resolution)/exefs/<BUILDID>.pchtxt
out/0100AE00096EA000/Force 4-3 (projection)/exefs/<BUILDID>.pchtxt
```

**These are alternatives. Enable one at a time, never both** — they would
double-correct and you'd be back to a wrong shape.

### Route 1: resolution (try this first)

Definitive Edition renders internally at 1920x1080 — it supersamples from 1080p
even in handheld. Engines that size their render target this way derive the
aspect ratio from it, so narrowing the width to the 4:3 partner of the same
height makes the game compute a genuine 4:3 projection by itself:

| Instead of | Becomes |
| --- | --- |
| 1920 x 1080 | 1440 x 1080 |
| 1280 x 720 | 960 x 720 |

The height is deliberately left alone — that preserves vertical detail, and the
narrower frame is *cheaper* to render, which matters on Android.

This route is the more reliable one here, and it's known to work on this game:
people have already shipped 720p and 540p resolution patches for Definitive
Edition, which means these values really are stored in the executable where the
script can find them.

### Route 2: projection constant

If the game hard-codes `1.7777778` (16/9) somewhere and builds its camera
matrix from it, rewriting that constant to `1.3333334` (4/3) gives true 4:3
directly. The script looks for it as f32 and f64, in both the `16/9` and `9/16`
forms.

## Step 3 — install

Copy the `0100AE00096EA000` folder into the `load` folder inside your Eden user
folder (the folder you picked the first time you opened Eden):

```
<Eden user folder>/load/0100AE00096EA000/Force 4-3 (resolution)/exefs/<BUILDID>.pchtxt
```

Long-press the game → **Add-ons** (or **Mods**) → tick the one you want. If it
doesn't appear, the layout is wrong — the `exefs` level is the one people miss.

## Step 4 — set Eden to Force 4:3

Long-press the game → Properties → Graphics → Aspect Ratio → **Force 4:3**.
Per-game, so it won't affect the rest of your library.

## Step 5 — check it's actually true 4:3

Load a battle and look at something you know is round — a shield boss, a
circular HUD element, the compass ring on the map. If it's an even circle,
you're done. If it's a vertical egg, the image is still squished: the patch
isn't applying (check the build ID matches) or you're on the wrong route.
A horizontal egg means the display setting isn't on.

---

## If several candidates turn up

Expect it, especially on the projection route — `1.7777778` shows up in menus,
the minimap and cutscene letterboxing too, not just the camera. Patch them all
first, see what breaks, then narrow:

```sh
# split one route's candidates into 4 mods, enable them one at a time
python3 make_43_patch.py /path/to/main --mode projection --split 4

# once you know which hits you want, keep only those
python3 make_43_patch.py /path/to/main --mode projection --only 2,5
```

Nothing here is permanent — untick the add-on and you're back to stock. Worst
case is a crash on boot or a mangled HUD.

## If the script finds nothing

Then the game builds its framebuffer size at runtime rather than storing it,
and there's no constant to hit. At that point the remaining option is to open
an existing community resolution mod for this game in a text editor and edit
the values it sets to a 4:3 pair — those mods already contain the correct build
ID and the hard-won offsets. `templates/example.pchtxt` shows the exact byte
formatting (little-endian, so 1440 = `0x5A0` is written `A0050000`).

## Caveats — worth knowing before you spend an evening on this

- **The HUD will not follow.** This is confirmed on Definitive Edition: people
  who shipped resolution patches for it report the UI doesn't rescale, so some
  elements sit wrong or go off-screen. The patch changes the camera, not the
  UI layout. This is the single most likely thing to annoy you.
- Cutscenes are authored as fixed 16:9 compositions and can crop badly.
- True 4:3 crops the sides, so you see less of the battlefield — flanking
  captains will surprise you more than you're used to.
- A game update invalidates the patch: the build ID stops matching and Eden
  silently skips it. Re-run the script after updating.

## Files here

- `make_43_patch.py` — reads your `main`, writes ready `.pchtxt` mods for both
  routes. No dependencies; uses python-lz4 if present, else its own LZ4 decoder.
- `templates/example.pchtxt` — annotated format reference for hand-editing.

For **Age of Calamity** instead, pass `--title-id 01002B00111A2000`. One extra
note there: that game glitches above roughly 810p, so prefer the 960x720 pair.

## Sources

- [Digital Foundry via My Nintendo News](https://mynintendonews.com/2018/06/02/digital-foundry-hyrule-warriors-definitive-edition-handheld-super-samples-from-1080p-rather-than-dropping-resolution/) — Definitive Edition renders at 1080p internally and supersamples in handheld
- [GBAtemp: HWDE 720p patch request](https://gbatemp.net/threads/request-hyrule-warriors-definitive-edition-720p-patch.674882/) — 720p/540p resolution patches exist for this game, and the UI doesn't scale with them
- [ChanseyIsTheBest/NX-60FPS-RES-GFX-Cheats — Definitive Edition](https://github.com/ChanseyIsTheBest/NX-60FPS-RES-GFX-Cheats/blob/main/titles/0100AE00096EA000/Hyrule%20Warriors%20Definitive%20Edition.txt) — title ID
- [theboy181/switch-ptchtxt-mods — Definitive Edition](https://github.com/theboy181/switch-ptchtxt-mods/tree/main/Hyrule%20Warriors:%20Definitive%20Edition) — existing pchtxt mods for this game
- [eden-emulator/eden-overrides](https://github.com/eden-emulator/eden-overrides) — per-game setting overrides
