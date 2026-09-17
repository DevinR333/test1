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

# Distinguishing data labels from code labels is done by naming convention.
# oracles-disasm uses camelCase with a descriptive suffix, so the suffix is a
# far better signal than a substring match anywhere in the name: a plain
# substring test classifies updateSpriteAnimation and loadRoomLayout as data
# because they contain "sprite" and "room".
DATA_SUFFIXES = (
    "data", "table", "tables", "header", "headers", "gfx", "graphics",
    "tiles", "tileset", "tilesets", "palette", "palettes", "map", "mapping",
    "mappings", "layout", "layouts", "list", "lists", "index", "indices",
    "pointer", "pointers", "ptr", "ptrs", "string", "strings", "text",
    "script", "scripts", "frames", "attributes", "collisions", "positions",
)

# A label beginning with a verb names a routine, whatever nouns follow it.
CODE_PREFIXES = ("load", "update", "draw", "get", "set", "init", "check",
                 "handle", "run", "do", "apply", "clear", "reset", "write",
                 "read", "copy", "make", "create", "delete", "find", "calc",
                 "compute", "process", "parse", "render", "start", "stop",
                 "enable", "disable", "toggle", "push", "pop", "call", "jump",
                 "func", "sub_", "routine", "animate", "add", "remove",
                 "inc", "dec", "show", "hide", "open", "close", "play")


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
    """True when the name suggests the label points at data, not instructions."""
    # A local label inside a routine (wla writes these as parent@child) is
    # part of that routine's code, whatever the parent is called.
    if "@" in label:
        return False

    stripped = label.lstrip(".@_")

    # Names are often namespaced with an underscore, and the routine name is
    # the part after it: paletteThread_calculateFadingPalettes is code even
    # though the whole string ends in a data-ish word. Test every segment.
    for segment in stripped.split("_"):
        if segment.lower().startswith(CODE_PREFIXES):
            return False

    # Otherwise decide on the trailing word of the camelCase name, so
    # animationGfxHeaders is data while addIndexToLoadedObjectGfx is not.
    words = re.findall(r"[a-z]+|[A-Z][a-z]*|\d+", stripped)
    if words and words[-1].lower() in DATA_SUFFIXES:
        return True
    return False


def entry_points(symbols):
    """Symbol addresses usable as code entry points, ROM only."""
    return [(bank, addr) for (bank, addr) in symbols if addr < 0x8000]


def summarise(symbols) -> str:
    banks = {}
    for (bank, _) in symbols:
        banks[bank] = banks.get(bank, 0) + 1
    return (f"  symbols        {len(symbols)} code labels across {len(banks)} banks")
