#!/usr/bin/env python3
"""Summarise a symbol file so extractors can be written against it.

Prints the detected format, how labels are spread across banks, and a sample
of the names in the categories asset extraction cares about. Output is a
summary, not a dump: it reports counts and a handful of example identifiers,
never the contents of the ROM.
"""

import argparse
import os
import re
import sys
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import symbols as symlib

# The categories worth locating first: graphics and palettes drive the
# renderer, rooms drive the map, and the sword labels are what the sprite
# editor needs to find.
CATEGORIES = {
    "sword":     r"sword",
    "graphics":  r"gfx|graphic|tilesets?\b|tiles\b",
    "palette":   r"pal(ette)?s?\b|pal_|_pal",
    "sprite":    r"sprite|oam|obj\b",
    "animation": r"anim|frame",
    "room/map":  r"room|map\b|area|screen|layout",
    "object":    r"object|enemy|npc|item",
}


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("symfile")
    ap.add_argument("--samples", type=int, default=6,
                    help="example names to show per category (default 6)")
    args = ap.parse_args()

    try:
        with open(args.symfile, "r", errors="replace") as fh:
            lines = fh.readlines()
    except OSError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    print(f"{args.symfile}")
    print(f"  {len(lines)} lines, {os.path.getsize(args.symfile)} bytes\n")

    print("  first lines, to confirm the format:")
    for line in lines[:8]:
        print(f"    {line.rstrip()[:78]}")

    # Parse with and without the data-label filter, to show what it removes.
    all_syms = symlib.load(args.symfile, skip_data=False)
    code_syms = symlib.load(args.symfile, skip_data=True)

    print(f"\n  parsed {len(all_syms)} labels "
          f"({len(all_syms) - len(code_syms)} look like data, "
          f"{len(code_syms)} like code)")

    if not all_syms:
        print("\n  Nothing parsed. The format differs from what symbols.py expects;")
        print("  the raw lines above are what I need to adjust it.")
        return 0

    banks = Counter(bank for bank, _ in all_syms)
    print(f"  spread across {len(banks)} banks, "
          f"busiest: " + ", ".join(f"{b}({n})" for b, n in banks.most_common(5)))

    lo = sum(1 for _, a in all_syms if a < 0x4000)
    hi = sum(1 for _, a in all_syms if 0x4000 <= a < 0x8000)
    ram = len(all_syms) - lo - hi
    print(f"  {lo} in fixed bank 0, {hi} in the switchable window, {ram} outside ROM")

    print("\n  categories:")
    by_name = sorted(((label, bank, addr) for (bank, addr), label in all_syms.items()),
                     key=lambda t: t[0].lower())
    for title, pattern in CATEGORIES.items():
        rx = re.compile(pattern, re.I)
        hits = [t for t in by_name if rx.search(t[0])]
        print(f"\n    {title:<10} {len(hits)} labels")
        for label, bank, addr in hits[:args.samples]:
            print(f"      {bank:02X}:{addr:04X}  {label}")
        if len(hits) > args.samples:
            print(f"      ... and {len(hits) - args.samples} more")

    return 0


if __name__ == "__main__":
    sys.exit(main())
