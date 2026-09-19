# Black Lab Blade

A 2D action platformer in the handheld-dungeon-crawler mould: short, dense
stages, sword combat, coins, hidden collectibles and a shop between runs.
The hero is a black lab who carries his blade in his jaws.

**To play: download `black-lab-blade.html` and double-click it.** That one
file is the entire game — no server, no install, no dependencies. It works
straight off your desktop.

The `src/` version is the same game split into readable modules; open
`index.html` to run that. Rebuild the single file after editing any source:

```sh
python3 tools/bundle.py
```

Every sprite, tile, sound and note is generated at runtime from code — there
are no image or audio files anywhere in the project.

## Controls

| Action | Keys |
| --- | --- |
| Move | `←` `→` / `A` `D` |
| Jump (hold for height) | `Z` / `Space` / `K` |
| Swing the blade | `X` / `J` |
| Drop through a platform | `↓` |
| Pause | `Esc` / `P` |
| Restart stage | `R` |
| Mute | `M` |

On the map screen `Enter` plays the selected stage and `X` opens the shop.
Touch controls appear automatically on touch devices.

## Playing on a phone

The single file works on a phone as-is: copy `black-lab-blade.html` to the
device and open it from the Files app. Everything is inlined, so it runs
offline with no network and no install. On-screen controls appear
automatically on a touchscreen, and can be switched on or off from the
pause menu (`^v`) on any device.

Want a real installable APK? Run `bash tools/make-apk.sh` **on your own
machine** — it wraps the game with Cordova, locks it to landscape
fullscreen and drops `black-lab-blade.apk` in the project root. It needs a
JDK and the Android SDK (Android Studio installs both). It cannot be run
in the Claude Code sandbox, whose proxy blocks `dl.google.com`, where both
the Android SDK and the Android Gradle Plugin are hosted.

## The game

Twelve stages across three worlds — Sunken Garden, Root Caverns, Kennel Keep
— each world ending in a boss:

* **Grumblegut**, a boar who charges and stuns himself on the walls.
* **Gloomwing**, a bat that circles high and dives.
* **The Kennel King**, an armoured hound with a greatsword and shockwaves.

Every stage hides **3 gems** and **2 treasure chests**. The chests are walled
up behind false masonry that is drawn with the same variant hash, depth
shading and trim as real wall, so nothing marks it out — you find them by
pushing into walls and dropping through floors that look solid. Gems and
chests are tracked in the HUD and on the map.

The hero double-jumps from the start. Coins persist between attempts and buy
gear at the trading post: blade tiers, collars for extra hearts, and relics
(a third jump, extra speed, coin magnet, doubled coin value, longer mercy
invulnerability). Progress saves to `localStorage`.

Falling in water or landing on spikes costs a heart and returns you to the
last safe footing rather than restarting the stage.

## Layout

```
index.html        canvas, script order
src/util.js       helpers, pixel-grid sprite baker
src/input.js      keyboard + touch, edge-triggered
src/audio.js      synthesised SFX and a step-sequenced chiptune
src/text.js       hand-drawn 5x7 bitmap font
src/art.js        sprite shader, tilesets, props, lighting, bosses
src/levels.js     stage data (generated - see tools/)
src/save.js       persistence and the shop catalogue
src/entities.js   hero, enemies, bosses, projectiles, pickups
src/world.js      tilemap, collision, camera, HUD
src/ui.js         title, world map, shop, pause, results
src/game.js       fixed-step loop and screen state machine
```

## Art

There are no image files. Characters are written as pixel grids and run
through an automatic shader that carves a tone ramp into the flat fill and
adds a rim light, so sprites read as lit volumes. Terrain is autotiled:
a block draws its body plus moulding on exposed tops, quoined trim on
exposed sides and corbels underneath, and buried tiles are darkened by depth
so the ground recedes into shadow. Interiors render an architectural back
wall — fluted columns with capitals, arched niches, a belt course, hung
banners and cobwebs — behind a warmer foreground, so the playable silhouette
separates from the scenery.

## Level tooling

Stage data is generated, then verified against the hero's real jump arc.

```sh
python3 tools/build_levels.py     # regenerate src/levels.js
node tools/reach.js               # prove every stage is completable
```

`build_levels.py` assembles terrain from segments (plateaus, steps, mesas,
water crossings) and cuts structures into it — pillars to hop across, arches
to walk under or stand on, platform tiers exactly two tiles apart.

`reach.js` derives the jump arc from the actual physics constants (3 tiles
up, ~3.5 tiles across) and floods the level graph from spawn to exit,
reporting any stage that cannot be finished and any collectible that cannot
be reached. Change a physics constant and re-run it to see what it breaks:

```sh
node tools/reach.js 6.7 1.75      # jump velocity, run speed
```
