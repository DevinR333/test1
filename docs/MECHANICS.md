# Buddy Bounce — mechanics teardown

The brief: "the jumping cow game" — the endless vertical tilt-jumper (Doodle Jump and its
descendants). This document is the reverse-engineering pass that the implementation is built
from: first *what the reference game actually does*, then *the exact numbers Buddy Bounce uses*.

All Buddy Bounce values are in **world units (wu)**. The camera always shows exactly
**2560 wu of height**, whatever the device is, so a jump covers the same fraction of the screen
on a 4:3 tablet, a 20:9 phone, in portrait, and in landscape. Width is derived:
`worldW = 2560 * (screenW / screenH)` — 1440 wu on a 16:9 portrait phone, 4551 wu on the same
phone in landscape.

**Two coordinate spaces.** The UI is laid out in its own 1600-unit-tall space that fills the
screen; the world is drawn inside it at 1600/2560 scale. That ratio *is* the camera zoom: Buddy
and the platforms are sized in absolute wu, so a taller world view means a smaller dog in more
sky, while the menus stay exactly the size they were. Zooming the game out is a one-line change
to `Tuning.VIEW_H`.

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
| Gravity | 6250 wu/s² | |
| Bounce impulse | 3250 wu/s | apex = v²/2g = **845 wu** = 33 % of the view height |
| Time up / down | 0.52 s / 0.52 s | one bounce cycle ≈ 1.04 s |
| Terminal fall speed | 5400 wu/s | keeps a long fall readable |
| Physics step | fixed 1/240 s, max 8 substeps/frame | no tunnelling at rocket speed |

Rationale: the apex must comfortably clear the largest platform gap, with enough margin left
over to steer. An 845 wu apex vs. a 627 wu worst-case gap leaves 218 wu (26 %) of slack.

**The yard floor.** A run opens standing on a full-width solid platform at the bottom of the
world, with a launch pad above it. Missing the first bounce therefore lands you back on the
grass rather than killing you; the floor is culled as soon as the camera leaves it behind.

---

## 2. Horizontal movement and the tilt model

**Reference behaviour**

* Tilt is mapped to *acceleration*, not to position, which is why the character feels like it
  has weight and overshoots slightly. There is a maximum horizontal speed.
* The playfield **wraps**: exit the right edge, re-enter on the left, at the same height. This
  is load-bearing — the wrap is often the fastest route to a platform.
* The character keeps its horizontal velocity through a bounce; landing does not stop it.

**Buddy Bounce**

* `targetVx = steer * maxVx`, and `vx` eases toward it: `vx += (targetVx - vx) * (1 - e^(-k·dt))`.
* **The rate is asymmetric, and this is the single most important feel decision in the game.**
  Accelerating is soft (k = 15 tilt / 26 touch); *braking* — heading back toward zero, or
  reversing — is hard (k = 38 / 55). With one symmetric rate the velocity lagged the input both
  on the way in and on the way out, so a mid-fall correction overshot and Buddy sailed past the
  ledge: the game felt imprecise in exactly the moment precision matters. Braking hard means
  "stop" is immediate and a correction lands where it was aimed.
* A mild **expo curve** (`|steer|^1.25`) on tilt and touch: a small movement is a small, precise
  nudge, while full deflection is still full speed.
* `maxVx = 1900 * (worldW / 1440)^0.6` — sub-linear so that a very wide landscape playfield does
  not turn into a twitch-fest.
* **Landing is forgiving on purpose.** The foot box is 56 wu either side of centre (narrower
  than the art), and clipping the corner of a ledge grants another 26 wu of grab *while he is
  still travelling toward it* — so a near-miss you were obviously aiming for catches, but you
  are never yanked onto something you were leaving.
* **Nothing is drawn for the controls.** No track, no knob, no gauge: touch works anywhere on
  the screen, so there is nothing to look at, find, or cover the action with.
* **Touch is a positional drag, not a rate control.** This is the single biggest feel change in
  the game and it is worth understanding why.

  Touch used to set a *speed*: the further the finger sat from where it went down, the faster
  Buddy went. That has a nasty property — to move him a distance you have to hold the finger out
  there for a length of time. A quick slide and release barely moved him at all, and the hard
  braking that makes a correction land also meant stopping the finger stopped him on the spot.
  Reaching the far side of the screen meant swiping the whole screen and *keeping* the thumb
  there.

  Now the finger drags a target position. Every pixel of finger travel moves that target by
  `DRAG_GAIN` × the same distance in world units (2.35×, so a thumb-flick crosses real ground),
  and Buddy servos to it at `err × DRAG_STIFFNESS` clamped to 1.45 × his top speed. Distance
  swiped maps to distance travelled, roughly 1:1 for any swipe he could physically follow, and
  when the finger stops he stops — inside 160–300 ms, without overshooting.

  Sensitivity is anchored to the screen's SHORT edge (`DRAG_SPAN`: crossing it moves him 3384
  world units). The obvious formulation - world units per UI unit of finger travel - looks
  orientation-independent and is not, because the UI scale comes from screen *height*, which
  nearly halves when the phone is turned on its side; the same thumb movement bought about 1.8x
  more ground in landscape, which felt like a different game. A phone's short edge is the same
  number of pixels whichever way up it is, so measuring against it gives identical sensitivity
  in both orientations and normalises across resolutions for free.

  The one guard rail is a lead cap: the target may never get further ahead of him than
  `DRAG_LEAD_SECONDS` (0.30 s) of travel at his top speed. That is expressed as *time*, not
  distance, on purpose — a fixed distance that felt right in portrait threw away most of a swipe
  in landscape, where the playfield is three times wider and he moves twice as fast. As a time
  it means the same thing on every device: "he may be up to a third of a second behind your
  finger." Only a flick faster than he can physically follow gets clipped, which is correct.

  No expo curve is applied on this path. Expo exists to make small *rate* inputs finer; on a
  positional control it would just make the dog not go where the finger went. Tilt and the pad
  keep both the expo and the velocity easing, because they are inherently rate controls.
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
| `SPRING` | Solid + a spring: 1.55× bounce. | 1 screen |
| `TRAMPOLINE` | Solid + a trampoline: 1.95× bounce, with a stretch animation. | 3 screens |

**The fragile rule.** `FRAGILE` is the only platform that gives no bounce at all, so a row
whose *only* platform is fragile is not a challenge — it is a forced death, with nothing left
to land on. Fragiles are therefore never generated as a row's main platform: they are added
*beside* one that bounces, as a trap to read and avoid. Everything else a row can throw at you
still leaves a way out.

Generation safety rules (these are what stop a run from ending unfairly):

* A fragile platform is never a row's only platform (see above).
* Never two hazard platforms in consecutive rows.
* Every 4th row is guaranteed plain `SOLID`.
* A row may spawn a second platform beside the first. That chance runs **38 % down in the yard
  falling to 5 % by 26 screens**, and the row gap grows from 300–380 wu to 575–640 wu over the
  same climb: busy and forgiving at the bottom, genuinely sparse at the top.

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
| Spring | impulse ×1.55 | 2030 wu ≈ 0.8 screens | 4 % |
| Trampoline | impulse ×1.95 | 3210 wu ≈ 1.3 screens | 0.8 % |
| Propeller cap | 2300 wu/s for 2.4 s | ≈ 5500 wu ≈ 2.2 screens | 0.30 % |
| Jetpack | 3100 wu/s for 2.8 s | ≈ 8700 wu ≈ 3.4 screens | 0.08 % |
| Rocket bone | 4200 wu/s for 3.2 s | ≈ 13 400 wu ≈ 5.3 screens | 0.02 % |
| Bubble shield | absorbs one hazard hit, 12 s | — | 0.22 % |
| Coin magnet | pulls coins within 830 wu, 7 s | — | 0.28 % |

Both columns came down hard after playtesting. The first pass had **one platform in six**
carrying something, and a plain spring clearing 1.3 screens while a rocket took ten — so a run
was mostly being fired upward by the scenery rather than climbed, and finding a rocket stopped
being an event because it stopped the game and played a cutscene at you. Now roughly one
platform in twenty has a spring, under one in a hundred has anything else, and a boost is a
*lift*: it skips some climbing and buys height, but you are still flying it and you can see
where you will come down. The bot's runs got 60–100 % longer in wall-clock time as a result,
at about the same heights — which is the whole point.

Flight rules: platforms are ignored (`vy` is driven, not integrated), hazards are destroyed on
contact, and the exit is a smooth hand-back to gravity rather than an instant drop.

---

## 5. Hazards

**Reference behaviour** — monsters sit on or hover near platforms. Touching one from the side
or below kills you; landing on its head from above kills *it* and bounces you. Black holes and
UFOs kill regardless of approach. Base game lets you shoot upward by tapping.

**Buddy Bounce** — the brief specifies left/right steering only, so there is no shooting; every
hazard is solvable by routing or by stomping:

| Role | Bounceable from above? | Intro | Movement |
|---|---|---|---|
| Drifter | yes (+120 pts, small bounce) | 4 screens | sine drift |
| Patroller | yes (+150 pts) | 11 screens | patrols horizontally, wraps |
| Static | **no** — kills on any contact | 20 screens | still, telegraphed by its own animation |
| Big static | **no** | 30 screens | still, slow swirl, faint pull |

A shield converts any lethal contact into a pop + brief invulnerability instead of a death.

### Hazards belong to their world

The simulation only knows those four *roles*, and they never change — every collision, stomp
and score rule is written against the role, not the creature. What the role *looks like* is a
property of the scene you are climbing, so a run through the Deep Blue meets fish rather than
bees. Twenty designs, five sets of four, dispatched in `render/EnemyArt.kt` on the current
scene's `fauna`:

| World | Drifter | Patroller | Static | Big static |
|---|---|---|---|---|
| Backyard Skies | bee | crow | thundercloud | void rift |
| Deep Blue | pufferfish | angler fish | jellyfish | whirlpool |
| Neon City | camera drone | glitch bird | spark turret | data vortex |
| Frozen Peaks | frost moth | ice bat | blizzard cloud | frozen rift |
| Emberfall | ember moth | flame imp | molten rock | obsidian rift |

Splitting role from art this way is what makes the set cheap to extend: a new world needs four
drawings and one line in its `Scene`, and not a single line of gameplay code.

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
* Score: `floor(maxHeightAboveStart × 0.0625)` + hazard bonuses. One screen of climb = 160 pts,
  so a 5 000-point run is ~31 screens.
* Game over: score is submitted to the local leaderboard under the name entered on first launch
  (top 10 kept, best run highlighted).

---

## 7. Difficulty curve

Everything is a function of `s` = screens climbed (`height / 1600`):

| Parameter | s = 0 | s = 6 | s = 16 | s = 32 | s ≥ 55 |
|---|---|---|---|---|---|
| Row gap (wu) | 300–380 | 330–410 | 400–480 | 480–560 | 560–630 |
| Second platform in a row | 38 % | 29 % | 14 % | 5 % | 5 % |
| Platform width (wu, base 9:16) | 190 | 184 | 172 | 158 | 143 |
| Crumbling main platform | 0 % | 12 % | 23 % | 30 % | 34 % |
| Fragile trap beside it | 0 % | 0 % | 15 % | 25 % | 30 % |
| Moving platform share | 0 % | 18 % | 28 % | 34 % | 36 % |
| Enemies per screen | 0 | 0.3 | 0.7 | 1.0 | 1.1 |

Platform width also scales with `(worldW / 1440)^0.35` so a landscape playfield is not a sea of
tiny ledges and a narrow portrait one is not a tightrope, and rows spawn proportionally more
platforms as the playfield gets wider.

---

## 8. Where Buddy Bounce deliberately differs

### Coins, and when they are yours

Coins are deliberately scarce, because 100 of them buys a pull and a pull should feel earned.

* **On the way up:** one coin roughly every 7 600–15 400 wu of climb (3–6 screens), placed off
  to one side so it is a small detour rather than a freebie. Every 5th one is a 5-coin bone.
* **At the end:** a height bonus of one coin per 300 points. This is most of a run's income and
  it means climbing, not hunting, is the way to get rich.
* A good run is ~12–20 coins, so a pull is four to seven runs of work.

**Banking rule.** Coins collected during a run live in the simulation and nowhere else. They
are banked **only when the run ends**, in a single synchronous commit that also writes the
score, the leaderboard and the run count (`Save.bankRun`). Two consequences, both deliberate:
killing the app mid-run loses that run's coins and cannot duplicate them, and anything already
banked is on disk before the game-over screen draws, so an impulsive swipe-away never costs the
player what they have earned. The pause screen shows the banked total as the headline number,
with the run's pick-ups underneath and a note that they bank at the end.

### The prize machine

One pull is 100 coins and returns one of four things:

| Prize | Chance | Notes |
|---|---|---|
| Consumable power-up | 74 % | the bread — a pull is never a total loss |
| **Trail** | **15 %** | rarity weighted; prefers one you don't own; duplicates refund 35 coins |
| Outfit | 10 % | rarity weighted; a duplicate refunds 35 coins |
| **A whole world** | **1 %** | only while any remain locked |

Outfit rarity runs Common 60 / Rare 27 / Epic 10 / Legendary 3, and a rarity you have completed
rolls down into one you haven't, so late pulls keep feeling like progress. 41 outfits are
collectable; every one you own can be swapped freely in the wardrobe, any time, for free.

### Music

Ten looping tracks, one per world, synthesised in `audio/Music.kt` and cached as WAVs the first
time a world is played - the app ships with no audio assets at all. What separates them is
timbre and rhythm rather than harmony: a square-wave bass on straight eighths under a
four-to-the-floor kick is a techno loop whatever notes it plays, and the same notes on a soft
sine with a shaker are the backyard. Tempos run from 60 BPM in Heaven to 134 in Sugar Rush.

Every loop is written to join itself cleanly - a note running past the end of the buffer wraps
round to the beginning instead of being cut off - and the whole thing is normalised to 0.82
peak. Music and effects have independent mute switches and volume sliders in Settings.

### Trails

40 of them, on the second tab of the wardrobe, and **every one has its own shape**.

The first pass built them from 14 shared draw styles crossed with colour pairs, and it showed:
"Flame" and "Ember" were the same mote in different oranges, and the three leafy ones were one
oval recoloured three times. A trail you can only tell apart by its tint is not a trail, it is a
palette swap. So each entry now names a style used by nothing else, with its own function in
`render/TrailArt.kt` - the flame is a licking tongue with a hot core, the ember is a cracked
chunk of char glowing through the splits, the leaf has a midrib and side veins, the clover has
three lobes and a stem, the bubble bursts into fragments rather than fading.

On top of the shape sits one of seven **motion profiles** - rise, fall, drift, hang, streak,
flutter, burst - which decides how a particle behaves once it exists. That is the difference
between snow and sparks made of the same number of pixels, and it means two trails that sit near
each other in the palette still read differently the moment anything moves. Each particle also
carries three colours: born *hot*, fading to *cool*, with an *accent* for cores, bands and
spatter.

Two rules keep them from becoming clutter, which was the explicit brief:

* **Short.** Nothing lives much past 0.45 s, most styles 0.25–0.40 s. The ribbon reads as a tail
  behind a moving dog and is gone before it can sit on top of a platform you are aiming for.
* **Metered by distance, not by time.** One particle roughly every 58 wu *travelled*. Standing
  still lays down nothing, and a rocket climb gets the same spacing as a slow bounce instead of
  a dense wall. A per-frame budget caps it so one long frame cannot dump a burst.

### Worlds (scenes)

A world is a full set of five altitude bands — its own skies, parallax silhouettes, platform
skins and starting ground. The backyard is Buddy's; the other four are the rarest thing the
machine hands out, and are picked from a menu of their own:

| World | The climb |
|---|---|
| Backyard Skies | Backyard › Treetops › Cloudline › Aurora › Orbit |
| Deep Blue | Seabed › Kelp Forest › Coral Reef › Sunlit Shallows › Open Sky |
| Neon City | Back Alley › Rooftops › Skyline › Smog Layer › Cyber Orbit |
| Frozen Peaks | Snowfield › Pine Woods › Ice Cliffs › Blizzard › Northern Lights |
| Emberfall | Magma Vents › Obsidian Spires › Ash Clouds › Ember Sky › Cinder Void |

### Consumable power-ups and the pre-run beat

Pressing PLAY does not start the run. It lays out the world, then offers whatever consumables
you own — one per run, spent whether you finish or not — and counts **3 · 2 · 1 · GO** over a
slightly dimmed view of the ground you are about to launch from. That pause is the point: it
gives you a moment to read the layout before anything moves — and holding a finger anywhere on
the screen runs the count down 4.5× faster, so a player who is already ready is never made to
wait for a beat they did not need. A finger still held when it reaches GO carries straight into
steering rather than going dead until it is lifted and put back down.

| Power-up | Effect |
|---|---|
| Moon Jump | opens with one colossal bounce, ~4 screens of free height |
| Bubble Start | begin inside a shield that eats the first hit |
| Magnet Paws | coins come to you, for the whole run |
| Feather Fall | 30 s of floaty, forgiving gravity |
| Lucky Paws | three times as many coins on the way up |
| Safety Net | one free save — a ledge appears under you as you fall |
| Coin Doubler | every coin, and the height bonus, counts double |
| Jetpack Start | start the run already flying |
| Head Start | begin six screens up, with the height already scored |
| Rocket Start | start on a rocket; ten screens before you touch a platform |

Anything that changes the layout (Head Start) is applied *before* the countdown so you can see
what you are jumping into; anything that is pure velocity fires on "GO".

### Testing back door

Entering **`u7d%4>`** as the player name (on first launch, or via Settings → Change Name)
unlocks every outfit, trail and world, stocks five of every power-up and adds 1 000 coins. It is
checked against the raw text before the name sanitiser runs, since that strips the punctuation.

### Other differences

* **No shooting.** The reference game's tap-to-shoot is dropped, per the brief: steering is the
  only input, so every hazard is solvable by routing or by stomping.
* **Orientation.** Portrait or landscape, switchable in Settings, with the tilt axis remapped
  through the display rotation and the whole playfield re-tuned for the new aspect ratio.

---

## 9. Balance check

The simulation has no Android dependencies, so it can be played headless. `tools/sim/Sim.kt`
runs a bot - it locks a reachable target platform at each bounce and steers toward it - through
eight runs in six aspect ratios, which is how the numbers above were tuned. Current results:

| Playfield | avg screens | best run | coins/run |
|---|---|---|---|
| portrait 3:4 (1080 wu) | 19.3 | 4 291 pts | 12.8 |
| portrait 9:16 (1440 wu) | 19.8 | 4 143 pts | 12.6 |
| portrait 20:9 (1707 wu) | 23.1 | 4 400 pts | 16.3 |
| landscape 4:3 (3413 wu) | 26.3 | 5 767 pts | 15.6 |
| landscape 16:9 (4551 wu) | 27.1 | 9 159 pts | 15.4 |
| landscape 21:9 (5973 wu) | 30.4 | 7 823 pts | 18.1 |

Every configuration is climbable, every run ends in a death rather than a stall, and the coin
rate puts a prize pull four to seven runs apart. The bot ignores coins entirely, so a player
who detours for them does better than these figures.

To re-run it after changing anything in `Tuning.kt`:

```bash
kotlinc app/src/main/java/com/blacklab/buddybounce/game/ tools/sim/Sim.kt -include-runtime -d sim.jar
java -jar sim.jar
```

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
