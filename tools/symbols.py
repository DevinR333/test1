"""Symbol-file import.

Heuristic code discovery has one real weakness: it cannot follow jump tables or
computed branches, so it misses code and can mistake data for instructions. A
symbol file from an existing disassembly removes that weakness outright - every
label is a confirmed code address in a confirmed bank.

Supported formats:
  wla-dx / no$gmb .sym   `BB:AAAA Label`   (what oracles-disasm emits)
  rgbds .sym             same layout, bank in hex
"""

import re

SYM_LINE = re.compile(r"^\s*([0-9A-Fa-f]{1,3}):([0-9A-Fa-f]{4})\s+(\S+)")

# Labels that name data rather than code. Feeding these to the recompiler as
# entry points would make it disassemble graphics, so they are filtered out.
DATA_HINTS = ("gfx", "tiles", "tileset", "palette", "pal_", "data", "table",
              "map", "text", "string", "sprite", "song", "music", "sound_data",
              "collision", "layout", "room", "area", "dungeon_layout")


def load(path, skip_data=True):
    """Parse a .sym file into {(bank, addr): label}."""
    out = {}
    section = "labels"
    with open(path, "r", errors="replace") as fh:
        for line in fh:
            line = line.split(";")[0].rstrip()
            if not line:
                continue
            if line.startswith("["):
                section = line.strip("[]").lower()
                continue
            if section not in ("labels", "symbols"):
                continue
            m = SYM_LINE.match(line)
            if not m:
                continue
            bank, addr, label = int(m.group(1), 16), int(m.group(2), 16), m.group(3)
            if skip_data and _looks_like_data(label):
                continue
            out[(bank, addr)] = label
    return out


def _looks_like_data(label: str) -> bool:
    low = label.lower().lstrip(".@_")
    return any(h in low for h in DATA_HINTS)


def entry_points(symbols):
    """Symbol addresses usable as code entry points, ROM only."""
    return [(bank, addr) for (bank, addr) in symbols if addr < 0x8000]


def summarise(symbols) -> str:
    banks = {}
    for (bank, _) in symbols:
        banks[bank] = banks.get(bank, 0) + 1
    return (f"  symbols        {len(symbols)} code labels across {len(banks)} banks")
