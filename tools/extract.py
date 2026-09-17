#!/usr/bin/env python3
"""Extract graphics and palettes from a Game Boy ROM.

Reads a ROM you supply and writes PNGs. Three modes:

  gfx       decode tiles at a known location into a PNG sheet
  palettes  decode CGB palettes at a known location
  scan      search the ROM for regions that look like tile data, for when
            you do not yet know where the graphics are

Addresses are given as a bank and an address within the CPU's view of that
bank, matching how a symbol file writes them: bank 3 address 0x4100 is
`03:4100`. A symbol file can be used instead of raw numbers.
"""

import argparse
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import rom as romlib
import symbols as symlib
import tiles as tilelib

BANK_SIZE = romlib.BANK_SIZE


def rom_offset(bank, addr):
    """Bank plus CPU address -> a flat offset into the ROM image."""
    if addr < BANK_SIZE:
        return addr                      # fixed bank 0 is mapped at 0x0000
    return bank * BANK_SIZE + (addr - BANK_SIZE)


def resolve(args, syms):
    """Work out (bank, addr) from --symbol, or from --bank/--addr."""
    if args.symbol:
        matches = [(b, a) for (b, a), name in syms.items() if name == args.symbol]
        if not matches:
            near = sorted(n for n in syms.values()
                          if args.symbol.lower() in n.lower())[:8]
            hint = ("\n  similar names: " + ", ".join(near)) if near else ""
            raise SystemExit(f"error: no symbol named {args.symbol!r}{hint}")
        if len(matches) > 1:
            raise SystemExit(f"error: {args.symbol!r} is defined in "
                             f"{len(matches)} places: {matches}")
        return matches[0]
    if args.addr is None:
        raise SystemExit("error: give either --symbol or --addr")
    return args.bank, args.addr


def cmd_gfx(args, r, syms):
    bank, addr = resolve(args, syms)
    off = rom_offset(bank, addr)
    size = args.tiles * tilelib.TILE_BYTES
    if off + size > len(r.data):
        raise SystemExit(f"error: {args.tiles} tiles at {bank:02X}:{addr:04X} "
                         f"runs past the end of the ROM")

    sheet = tilelib.decode_sheet(r.data[off:off + size], args.tiles)

    palette = None
    if args.palette_addr is not None:
        poff = rom_offset(args.palette_bank, args.palette_addr)
        palette = tilelib.decode_palette(r.data, poff)

    pixels = tilelib.compose(sheet, columns=args.columns)
    w, h = tilelib.write_png(args.out, pixels, palette)
    print(f"  {args.tiles} tiles from {bank:02X}:{addr:04X} "
          f"(ROM offset 0x{off:06X})")
    print(f"  wrote {args.out}  {w}x{h} px, {args.columns} tiles per row")
    if palette:
        print(f"  palette: {' '.join('#%02x%02x%02x' % c for c in palette)}")
    else:
        print("  palette: neutral greys (pass --palette-addr for the real one)")


def cmd_palettes(args, r, syms):
    bank, addr = resolve(args, syms)
    off = rom_offset(bank, addr)
    print(f"  {args.count} palettes from {bank:02X}:{addr:04X} "
          f"(ROM offset 0x{off:06X})\n")
    swatch = []
    for i in range(args.count):
        cols = tilelib.decode_palette(r.data, off + i * 8, colours=4)
        hexes = " ".join("#%02x%02x%02x" % c for c in cols)
        print(f"    {i}: {hexes}")
        swatch.append(cols)
    if args.out:
        # One row of 8x8 blocks per palette, so they can be eyeballed.
        pixels = []
        for cols in swatch:
            for _ in range(8):
                row = []
                for c in range(4):
                    row += [c] * 8
                pixels.append(row)
        flat = [[0] * len(pixels[0]) for _ in pixels]
        pal_flat = []
        for pi, cols in enumerate(swatch):
            for y in range(8):
                for x in range(32):
                    flat[pi * 8 + y][x] = pi * 4 + (x // 8)
            pal_flat += cols
        tilelib.write_png(args.out, flat, pal_flat)
        print(f"\n  wrote {args.out}")


def shannon_entropy(data):
    """Bits of entropy per byte, 0.0 (uniform) to 8.0 (random)."""
    if not data:
        return 0.0
    counts = [0] * 256
    for b in data:
        counts[b] += 1
    n = float(len(data))
    total = 0.0
    for c in counts:
        if c:
            p = c / n
            total -= p * math.log2(p)
    return total


def looks_like_tiles(chunk):
    """Score how much a 16-byte-aligned run resembles 2bpp tile data.

    Two signals, both needed. Padding is long runs of a single byte, so a
    region of mostly uniform tiles is rejected outright. Beyond that, entropy
    separates the cases: padding sits near zero, compressed data and encrypted
    or packed blobs approach eight bits per byte, and uncompressed graphics sit
    in a broad band between.

    The band is deliberately wide. Tile data varies enormously - a four-colour
    sprite sheet and a detailed background use very different amounts of the
    byte range - so an early version peaked the score at one entropy value and
    scored real graphics just under the threshold.
    """
    if len(chunk) < 32:
        return 0.0

    tile_count = max(1, len(chunk) // 16)
    uniform = sum(1 for i in range(0, tile_count * 16, 16)
                  if len(set(chunk[i:i + 16])) <= 1)
    non_uniform = 1.0 - (uniform / tile_count)
    if non_uniform < 0.25:
        return 0.0                       # mostly blank: padding, not graphics

    e = shannon_entropy(chunk)
    if e <= 1.0 or e >= 7.6:
        band = 0.0                       # flat padding, or compressed/random
    elif e < 2.0:
        band = (e - 1.0)                 # ramp in
    elif e > 6.5:
        band = (7.6 - e) / 1.1           # ramp out
    else:
        band = 1.0                       # the broad plausible range

    return non_uniform * band


def cmd_scan(args, r, syms):
    window = args.min_tiles * tilelib.TILE_BYTES
    step = window // 2
    print(f"  scanning {len(r.data) // 1024} KiB in {window}-byte windows\n")

    candidates = []
    for off in range(0, len(r.data) - window, step):
        score = looks_like_tiles(r.data[off:off + window])
        if score >= args.threshold:
            candidates.append((score, off))

    # Merge overlapping hits into runs.
    candidates.sort(key=lambda t: t[1])
    merged = []
    for score, off in candidates:
        if merged and off <= merged[-1][2]:
            merged[-1][2] = off + window
            merged[-1][0] = max(merged[-1][0], score)
        else:
            merged.append([score, off, off + window])

    merged.sort(key=lambda t: -(t[2] - t[1]))
    print(f"  {len(merged)} candidate regions (largest first):\n")
    for score, start, end in merged[:args.limit]:
        bank = start // BANK_SIZE
        addr = start % BANK_SIZE + (0 if bank == 0 else 0x4000)
        ntiles = (end - start) // tilelib.TILE_BYTES
        label = syms.get((bank, addr), "")
        print(f"    {bank:02X}:{addr:04X}  offset 0x{start:06X}  "
              f"{ntiles:5d} tiles  score {score:.2f}  {label}")
    if len(merged) > args.limit:
        print(f"\n    ... and {len(merged) - args.limit} more")
    print(f"\n  To look at one:\n"
          f"    python3 tools/extract.py {args.rom} gfx "
          f"--bank BANK --addr ADDR --tiles 128 -o sheet.png")


def cmd_find_gfx(args, r, syms):
    """Cross-reference symbols against the tile-likeness score.

    Neither signal is sufficient alone. A name can only suggest what a label
    points at, and the entropy test cannot tell uncompressed graphics from
    dense machine code. Together they are decisive: a label the naming
    convention calls data, whose bytes also score as tile-like, is graphics.
    """
    if not syms:
        raise SystemExit("error: find-gfx needs --symbols")

    # A label's data runs until the next label in the same bank.
    by_bank = {}
    for (bank, addr), name in syms.items():
        if addr < 0x8000:
            by_bank.setdefault(bank, []).append((addr, name))
    for entries in by_bank.values():
        entries.sort()

    results = []
    for bank, entries in by_bank.items():
        for i, (addr, name) in enumerate(entries):
            nxt = entries[i + 1][0] if i + 1 < len(entries) else addr + args.max_bytes
            extent = min(nxt - addr, args.max_bytes)
            if extent < args.min_tiles * tilelib.TILE_BYTES:
                continue

            off = rom_offset(bank, addr)
            if off + extent > len(r.data):
                continue

            score = looks_like_tiles(r.data[off:off + extent])
            if score < args.min_score:
                continue

            is_data = symlib._looks_like_data(name)
            if args.data_only and not is_data:
                continue

            # Rank names the convention calls data above ones it does not.
            results.append((score + (0.5 if is_data else 0.0), score,
                            bank, addr, extent // tilelib.TILE_BYTES, name, is_data))

    results.sort(key=lambda t: -t[0])
    print(f"  {len(results)} labels look like tile data\n")
    print(f"    {'bank:addr':<10} {'tiles':>6} {'score':>6}  {'kind':<5} name")
    for _, score, bank, addr, ntiles, name, is_data in results[:args.limit]:
        kind = "data" if is_data else "code?"
        print(f"    {bank:02X}:{addr:04X}   {ntiles:>6} {score:>6.2f}  {kind:<5} {name}")
    if len(results) > args.limit:
        print(f"\n    ... and {len(results) - args.limit} more "
              f"(raise --limit to see them)")

    if results:
        _, _, bank, addr, ntiles, name, _ = results[0]
        print(f"\n  Look at the top hit:\n"
              f"    python3 tools/extract.py {args.rom} --symbols {args.symbols} \\\n"
              f"        gfx --symbol {name} --tiles {min(ntiles, 256)} -o {name}.png")


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("rom")
    ap.add_argument("--symbols", help="a .sym file, to name regions and use --symbol")
    sub = ap.add_subparsers(dest="cmd", required=True)

    g = sub.add_parser("gfx", help="decode tiles into a PNG")
    g.add_argument("--bank", type=lambda v: int(v, 16), default=0)
    g.add_argument("--addr", type=lambda v: int(v, 16))
    g.add_argument("--symbol", help="use a label's address instead of --bank/--addr")
    g.add_argument("--tiles", type=int, default=128)
    g.add_argument("--columns", type=int, default=16)
    g.add_argument("--palette-bank", type=lambda v: int(v, 16), default=0)
    g.add_argument("--palette-addr", type=lambda v: int(v, 16))
    g.add_argument("-o", "--out", default="tiles.png")
    g.set_defaults(func=cmd_gfx)

    p = sub.add_parser("palettes", help="decode CGB palettes")
    p.add_argument("--bank", type=lambda v: int(v, 16), default=0)
    p.add_argument("--addr", type=lambda v: int(v, 16))
    p.add_argument("--symbol")
    p.add_argument("--count", type=int, default=8)
    p.add_argument("-o", "--out")
    p.set_defaults(func=cmd_palettes)

    f = sub.add_parser("find-gfx",
                       help="cross-reference symbols with tile-likeness (start here)")
    f.add_argument("--min-score", type=float, default=0.6)
    f.add_argument("--min-tiles", type=int, default=8)
    f.add_argument("--max-bytes", type=int, default=0x2000,
                   help="cap on how far a label's data may run (default 8 KiB)")
    f.add_argument("--data-only", action="store_true",
                   help="only labels the naming convention calls data")
    f.add_argument("--limit", type=int, default=40)
    f.set_defaults(func=cmd_find_gfx)

    s = sub.add_parser("scan", help="find regions that look like tile data")
    s.add_argument("--min-tiles", type=int, default=32)
    s.add_argument("--threshold", type=float, default=0.55)
    s.add_argument("--limit", type=int, default=25)
    s.set_defaults(func=cmd_scan)

    args = ap.parse_args()

    try:
        r = romlib.load(args.rom)
    except (OSError, ValueError) as exc:
        raise SystemExit(f"error: {exc}")

    syms = symlib.load(args.symbols, skip_data=False) if args.symbols else {}
    if syms:
        print(f"  {len(syms)} symbols loaded\n")

    args.func(args, r, syms)
    return 0


if __name__ == "__main__":
    sys.exit(main())
