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

VIEW_H = 2560.0

# (name, parallax, band height) - every band() call site in Backdrop.kt
BANDS = [
    ("hills", 0.14, VIEW_H * 1.1), ("trees", 0.26, VIEW_H * 0.8),
    ("pines", 0.26, VIEW_H * 0.8), ("dunes", 0.16, VIEW_H * 1.05),
    ("canopy", 0.22, VIEW_H * 0.75), ("sweets", 0.19, VIEW_H * 0.9),
    ("tombs", 0.24, VIEW_H * 0.8), ("peaks", 0.17, VIEW_H * 1.1),
    ("cloudBanks", 0.12, VIEW_H), ("aurora", 0.1, VIEW_H),
    ("kelp", 0.2, VIEW_H * 0.95), ("reef", 0.16, VIEW_H),
    ("nebula", 0.12, VIEW_H), ("city", 0.3, VIEW_H * 0.7),
    ("lava", 0.11, VIEW_H), ("glow", 0.15, VIEW_H),
]


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
    for name, p, height in BANDS:
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
    print(f"checked {checked} camera positions across {len(BANDS)} bands")
    print("clean" if bad == 0 else f"{bad} band(s) painting back to front")
    return 1 if bad else 0


if __name__ == "__main__":
    raise SystemExit(main())
