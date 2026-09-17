"""Recursive-descent code discovery.

Static recompilation needs to know which ROM bytes are instructions before it
can emit anything. Game Boy ROMs interleave code, graphics, level data and text
with no separation, so we start from the addresses the hardware guarantees are
code and follow control flow from there.

What this pass cannot resolve is recorded rather than guessed at: `jp hl` and
`ret` go to addresses only known at run time, and the emitter turns those into
dispatches instead of direct branches.
"""

from dataclasses import dataclass, field

import math

import sm83
from rom import BANK_SIZE, Rom

# Addresses the hardware itself jumps to. Everything reachable starts here.
RST_VECTORS = [0x00, 0x08, 0x10, 0x18, 0x20, 0x28, 0x30, 0x38]
INTERRUPT_VECTORS = [0x40, 0x48, 0x50, 0x58, 0x60]
ENTRY_POINT = 0x100

# MBC control registers. A write into these ranges reconfigures the mapper, so
# the bank tracker watches for them.
BANK_SELECT_LO = 0x2000
BANK_SELECT_HI = 0x3FFF


@dataclass
class Block:
    """A straight-line run of instructions ending in a control-flow change."""
    bank: int
    start: int
    insns: list = field(default_factory=list)
    successors: set = field(default_factory=set)   # (bank, addr) pairs
    terminator: str = sm83.NEXT

    @property
    def end(self) -> int:
        return self.insns[-1].end if self.insns else self.start


@dataclass
class Program:
    rom: Rom
    blocks: dict = field(default_factory=dict)        # (bank, addr) -> Block
    entry_points: set = field(default_factory=set)
    call_targets: set = field(default_factory=set)
    indirect_sites: list = field(default_factory=list)  # (bank, addr) of jp hl
    bank_switches: list = field(default_factory=list)   # (bank, addr, new_bank)
    graphics_banks: set = field(default_factory=set)

    @property
    def coverage(self) -> dict:
        """Bytes identified as code, per bank."""
        out = {}
        for (bank, _), blk in self.blocks.items():
            out[bank] = out.get(bank, 0) + sum(i.length for i in blk.insns)
        return out


def _in_rom(addr: int) -> bool:
    return addr < 0x8000


def _entropy(data) -> float:
    """Bits per byte, 0.0 (uniform) to 8.0 (random)."""
    if not data:
        return 0.0
    counts = [0] * 256
    for b in data:
        counts[b] += 1
    n = float(len(data))
    total = 0.0
    for c in counts:
        if c:
            pr = c / n
            total -= pr * math.log2(pr)
    return total


def graphics_banks(rom: Rom) -> set:
    """Banks that hold uncompressed graphics rather than code.

    Tile data decodes as perfectly valid SM83 instructions, so recursive
    descent that wanders into a graphics bank will disassemble the whole thing
    and emit tens of thousands of lines of nonsense per bank. Nothing in the
    instruction stream reveals the mistake, so the banks are identified up
    front and left alone.

    Two signals, both required. Real code calls things: it is dense with the
    call, jump and return opcodes, while tile data contains them only by
    coincidence. Graphics also sit in a middle band of entropy, below packed
    data and above padding.
    """
    out = set()
    for bank in range(rom.bank_count):
        data = rom.bank(bank)
        if not data:
            continue

        e = _entropy(data)
        if e < 1.0:
            continue                      # padding: harmless, discovery stops

        # Density of the opcodes that structure real code.
        structural = sum(data.count(op) for op in (
            0xC3,   # jp
            0xCD,   # call
            0xC9,   # ret
            0x18,   # jr
            0x20, 0x28, 0x30, 0x38,       # conditional jr
        ))
        density = structural / len(data)

        # Code is typically several percent structural opcodes. Tile data
        # lands far below that, and its entropy stays in the graphics band.
        if density < 0.012 and 1.0 <= e <= 7.0:
            out.add(bank)
    return out


def discover(rom: Rom, extra_entries=(), skip_graphics=True) -> Program:
    """Walk every statically reachable instruction in `rom`."""
    prog = Program(rom=rom)
    prog.graphics_banks = graphics_banks(rom) if skip_graphics else set()

    # Seed with the vectors, then any caller-supplied addresses (a symbol file,
    # or targets recovered from a jump table by hand).
    seeds = [(0, a) for a in RST_VECTORS + INTERRUPT_VECTORS + [ENTRY_POINT]]
    seeds += [e for e in extra_entries if e[0] not in prog.graphics_banks]
    # The header's entry point is almost always a jump into the real start.
    prog.entry_points.update(seeds)

    worklist = list(seeds)
    seen = set()

    while worklist:
        bank, addr = worklist.pop()
        # Addresses below 0x4000 always live in the fixed bank, whichever bank
        # was mapped when we got here. Normalising now keeps the dictionary key
        # and Block.bank in agreement; when they disagreed, the emitter placed
        # a block in one bank's function while judging its jumps against
        # another, and emitted a goto to a label in a different function.
        if addr < BANK_SIZE:
            bank = 0
        if (bank, addr) in seen or not _in_rom(addr):
            continue
        seen.add((bank, addr))

        if bank in prog.graphics_banks:
            continue

        block = _trace_block(rom, prog, bank, addr)
        if block is None:
            continue
        prog.blocks[(bank, addr)] = block
        for succ in block.successors:
            if succ not in seen:
                worklist.append(succ)

    return prog


def _trace_block(rom: Rom, prog: Program, bank: int, addr: int):
    """Decode forward from `addr` until control flow leaves the block."""
    data, base = rom.window(bank)
    if not (base <= addr < base + BANK_SIZE):
        # Address is not in the window this bank maps; bank 0 is always at 0x0000.
        if addr < BANK_SIZE:
            data, base, bank = rom.bank(0), 0x0000, 0
        else:
            return None

    block = Block(bank=bank, start=addr)
    pc = addr
    # Tracks the last immediate loaded into A, so a following write to the
    # bank register tells us which bank the next call lands in.
    pending_a = None

    while True:
        offset = pc - base
        if offset < 0 or offset >= len(data):
            break

        insn = sm83.decode(data, offset, pc, bank)
        block.insns.append(insn)
        flow = insn.op.flow

        # --- bank tracking -------------------------------------------------
        if insn.op.mnem == "ld_a_n8":
            pending_a = insn.value
        elif insn.op.mnem == "ld_a16_a" and BANK_SELECT_LO <= insn.value <= BANK_SELECT_HI:
            if pending_a is not None:
                prog.bank_switches.append((bank, pc, pending_a))
                bank = pending_a or 1
                pending_a = None
        elif insn.op.dst == "a" or insn.op.mnem.startswith(("pop_", "ld_a_")):
            pending_a = None

        # --- control flow --------------------------------------------------
        if flow == sm83.NEXT:
            pc = insn.end
            continue

        block.terminator = flow

        if flow in (sm83.JUMP, sm83.CJUMP):
            if insn.target >= 0 and _in_rom(insn.target):
                block.successors.add((_bank_for(insn.target, bank), insn.target))
            if flow == sm83.CJUMP:
                block.successors.add((bank, insn.end))
        elif flow in (sm83.CALL, sm83.CCALL):
            if insn.target >= 0 and _in_rom(insn.target):
                tgt = (_bank_for(insn.target, bank), insn.target)
                block.successors.add(tgt)
                prog.call_targets.add(tgt)
            # A call returns, so the instruction after it is still reachable.
            block.successors.add((bank, insn.end))
        elif flow == sm83.RST:
            prog.call_targets.add((0, insn.target))
            block.successors.add((0, insn.target))
            block.successors.add((bank, insn.end))
        elif flow == sm83.CRET:
            block.successors.add((bank, insn.end))
        elif flow == sm83.IJUMP:
            prog.indirect_sites.append((bank, pc))
        elif flow == sm83.HALT:
            # halt resumes on interrupt; execution continues after it.
            block.successors.add((bank, insn.end))
        # RET, RETI, STOP and ILLEGAL end the block with no static successor.
        break

    return block


def _bank_for(addr: int, current: int) -> int:
    """Bank 0 is fixed at 0x0000-0x3FFF; the switchable window is 0x4000-0x7FFF."""
    return 0 if addr < BANK_SIZE else current


def report(prog: Program) -> str:
    rom = prog.rom
    cov = prog.coverage
    total_code = sum(cov.values())
    total_rom = len(rom.data)
    lines = [
        f"  blocks found   {len(prog.blocks)}",
        f"  call targets   {len(prog.call_targets)}",
        f"  code bytes     {total_code} of {total_rom} "
        f"({100.0 * total_code / total_rom:.1f}% of ROM)",
        f"  bank switches  {len(prog.bank_switches)} detected statically",
        f"  indirect jumps {len(prog.indirect_sites)} unresolved (jp hl)",
        f"  graphics banks {len(prog.graphics_banks)} excluded as data",
    ]
    cold = [b for b in range(rom.bank_count) if cov.get(b, 0) == 0]
    if cold:
        preview = ", ".join(str(b) for b in cold[:12])
        more = f" (+{len(cold) - 12} more)" if len(cold) > 12 else ""
        lines.append(f"  banks with no reachable code: {preview}{more}")
    return "\n".join(lines)
