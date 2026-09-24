# Prize machine tools

## GachaCheck.kt - is it actually random?

"It calls Random" is not an answer; a rigged machine calls Random too. This pulls the handle a
million times against a real `Save` and measures what comes out, looking for the things a
rigged machine would show:

- the four categories land on their declared odds, within sampling error (4 sigma)
- every weighted table - power-ups, trail rarities, outfit rarities - matches its own weights
- the chance of a jackpot is the same right after a jackpot, after an ordinary pull, and after
  a 200-pull drought: no streak breaker, no pity timer
- the wait between jackpots is geometric in both mean and spread, which a pity timer would
  truncate
- a save with 60 runs and 54,000 coins banked draws the identical sequence to a fresh one, so
  nothing outside the draw reaches it
- a band with nothing left in it falls through instead of handing out a prize that cannot exist
- `System.nanoTime()` separates back-to-back seeds, so two players opening the machine in the
  same moment do not get the same prize

<!-- The odds live in app/.../data/PrizeRoll.kt rather than in the screen that draws the
     machine, specifically so this can run without a device. -->

    kotlinc -cp <stubs> -d out $(find app/src/main/java -name '*.kt') \
        tools/fakes/Fakes.kt tools/gacha/GachaCheck.kt
    java -cp out:<stubs> GachaCheckKt

Two deliberate biases it will NOT flag, because they are the intended design: within the trail
band the draw prefers trails you do not own yet, and within the outfit band a rarity you have
completed rolls down into one you have not. The category odds above are unaffected.
