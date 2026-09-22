# Hyrule Warriors: Definitive Edition -> true 4:3 on Eden (Android)

**Ready to install:** `HWDE-Force-4-3.zip`, or the `mod/` folder.
Title ID `0100AE00096EA000` · **game version 1.0.0 only** (build ID
`0C869F41B8B9175E29B2329F594E0A4F`).

This is a real projection change — correct proportions with the sides cropped,
not a squashed 16:9 picture.

## Install

1. Unzip. You get `0100AE00096EA000/Force 4-3/exefs/4-3.pchtxt`.
2. Drop the `0100AE00096EA000` folder into the **`load`** folder inside your
   Eden user folder (the folder you picked the first time you opened Eden).
   Final path:
   ```
   <Eden user folder>/load/0100AE00096EA000/Force 4-3/exefs/4-3.pchtxt
   ```
3. In Eden: long-press the game → **Add-ons** (or **Mods**) → tick **Force 4-3**.
4. Long-press the game → **Properties → Graphics → Aspect Ratio → Force 4:3**.

**Step 4 is not optional.** The patch makes the game *draw* a 4:3 scene; the
setting is what *presents* it at the right proportions. Patch without the
setting = stretched. Setting without the patch = squished (what you had).
Both = true 4:3.

## Check it worked

Load a battle and find something you know is round — a shield boss, a circular
HUD element, the compass ring. An even circle means it's working. A vertical
egg means the patch isn't applying. A horizontal egg means step 4 is off.

## If it doesn't show up, or nothing changes

You're almost certainly not on v1.0.0. Eden checks the build ID inside the
patch against your game and silently skips it if they differ — which is the
safety net that stops a wrong patch corrupting anything, but it fails quietly.

Updated copies of the game have a different build ID, and the offsets move too,
so the file has to be rebuilt against your exact version. That's what
`make_43_patch.py` is for — see below.

## What the patch actually does

The game loads its aspect ratio as a float into register `W25` via a two
instruction pair. Stock it loads 16/9; this rewrites both halves to 4/3:

```
003DF5A8  MOVZ W25, #0x3FAA, LSL #16
003DF5B0  MOVK W25, #0xAAAB
          => W25 = 0x3FAAAAAB = 1.3333334 = 4/3
```

Credit where it's due: **the offsets are [theboy181's](https://github.com/theboy181/switch-ptchtxt-mods)**,
taken from their 21:9 mod for this same build. Their pair loads `0x40155555`
(21/9) at exactly these two addresses. Only the loaded value differs here.

---

## Rebuilding it for a different game version

You need the game's `main` file — the executable from its ExeFS. Your NSP
contains it, but not in a form anything can read directly: an NSP is a PFS0
container of encrypted NCAs, so extracting it needs `prod.keys` (the same keys
Eden already uses to run the game).

**Easiest route — Ryujinx on a PC:** add the NSP to your library, right-click
the game → **Extract Data → ExeFS**. `main` lands in the output folder.

**Command line:** with `hactoolnet` and `prod.keys`:

```sh
hactoolnet -k prod.keys -t pfs0 game.nsp --outdir nsp_out
# the Program NCA is the large one
hactoolnet -k prod.keys -t nca --exefsdir exefs nsp_out/<program>.nca
# -> exefs/main
```

Note that **Eden and yuzu cannot do this** — they dump RomFS, not ExeFS. (I
said otherwise earlier in this project; that was wrong.)

Then:

```sh
python3 make_43_patch.py /path/to/main
```

It reads the build ID out of your dump, finds the values controlling the
aspect, and writes ready `.pchtxt` mods — one per route:

- **resolution** — narrows the render target to a 4:3 shape (1920x1080 →
  1440x1080), so the game derives 4:3 itself. Also cheaper to render.
- **projection** — rewrites a hard-coded 16/9 constant, like the shipped patch
  above does.

They're alternatives; enable one at a time. Pure Python, no dependencies, runs
in Termux. `--split N` and `--only 2,5` help when several candidates turn up.

## Caveats

- **The HUD does not follow.** Confirmed on this game by people who shipped
  resolution patches for it: the UI doesn't rescale, so some elements sit wrong
  or run off-screen. The patch changes the camera, not the UI layout. This is
  the thing most likely to annoy you, and there's no clean fix short of editing
  the game's UI layouts.
- Cutscenes are authored as fixed 16:9 compositions and can crop badly.
- You see less of the battlefield — flanking captains will surprise you.
- Nothing here is permanent. Untick the add-on and you're back to stock.

## Files

- `HWDE-Force-4-3.zip` / `mod/` — the installable patch, v1.0.0.
- `make_43_patch.py` — rebuilds the patch for any other version from `main`.
- `templates/example.pchtxt` — annotated format reference.

For **Age of Calamity**, pass `--title-id 01002B00111A2000`; prefer the 960x720
pair there, as it glitches above roughly 810p.

## Sources

- [theboy181/switch-ptchtxt-mods](https://github.com/theboy181/switch-ptchtxt-mods) — the 21:9 mod whose offsets this reuses
- [Digital Foundry via My Nintendo News](https://mynintendonews.com/2018/06/02/digital-foundry-hyrule-warriors-definitive-edition-handheld-super-samples-from-1080p-rather-than-dropping-resolution/) — internal 1080p rendering
- [GBAtemp: HWDE 720p patch](https://gbatemp.net/threads/request-hyrule-warriors-definitive-edition-720p-patch.674882/) — resolution patches work, UI doesn't scale
- [yuzu game modding docs](https://yuzu-mirror.github.io/help/feature/game-modding/) — load folder layout
