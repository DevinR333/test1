#!/usr/bin/env python3
"""
Checks that no two parts of the prize machine overlap.

The machine is drawn from a handful of fractions of its own width in
GachaScreen.drawMachine, and the failure mode is always the same: a part gets
nudged, nothing complains, and it quietly paints over the one next to it - the
chute swallowed the coin slot and the left of the crank that way, and the
nameplate clipped the bottom of the globe. Mirror the numbers here and the
bands can be checked without a device.

Keep in step with drawMachine. Run: python3 tools/machine/layout.py
"""

w = 1.0
h = w / 0.72
top = 0.0

domeR = w * 0.38
domeCY = top + domeR + w * 0.05
bodyTop = domeCY + domeR          # the glass rests ON the cabinet
bodyBottom = top + h
bodyH = bodyBottom - bodyTop

signTop = bodyTop + bodyH * 0.086
signH = bodyH * 0.207
rowCY = bodyTop + bodyH * 0.527
chuteTop = bodyTop + bodyH * 0.794
chuteH = bodyH * 0.147

PARTS = {
    "globe": (domeCY - domeR, domeCY + domeR, -domeR, domeR),
    "collar": (bodyTop - domeR * 0.24, bodyTop + domeR * 0.1, -domeR * 1.02, domeR * 1.02),
    "nameplate": (signTop, signTop + signH, -w * 0.36, w * 0.36),
    "coin slot": (rowCY - w * 0.06, rowCY + w * 0.06, -w * 0.38, -w * 0.14),
    "crank": (rowCY - w * 0.135, rowCY + w * 0.135, w * 0.26 - w * 0.135, w * 0.26 + w * 0.135),
    "chute": (chuteTop, chuteTop + chuteH, -w * 0.19, w * 0.19),
}

# The globe seats into its collar on purpose; nothing else may touch.
ALLOWED = {frozenset({"globe", "collar"})}

def main():
    print(f"body {bodyTop:.3f} .. {bodyBottom:.3f}  (height {bodyH:.3f})")
    for name, (y0, y1, x0, x1) in PARTS.items():
        print(f"  {name:<10} y {y0:.3f}..{y1:.3f}   x {x0:+.3f}..{x1:+.3f}")
    print()

    bad = 0
    names = list(PARTS)
    for i in range(len(names)):
        for j in range(i + 1, len(names)):
            a, b = PARTS[names[i]], PARTS[names[j]]
            yo = min(a[1], b[1]) - max(a[0], b[0])
            xo = min(a[3], b[3]) - max(a[2], b[2])
            if yo > 1e-6 and xo > 1e-6:
                pair = frozenset({names[i], names[j]})
                if pair in ALLOWED:
                    print(f"ok   {names[i]} seats into {names[j]}")
                else:
                    print(f"BAD  {names[i]} overlaps {names[j]}: y {yo:.3f} x {xo:.3f}")
                    bad += 1

    margin = bodyBottom - (chuteTop + chuteH)
    if margin < 0:
        print(f"BAD  chute hangs {-margin:.3f} below the cabinet")
        bad += 1
    sink = (domeCY + domeR) - bodyTop
    if sink > 1e-6:
        print(f"BAD  globe sinks {sink:.3f} into the cabinet")
        bad += 1

    print()
    print("clean" if bad == 0 else f"{bad} problem(s)")
    return 1 if bad else 0

if __name__ == "__main__":
    raise SystemExit(main())
