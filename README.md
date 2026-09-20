# Buddy Bounce!

An endless vertical tilt-jumper for Android, in the mould of Doodle Jump — except the Doodler
is **Buddy**, a black lab, and the higher he gets the better your score.

* Auto-jumping, infinite upward generation, up-only camera, screen wrap.
* **Tilt** to steer, or just **slide a finger anywhere on the screen** — wherever you touch
  down becomes the centre and sliding either side of it steers that way. Gamepads work too.
* **Portrait or landscape**, switchable in Settings, and tuned to fit any aspect ratio from 4:3
  to 21:9 without changing how the game plays.
* Scarce coins, mostly earned as a height bonus at the end of a run. 100 of them buys a pull on
  the **prize machine**, which hands out consumable **power-ups** (55%), **outfits** (44%) and,
  at 1%, a **whole new world** to climb.
* **39 outfits** and **5 worlds**, all swappable freely, any time, for free.
* A **local leaderboard** under the name you enter on first launch.

The reverse-engineering notes the whole thing is built from — platform taxonomy, the power-up
ladder, camera rules, scoring, the difficulty curve, and the exact numbers used here — are in
[`docs/MECHANICS.md`](docs/MECHANICS.md).

---

## Building

Requirements: JDK 17, Android SDK with **API 35** installed. Nothing else — the app has
**no third-party dependencies at all**, just the Android framework and the Kotlin stdlib.

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # straight onto a connected device
```

Or open the folder in Android Studio and hit Run.

`minSdk` is 26 (Android 8.0), `targetSdk`/`compileSdk` 35.

## Playing

| | |
|---|---|
| Steer | Tilt the phone, slide a finger anywhere on screen, or push left/right on a pad |
| Jump | Never — Buddy bounces on his own the instant he lands. That's the whole game |
| Pause | Top-right button, or Back |
| Score | Height climbed. Only your highest point counts |
| Coins | Rare pickups plus a height bonus at the end. Banked when the run ends |

Touch steering is **relative**: wherever your finger goes down is the centre, and how far you
slide either side of it is how hard Buddy leans. It works anywhere on the screen, so there is
nothing to hunt for and nothing covering the action. Tilt has a dead zone, an adjustable
sensitivity and a "set neutral tilt" button so you can play lying down.

Pressing PLAY lays the world out, lets you spend one power-up if you have any, and counts
3 · 2 · 1 · GO so you can read the ground before it starts moving.

## What's in the world

Platforms come in seven flavours (static, sliding, hovering, crumbling, fragile, spring,
trampoline), re-skinned across each world's five altitude bands, which then loop with a drift.
A fragile platform gives no bounce at all, so the generator never makes one a row's only
platform — it is always a trap set beside a real route. The boost ladder runs spring →
trampoline → propeller cap → jetpack → rocket bone, plus a bubble shield and a coin magnet.
Bees and crows can be stomped from above; storm clouds and void rifts have to be routed around.

Buddy himself is drawn in profile — blocky skull, square muzzle, drop ear, deep chest, otter
tail — and the whole rig mirrors so he always faces the way he is going.

## Layout

```
app/src/main/java/com/blacklab/buddybounce/
├── MainActivity.kt        activity, surface, accelerometer, orientation lock, name prompt
├── GameSurfaceView.kt     render thread on a hardware canvas + input plumbing
├── Game.kt                screen state machine, draw order, and all the "juice" wiring
├── game/                  the simulation
│   ├── Tuning.kt          every gameplay constant, and the aspect-ratio corrections
│   ├── World.kt           generation, one-way collisions, camera, difficulty
│   ├── Buddy.kt           player physics state + animation state
│   ├── Entities.kt        platforms, pick-ups, hazards, object pools
│   └── MathX.kt/Hash.kt   allocation-free float and noise helpers
├── render/                everything visual, drawn as vectors on a Canvas
│   ├── BuddyArt.kt        the dog rig: squash, stretch, lean, ear flap, tail wag
│   ├── OutfitArt.kt       20 outfits composed onto that same rig
│   ├── GameRenderer.kt    platforms, coins, power-ups, hazards
│   ├── Backdrop.kt        parallax sky, biomes, cross-fades
│   ├── Art.kt             the few things worth baking to bitmaps (shadows, glows, clouds)
│   └── Fx.kt              pooled particles and score pops
├── ui/                    an immediate-mode UI kit + every screen
│   ├── PreRunScreen.kt    power-up picker and the 3-2-1-GO countdown
│   ├── GachaScreen.kt     the prize machine and its three prize types
│   └── ScenesScreen.kt    the worlds you have unlocked
├── input/Controls.kt      tilt (display-rotation aware), gauge and gamepad → one steer value
├── audio/Audio.kt         all sound effects synthesised at first launch, no audio assets
└── data/                  SharedPreferences save, outfit catalogue
```

Two design decisions worth knowing before you edit anything:

1. **Two coordinate spaces.** The UI is laid out in a 1600-unit-tall space that fills the
   screen; the world is drawn inside it at 1600/2560 scale. That ratio is the camera zoom, so
   changing `Tuning.VIEW_H` zooms the game without touching the menus. Width falls out of the
   aspect ratio, and `Tuning.Metrics` corrects speed, platform size and row density for it.
2. **The game loop never allocates.** Entities and particles come from pools, paints and paths
   are fields, and colour filters are cached — a GC pause mid-run is the worst possible bug.
3. **Coins bank only when a run ends**, in one synchronous commit. See `Save.bankRun`.

## Tuning it

Open `game/Tuning.kt`. Gravity, bounce impulse, the boost ladder, spawn rarities, the
difficulty curve and the economy are all there, each next to a comment explaining what it is
and why it has that value. `docs/MECHANICS.md` explains where the numbers came from.

Because the simulation has no Android dependencies, it can be played headless. `tools/sim/Sim.kt`
runs a bot through eight runs in six aspect ratios and prints how far it got — run it after
changing anything in `Tuning.kt`:

```bash
kotlinc app/src/main/java/com/blacklab/buddybounce/game/*.kt tools/sim/Sim.kt -include-runtime -d sim.jar
java -jar sim.jar
```
