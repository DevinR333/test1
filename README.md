# gbrecomp

A static recompiler for Game Boy / Game Boy Color ROMs: it reads a ROM you
supply, finds the executable code in it, and emits native C that a host
compiler turns into ARM64 (or x86-64) machine code.

This is not an emulator. There is no interpreter dispatch loop in the output —
the game's control flow becomes real branches in a real binary. What it is
*not* is a decompilation: the output is machine-generated C, not readable
source, and it does not recover names, types or structure.

## Status

Working:

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

Not written yet:

- The runtime implementation behind `gb.h`: PPU, APU, timers, MBC write
  handling, interrupt dispatch, and the interpreter fallback.
- The Android shell (NDK build, surface, touch controls, audio out).

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
