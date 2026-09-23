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
