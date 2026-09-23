# True 4:3 patches for Eden (Android)

Real projection/render changes — correct proportions with the sides cropped,
not a squashed 16:9 picture.

| Game | Version | File | Status |
| --- | --- | --- | --- |
| Hyrule Warriors: Definitive Edition | 1.0.0 | `HWDE-Force-4-3.zip` | **confirmed working** |
| The Legend of Zelda: Skyward Sword HD | 1.0.0 | `SSHD-Force-4-3.zip` | **experimental** — see below |
| Goblin Sword | 1.2.0 | — | not possible yet, needs your `main` |

## How to install any of them

1. Unzip. You get a folder named after the title ID.
2. Drop that folder into the **`load`** folder in your Eden user folder:
   ```
   <Eden user folder>/load/<TITLE ID>/Force 4-3/exefs/4-3.pchtxt
   ```
3. Long-press the game → **Add-ons** → tick **Force 4-3**.
4. Long-press the game → **Properties → Graphics → Aspect Ratio → Force 4:3**.

Step 4 is required in every case: the patch makes the game *draw* 4:3, the
setting *presents* it correctly.

**Check it worked:** find something round — a shield boss, a circular HUD
element. Even circle = working. Vertical egg = still squished. Horizontal egg =
step 4 is off.

---

## 1. Hyrule Warriors: Definitive Edition — confirmed

Title ID `0100AE00096EA000`, build `0C869F41B8B9175E29B2329F594E0A4F`.

The game loads its aspect ratio as a float into `W25`. Stock loads 16/9; this
rewrites both halves to 4/3:

```
003DF5A8  MOVZ W25, #0x3FAA, LSL #16
003DF5B0  MOVK W25, #0xAAAB          => 0x3FAAAAAB = 1.3333334 = 4/3
```

Offsets are [theboy181's](https://github.com/theboy181/switch-ptchtxt-mods),
from their 21:9 mod for this same build — their pair loads `0x40155555` (21/9)
at exactly these addresses. Only the value differs. That direct correspondence
is why this one was a sure thing.

## 2. Skyward Sword HD — experimental, please report back

Title ID `01002DA013484000`, build `D824D4B1BBD45D03668380D6D041BA4CA00A64FF`.

**This one is inferred, not translated.** No aspect-ratio mod exists for this
game anywhere — theboy181 and Kenji-NX both only ship resolution mods — so
there's no known 16:9 constant to rewrite. Instead this narrows the render
target to a 4:3 shape and relies on the game deriving its aspect from that:

```
docked    1920 x 1080  ->  1440 x 1080
handheld  1280 x  720  ->   960 x  720
```

Only the widths change; every height is left at stock, so vertical detail is
untouched and the narrower frame is actually cheaper to render.

Offsets come from theboy181's 2K and 4K mods for this build, which write a
`(width, height)` u32 pair at `016CAC94`, `013A916C` and `013A915C`, plus
`MOVZ W8` immediates at `00EC803C` (width) and `00EC8068` (height). This patch
writes only the width half of each.

**Two ways this can go.** If the game derives its aspect from the render
target, you get true 4:3. If it hard-codes 16:9 somewhere instead, you'll get a
squished picture — which means this route doesn't work for this game, and we'd
need the real aspect constant out of your `main`. Do the round-object check and
tell me which you got.

One loose end: theboy181's 4K mod also touches `013A9164`, a third entry in the
same resolution table. Its stock height is unknown, so it's deliberately left
alone. If the picture is right docked but wrong handheld (or vice versa),
that's the first thing to look at.

## 3. Goblin Sword — I need the executable

Title ID `010067C010F88000`. No mods exist for this game anywhere — no
resolution mod, no FPS mod, nothing. There are no community offsets to build
on, so unlike the two above there is genuinely nothing to work from without the
game's `main` file.

Two things worth knowing before you go get it:

- It's a **2D** game. In 2D there's no projection matrix to correct — sprites
  aren't distorted by aspect, they're already drawn right. So "4:3" here can
  only mean *showing less of the level horizontally*, which the game may or may
  not handle gracefully (UI anchored off-screen, camera logic assuming a wider
  view). It's a different kind of change from the two 3D games above.
- Being a small indie title, its `main` is likely only a few MB — small enough
  to hand to me directly, unlike a big first-party game.

Get `main` out of your NSP (see below), send it over, and I'll build the patch
and tell you honestly whether there's anything patchable in there.

---

## Getting `main` out of an NSP

An NSP is a PFS0 container of encrypted NCAs, so extraction needs `prod.keys` —
the same keys Eden already uses to run the game.

**Easiest — Ryujinx on a PC:** add the game, right-click → **Extract Data →
ExeFS**. `main` lands in the output folder.

**Command line,** with `hactoolnet` and `prod.keys`:

```sh
hactoolnet -k prod.keys -t pfs0 game.nsp --outdir nsp_out
hactoolnet -k prod.keys -t nca --exefsdir exefs nsp_out/<program>.nca   # the large NCA
# -> exefs/main
```

Eden and yuzu **cannot** do this — they dump RomFS, not ExeFS.

Then either send me `main`, or run it yourself:

```sh
python3 make_43_patch.py /path/to/main --title-id 010067C010F88000
```

It reads the build ID from your dump, finds the values controlling the aspect,
and writes ready `.pchtxt` mods for both routes (resolution and projection).
Pure Python, no dependencies, runs in Termux. `--split N` and `--only 2,5` help
when several candidates turn up.

## Caveats that apply to all of these

- **The HUD does not follow.** UI is laid out for 16:9 and won't rescale, so
  expect elements to sit oddly or run off-screen. The patch changes the camera,
  not the UI.
- Cutscenes authored as fixed 16:9 compositions can crop badly.
- You see less horizontally. In Hyrule Warriors that means spotting flanking
  captains later; in a platformer it can matter more.
- Patches are version-locked. Eden checks the build ID and silently skips a
  mismatch, so an updated game shows no change rather than an error.
- Nothing is permanent — untick the add-on and you're back to stock.

## Files

- `HWDE-Force-4-3.zip` / `mod-hyrule-warriors-de/` — Hyrule Warriors DE v1.0.0.
- `SSHD-Force-4-3.zip` / `mod-skyward-sword-hd/` — Skyward Sword HD v1.0.0.
- `make_43_patch.py` — builds a patch from any game's `main`.
- `templates/example.pchtxt` — annotated format reference.

## Sources

- [theboy181/switch-ptchtxt-mods](https://github.com/theboy181/switch-ptchtxt-mods) — 21:9 mod for HW:DE, 2K/4K mods for SS HD
- [Kenji-NX/switch-pchtxt-mods](https://github.com/Kenji-NX/switch-pchtxt-mods) — SS HD resolution mods (2K–8K), no aspect mod
- [tinfoil.io](https://tinfoil.io/Title/010067C010F88000) — Goblin Sword title ID
- [yuzu game modding docs](https://yuzu-mirror.github.io/help/feature/game-modding/) — load folder layout
