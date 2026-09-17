#!/usr/bin/env python3
"""Identify and compare Game Boy ROMs.

Two jobs:

  identify   report what a ROM actually is, so you can confirm a dump is clean
             and matches the version a disassembly targets

  compare    diff two ROMs and summarise where they differ, for checking a
             disassembly's build output against an original dump

Nothing here is written anywhere; both modes only read.
"""

import argparse
import hashlib
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import rom as romlib

# Internal header titles for the two Oracle games. The cartridge header is the
# reliable way to tell them apart; a renamed file is not.
KNOWN_TITLES = {
    "ZELDA DIN": "The Legend of Zelda: Oracle of Seasons",
    "ZELDA NAYRU": "The Legend of Zelda: Oracle of Ages",
}


def digests(data):
    return {
        "crc32": f"{__import__('zlib').crc32(data) & 0xFFFFFFFF:08x}",
        "md5": hashlib.md5(data).hexdigest(),
        "sha1": hashlib.sha1(data).hexdigest(),
    }


def identify(path):
    try:
        r = romlib.load(path)
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    print(f"{path}")
    print(romlib.describe(r))

    d = digests(r.data)
    print(f"  crc32          {d['crc32']}")
    print(f"  md5            {d['md5']}")
    print(f"  sha1           {d['sha1']}")

    match = next((full for t, full in KNOWN_TITLES.items()
                  if r.title.upper().startswith(t)), None)
    print()
    if match:
        print(f"  This is {match}.")
    else:
        print(f"  Header title {r.title!r} is not one of the Oracle games.")

    if not r.header_checksum_ok:
        print("  The header checksum is wrong. Real hardware refuses to boot a")
        print("  cartridge that fails this, so the dump is probably damaged.")
    elif not r.global_checksum_ok:
        print("  The header checksum passes but the global checksum does not.")
        print("  Hardware ignores the global one, so the game will still run,")
        print("  but the dump differs from the original release somewhere.")
    else:
        print("  Both checksums pass; this looks like a clean dump.")

    print()
    print("  Compare this sha1 against the one your disassembly expects. If they")
    print("  match, the disassembly targets your exact revision.")
    return 0


def compare(path_a, path_b):
    with open(path_a, "rb") as fh:
        a = fh.read()
    with open(path_b, "rb") as fh:
        b = fh.read()

    print(f"a: {path_a}  ({len(a)} bytes)")
    print(f"b: {path_b}  ({len(b)} bytes)")

    if digests(a)["sha1"] == digests(b)["sha1"]:
        print("\n  Identical.")
        return 0

    if len(a) != len(b):
        big, small = max(len(a), len(b)), min(len(a), len(b))
        print(f"\n  Sizes differ: {len(a)} vs {len(b)} bytes.")
        if big % small == 0:
            print(f"  The larger is exactly {big // small}x the smaller.")
        print()
        print("  Two ROMs of different sizes are different builds, not a damaged")
        print("  dump. A disassembly built in its modifiable configuration stores")
        print("  graphics and text uncompressed so they can be edited, which")
        print("  inflates the ROM and moves everything after each expanded asset.")
        print("  Only a precompressed build is comparable to an original dump.")
        print()
        print("  The byte comparison below is therefore not meaningful here.")

    n = min(len(a), len(b))
    diffs = [i for i in range(n) if a[i] != b[i]]
    print(f"\n  {len(diffs)} of {n} shared bytes differ "
          f"({100.0 * len(diffs) / n:.3f}%)")

    if diffs:
        print(f"  first difference at 0x{diffs[0]:06X} "
              f"(bank {diffs[0] // romlib.BANK_SIZE}): "
              f"{a[diffs[0]]:02X} vs {b[diffs[0]]:02X}")

        # Which banks are affected, and how much of each.
        per_bank = {}
        for i in diffs:
            per_bank.setdefault(i // romlib.BANK_SIZE, 0)
            per_bank[i // romlib.BANK_SIZE] += 1
        print(f"  {len(per_bank)} of {n // romlib.BANK_SIZE} banks affected")
        for bank, count in sorted(per_bank.items())[:10]:
            print(f"    bank {bank:3d}  {count:6d} bytes")
        if len(per_bank) > 10:
            print(f"    ... and {len(per_bank) - 10} more banks")

        # A build that differs only in padding is the expected outcome when a
        # disassembly handles empty space differently from the original.
        if len(a) != len(b):
            return 0

        filler = sum(1 for i in diffs if a[i] in (0x00, 0xFF) and b[i] in (0x00, 0xFF))
        if filler == len(diffs):
            print("\n  Every difference is between 0x00 and 0xFF padding bytes.")
            print("  That is unused space, not game code. A build that differs")
            print("  only here behaves identically to the original.")
        elif filler:
            print(f"\n  {filler} of the differences are padding; "
                  f"{len(diffs) - filler} are not.")
    return 0


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("rom", help="the ROM to identify")
    ap.add_argument("other", nargs="?", help="a second ROM; given both, compare them")
    args = ap.parse_args(argv)
    return compare(args.rom, args.other) if args.other else identify(args.rom)


if __name__ == "__main__":
    sys.exit(main())
