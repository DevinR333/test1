"""Game Boy graphics codec.

Everything the PPU draws is built from 8x8 tiles in 2bpp planar format: 16
bytes per tile, two bytes per row, one byte holding the low bit of each pixel
and the next holding the high bit. That gives four values per pixel, which
index a four-colour palette.

This module is deliberately game-agnostic. It decodes and re-encodes whatever
tile data it is handed, so it serves both asset extraction and the live sprite
editor, and it carries no artwork of its own.
"""

import struct
import zlib

TILE_W = TILE_H = 8
TILE_BYTES = 16          # 8 rows x 2 bitplane bytes

# Object attribute bits (byte 3 of an OAM entry) on CGB.
ATTR_PALETTE = 0x07
ATTR_VRAM_BANK = 0x08
ATTR_DMG_PALETTE = 0x10
ATTR_XFLIP = 0x20
ATTR_YFLIP = 0x40
ATTR_PRIORITY = 0x80


def decode_tile(data, offset=0):
    """One 8x8 tile -> list of 8 rows of 8 palette indices (0-3)."""
    rows = []
    for y in range(TILE_H):
        lo = data[offset + y * 2]
        hi = data[offset + y * 2 + 1]
        rows.append([((hi >> (7 - x)) & 1) << 1 | ((lo >> (7 - x)) & 1)
                     for x in range(TILE_W)])
    return rows


def encode_tile(rows):
    """8 rows of 8 palette indices -> 16 bytes, ready to write back to VRAM."""
    if len(rows) != TILE_H or any(len(r) != TILE_W for r in rows):
        raise ValueError("a tile must be exactly 8x8")
    out = bytearray()
    for row in rows:
        lo = hi = 0
        for x, px in enumerate(row):
            if not 0 <= px <= 3:
                raise ValueError(f"palette index {px} out of range 0-3")
            bit = 7 - x
            lo |= (px & 1) << bit
            hi |= ((px >> 1) & 1) << bit
        out += bytes((lo, hi))
    return bytes(out)


def decode_sheet(data, count=None, offset=0):
    """A run of consecutive tiles."""
    if count is None:
        count = (len(data) - offset) // TILE_BYTES
    return [decode_tile(data, offset + i * TILE_BYTES) for i in range(count)]


def flip(rows, x=False, y=False):
    out = [list(r) for r in rows]
    if x:
        out = [list(reversed(r)) for r in out]
    if y:
        out = list(reversed(out))
    return out


def compose(tiles, columns, rows=None):
    """Lay tiles out in a grid -> a 2D array of palette indices."""
    rows = rows if rows is not None else (len(tiles) + columns - 1) // columns
    out = [[0] * (columns * TILE_W) for _ in range(rows * TILE_H)]
    for i, tile in enumerate(tiles):
        if i >= columns * rows:
            break
        tx, ty = (i % columns) * TILE_W, (i // columns) * TILE_H
        for y, line in enumerate(tile):
            out[ty + y][tx:tx + TILE_W] = line
    return out


# --- CGB palettes --------------------------------------------------------
# The Color Game Boy stores colours as little-endian 15-bit BGR555, eight
# colours per palette block.

def decode_bgr555(lo, hi):
    """One CGB colour -> (r, g, b) at 8 bits per channel."""
    v = lo | (hi << 8)
    r, g, b = v & 0x1F, (v >> 5) & 0x1F, (v >> 10) & 0x1F
    # Widen 5 bits to 8 by replicating the high bits, so 0x1F maps to 0xFF.
    return (r << 3) | (r >> 2), (g << 3) | (g >> 2), (b << 3) | (b >> 2)


def decode_palette(data, offset=0, colours=4):
    return [decode_bgr555(data[offset + i * 2], data[offset + i * 2 + 1])
            for i in range(colours)]


def encode_bgr555(r, g, b):
    v = (r >> 3) | ((g >> 3) << 5) | ((b >> 3) << 10)
    return bytes((v & 0xFF, v >> 8))


# A neutral four-step ramp, used when no palette is supplied. Chosen to make
# extracted tiles legible rather than to match any particular hardware.
DEFAULT_PALETTE = [(255, 255, 255), (170, 170, 170), (85, 85, 85), (0, 0, 0)]


# --- PNG output ----------------------------------------------------------
# Written by hand so asset extraction needs nothing outside the standard
# library.

def write_png(path, pixels, palette=None):
    """Write a 2D array of palette indices as an RGB PNG."""
    palette = palette or DEFAULT_PALETTE
    height = len(pixels)
    width = len(pixels[0]) if height else 0

    raw = bytearray()
    for row in pixels:
        raw.append(0)                      # per-scanline filter: none
        for px in row:
            raw += bytes(palette[px % len(palette)])

    def chunk(tag, payload):
        body = tag + payload
        return (struct.pack(">I", len(payload)) + body
                + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF))

    with open(path, "wb") as fh:
        fh.write(b"\x89PNG\r\n\x1a\n")
        fh.write(chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)))
        fh.write(chunk(b"IDAT", zlib.compress(bytes(raw), 9)))
        fh.write(chunk(b"IEND", b""))
    return width, height


# --- indexed PNG read/write ----------------------------------------------
# The disassembly stores graphics as 2-bit indexed PNGs. Saving one back as
# RGB would still open in an image viewer but would no longer assemble, so
# both directions preserve the indexed format.

def _unfilter(raw, width, height, bpp, stride):
    """Undo PNG per-scanline filtering. Returns the raw bytes per row."""
    out = bytearray()
    prev = bytearray(stride)
    pos = 0
    for _ in range(height):
        ftype = raw[pos]; pos += 1
        line = bytearray(raw[pos:pos + stride]); pos += stride
        if ftype == 1:      # Sub
            for i in range(bpp, stride):
                line[i] = (line[i] + line[i - bpp]) & 0xFF
        elif ftype == 2:    # Up
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif ftype == 3:    # Average
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xFF
        elif ftype == 4:    # Paeth
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                b = prev[i]
                c = prev[i - bpp] if i >= bpp else 0
                pp = a + b - c
                pa, pb, pc = abs(pp - a), abs(pp - b), abs(pp - c)
                pred = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pred) & 0xFF
        elif ftype != 0:
            raise ValueError(f"unknown PNG filter type {ftype}")
        out += line
        prev = line
    return bytes(out)


def read_png(path):
    """Read an indexed or greyscale PNG.

    Returns (pixels, palette, depth) where pixels is a 2D array of palette
    indices. Only the colour types the disassembly uses are supported.
    """
    with open(path, "rb") as fh:
        data = fh.read()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"{path}: not a PNG")

    pos, idat, palette, hdr = 8, bytearray(), None, None
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        tag = data[pos + 4:pos + 8]
        payload = data[pos + 8:pos + 8 + length]
        if tag == b"IHDR":
            hdr = struct.unpack(">IIBBBBB", payload)
        elif tag == b"PLTE":
            palette = [tuple(payload[i:i + 3]) for i in range(0, len(payload), 3)]
        elif tag == b"IDAT":
            idat += payload
        elif tag == b"IEND":
            break
        pos += 12 + length

    if hdr is None:
        raise ValueError(f"{path}: no IHDR")
    width, height, depth, ctype, _, _, interlace = hdr
    if interlace:
        raise ValueError(f"{path}: interlaced PNGs are not supported")
    if ctype not in (0, 3):
        raise ValueError(f"{path}: colour type {ctype} is not indexed or greyscale")

    stride = (width * depth + 7) // 8
    raw = _unfilter(zlib.decompress(bytes(idat)), width, height,
                    max(1, depth // 8), stride)

    pixels, mask = [], (1 << depth) - 1
    per_byte = 8 // depth
    for y in range(height):
        row, base = [], y * stride
        for x in range(width):
            byte = raw[base + x // per_byte]
            shift = 8 - depth * (x % per_byte + 1)
            row.append((byte >> shift) & mask)
        pixels.append(row)

    if palette is None:                      # greyscale: synthesise a ramp
        levels = (1 << depth) - 1
        palette = [(v * 255 // levels,) * 3 for v in range(levels + 1)]
    return pixels, palette, depth


def write_png_indexed(path, pixels, palette, depth=2):
    """Write a 2D array of palette indices as an indexed PNG."""
    height = len(pixels)
    width = len(pixels[0]) if height else 0
    per_byte = 8 // depth
    stride = (width * depth + 7) // 8
    limit = (1 << depth) - 1

    raw = bytearray()
    for row in pixels:
        raw.append(0)                        # filter: none
        packed = bytearray(stride)
        for x, px in enumerate(row):
            if not 0 <= px <= limit:
                raise ValueError(f"index {px} does not fit in {depth} bits")
            shift = 8 - depth * (x % per_byte + 1)
            packed[x // per_byte] |= px << shift
        raw += packed

    plte = bytearray()
    for c in palette[:1 << depth]:
        plte += bytes(c[:3])

    def chunk(tag, payload):
        body = tag + payload
        return (struct.pack(">I", len(payload)) + body
                + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF))

    with open(path, "wb") as fh:
        fh.write(b"\x89PNG\r\n\x1a\n")
        fh.write(chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, depth, 3, 0, 0, 0)))
        fh.write(chunk(b"PLTE", bytes(plte)))
        fh.write(chunk(b"IDAT", zlib.compress(bytes(raw), 9)))
        fh.write(chunk(b"IEND", b""))
    return width, height
