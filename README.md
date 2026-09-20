# Buddy Bounce!

An endless vertical tilt-jumper for Android, in the mould of Doodle Jump — except the Doodler
is **Buddy**, a black lab, and the higher he gets the better your score.

* Auto-jumping, infinite upward generation, up-only camera, screen wrap.
* **Tilt** to steer, or use the **slide gauge** at the bottom of the screen, or a **gamepad** —
  pick one or all three in Settings.
* **Portrait or landscape**, switchable in Settings, and tuned to fit any aspect ratio from 4:3
  to 21:9 without changing how the game plays.
* Rare coins on the way up; 100 coins buys a pull on the **prize machine**, which dispenses
  outfits for Buddy by rarity.
* A **wardrobe** you can swap freely, any time, for free.
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
| Steer | Tilt the phone, slide a finger along the bottom gauge, or push left/right on a pad |
| Jump | Never — Buddy bounces on his own the instant he lands. That's the whole game |
| Pause | Top-right button, or Back |
| Score | Height climbed. Only your highest point counts |
| Coins | Rare pickups; bones are worth 5. They persist between runs |

Steering is *absolute* on the gauge: wherever your finger sits along the track is where Buddy
leans, like a slider rather than a d-pad. Tilt has a dead zone, an adjustable sensitivity and a
"set neutral tilt" button so you can play lying down.

## What's in the world

Platforms come in seven flavours (static, sliding, hovering, crumbling, fragile, spring,
trampoline), re-skinned across five altitude biomes — Backyard, Treetops, Cloudline, Aurora,
Orbit — which then loop with a drift. The boost ladder runs spring → trampoline → propeller cap
→ jetpack → rocket bone, plus a bubble shield and a coin magnet. Bees and crows can be stomped
from above; storm clouds and void rifts have to be routed around.

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
├── input/Controls.kt      tilt (display-rotation aware), gauge and gamepad → one steer value
├── audio/Audio.kt         all sound effects synthesised at first launch, no audio assets
└── data/                  SharedPreferences save, outfit catalogue
```

Two design decisions worth knowing before you edit anything:

1. **Everything is in world units.** The camera always shows exactly 1600 wu of height, and a
   single `canvas.scale()` maps that onto the device. Width falls out of the aspect ratio, and
   `Tuning.Metrics` corrects speed, platform size and row density for it. That's why portrait
   and landscape both feel right.
2. **The game loop never allocates.** Entities and particles come from pools, paints and paths
   are fields, and colour filters are cached — a GC pause mid-run is the worst possible bug.

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
