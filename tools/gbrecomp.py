#!/usr/bin/env python3
"""gbrecomp - static recompiler for Game Boy / Game Boy Color ROMs.

Reads a ROM, finds the code in it, and emits native C. The ROM is never
redistributed by this tool: it is read from a path you supply and only the
generated C is written out.

    python3 tools/gbrecomp.py path/to/game.gbc -o build/src
"""

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import discover
import emit
import rom as romlib
import symbols as symlib


def parse_entries(values):
    """--entry BANK:ADDR, for jump-table targets discovery cannot see."""
    out = []
    for v in values or []:
        bank, _, addr = v.partition(":")
        if not addr:
            bank, addr = "0", bank
        out.append((int(bank, 0), int(addr, 16)))
    return out


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("rom", help="path to the .gb/.gbc ROM to recompile")
    ap.add_argument("-o", "--out", default="build/src", help="output directory for generated C")
    ap.add_argument("--entry", action="append", metavar="BANK:ADDR",
                    help="extra code entry point, repeatable (e.g. 3:4A20)")
    ap.add_argument("--symbols", metavar="FILE",
                    help="a .sym file from an existing disassembly; every label "
                         "becomes a confirmed code entry point")
    ap.add_argument("--keep-data-labels", action="store_true",
                    help="do not filter labels whose names look like data")
    ap.add_argument("--report-only", action="store_true",
                    help="analyse and report, emit nothing")
    args = ap.parse_args(argv)

    try:
        r = romlib.load(args.rom)
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    print(f"cartridge: {args.rom}")
    print(romlib.describe(r))
    if not r.header_checksum_ok:
        print("  warning: header checksum is wrong; this may not be a clean dump")

    entries = parse_entries(args.entry)
    if args.symbols:
        try:
            syms = symlib.load(args.symbols, skip_data=not args.keep_data_labels)
        except OSError as exc:
            print(f"error: {exc}", file=sys.stderr)
            return 1
        print(symlib.summarise(syms))
        entries += symlib.entry_points(syms)

    print("\nanalysing:")
    prog = discover.discover(r, entries)
    print(discover.report(prog))

    if args.report_only:
        return 0

    print("\nemitting:")
    files, unhandled = emit.generate(prog)
    os.makedirs(args.out, exist_ok=True)
    total = 0
    for name, content in sorted(files.items()):
        path = os.path.join(args.out, name)
        with open(path, "w") as fh:
            fh.write(content)
        total += content.count("\n") + 1
        print(f"  {path}  ({content.count(chr(10)) + 1} lines)")
    print(f"  {len(files)} files, {total} lines of C")

    if unhandled:
        print("\n  instructions routed to the interpreter fallback:")
        for m, n in sorted(unhandled.items(), key=lambda kv: -kv[1]):
            print(f"    {m:<16} {n}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
