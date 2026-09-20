# Buddy Bounce — mechanics teardown

The brief: "the jumping cow game" — the endless vertical tilt-jumper (Doodle Jump and its
descendants). This document is the reverse-engineering pass that the implementation is built
from: first *what the reference game actually does*, then *the exact numbers Buddy Bounce uses*.

All Buddy Bounce values are in **world units (wu)**. The camera always shows exactly
**1600 wu of height**, whatever the device is, so a jump covers the same fraction of the screen
on a 4:3 tablet, a 20:9 phone, in portrait, and in landscape. Width is derived:
`worldW = 1600 * (screenW / screenH)` — 900 wu on a 16:9 portrait phone, 2844 wu on the same
phone in landscape.

---

## 1. The core loop

**Reference behaviour**

* The character *never* jumps on command. It bounces automatically the instant it lands on a
  platform, always to the same height. The only input is horizontal.
* Gravity is constant. The bounce is a fixed impulse, so the rhythm is metronomic: ~0.5 s up,
  ~0.5 s down.
* The world is one endless vertical column. Platforms are generated above, culled below.
* Death has exactly one cause in the base game: falling off the bottom of the view. (Monsters
  and black holes add a second.)

**Buddy Bounce**

| Quantity | Value | Notes |
|---|---|---|
| Gravity | 3300 wu/s² | |
| Bounce impulse | 1720 wu/s | apex = v²/2g = **448 wu** = 28 % of the view height |
| Time up / down | 0.52 s / 0.52 s | one bounce cycle ≈ 1.04 s |
| Terminal fall speed | 3000 wu/s | keeps a long fall readable |
| Physics step | fixed 1/240 s, max 8 substeps/frame | no tunnelling at rocket speed |

Rationale: the apex must comfortably clear the largest platform gap, with enough margin left
over to steer. 448 wu apex vs. a 336 wu worst-case gap leaves 112 wu (25 %) of slack.

---

## 2. Horizontal movement and the tilt model

**Reference behaviour**

* Tilt is mapped to *acceleration*, not to position, which is why the character feels like it
  has weight and overshoots slightly. There is a maximum horizontal speed.
* The playfield **wraps**: exit the right edge, re-enter on the left, at the same height. This
  is load-bearing — the wrap is often the fastest route to a platform.
* The character keeps its horizontal velocity through a bounce; landing does not stop it.

**Buddy Bounce**

* `targetVx = steer * maxVx`, and `vx` eases toward it: `vx += (targetVx - vx) * (1 - e^(-k·dt))`
  with **k = 9** for tilt (weighty, slight overshoot) and **k = 16** for the touch gauge and
  gamepad (crisper, because those inputs are already absolute).
* `maxVx = 1035 * (worldW / 900)^0.6` — sub-linear so that a very wide landscape playfield does
  not turn into a twitch-fest. 16:9 portrait ≈ 1035 wu/s (crosses the screen in 0.87 s);
  16:9 landscape ≈ 2070 wu/s (crosses in 1.37 s).
* Tilt input: raw accelerometer, remapped through the display rotation (so it works identically
  in portrait and landscape), minus a calibration offset captured on demand, with a 0.6 m/s²
  dead zone, divided by a 4.5 m/s² full-scale (≈ 27° of tilt), then clamped to [-1, 1] and
  multiplied by the sensitivity setting (0.5–1.8).
* Screen wrap is on for the player, coins and enemies; platforms never straddle the seam.

---

## 3. Platform taxonomy

**Reference behaviour** — colour encodes behaviour, and the mix shifts with altitude:

| Reference | Behaviour |
|---|---|
| Green | Static. Safe. Dominant at low altitude. |
| Blue | Slides horizontally, bounces off the side walls. |
| Grey/white | One-use: vanishes the moment you bounce off it. |
| Brown | Breaks on contact and gives **no** bounce — you fall straight through. |
| Yellow | Shifts or explodes after a short countdown. |
| (rare) flashing white | Teleports vertically on contact. |

The critical detail is that the brown one is the only type that does *not* bounce you: the
player has to read the colour *before* landing. Everything else is a timing problem, brown is a
routing problem.

**Buddy Bounce** — same taxonomy, re-skinned per biome (a garden plank in the backyard is a
branch in the treetops, a cloud slab above that, a crystal shard in the aurora, an asteroid in
space). Behaviour is identical across skins so the read stays learnable:

| Type | Behaviour | Intro altitude |
|---|---|---|
| `SOLID` | Static. | from the start |
| `SLIDER` | Moves horizontally 80–190 wu/s, reverses at the playfield edges. | 2 screens |
| `HOVER` | Moves vertically ±55 wu at 0.6 Hz. | 9 screens |
| `CRUMBLE` | Bounces you once, then crumbles away over 0.35 s. | 4 screens |
| `FRAGILE` | Gives **no** bounce; shatters and drops you. | 6 screens |
| `SPRING` | Solid + a spring: 2.0× bounce. | 1 screen |
| `TRAMPOLINE` | Solid + a trampoline: 2.6× bounce, with a stretch animation. | 3 screens |

Generation safety rules (these are what stop a run from ending unfairly):

* Never two non-bouncing platforms (`FRAGILE`) in a row; never three "hazard" platforms
  (fragile/crumble) in a row.
* Every 6th row is guaranteed `SOLID`.
* A row may spawn a second platform beside the first (12 % chance, rising with difficulty) to
  widen the route.

---

## 4. Power-ups

**Reference behaviour** — a strict boost ladder, all sourced from platforms, all overriding
normal physics while active. Ascending order: spring < trampoline < propeller hat < jetpack <
rocket. Flight power-ups make you invulnerable while they last, and you cannot land on
platforms mid-flight — you pass straight through them. The shield is the odd one out: no boost,
one free hit.

**Buddy Bounce** — the same ladder, tuned so each tier is visibly a rung above the last:

| Power-up | Effect | Height gained | Rarity/platform |
|---|---|---|---|
| Spring | impulse ×2.0 (3440 wu/s) | 1790 wu ≈ 1.1 screens | 9 % |
| Trampoline | impulse ×2.6 (4472 wu/s) | 3030 wu ≈ 1.9 screens | 3 % |
| Propeller cap | 1900 wu/s for 3.2 s | ≈ 6100 wu ≈ 3.8 screens | 1.6 % |
| Jetpack | 2650 wu/s for 4.0 s | ≈ 10 600 wu ≈ 6.6 screens | 0.8 % |
| Rocket bone | 3600 wu/s for 4.6 s | ≈ 16 500 wu ≈ 10 screens | 0.18 % |
| Bubble shield | absorbs one hazard hit, 12 s | — | 1.1 % |
| Coin magnet | pulls coins within 520 wu, 7 s | — | 1.3 % |

Flight rules: platforms are ignored (`vy` is driven, not integrated), hazards are destroyed on
contact, and the exit is a smooth hand-back to gravity rather than an instant drop.

---

## 5. Hazards

**Reference behaviour** — monsters sit on or hover near platforms. Touching one from the side
or below kills you; landing on its head from above kills *it* and bounces you. Black holes and
UFOs kill regardless of approach. Base game lets you shoot upward by tapping.

**Buddy Bounce** — the brief specifies left/right steering only, so there is no shooting; every
hazard is solvable by routing or by stomping:

| Hazard | Bounceable from above? | Intro | Movement |
|---|---|---|---|
| Bee | yes (+120 pts, small bounce) | 4 screens | sine drift |
| Crow | yes (+150 pts) | 11 screens | patrols horizontally, wraps |
| Storm cloud | **no** — kills on any contact | 20 screens | static, telegraphed by arcing sparks |
| Void rift | **no** | 30 screens | static, slow swirl, faint pull |

A shield converts any lethal contact into a pop + brief invulnerability instead of a death.

---

## 6. Camera and scoring

**Reference behaviour**

* The camera only ever moves **up**. It tracks the character while it rises above an anchor line
  roughly 40–45 % down the screen and never follows it back down — that asymmetry is what makes
  falling lethal.
* Score is the height climbed, and it only counts your *highest* point (falling doesn't subtract
  and re-climbing the same stretch doesn't re-score).

**Buddy Bounce**

* Anchor line: 45 % from the top. `camY = max(camY, playerY - 0.55 * 1600)`, then eased toward
  that target at 18/s for a silky rise, never downward.
* Death: the whole sprite is below the camera bottom, or lethal hazard contact.
* Score: `floor(maxHeightAboveStart / 10)` + hazard bonuses. So one screen of climb = 160 pts,
  and a 10 000-point run is ~62 screens.
* Game over: score is submitted to the local leaderboard under the name entered on first launch
  (top 10 kept, best run highlighted).

---

## 7. Difficulty curve

Everything is a function of `s` = screens climbed (`height / 1600`):

| Parameter | s = 0 | s = 6 | s = 16 | s = 32 | s ≥ 55 |
|---|---|---|---|---|---|
| Row gap (wu) | 170–215 | 195–255 | 225–295 | 255–325 | 265–336 |
| Platform width (wu, base 9:16) | 200 | 190 | 175 | 158 | 148 |
| Hazard platform share | 0 % | 14 % | 26 % | 34 % | 38 % |
| Moving platform share | 0 % | 18 % | 28 % | 34 % | 36 % |
| Enemies per screen | 0 | 0.35 | 0.8 | 1.15 | 1.3 |

Platform width also scales with `(worldW / 900)^0.35` so a landscape playfield is not a sea of
tiny ledges and a narrow portrait one is not a tightrope, and rows spawn proportionally more
platforms as the playfield gets wider.

---

## 8. Where Buddy Bounce deliberately differs

* **Coins and the gacha.** Roughly one platform in eight carries a coin, and 4.5% of rows
  instead throw a 4-6 coin arc between two platforms that is worth going out of your way for;
  ~1 coin in 11 is a 5-coin bone instead. A good run is 20-30 coins. 100 coins buys one pull
  on the coin machine — a Crossy-Road-style prize
  machine that dispenses a random outfit for Buddy, weighted by rarity, with duplicate
  protection: a duplicate refunds 35 coins, and a rarity you have completed rolls down into
  one you haven't.
* **Wardrobe.** Every unlocked outfit can be equipped and swapped freely, any time, for free.
* **No shooting.** The reference game's tap-to-shoot is dropped, per the brief.
* **Orientation.** Portrait or landscape, switchable in Settings, with the tilt axis remapped
  through the display rotation and the whole playfield re-tuned for the new aspect ratio.

---

## Sources consulted for reference behaviour

* Doodle Jump Wiki — [Classic](https://doodle-jump.fandom.com/wiki/Classic),
  [Movable Platforms](https://doodle-jump.fandom.com/wiki/Movable_Platforms),
  [Propeller Hat](https://doodle-jump.fandom.com/wiki/Propeller_Hat)
* [Doodle Jump — Wikipedia](https://en.wikipedia.org/wiki/Doodle_Jump)
* [Doodle Jump high score & power-up guide](https://doodlejump.io/doodle-jump-high-score-secrets-guide)
* [Doodle Jump gameplay guide](https://www.playdoodlejumpgame.com/doodle_jump_gameplay/),
  [levels guide](https://www.playdoodlejumpgame.com/doodle_jump_levels/)
* [How to Make Doodle Jump with Felgo](https://felgo.com/doc/howto-doodle-jump-game-basic-tutorial/)
  (physics scaffolding reference)

---

## 9. Balance check

The simulation has no Android dependencies, so it can be played headless. `tools/sim/Sim.kt`
runs a bot - it locks a reachable target platform at each bounce and steers toward it - through
eight runs in six aspect ratios, which is how the numbers above were tuned. Current results:

| Playfield | avg screens | best run | coins/run |
|---|---|---|---|
| portrait 21:9 (675 wu) | 22.2 | 5 899 pts | ~27 |
| portrait 16:9 (900 wu) | 22.6 | 4 235 pts | ~22 |
| portrait 3:4 (1067 wu) | 19.3 | 4 750 pts | ~16 |
| landscape 4:3 (2133 wu) | 29.0 | 7 426 pts | ~19 |
| landscape 16:9 (2844 wu) | 31.6 | 7 606 pts | ~18 |
| landscape 21:9 (3733 wu) | 34.2 | 7 899 pts | ~20 |

Wide playfields are the easier ones - there is simply more room to line up a landing - and the
gap is small enough to be a matter of taste rather than a reason to pick an orientation. A
human who detours for coins will beat the bot's coin rate comfortably, which puts a prize pull
every three or four runs.

To re-run it after changing anything in `Tuning.kt`:

```bash
kotlinc app/src/main/java/com/blacklab/buddybounce/game/*.kt tools/sim/Sim.kt -include-runtime -d sim.jar
java -jar sim.jar
```
