"""Round-trip and correctness checks for the graphics codec.

All test patterns are generated here rather than taken from any cartridge, so
the suite stays self-contained.
"""
import os
import sys
import random

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "tools"))
import tiles

fails = []


def check(cond, msg):
    if not cond:
        fails.append(msg)


# A hand-built tile: a diagonal using all four palette values.
pattern = [[(x + y) % 4 for x in range(8)] for y in range(8)]
raw = tiles.encode_tile(pattern)
check(len(raw) == tiles.TILE_BYTES, f"encoded tile is {len(raw)} bytes, want 16")
check(tiles.decode_tile(raw) == pattern, "decode(encode(t)) != t")
print(f"  encode/decode round-trip: ok ({len(raw)} bytes per tile)")

# Exhaustive round-trip on random tiles.
random.seed(1234)
for _ in range(500):
    t = [[random.randint(0, 3) for _ in range(8)] for _ in range(8)]
    check(tiles.decode_tile(tiles.encode_tile(t)) == t, "random round-trip failed")
print("  500 random tiles round-trip: ok")

# Planar layout: pixel value 2 must set the high plane only.
solid2 = tiles.encode_tile([[2] * 8 for _ in range(8)])
check(all(solid2[i] == 0x00 for i in range(0, 16, 2)), "low plane should be clear")
check(all(solid2[i] == 0xFF for i in range(1, 16, 2)), "high plane should be set")
print("  bitplane layout matches hardware: ok")

# Flips.
check(tiles.flip(pattern, x=True)[0] == list(reversed(pattern[0])), "x-flip wrong")
check(tiles.flip(pattern, y=True)[0] == pattern[7], "y-flip wrong")
print("  x/y flip: ok")

# Bad input is rejected rather than silently truncated.
for bad, why in (([[0] * 8] * 7, "wrong height"), ([[4] * 8] * 8, "index out of range")):
    try:
        tiles.encode_tile(bad)
        fails.append(f"encode_tile accepted {why}")
    except ValueError:
        pass
print("  invalid tile data rejected: ok")

# CGB colour decoding: 15-bit BGR555 white and red.
check(tiles.decode_bgr555(0xFF, 0x7F) == (255, 255, 255), "white decode wrong")
check(tiles.decode_bgr555(0x1F, 0x00) == (255, 0, 0), "red decode wrong")
check(tiles.encode_bgr555(255, 255, 255) == b"\xff\x7f", "white encode wrong")
print("  CGB BGR555 palette codec: ok")

# PNG writer produces a real file with a valid signature.
sheet = tiles.compose([pattern] * 16, columns=4)
out = os.environ.get("SCRATCH", "/tmp") + "/sheet.png"
w, h = tiles.write_png(out, sheet)
with open(out, "rb") as fh:
    sig = fh.read(8)
check(sig == b"\x89PNG\r\n\x1a\n", "PNG signature wrong")
check((w, h) == (32, 32), f"sheet should be 32x32, got {w}x{h}")
print(f"  PNG export: ok ({w}x{h}, {os.path.getsize(out)} bytes)")

print("\n" + ("FAILURES:\n  " + "\n  ".join(fails) if fails else "all tile codec tests passed"))
sys.exit(1 if fails else 0)
