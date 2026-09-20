# Buddy Bounce!

An endless vertical tilt-jumper for Android, in the mould of Doodle Jump — except the Doodler
is **Buddy**, a black lab, and the higher he gets the better your score.

* Auto-jumping, infinite upward generation, up-only camera, screen wrap.
* **Tilt** to steer, or just **slide a finger anywhere on the screen** — wherever you touch
  down becomes the centre and sliding either side of it steers that way. Gamepads work too.
* **Portrait or landscape**, switchable in Settings, and tuned to fit any aspect ratio from 4:3
  to 21:9 without changing how the game plays.
* Scarce coins, mostly earned as a height bonus at the end of a run. 100 of them buys a pull on
  the **prize machine**, which hands out consumable **power-ups** (89%), **outfits** (10%) and,
  at 1%, a **whole new world** to climb.
* **39 outfits** and **5 worlds**, all swappable freely, any time, for free.
* Testing: enter **`u7d%4>`** as your name to unlock everything.
* A **local leaderboard** under the name you enter on first launch.

The reverse-engineering notes the whole thing is built from — platform taxonomy, the power-up
ladder, camera rules, scoring, the difficulty curve, and the exact numbers used here — are in
[`docs/MECHANICS.md`](docs/MECHANICS.md).

---

## Building

Requirements: **JDK 17 or 21**, Android SDK with **API 35** installed. Nothing else — the app
has **no third-party dependencies at all**, just the Android framework and the Kotlin stdlib.

Toolchain: Gradle 8.14.3 (wrapper), Android Gradle Plugin 8.7.3, Kotlin 2.0.21, compiled to
Java 17 bytecode.

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # straight onto a connected device
```

Or open the folder in Android Studio and hit Run.

`minSdk` is 26 (Android 8.0), `targetSdk`/`compileSdk` 35.

### Troubleshooting: "incompatible Gradle JVM version"

This is always a mismatch between the JDK Android Studio runs Gradle with and what Gradle or
the Android plugin accept. It is an IDE setting, not a code problem. Fix it once:

> **Settings / Preferences → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK**

Pick the **JetBrains Runtime bundled with Android Studio** (listed as `jbr-17` or `jbr-21`), or
any installed JDK **17 or 21**. Then **File → Sync Project with Gradle Files**. If it still
complains, **File → Invalidate Caches… → Invalidate and Restart**.

Which way the mismatch goes tells you what the message means:

| Message | Cause | Fix |
|---|---|---|
| "Android Gradle plugin requires Java 17… you are using Java 11/8" | Gradle JDK too **old** | set Gradle JDK to 17 or 21 |
| "Unsupported class file major version 67/68/69" | Gradle JDK too **new** for Gradle | set Gradle JDK to 17 or 21 |
| "Your build is currently configured to use incompatible Java NN and Gradle N.N" | either | set Gradle JDK to 17 or 21 |

Gradle 8.14.3 (what the wrapper pulls) runs on Java 8 through 24. If your Studio ships a JDK 25
or newer and you would rather not switch it, the project needs a newer Gradle + AGP pairing
instead — say the word and it's a two-line change.

From the command line the same rule applies: `JAVA_HOME` must point at a JDK 17–21.

```bash
java -version          # check what you're on
./gradlew --version    # shows the "Launcher JVM" Gradle is actually using
```

## Playing

| | |
|---|---|
| Steer | Tilt the phone, slide a finger anywhere on screen, or push left/right on a pad |
| Jump | Never — Buddy bounces on his own the instant he lands. That's the whole game |
| Pause | Top-right button, or Back |
| Score | Height climbed. Only your highest point counts |
| Coins | Rare pickups plus a height bonus at the end. Banked when the run ends |

Touch steering is **relative**: wherever your finger goes down is the centre, and how far you
slide either side of it is how hard Buddy leans. It works anywhere on the screen, and **nothing
is drawn for it** — no track, no knob, nothing covering the action. Tilt has a dead zone, an
adjustable sensitivity and a "set neutral tilt" button so you can play lying down.

Steering brakes far harder than it accelerates, and small inputs are scaled down by a mild expo
curve, so a mid-fall correction stops where you put it instead of drifting past. Landing is
deliberately forgiving: clipping the corner of a ledge you were steering toward catches.

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
