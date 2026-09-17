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

    @property
    def coverage(self) -> dict:
        """Bytes identified as code, per bank."""
        out = {}
        for (bank, _), blk in self.blocks.items():
            out[bank] = out.get(bank, 0) + sum(i.length for i in blk.insns)
        return out


def _in_rom(addr: int) -> bool:
    return addr < 0x8000


def discover(rom: Rom, extra_entries=()) -> Program:
    """Walk every statically reachable instruction in `rom`."""
    prog = Program(rom=rom)

    # Seed with the vectors, then any caller-supplied addresses (a symbol file,
    # or targets recovered from a jump table by hand).
    seeds = [(0, a) for a in RST_VECTORS + INTERRUPT_VECTORS + [ENTRY_POINT]]
    seeds += list(extra_entries)
    # The header's entry point is almost always a jump into the real start.
    prog.entry_points.update(seeds)

    worklist = list(seeds)
    seen = set()

    while worklist:
        bank, addr = worklist.pop()
        if (bank, addr) in seen or not _in_rom(addr):
            continue
        seen.add((bank, addr))

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
    ]
    cold = [b for b in range(rom.bank_count) if cov.get(b, 0) == 0]
    if cold:
        preview = ", ".join(str(b) for b in cold[:12])
        more = f" (+{len(cold) - 12} more)" if len(cold) > 12 else ""
        lines.append(f"  banks with no reachable code: {preview}{more}")
    return "\n".join(lines)
