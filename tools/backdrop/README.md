# Backdrop tools

Three probes for the parallax backdrop. The first two are pass/fail; the third is the one
that actually found the bugs, because it lets you LOOK at what the game draws.

All of them drive the real `Backdrop` and the real `Palettes` - nothing here re-implements
the art.

## bands.py - painting order

Reads every `band(camY, p, h)` call site out of `Backdrop.kt` and `BandArt.kt` and checks
that the three repeats are handed out far-to-near across 400 camera positions each. Most
band styles fill downward from their base line, so a repeat handed out after a nearer one
sheets its ground over everything below it.

    python3 tools/backdrop/bands.py

## BackdropAudit.kt - is anything there

Splits the screen into three zones (sky / mid / near) and counts the marks each band puts in
each, across its whole altitude range. A band that leaves a zone empty is a hole in the art.

This is necessary but nowhere near sufficient: it counts marks, not pictures. The jungle's
"Root Floor" scored perfectly while drawing the treetop canopy, and a band whose silhouettes
are painted a shade off its own sky counts full marks and looks like an empty gradient.

## BackdropDump.kt - what it actually looks like

Writes one SVG per world: five bands across, three camera heights down. This is what found
the real problems - ground fills tiling over the whole sky, silhouettes invisible against
their own palette, shelf humps so tall the gaps between them read as hanging teeth.

Both Kotlin probes build against the RECORDING stubs (a `Canvas` that records draw calls
with their geometry instead of painting), not the plain ones:

    # stubs: the plain Android stubs, with android/graphics/* replaced by the recording ones
    kotlinc -cp <recording-stubs> -d out $(find app/src/main/java -name '*.kt') \
        tools/backdrop/BackdropDump.kt
    java -cp out:<recording-stubs> BackdropDumpKt out-dir

Then rasterise the SVGs with any browser to look at them.

A caution learned the hard way: the probe is only as honest as its stubs. `RectF.set` was a
no-op for a long time, so every `drawRect`/`drawRoundRect`/`drawOval` and every blitted
bitmap recorded as a zero-size box at the origin and simply vanished - roughly half the
backdrop. If a band looks empty in the dump, suspect the stub before the art.

## SeamCheck.kt - the hard horizontal rules

Ground, water, rock and buildings are all silhouettes filled DOWNWARD from their own line, and
each one used to stop at a fixed depth. Whenever that depth landed inside the frame you got a
straight edge right across the screen with sky underneath it. Whether one showed depended on
the camera height, which is why they were easy to miss by eye and turned up everywhere.

This looks for them directly: a filled shape that spans nearly the whole width, is not a
full-screen wash, is solid enough to see, is TALL (a fence rail, a roof parapet, a cake shelf
and the plinth under the organ pipes are all legitimately full width and hard edged - they are
just thin), has a genuinely FLAT bottom rather than a wavy one that happens to have a low point
(the underside of the sea is rippled and meant to be seen), and is not simply covered by
something painted after it (an alley wall stops where the road starts).

Every scene, every band, 48 camera heights each - 2400 positions.

    kotlinc -cp <recording-stubs> -d out $(find app/src/main/java -name '*.kt') \
        tools/backdrop/SeamCheck.kt
    java -cp out:<recording-stubs> SeamCheckKt

The fix is `deep()` in Backdrop.kt and BandArt.kt: a downward fill ends past the bottom of the
frame instead of at a fixed depth. Whatever is nearer is painted after and covers it, and below
a horizon there is supposed to be more of the same rather than sky. A fill entirely ABOVE the
screen is left alone - stretching that one down would drop a slab over the whole frame.

## FilmStrip.kt + png.py - measuring pop by looking at pixels

Every probe built out of draw *calls* eventually lied. Mark counts pass when the art is present
but wrong. Bounding-box coverage saturates at ~1.0 everywhere. Ink area counts paint that
something else covers a moment later. The only measurement that matched what the screen
actually does is the one that rasterises.

FilmStrip renders a run of frames per scene as SVG - 20 frames, 0.12 screens apart, starting
from `startScreens` - driving the game's own `Tuning.biomeIndex`/`biomeBlend` so the band
cross-fade is the real one and not an approximation. Chromium turns each SVG into a PNG
(`/opt/pw-browsers/chromium-*/chrome-linux/chrome --headless --screenshot`), and `png.py` -
a pure-Python decoder, since the container has neither PIL nor numpy - reads the pixels back.

Two numbers per scene: the worst single-frame difference (the "jump", a pop), and the mean
frame-to-frame difference (the "step", how smoothly it scrolls). Both are only meaningful in
comparison, so render the same strips from a known-good commit and diff:

    git worktree add /tmp/approved <good-sha>

then build and run FilmStrip against each tree and compare scene by scene. Two sweeps are
worth taking - the first area from 0 screens, and across the cross-fade from 6.6 screens.
A change ships when no scene is worse than the approved build in either sweep.

## empty.py - has an area gone bare?

Scenery is laid out ONCE per area now (see `band()`), so it sweeps down through the frame and
leaves instead of coming round again. That is the whole point - "ground, sky, ground again"
inside one area is what it removes - but it means the old way of keeping a frame full is gone,
and an area can end up as nothing but sky.

This reads a filmstrip back as pixels and, for each frame, measures how much the rows vary
horizontally. A bare sky gradient is flat along every row and scores near zero; a ridge, a
tower, a tree or a cloud breaks it. The number that matters is the lowest-scoring frame of a
band - the emptiest moment you can reach in that area.

    python3 tools/backdrop/empty.py <strip-dir> 10

Judge it against the same measurement from a known-good commit rather than against an absolute.
The bands whose style is CLOUDS, AURORA or NEBULA are meant to be sparse and always score low.

## pop.py - does it jump?

The same strips, differenced frame to frame: the worst single step is a pop, the average step is
how smoothly it scrolls. Render a fine sweep (20 frames, 0.12 screens apart) from the start of an
area and again across the cross-fade at 6.6 screens, from this tree and from the approved commit,
and compare.

    python3 tools/backdrop/pop.py <strip-dir> 20
