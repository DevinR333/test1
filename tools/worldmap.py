#!/usr/bin/env python3
"""Build world map data from a disassembly checkout.

The hardware renders a 160x144 window and nothing else, so a free camera has
to draw the world itself. Everything needed is in the disassembly as plain
binary files, which sidesteps the question of whether a given ROM stores its
room layouts compressed.

  rooms/<game>/small/roomXXXX.bin   10x8 metatile indices for one room
  rooms/<game>/large/roomXXXX.bin   16x11, used by dungeons and interiors
  rooms/<game>/groupNTilesets.bin   which tileset each room in a group uses
  tileset_layouts/<game>/
      tilesetMappingsNN.bin         256 metatiles, each four tiles of two
                                    bytes: tile index then attributes
      tilesetCollisionsNN.bin       one byte per metatile

A group is a 16x16 grid of rooms. Group 0 is the overworld.

Emits a C file holding the layouts and mappings, so the renderer needs no
files at run time.
"""

import argparse
import os
import sys

SMALL_W, SMALL_H = 10, 8
LARGE_W, LARGE_H = 16, 11
METATILE_PX = 16
GROUP_COLS = GROUP_ROWS = 16
ROOMS_PER_GROUP = GROUP_COLS * GROUP_ROWS


def load(path):
    with open(path, "rb") as fh:
        return fh.read()


def collect_group(disasm, game, group, tilesets):
    """Room layouts for one group, in room order, plus the tileset each uses.

    Which file holds a room is not decided by the group alone. The layout
    group in the room's tileset picks between four copies of the overworld,
    one per season, so a room in a place that is always summer - the desert -
    reads its layout from the summer set even in a spring world.
    """
    base = os.path.join(disasm, "rooms", game)

    tileset_path = os.path.join(base, f"group{group}Tilesets.bin")
    room_tilesets = load(tileset_path) if os.path.exists(tileset_path) else bytes(256)

    layouts, missing = [], 0
    for room in range(ROOMS_PER_GROUP):
        number = room_tilesets[room] & 0x7F if room < len(room_tilesets) else 0
        entry = tilesets[number] if number < len(tilesets) else None
        layout_group = entry["group"] if entry else group

        index = layout_group * 0x100 + room
        small = os.path.join(base, "small", f"room{index:04x}.bin")
        large = os.path.join(base, "large", f"room{index:04x}.bin")
        if os.path.exists(small):
            layouts.append(("small", load(small)))
        elif os.path.exists(large):
            layouts.append(("large", load(large)))
        else:
            layouts.append(("none", b""))
            missing += 1

    return layouts, room_tilesets, missing


def collect_mappings(disasm, game, season="spring"):
    """Metatile definitions for every tileset, keyed by tileset number.

    Two directory layouts exist. A normal checkout keeps one file per tileset
    in tileset_layouts/. The modifiable build expands them per season into
    tileset_layouts_expanded/, named tilesetMappingsNN_<season>.bin, because
    the seasons change what the world looks like.

    Finding neither used to return an empty table, and an empty table makes
    every metatile resolve to tile zero, which draws the entire world as one
    repeated pattern. It raises now instead.
    """
    out = {}
    tried = []

    for sub in (os.path.join("tileset_layouts", game),
                os.path.join("tileset_layouts_expanded", game)):
        base = os.path.join(disasm, sub)
        tried.append(sub)
        if not os.path.isdir(base):
            continue

        for name in sorted(os.listdir(base)):
            if not name.startswith("tilesetMappings") or not name.endswith(".bin"):
                continue
            stem = name[len("tilesetMappings"):-len(".bin")]

            # Either "NN" or "NN_season".
            if "_" in stem:
                number, _, which = stem.partition("_")
                if which != season:
                    continue
            else:
                number = stem
            if len(number) != 2:
                continue                 # skip the Indices/Attributes variants

            try:
                out.setdefault(int(number, 16), load(os.path.join(base, name)))
            except ValueError:
                continue

        if out:
            return out, sub

    raise SystemExit(
        "error: no tileset metatile definitions found.\n"
        "  Looked for tilesetMappingsNN.bin under:\n    "
        + "\n    ".join(tried)
        + "\n  Without them every room draws as a single repeated tile.")




# --- tileset graphics and colours ----------------------------------------
# The renderer cannot use whatever the hardware currently holds: video memory
# only ever contains the tileset for the area the player is standing in, so
# every other room would draw from unrelated tiles. Each tileset's own
# graphics and palettes are resolved here instead.
#
# The chain runs: a room names a tileset, a tileset names a graphics header
# and a palette header, a graphics header lists files and the video addresses
# they load to, and a palette header names a block of colour data.

import re

VRAM_BASE = 0x8000
VRAM_SIZE = 0x2000
VRAM_BANKS = 2
PALETTE_BYTES = 8 * 4 * 2          # eight palettes, four colours, two bytes


def _strip(line):
    return line.split(";")[0].rstrip()


def _destination(name, hex_addr):
    """A gfx header entry's destination: an address and a video memory bank.

    Destinations are aligned to sixteen bytes, so the low four bits carry the
    bank number instead. Taking the whole word as an address puts every bank 1
    entry one byte below where it belongs, in bank 0, on top of the tiles
    already there - which is most of a tileset's own graphics: the overworld
    loads its area tiles to $9600 in bank 1.
    """
    addr = int(hex_addr, 16)
    return (name, addr & 0xFFF0, addr & 0x0F)


def parse_gfx_headers(disasm, game):
    """GFXH name -> [(gfx file name, destination address, vram bank)]"""
    out, cur = {}, None
    path = os.path.join(disasm, "data", game, "gfxHeaders.s")
    if not os.path.exists(path):
        return out
    for line in open(path, errors="replace"):
        line = _strip(line).strip()
        m = re.match(r"m_GfxHeaderStart\s+\$?[0-9a-fA-F]+,\s*(\w+)", line)
        if m:
            cur = m.group(1)
            out[cur] = []
            continue
        if line.startswith("m_GfxHeaderEnd"):
            cur = None
            continue
        m = re.match(r"m_GfxHeader\s+(\w+),\s*\$([0-9a-fA-F]+)", line)
        if m and cur is not None:
            out[cur].append(_destination(m.group(1), m.group(2)))
    return out


def parse_unique_gfx_headers(disasm, game):
    """UNIQUE_GFXH name -> [(gfx file, destination address, vram bank)].

    A tileset names two graphics headers: a shared one for the terrain its
    whole region uses, and a unique one carrying the tiles particular to that
    area. Loading only the shared one leaves gaps in video memory, and tile
    indices pointing into those gaps draw as whatever happens to be there.
    """
    out, cur = {}, None
    path = os.path.join(disasm, "data", game, "uniqueGfxHeaders.s")
    if not os.path.exists(path):
        return out
    for line in open(path, errors="replace"):
        line = _strip(line).strip()
        m = re.match(r"m_UniqueGfxHeaderStart\s+\$?[0-9a-fA-F]+,\s*(\w+)", line)
        if m:
            cur = m.group(1)
            out[cur] = []
            continue
        if line.startswith("m_GfxHeaderEnd"):
            cur = None
            continue
        m = re.match(r"m_GfxHeader\s+(\w+),\s*\$([0-9a-fA-F]+)", line)
        if m and cur is not None:
            out[cur].append(_destination(m.group(1), m.group(2)))
    return out


TILESET_ENTRY = 8       # bytes per tileset definition
TILESET_SLOTS = 128     # a room's tileset byte names one of these


def _tileset_entries(text, label):
    """The fixed-size tileset records that follow `label`, in order.

    A record is eight bytes. Most are written as four `.db` lines; the ones
    that change with the season are written as a macro naming a table of four
    records, one per season, and occupy the same eight bytes.
    """
    m = re.search(rf"^{label}:(.*?)(?=^\w+:|\Z)", text, re.S | re.M)
    body = m.group(1) if m else ""

    entries, fields = [], []
    for line in body.splitlines():
        line = _strip(line).strip()
        m = re.match(r"m_SeasonalTileset\s+(\w+)", line)
        if m:
            entries.append(("seasonal", m.group(1)))
            fields = []
            continue
        m = re.match(r"\.db\s+(.+)$", line)
        if not m:
            continue
        fields += [p.strip() for p in m.group(1).split(",")]
        while len(fields) >= TILESET_ENTRY:
            entries.append(("fields", fields[:TILESET_ENTRY]))
            fields = fields[TILESET_ENTRY:]
    return entries


def _byte(token):
    token = token.strip()
    if token.startswith("$"):
        try:
            return int(token[1:], 16)
        except ValueError:
            return None
    return int(token) if token.isdigit() else None


def parse_tilesets(disasm, game, season=0):
    """Every tileset definition, indexed by tileset number.

    The eight bytes of a record are, in order: collision and dungeon, flags,
    unique graphics, main graphics, palette header, metatile layout, layout
    group, animation.

    The layout byte matters most here. It is not the tileset's own number:
    tilesets share metatile definitions, and the files in tileset_layouts are
    named after the layout, so using the tileset number picks the wrong
    metatiles for most of the world. The layout group is the season, and says
    which of the four copies of a room to read.
    """
    path = os.path.join(disasm, "data", game, "tilesets.s")
    if not os.path.exists(path):
        return []
    text = open(path, errors="replace").read()

    out = []
    for kind, value in _tileset_entries(text, "tilesetData"):
        fields = value
        if kind == "seasonal":
            seasonal = [f for k, f in _tileset_entries(text, value) if k == "fields"]
            if not seasonal:
                out.append(None)
                continue
            fields = seasonal[season] if season < len(seasonal) else seasonal[0]

        layout = _byte(fields[5])
        group = _byte(fields[6])
        out.append({
            "uniq":   fields[2],
            "gfxh":   fields[3],
            "palh":   fields[4],
            "layout": layout if layout is not None else 0,
            "group":  group if group is not None else 0,
        })
    return out


def parse_palette_headers(disasm, game):
    """Background palette entries, keyed by name and by numeric index.

    A tileset names one header for palettes 2 to 7 and refers to another by
    number for palettes 0 and 1. Reading only the named one left the first two
    palettes unset, and they are white by default, so roughly a fifth of the
    world rendered blank.
    """
    out, cur = {}, None
    path = os.path.join(disasm, "data", game, "paletteHeaders.s")
    if not os.path.exists(path):
        return out
    for line in open(path, errors="replace"):
        line = _strip(line).strip()
        m = re.match(r"m_PaletteHeaderStart\s+\$?([0-9a-fA-F]+),\s*(\w+)", line)
        if m:
            cur = m.group(2)
            out[cur] = []
            # Also reachable by the number the header was declared with, which
            # is how a tileset refers to the header for palettes 0 and 1.
            out[("index", int(m.group(1), 16))] = out[cur]
            continue
        m = re.match(r"m_PaletteHeaderBg\s+(\d+),\s*(\d+),\s*(\w+)", line)
        if m and cur is not None:
            out[cur].append((int(m.group(1)), int(m.group(2)), m.group(3)))
    return out


def parse_palette_data(disasm, game):
    """Label -> raw colour bytes, little-endian 15-bit BGR."""
    path = os.path.join(disasm, "data", game, "paletteData.s")
    if not os.path.exists(path):
        return {}
    text = open(path, errors="replace").read()
    out = {}
    for m in re.finditer(r"^(\w+):(.*?)(?=^\w+:|\Z)", text, re.S | re.M):
        label, body = m.group(1), m.group(2)
        raw = bytearray()
        for c in re.finditer(r"m_RGB16\s+\$?([0-9a-fA-F]+)\s+\$?([0-9a-fA-F]+)\s+\$?([0-9a-fA-F]+)",
                             body):
            r, g, b = (int(c.group(i), 16) & 0x1F for i in (1, 2, 3))
            v = r | (g << 5) | (b << 10)
            raw += bytes((v & 0xFF, v >> 8))
        if raw:
            out[label] = bytes(raw)
    return out


def find_gfx_file(disasm, game, name):
    for sub in (f"gfx_compressible/{game}", "gfx_compressible/common",
                f"gfx/{game}", "gfx/common"):
        for ext in (".png", ".bin"):
            path = os.path.join(disasm, sub, name + ext)
            if os.path.exists(path):
                return path
    return None


def gfx_tile_bytes(path):
    """A graphics file as raw 2bpp tile data."""
    if path.endswith(".bin"):
        return load(path)

    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import tiles as tilelib
    pixels, _, _ = tilelib.read_png(path)

    # Tiles run down each 8-pixel column strip, then across, which is how the
    # hardware expects consecutive tile indices to appear.
    out = bytearray()
    rows = len(pixels) // 8
    cols = (len(pixels[0]) // 8) if pixels else 0
    for ty in range(rows):
        for tx in range(cols):
            tile = [pixels[ty * 8 + y][tx * 8:tx * 8 + 8] for y in range(8)]
            out += tilelib.encode_tile(tile)
    return bytes(out)


def build_tileset_assets(disasm, game, tilesets):
    """Per tileset: an image of video memory, its palettes, and which of them
    it actually defines.

    A tileset's palette header supplies background palettes 2 to 7 only.
    Palettes 0 and 1 are shared - the status bar and the parts of the world
    every area has in common - and are loaded once, not per tileset, so they
    are taken from the running machine instead of from here.
    """
    headers = parse_gfx_headers(disasm, game)
    unique = parse_unique_gfx_headers(disasm, game)
    pal_headers = parse_palette_headers(disasm, game)
    pal_data = parse_palette_data(disasm, game)

    vram_out, pal_out, mask_out, loaded = [], [], [], 0
    cache = {}

    for entry in tilesets:
        # Both banks, one after the other, the way the renderer reads them.
        vram = bytearray(VRAM_SIZE * VRAM_BANKS)
        palettes = bytearray(PALETTE_BYTES)
        defined = 0

        if entry is not None:
            # Shared terrain first, then the area's own tiles over the top,
            # which is the order the game loads them in.
            for name, dest, bank in (list(headers.get(entry["gfxh"], []))
                                     + list(unique.get(entry["uniq"], []))):
                if name not in cache:
                    path = find_gfx_file(disasm, game, name)
                    cache[name] = gfx_tile_bytes(path) if path else b""
                data = cache[name]
                if not data or bank >= VRAM_BANKS:
                    continue
                off = (dest - VRAM_BASE) + bank * VRAM_SIZE
                limit = (bank + 1) * VRAM_SIZE
                if bank * VRAM_SIZE <= off < limit:
                    end = min(limit, off + len(data))
                    vram[off:end] = data[:end - off]
                    loaded += 1

            for first, count, label in pal_headers.get(entry["palh"], []):
                raw = pal_data.get(label, b"")
                off = first * 8
                take = min(len(raw), count * 8, PALETTE_BYTES - off)
                if take > 0:
                    palettes[off:off + take] = raw[:take]
                for i in range(first, min(first + count, 8)):
                    defined |= 1 << i

        vram_out.append(bytes(vram))
        pal_out.append(bytes(palettes))
        mask_out.append(defined)

    return vram_out, pal_out, mask_out, loaded, len(headers), len(pal_data)


def emit_c(path, layouts, room_tilesets, tilesets, mappings, group,
           used, assets=None):
    lines = [
        "/* Generated by tools/worldmap.py - do not edit.",
        " *",
        " * Room layouts and tileset mappings for the world map renderer.",
        " * A group is a 16x16 grid of rooms; each small room is 10x8 metatiles",
        " * and each metatile is four 8x8 tiles, so the group covers",
        f" * {GROUP_COLS * SMALL_W * METATILE_PX}x{GROUP_ROWS * SMALL_H * METATILE_PX} pixels.",
        " */",
        '#include "worldmap.h"',
        "",
        f"const int gb_world_group = {group};",
        "",
        "/* Metatile indices, one room after another in room order. */",
        "const uint8_t gb_world_rooms[GB_WORLD_ROOMS][GB_ROOM_TILES] = {",
    ]

    for kind, data in layouts:
        cells = list(data[:SMALL_W * SMALL_H])
        cells += [0] * (SMALL_W * SMALL_H - len(cells))
        lines.append("    {" + ",".join(str(c) for c in cells) + "},")
    lines += ["};", ""]

    lines.append("/* Which tileset each room uses. */")
    lines.append("const uint8_t gb_world_room_tileset[GB_WORLD_ROOMS] = {")
    row = [str(room_tilesets[i] if i < len(room_tilesets) else 0)
           for i in range(ROOMS_PER_GROUP)]
    for i in range(0, len(row), 16):
        lines.append("    " + ",".join(row[i:i + 16]) + ",")
    lines += ["};", ""]

    lines.append("/* Which metatile definitions each tileset uses. Tilesets")
    lines.append(" * share these, so a tileset's own number is the wrong")
    lines.append(" * index for all but a handful of them. */")
    lines.append(f"const uint8_t gb_world_tileset_layout[{TILESET_SLOTS}] = {{")
    row = [str(tilesets[i]["layout"] if i < len(tilesets) and tilesets[i] else 0)
           for i in range(TILESET_SLOTS)]
    for i in range(0, len(row), 16):
        lines.append("    " + ",".join(row[i:i + 16]) + ",")
    lines += ["};", ""]

    top = max(mappings) + 1 if mappings else 1
    lines.append("/* Metatile definitions: four tiles of index and attributes. */")
    lines.append(f"const uint8_t gb_world_mappings[{top}][GB_MAPPING_BYTES] = {{")
    for i in range(top):
        data = mappings.get(i)
        if data is None:
            lines.append("    {0},")
            continue
        vals = list(data[:2048]) + [0] * max(0, 2048 - len(data))
        chunks = [",".join(str(v) for v in vals[j:j + 32]) for j in range(0, 2048, 32)]
        lines.append("    {" + ",\n     ".join(chunks) + "},")
    lines += ["};", "", f"const int gb_world_mapping_count = {top};", ""]

    vram_imgs, palettes, masks = (assets if assets else ([], [], []))

    # Only the tilesets this group's rooms ask for. All 128 would be a couple
    # of megabytes of tile pixels, nearly all of it dungeons and interiors the
    # world map never draws.
    slot_of = [0xFF] * TILESET_SLOTS
    for slot, number in enumerate(used):
        slot_of[number] = slot

    lines.append("/* Where each tileset's graphics are, or 0xFF for one this")
    lines.append(" * group never uses. */")
    lines.append(f"const uint8_t gb_world_tileset_asset[{TILESET_SLOTS}] = {{")
    row = [str(v) for v in slot_of]
    for i in range(0, len(row), 16):
        lines.append("    " + ",".join(row[i:i + 16]) + ",")
    lines += ["};", ""]

    vram_imgs = [vram_imgs[n] for n in used] if vram_imgs else []
    palettes = [palettes[n] for n in used] if palettes else []
    masks = [masks[n] for n in used] if masks else []
    count = max(1, len(vram_imgs))
    lines.append("/* Each tileset's own tile pixels: video memory only ever")
    lines.append(" * holds the area the player is in, so the rest of the world")
    lines.append(" * has to bring its own. */")
    lines.append(f"const uint8_t gb_world_tileset_vram[{count}][GB_TILESET_VRAM] = {{")
    span = VRAM_SIZE * VRAM_BANKS
    for img in vram_imgs or [bytes(span)]:
        vals = list(img) + [0] * (span - len(img))
        chunks = [",".join(str(v) for v in vals[j:j + 32]) for j in range(0, span, 32)]
        lines.append("    {" + ",\n     ".join(chunks) + "},")
    lines += ["};", ""]

    lines.append("/* And its own colours. */")
    lines.append(f"const uint8_t gb_world_tileset_palette[{count}][GB_TILESET_PALETTE] = {{")
    for pal in palettes or [b"\xFF" * 64]:
        vals = list(pal) + [0xFF] * (64 - len(pal))
        lines.append("    {" + ",".join(str(v) for v in vals) + "},")
    lines += ["};", ""]

    lines.append("/* Which of those eight a tileset actually sets. The two it")
    lines.append(" * leaves alone are shared across the whole game and loaded")
    lines.append(" * once, so they are read from the running machine. */")
    lines.append(f"const uint8_t gb_world_tileset_palette_mask[{count}] = {{")
    row = [str(m) for m in (masks or [0])]
    for i in range(0, len(row), 16):
        lines.append("    " + ",".join(row[i:i + 16]) + ",")
    lines += ["};", "", f"const int gb_world_tileset_count = {count};", ""]

    with open(path, "w") as fh:
        fh.write("\n".join(lines))
    return len(lines)


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("disasm", help="path to the oracles-disasm checkout")
    ap.add_argument("--game", default="seasons", choices=["seasons", "ages"])
    ap.add_argument("--group", type=int, default=0,
                    help="0 is the overworld (default)")
    ap.add_argument("-o", "--out", default="build/world_data.c")
    args = ap.parse_args()

    if not os.path.isdir(os.path.join(args.disasm, "rooms")):
        raise SystemExit(f"error: {args.disasm} has no rooms/ directory")

    tilesets = parse_tilesets(args.disasm, args.game)
    if not tilesets:
        raise SystemExit("error: no tileset definitions found in "
                         f"data/{args.game}/tilesets.s")
    layouts, room_tilesets, missing = collect_group(args.disasm, args.game,
                                                   args.group, tilesets)
    mappings, mapping_dir = collect_mappings(args.disasm, args.game)

    present = sum(1 for k, _ in layouts if k != "none")
    sizes = {}
    for kind, _ in layouts:
        sizes[kind] = sizes.get(kind, 0) + 1

    print(f"  group {args.group} ({args.game})")
    print(f"    rooms found     {present} of {ROOMS_PER_GROUP}"
          + (f", {missing} missing" if missing else ""))
    print(f"    room sizes      " + ", ".join(f"{k}: {v}" for k, v in sorted(sizes.items())))
    print(f"    tileset mappings {len(mappings)} from {mapping_dir}")
    if len(mappings) < 16:
        raise SystemExit(f"error: only {len(mappings)} tileset mappings found, "
                         "far too few. The world would draw as one repeated "
                         "tile, so this is treated as a failure.")
    print(f"    world extent    {GROUP_COLS * SMALL_W * METATILE_PX}"
          f"x{GROUP_ROWS * SMALL_H * METATILE_PX} px")

    (vram_imgs, pal_imgs, pal_masks, chunks,
     nheaders, npal) = build_tileset_assets(args.disasm, args.game, tilesets)
    print(f"    tileset graphics {len(vram_imgs)} tilesets, "
          f"{chunks} graphics chunks loaded")

    used = sorted({room_tilesets[i] & 0x7F for i in range(ROOMS_PER_GROUP)})
    lost = [t for t in used if t >= len(tilesets) or tilesets[t] is None]
    if lost:
        raise SystemExit("error: rooms use tilesets with no definition: "
                         + ", ".join(hex(t) for t in lost))
    blank = [t for t in used if not pal_masks[t]]
    if blank:
        raise SystemExit("error: tilesets with no palettes at all: "
                         + ", ".join(hex(t) for t in blank))
    print(f"    tilesets in use  {len(used)}, all with graphics and palettes")

    os.makedirs(os.path.dirname(args.out) or ".", exist_ok=True)
    n = emit_c(args.out, layouts, room_tilesets, tilesets, mappings, args.group,
               used, (vram_imgs, pal_imgs, pal_masks))
    print(f"    wrote {args.out} ({n} lines)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
