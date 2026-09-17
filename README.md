# Oracle of Seasons — native Android port

Working toward a native Android build of Zelda: Oracle of Seasons with a real
camera (free zoom), correct-speed high-refresh rendering, and live sprite
editing.

The game is exceptionally well disassembled — `oracles-disasm` is a complete,
documented, *relocatable* disassembly of both Oracle games, stress-tested for
years by the randomizer community. That is the specification this port is
built against. You supply your own cartridge dump; no ROM or game asset is
stored here.

## Two routes, and which one this is

**Route A — static recompilation.** Lift SM83 to C, compile to ARM64. No
interpreter loop. Accurate and mechanical, but it inherits the hardware's
160x144 window, so it *cannot* zoom out: the console never rendered anything
outside that rectangle, and the off-screen tile margin is stale while
off-viewport objects are not in OAM at all.

**Route B — reimplementation.** Rewrite the game against a modern renderer,
using the disassembly as the behavioural spec. The world lives in memory, so
the camera is free and zoom-out is trivial. This is the larger effort and it
is the main line of this repo.

Route A is kept because it stays useful: a recompiled build is a reference to
diff behaviour against while porting logic, and its ROM and symbol handling is
shared with asset extraction.

## Status



Shared foundation:

- **`tools/rom.py`** — cartridge header parsing, mapper detection
  (MBC1/2/3/5), bank windowing, checksum validation.
- **`tools/symbols.py`** — `.sym` import, so `oracles-disasm` labels become
  known addresses.
- **`tools/tiles.py`** — 2bpp tile codec, CGB BGR555 palettes, and a
  dependency-free PNG writer. Used by both asset extraction and the sprite
  editor.
- **`runtime/present.{h,c}`** — screen fitting and zoom viewport maths.

Route A (complete front half):

- **`tools/sm83.py`** — complete SM83 instruction tables (256 base + 256
  CB-prefixed opcodes), with lengths, M-cycle costs and control-flow classes.
- **`tools/rom.py`** — cartridge header parsing, mapper detection
  (MBC1/2/3/5), bank windowing, checksum validation.
- **`tools/discover.py`** — recursive-descent code discovery from the hardware
  entry points, with static bank-switch tracking.
- **`tools/symbols.py`** — `.sym` import, so an existing disassembly's labels
  become confirmed entry points.
- **`tools/emit.py`** — the C emitter. Intra-bank jumps become `goto`;
  everything else goes through a dispatcher.
- **`runtime/gb.h`** — the ABI the generated code targets.

- **`runtime/alu.c`**, **`runtime/memory.c`** — flag-accurate ALU, full
  address decoding, MBC1/2/3/5 mapper writes.

Not written yet (route B main line):

- Asset extraction: tilesets, palettes, room layouts, object placements.
- Renderer with a free camera.
- Entity system, collision, and the game logic itself.
- The Android shell (NDK build, surface, touch controls, audio).

## Getting set up

You need a local build of `oracles-disasm` to get symbols and a reference ROM.
One command does the whole thing — toolchain, clone, and build:

```sh
./scripts/setup-disasm.sh
```

It installs WLA-DX v10.6 from source (the version the disassembly requires;
distro packages are usually older), pulls the disassembly into `external/`,
and runs `make seasons`. Re-running it is safe. Add `--hack-base` for the
modifiable build, which is the one you want if you intend to edit graphics.

Then check what you have:

```sh
python3 tools/identify.py your-dump.gbc
python3 tools/identify.py external/oracles-disasm/seasons.gbc
python3 tools/identify.py your-dump.gbc external/oracles-disasm/seasons.gbc
```

**Expect the Seasons comparison to differ.** The project documents that WLA
does not reproduce a byte-exact Seasons ROM, because it handles empty space
differently. `identify.py` checks whether every difference falls in padding
and tells you so — if it does, the build is good. (Ages does match exactly.)

## Usage

```sh
python3 tools/gbrecomp.py game.gbc -o build/src
python3 tools/gbrecomp.py game.gbc --symbols game.sym -o build/src
python3 tools/gbrecomp.py game.gbc --report-only
```

`--symbols` matters a lot — see below.

## Why a symbol file changes everything

Pure static analysis cannot resolve `jp hl`. Game Boy code uses it constantly
for state machines and jump tables, so a heuristic-only pass misses real code
and can mistake graphics data for instructions.

A `.sym` file from an existing disassembly removes that problem: every label is
a confirmed code address in a confirmed bank. Coverage goes from "whatever
recursive descent happened to reach" to something close to complete.

## The honest limits

- **Native buys no speed here.** A GBC runs at ~4 MHz; any modern phone
  interprets that hundreds of times faster than real time. The reason to want
  native output is toolchain and modifiability, not performance.
- **The hardware still has to be emulated.** Recompiling the CPU does not
  recompile the PPU. Generated code still writes display registers and depends
  on exact timing, so the runtime counts cycles and catches the hardware up.
- **Coverage is never provably complete.** Code copied into HRAM at run time
  (the OAM DMA routine, in nearly every game) needs the interpreter fallback.

## Legal

This tool contains no game code and ships no ROM. It reads a ROM from a path
you supply and writes only generated C. Dumping a cartridge you own for your
own use is your business; the generated output of a commercial game is a
derivative work of that game and is not yours to distribute.

## Testing

`tests/make_fixture.py` builds a small synthetic ROM from scratch, exercising
conditional branches, bank switching, indirect jumps and interrupt vectors. It
contains no commercial code, so the test suite is self-contained.

```sh
python3 tests/make_fixture.py tests/fixture.gb
python3 tools/gbrecomp.py tests/fixture.gb -o build/src
```
