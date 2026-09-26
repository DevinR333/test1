# Enemy tools

## EnemyBoxCheck.kt - the hit box against the creature in it

The simulation has four enemy ROLES - bee, crow, storm, rift - and every world dresses those
roles as its own creature. Forty pictures, and for a long time four boxes between them. Nothing
kept a box and its picture together, so they drifted: in Heaven the rift's box was 92 wide on a
creature 46 wide, which killed the player from a clear head's width of empty sky, and in Frozen
Peaks the storm's was 62 tall on one 119 tall, so half of it could be walked through.

This drives the real `EnemyArt` against the recording canvas for every world and role, over a
full cycle of each creature's animation and both facings, and measures the ink it puts down.
Faint ops are left out: a glow, an aura or a trailing sparkle is scenery, and scenery must not
be lethal.

The rule it enforces is one-sided. A box may sit inside its creature - slack there is invisible
and always reads as fair - but it may never reach past it, because that kills the player with
nothing on screen to blame. `game/EnemyBox.kt` holds the table at 85% of the drawn body, and
this probe fails if the art has outgrown it, printing the table it would write instead.

    kotlinc -cp <recording-stubs> -d out $(find app/src/main/java -name '*.kt') \
        tools/enemy/EnemyBoxCheck.kt
    java -cp out:<recording-stubs> EnemyBoxCheckKt

Run it after touching `EnemyArt.kt`. Its rows are in FAUNA order, which is not the order the
worlds appear in the menu - Heaven's fauna is 5 while it is the last scene on the shelf. Writing
the table in scene order put Heaven's boxes on Hollow Hill and Hollow Hill's on Heaven.

The other half of the same job is `tools/stomp/StompCheck.kt`, which drops Buddy on all forty of
them from every speed and offset, and also falls past them in clear air to check that nothing
dies when he never touched it.
