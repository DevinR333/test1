#!/usr/bin/env python3
"""
make_43_patch.py -- build a 4:3 aspect-ratio exefs patch (.pchtxt) for a
Nintendo Switch game, from that game's own `main` NSO.

Why you have to run this yourself: a .pchtxt patches byte offsets inside one
specific build of one specific game.  Those offsets do not exist until the
game's executable is on disk, and they change with every game update.  So this
script reads YOUR dump, pulls the build ID out of it, finds the 16:9 constants,
and writes a .pchtxt that is already correct for your copy.

Usage
-----
    python3 make_43_patch.py /path/to/main
    python3 make_43_patch.py /path/to/main --out-dir ./out
    python3 make_43_patch.py /path/to/main --only 0,3,7        # keep some hits
    python3 make_43_patch.py /path/to/main --split 8           # bisecting set

`main` lives in the game's exefs.  Get it by extracting the XCI/NSP with
hactoolnet / nstool, or in Eden: long-press the game -> Properties ->
"Dump ExeFS" (wording varies by build).

No third-party modules required; it uses python-lz4 if you happen to have it,
otherwise a built-in LZ4 block decoder.
"""

from __future__ import annotations

import argparse
import struct
import sys
from pathlib import Path

# --------------------------------------------------------------------------
# The constants we hunt for.
#
# A game that hard-codes its aspect ratio stores it as a float somewhere in
# .rodata/.data.  Patching 16:9 -> 4:3 there makes the game itself build a 4:3
# projection matrix, which is real 4:3 (correct geometry, sides cropped) rather
# than a squashed 16:9 image.
# --------------------------------------------------------------------------

def f32(value: float) -> bytes:
    return struct.pack("<f", value)


def f64(value: float) -> bytes:
    return struct.pack("<d", value)


def u32(value: int) -> bytes:
    return struct.pack("<I", value)


# Resolution pairs. If a game sizes its render target from these numbers and
# derives the aspect from the result -- which is the common modern design, and
# what Definitive Edition appears to do -- then narrowing the WIDTH to the 4:3
# partner of the same height makes the game itself render true 4:3. The height
# is deliberately left alone: keeping it fixed preserves vertical detail and
# costs less to render.
RESOLUTION_PAIRS = [
    (1920, 1080, 1440),
    (1280, 720, 960),
    (1600, 900, 1200),
    (2560, 1440, 1920),
    (3840, 2160, 2880),
]

ASPECT_CANDIDATES = [
    # (label, bytes to find, bytes to write, how much to trust a hit)
    ("f32 16/9 = 1.7777778", f32(16 / 9), f32(4 / 3), "high"),
    ("f32 9/16 = 0.5625", f32(9 / 16), f32(3 / 4), "high"),
    ("f64 16/9 = 1.7777777777777777", f64(16 / 9), f64(4 / 3), "high"),
    ("f64 9/16 = 0.5625", f64(9 / 16), f64(3 / 4), "high"),
]

NSO_HEADER_SIZE = 0x100


# --------------------------------------------------------------------------
# LZ4 block decompression (NSO segments are LZ4-block compressed)
# --------------------------------------------------------------------------

def _lz4_block_decompress_py(src: bytes, expected_size: int) -> bytes:
    """Minimal LZ4 block decoder. Pure python, but chunk-copies where it can."""
    dst = bytearray()
    i = 0
    n = len(src)
    while i < n:
        token = src[i]
        i += 1

        lit_len = token >> 4
        if lit_len == 0x0F:
            while True:
                b = src[i]
                i += 1
                lit_len += b
                if b != 0xFF:
                    break
        if lit_len:
            dst += src[i:i + lit_len]
            i += lit_len

        # The last sequence of a block is literals only, with no match after it.
        if i >= n:
            break

        offset = src[i] | (src[i + 1] << 8)
        i += 2
        if offset == 0:
            raise ValueError("corrupt LZ4 stream (zero match offset)")

        match_len = token & 0x0F
        if match_len == 0x0F:
            while True:
                b = src[i]
                i += 1
                match_len += b
                if b != 0xFF:
                    break
        match_len += 4

        start = len(dst) - offset
        if start < 0:
            raise ValueError("corrupt LZ4 stream (match before start of output)")

        if offset >= match_len:
            dst += dst[start:start + match_len]
        else:
            # Overlapping copy: repeat the `offset`-sized window.
            remaining = match_len
            while remaining > 0:
                take = min(offset, remaining)
                dst += dst[start:start + take]
                start += take
                remaining -= take

    if expected_size and len(dst) != expected_size:
        raise ValueError(
            f"LZ4 output was {len(dst)} bytes, header said {expected_size}"
        )
    return bytes(dst)


def lz4_block_decompress(src: bytes, expected_size: int) -> bytes:
    try:
        import lz4.block  # type: ignore
    except ImportError:
        return _lz4_block_decompress_py(src, expected_size)
    return lz4.block.decompress(src, uncompressed_size=expected_size)


# --------------------------------------------------------------------------
# NSO parsing
# --------------------------------------------------------------------------

class Segment:
    def __init__(self, name: str, file_offset: int, memory_offset: int, size: int):
        self.name = name
        self.file_offset = file_offset
        self.memory_offset = memory_offset
        self.size = size

    def contains(self, image_offset: int) -> bool:
        return self.memory_offset <= image_offset < self.memory_offset + self.size


class NSO:
    """A decompressed NSO image, laid out the way emulators lay it out.

    `image` starts at the first segment's memory offset (0), so an offset into
    `image` is exactly what goes in a .pchtxt that carries
    `@flag offset_shift 0x100`.
    """

    def __init__(self, path: Path):
        raw = path.read_bytes()
        if len(raw) < NSO_HEADER_SIZE or raw[:4] != b"NSO0":
            raise ValueError(
                f"{path} is not an NSO (expected magic 'NSO0'). "
                "Point this at the `main` file from the game's exefs."
            )

        self.path = path
        flags = struct.unpack_from("<I", raw, 0x0C)[0]
        self.build_id_full = raw[0x40:0x60]

        seg_defs = [("text", 0x10, 0x60, 0), ("rodata", 0x20, 0x64, 1), ("data", 0x30, 0x68, 2)]
        self.segments: list[Segment] = []
        image = bytearray()

        for name, hdr_off, csize_off, flag_bit in seg_defs:
            file_offset, memory_offset, size = struct.unpack_from("<III", raw, hdr_off)
            compressed_size = struct.unpack_from("<I", raw, csize_off)[0]
            blob = raw[file_offset:file_offset + compressed_size]

            if flags & (1 << flag_bit):
                blob = lz4_block_decompress(blob, size)
            else:
                blob = blob[:size]

            if len(image) < memory_offset:
                image += b"\x00" * (memory_offset - len(image))
            image[memory_offset:memory_offset + size] = blob
            self.segments.append(Segment(name, file_offset, memory_offset, size))

        self.image = bytes(image)

    @property
    def nsobid(self) -> str:
        """The 40-hex-char form used in `@nsobid-` lines."""
        return self.build_id_full[:0x14].hex().upper()

    def section_of(self, image_offset: int) -> str:
        for seg in self.segments:
            if seg.contains(image_offset):
                return seg.name
        return "?"


# --------------------------------------------------------------------------
# Scanning
# --------------------------------------------------------------------------

class Hit:
    def __init__(self, label, offset, section, find, replace, confidence, aligned,
                 kind="projection"):
        self.kind = kind
        self.label = label
        self.offset = offset
        self.section = section
        self.find = find
        self.replace = replace
        self.confidence = confidence
        self.aligned = aligned

    def score(self) -> tuple:
        # Constants live in rodata/data and are naturally aligned. Matches in
        # .text are usually a coincidence inside an instruction stream.
        return (
            self.confidence == "high",
            self.section in ("rodata", "data"),
            self.aligned,
        )

    @property
    def likely(self) -> bool:
        return all(self.score())


def scan_projection(nso: NSO) -> list[Hit]:
    """Route 1: rewrite a hard-coded 16:9 constant into 4:3."""
    hits: list[Hit] = []
    image = nso.image
    for label, find, replace, confidence in ASPECT_CANDIDATES:
        start = 0
        while True:
            idx = image.find(find, start)
            if idx < 0:
                break
            start = idx + 1
            hits.append(
                Hit(
                    label=label,
                    offset=idx,
                    section=nso.section_of(idx),
                    find=find,
                    replace=replace,
                    confidence=confidence,
                    aligned=(idx % len(find) == 0),
                )
            )
    hits.sort(key=lambda h: h.offset)
    return hits


def scan_resolution(nso: NSO) -> list[Hit]:
    """Route 2: narrow the render target to a 4:3 shape.

    Looks for a width and height stored side by side (either order), as ints or
    as floats, and rewrites only the width.
    """
    hits: list[Hit] = []
    image = nso.image
    for width, height, new_width in RESOLUTION_PAIRS:
        for encode, tag in ((u32, "u32"), (f32, "f32")):
            w_bytes, h_bytes, new_bytes = encode(width), encode(height), encode(new_width)
            label = f"{tag} {width}x{height} -> {new_width}x{height}"
            for pattern, width_at in (
                (w_bytes + h_bytes, 0),          # width first
                (h_bytes + w_bytes, len(h_bytes)),  # height first
            ):
                start = 0
                while True:
                    idx = image.find(pattern, start)
                    if idx < 0:
                        break
                    start = idx + 1
                    offset = idx + width_at
                    hits.append(
                        Hit(
                            label=label,
                            offset=offset,
                            section=nso.section_of(offset),
                            find=w_bytes,
                            replace=new_bytes,
                            confidence="high",
                            aligned=(offset % 4 == 0),
                            kind="resolution",
                        )
                    )
    hits.sort(key=lambda h: h.offset)
    return hits


# --------------------------------------------------------------------------
# pchtxt output
# --------------------------------------------------------------------------

HEADER = """\
# {title}
#
# Route: {route}
# Generated by make_43_patch.py from: {source}
# Full build ID: {full_build_id}
#
# Comment a line out with '#' to disable just that one patch.

@nsobid-{nsobid}

@flag offset_shift 0x100
@enabled
"""

ROUTE_BLURB = {
    "projection": "rewrite the hard-coded 16:9 constant so the game builds a 4:3 projection",
    "resolution": "narrow the render target to a 4:3 shape so the game derives 4:3 itself",
}


def render(nso: NSO, hits: list[Hit], title: str, route: str) -> str:
    out = HEADER.format(
        title=title,
        route=ROUTE_BLURB[route],
        source=nso.path.name,
        full_build_id=nso.build_id_full.hex().upper(),
        nsobid=nso.nsobid,
    )
    for hit in hits:
        out += "// {label}  [{section}]  was {was}\n".format(
            label=hit.label,
            section=hit.section,
            was=hit.find.hex().upper(),
        )
        out += "{offset:08X} {value}\n\n".format(
            offset=hit.offset, value=hit.replace.hex().upper()
        )
    out += "@stop\n"
    return out


# --------------------------------------------------------------------------

def parse_only(spec: str, count: int) -> set[int]:
    wanted: set[int] = set()
    for part in spec.split(","):
        part = part.strip()
        if not part:
            continue
        if "-" in part:
            lo, hi = part.split("-", 1)
            wanted.update(range(int(lo), int(hi) + 1))
        else:
            wanted.add(int(part))
    bad = [i for i in wanted if i >= count]
    if bad:
        raise SystemExit(f"--only refers to hits that do not exist: {bad}")
    return wanted


def write_mod(out_dir: Path, title_id: str, name: str, nso: NSO,
              hits: list[Hit], route: str) -> Path:
    folder = out_dir / title_id / name / "exefs"
    folder.mkdir(parents=True, exist_ok=True)
    path = folder / f"{nso.nsobid}.pchtxt"
    path.write_text(render(nso, hits, name, route))
    print(f"  wrote {path}  ({len(hits)} patch line(s))")
    return path


def report(route: str, hits: list[Hit]) -> None:
    print(f"\n{route}: {len(hits)} candidate(s)")
    for i, hit in enumerate(hits):
        print(f"  [{i:3}] 0x{hit.offset:08X}  {hit.section:<6}  {hit.label}")


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(
        description="Build a true-4:3 .pchtxt from a game's `main` NSO.")
    ap.add_argument("nso", type=Path, help="path to the game's exefs `main` file")
    ap.add_argument("--title-id", default="0100AE00096EA000",
                    help="16-hex-char title ID for the output folder name "
                         "(default: Hyrule Warriors: Definitive Edition)")
    ap.add_argument("--mode", choices=("resolution", "projection", "both"), default="both",
                    help="which route to build. resolution = narrow the render target "
                         "(most reliable); projection = patch the 16:9 constant; "
                         "both (default) = write one mod for each, enable one at a time")
    ap.add_argument("--out-dir", type=Path, default=Path("out"),
                    help="where to write the mod folder")
    ap.add_argument("--only", help="comma-separated hit indexes / ranges to patch, e.g. 0,2,5-7 "
                                   "(requires a single --mode)")
    ap.add_argument("--split", type=int, metavar="N",
                    help="also write N variants, each patching a different slice of the hits, "
                         "for bisecting (requires a single --mode)")
    ap.add_argument("--all-hits", action="store_true",
                    help="include low-confidence hits (.text matches, unaligned)")
    args = ap.parse_args(argv)

    if args.mode == "both" and (args.only or args.split):
        ap.error("--only and --split need a single --mode (resolution or projection)")

    nso = NSO(args.nso)
    print(f"Build ID : {nso.nsobid}")
    print("Image    : {:,} bytes  {}".format(
        len(nso.image),
        ", ".join(f"{s.name}@0x{s.memory_offset:X} ({s.size:,})" for s in nso.segments)))

    routes = ["resolution", "projection"] if args.mode == "both" else [args.mode]
    scanners = {"resolution": scan_resolution, "projection": scan_projection}
    names = {
        "resolution": "Force 4-3 (resolution)",
        "projection": "Force 4-3 (projection)",
    }

    wrote_any = False
    for route in routes:
        hits = scanners[route](nso)
        if not args.all_hits:
            hits = [h for h in hits if h.likely]
        report(route, hits)
        if not hits:
            print("  nothing to patch via this route")
            continue

        selected = hits
        if args.only:
            selected = [h for i, h in enumerate(hits) if i in parse_only(args.only, len(hits))]

        write_mod(args.out_dir, args.title_id, names[route], nso, selected, route)
        wrote_any = True

        if args.split:
            chunk = max(1, -(-len(hits) // args.split))
            for n in range(0, len(hits), chunk):
                write_mod(args.out_dir, args.title_id,
                          f"{names[route]} part {n // chunk + 1}", nso,
                          hits[n:n + chunk], route)

    if not wrote_any:
        print(
            "\nNothing found by either route. The game most likely computes its\n"
            "framebuffer size at runtime rather than storing it. See README.md ->\n"
            "'If the script finds nothing'.",
            file=sys.stderr,
        )
        return 2

    print(f"\nCopy the '{args.title_id}' folder into <Eden user folder>/load/ and enable")
    print("ONE of these mods at a time under the game's Add-ons. Try the resolution")
    print("one first. Remember to also set Eden's Aspect Ratio to Force 4:3.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
