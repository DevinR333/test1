#!/usr/bin/env python3
"""
Checks that parallax band repeats are painted far-to-near.

Backdrop.band hands out three repeats of a scenery band. Most band styles fill
DOWNWARD from their base line - a horizon with ground under it - so whichever
repeat is handed out LAST ends up covering the ones below it on screen. Handing
them out in ascending index does exactly the wrong thing: baseY falls as the
index rises, so the highest, most distant repeat is drawn last and its ground
sheets over the nearer bands and the sky.

Mirror of Backdrop.band. Run: python3 tools/backdrop/bands.py
"""
import math
import os
import re

VIEW_H = 2560.0

# Every band() call site, read straight out of the source. Listing them by hand meant the
# table drifted from the code - it had sixteen entries while the two files between them have
# many more, and a new style could be wrong for as long as nobody updated the list.
SOURCES = [
    "app/src/main/java/com/blacklab/buddybounce/render/Backdrop.kt",
    "app/src/main/java/com/blacklab/buddybounce/render/BandArt.kt",
]

CALL = re.compile(r"\bband\(camY,\s*([0-9.]+)f,\s*([^)]+?)\)\s*\{")
LOCAL_H = re.compile(r"\bval h = Tuning\.VIEW_H(?:\s*\*\s*([0-9.]+)f)?")
FUNC = re.compile(r"\bfun ([a-zA-Z]+)\(")


def scan():
    """(name, parallax, height) for every band() call in the render code."""
    out = []
    for path in SOURCES:
        src = open(path).read()
        fname = "?"
        h_local = None
        pos = 0
        for line in src.split("\n"):
            m = FUNC.search(line)
            if m:
                fname = m.group(1)
                h_local = None
            m = LOCAL_H.search(line)
            if m:
                h_local = VIEW_H * (float(m.group(1)) if m.group(1) else 1.0)
            m = CALL.search(line)
            if not m:
                continue
            p = float(m.group(1))
            expr = m.group(2).strip()
            if expr == "h":
                if h_local is None:
                    raise SystemExit(f"{path}: band() in {fname} uses h before it is set")
                height = h_local
            elif expr.startswith("Tuning.VIEW_H"):
                mult = expr.split("*")
                height = VIEW_H * (float(mult[1].strip().rstrip("f")) if len(mult) > 1 else 1.0)
            else:
                raise SystemExit(f"{path}: band() in {fname} has an unreadable height: {expr}")
            out.append((f"{os.path.basename(path)[:-3]}.{fname}", p, height))
    return out


def repeats(cam_y, p, height):
    """What band() hands to the body, in order."""
    i0 = math.floor((cam_y * p) / height)
    out = []
    for k in range(2, -1, -1):                    # far first - must match Backdrop.band
        idx = i0 + k
        base_y = VIEW_H - (idx * height - cam_y * p)
        if base_y < -height * 1.6 or base_y > VIEW_H + height:
            continue
        out.append((idx, base_y))
    return out


def main():
    bad = 0
    checked = 0
    bands = scan()
    for name, p, height in bands:
        for cam_y in [c * 137.0 for c in range(0, 400)]:
            order = repeats(cam_y, p, height)
            checked += 1
            # Drawn later must be LOWER on screen (larger baseY): near covers far.
            for a, b in zip(order, order[1:]):
                if b[1] < a[1] - 1e-6:
                    print(f"BAD {name}: camY={cam_y:.0f} draws baseY {a[1]:.0f} "
                          f"then {b[1]:.0f} - the farther one is painted last")
                    bad += 1
                    break
            else:
                continue
            break
    print(f"checked {checked} camera positions across {len(bands)} band call sites")
    print("clean" if bad == 0 else f"{bad} band(s) painting back to front")
    return 1 if bad else 0


if __name__ == "__main__":
    raise SystemExit(main())
