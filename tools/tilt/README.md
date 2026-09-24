# Tilt tools

## TiltCheck.kt - which way is down

Drives the real `TiltMap` with real physical poses and asserts where gravity ends up pointing
on screen: 4 display rotations x 11 poses (upright, upside down, on either side, the four
diagonals, flat face-up, flat face-down, tipped back) x 3 assertions.

Sign conventions across two accelerometer axes and four rotations are not something to
eyeball, so each pose is built from first principles rather than from the mapping under test:
the probe works out where the screen's own right and down axes point in device coordinates,
puts the phone in a pose, computes what an accelerometer would really read there, and checks
that TiltMap resolves it back.

    kotlinc -cp <stubs> -d out $(find app/src/main/java -name '*.kt') tools/tilt/TiltCheck.kt
    java -cp out:<stubs> TiltCheckKt

It also prints the poses the previous one-axis version got wrong, which is why it exists.
