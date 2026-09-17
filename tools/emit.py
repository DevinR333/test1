"""C emitter: turns discovered SM83 blocks into native C.

Shape of the output: one C function per ROM bank, entered by address. Jumps
inside a bank become plain `goto`, which the host compiler turns into direct
branches - that is where the interpreter dispatch loop disappears. Calls and
anything whose target is not statically known go through `gb_dispatch`, which
falls back to the interpreter for code the discovery pass never reached
(jump tables, code copied into HRAM, data-driven branches).
"""

import sm83

R8_FIELD = {"a": "A", "b": "B", "c": "C", "d": "D", "e": "E", "h": "H", "l": "L"}
ALU_CALL = {"add": "alu_add", "adc": "alu_adc", "sub": "alu_sub",
            "sbc": "alu_sbc", "and": "alu_and", "xor": "alu_xor",
            "or": "alu_or", "cp": "alu_cp"}
CB_CALL = {"rlc": "alu_rlc", "rrc": "alu_rrc", "rl": "alu_rl", "rr": "alu_rr",
           "sla": "alu_sla", "sra": "alu_sra", "swap": "alu_swap", "srl": "alu_srl"}
COND_EXPR = {"nz": "!FLAG_Z", "z": "FLAG_Z", "nc": "!FLAG_C", "c": "FLAG_C"}

# Emitted at every loop back-edge. Recompiled code does not leave the bank
# function while looping, so without the sync the hardware never advances and
# a game waiting on a register spins forever. The stop check is what lets the
# frontend halt a running game at all: nothing else in a loop ever unwinds.
# gb_sync returns immediately when no cycles have elapsed, so a tight loop
# pays one comparison.
GB_LOOP_SYNC = "gb_sync(gb); if (gb->stopped) return;"

# Flow classes whose entire effect is the branch itself.
CONTROL_ONLY = frozenset({
    sm83.JUMP, sm83.CJUMP, sm83.CALL, sm83.CCALL, sm83.RST,
    sm83.RET, sm83.CRET, sm83.RETI, sm83.IJUMP,
})


def _label(addr: int) -> str:
    return f"L_{addr:04X}"


def _get8(r: str) -> str:
    """Read an r8 operand as a C expression."""
    return "gb_read(gb, HL)" if r == "(hl)" else R8_FIELD[r]


def _set8(r: str, expr: str) -> str:
    """Write an r8 operand as a C statement."""
    return f"gb_write(gb, HL, {expr});" if r == "(hl)" else f"{R8_FIELD[r]} = {expr};"


def _set16(r: str, expr: str) -> str:
    """Write an r16 operand as a C statement."""
    r = r.lower()
    return f"SP = {expr};" if r == "sp" else f"gb_set_{r}(gb, {expr});"


class Emitter:
    def __init__(self, prog):
        self.prog = prog
        self.unhandled = {}     # mnemonic -> count, for the coverage report

    # -- per-instruction ----------------------------------------------------
    def instruction(self, insn) -> list:
        """C statements implementing one instruction. Empty list = unhandled."""
        op, m, v = insn.op, insn.op.mnem, insn.value

        # Pure control flow carries no body: terminator() emits it.
        if op.flow in CONTROL_ONLY:
            return ["/* control flow emitted below */"]

        if m == "nop":
            return ["/* nop */"]
        if m == "di":
            return ["gb->ime = 0;"]
        if m == "ei":
            return ["gb->ime_pending = 1;"]
        if m == "halt":
            return ["gb_halt(gb);"]
        if m == "stop":
            return ["gb_stop(gb);"]
        if m == "prefix_cb":
            return []

        # 8-bit loads: ld r, r' and ld r, n8
        if m.startswith("ld_") and op.dst in R8_FIELD or m.endswith("_n8") and op.dst:
            if op.imm == sm83.IMM_N8 and m.startswith("ld_"):
                return [_set8(op.dst, f"0x{v:02X}")]
            if op.src in R8_FIELD or op.src == "(hl)":
                return [_set8(op.dst, _get8(op.src))]

        if m.startswith("ld_") and op.dst == "(hl)" and op.src in R8_FIELD:
            return [_set8("(hl)", _get8(op.src))]

        # 16-bit immediate loads
        if op.imm == sm83.IMM_N16 and m.startswith("ld_"):
            return [_set16(op.dst, f"0x{v:04X}")]

        # Pointer loads/stores, including the post-increment/decrement forms
        for ptr, step in (("bc", 0), ("de", 0), ("hl+", 1), ("hl-", -1)):
            reg = ptr[:2].upper()
            if m == f"ld_{ptr}_a":
                out = [f"gb_write(gb, {reg}, A);"]
                if step:
                    out.append(_set16(reg, f"({reg} {'+' if step > 0 else '-'} 1) & 0xFFFF"))
                return out
            if m == f"ld_a_{ptr}":
                out = [f"A = gb_read(gb, {reg});"]
                if step:
                    out.append(_set16(reg, f"({reg} {'+' if step > 0 else '-'} 1) & 0xFFFF"))
                return out

        # High page (0xFF00) IO
        if m == "ldh_a8_a":
            return [f"gb_write(gb, 0x{0xFF00 + v:04X}, A);"]
        if m == "ldh_a_a8":
            return [f"A = gb_read(gb, 0x{0xFF00 + v:04X});"]
        if m == "ld_ff00c_a":
            return ["gb_write(gb, 0xFF00 + C, A);"]
        if m == "ld_a_ff00c":
            return ["A = gb_read(gb, 0xFF00 + C);"]
        if m == "ld_a16_a":
            return [f"gb_write(gb, 0x{v:04X}, A);"]
        if m == "ld_a_a16":
            return [f"A = gb_read(gb, 0x{v:04X});"]
        if m == "ld_a16_sp":
            return [f"gb_write16(gb, 0x{v:04X}, SP);"]

        # 8-bit inc/dec
        if m.startswith("inc_") and op.dst in ("a", "b", "c", "d", "e", "h", "l", "(hl)"):
            return [_set8(op.dst, f"alu_inc(gb, {_get8(op.dst)})")]
        if m.startswith("dec_") and op.dst in ("a", "b", "c", "d", "e", "h", "l", "(hl)"):
            return [_set8(op.dst, f"alu_dec(gb, {_get8(op.dst)})")]

        # 16-bit inc/dec/add
        if m.startswith("inc_") and op.dst in ("bc", "de", "hl", "sp"):
            return [_set16(op.dst, f"({op.dst.upper()} + 1) & 0xFFFF")]
        if m.startswith("dec_") and op.dst in ("bc", "de", "hl", "sp"):
            return [_set16(op.dst, f"({op.dst.upper()} - 1) & 0xFFFF")]
        if m.startswith("add_hl_"):
            return [_set16("hl", f"alu_add16(gb, HL, {op.src.upper()})")]
        if m == "add_sp_e8":
            return [f"SP = alu_add_sp(gb, {v});"]
        if m == "ld_hl_sp_e8":
            return [_set16("hl", f"alu_add_sp(gb, {v})")]
        if m == "ld_sp_hl":
            return ["SP = HL;"]

        # 8-bit ALU, register and immediate forms
        for name, fn in ALU_CALL.items():
            if m == f"{name}_a_n8":
                return [f"{fn}(gb, 0x{v:02X});"]
            if m.startswith(f"{name}_a_") and op.src:
                return [f"{fn}(gb, {_get8(op.src)});"]

        for m2, fn in (("rlca", "alu_rlca"), ("rrca", "alu_rrca"),
                       ("rla", "alu_rla"), ("rra", "alu_rra"),
                       ("daa", "alu_daa"), ("cpl", "alu_cpl"),
                       ("scf", "alu_scf"), ("ccf", "alu_ccf")):
            if m == m2:
                return [f"{fn}(gb);"]

        # Stack
        if m.startswith("push_"):
            return [f"gb_push(gb, {op.src.upper()});"]
        if m.startswith("pop_"):
            reg = op.dst.upper()
            if op.dst == "af":
                return ["gb_pop_af(gb);"]
            return [_set16(op.dst, "gb_pop(gb)")]

        # CB-prefixed shifts, rotates and bit operations
        parts = m.split("_")
        if parts[0] in CB_CALL:
            r = "_".join(parts[1:])
            return [_set8(r, f"{CB_CALL[parts[0]]}(gb, {_get8(r)})")]
        if parts[0] == "bit":
            return [f"alu_bit(gb, {_get8('_'.join(parts[2:]))}, {parts[1]});"]
        if parts[0] in ("res", "set"):
            r, b = "_".join(parts[2:]), int(parts[1])
            mask = (1 << b)
            expr = (f"{_get8(r)} & 0x{0xFF ^ mask:02X}" if parts[0] == "res"
                    else f"{_get8(r)} | 0x{mask:02X}")
            return [_set8(r, expr)]

        if op.flow == sm83.ILLEGAL:
            return [f"gb_illegal(gb, 0x{insn.raw[0]:02X});"]

        self.unhandled[m] = self.unhandled.get(m, 0) + 1
        return []

    # -- per-block ----------------------------------------------------------
    def block(self, blk) -> list:
        out = [f"{_label(blk.start)}:;"]
        for insn in blk.insns:
            op = insn.op
            if op.mnem == "prefix_cb":
                continue
            body = self.instruction(insn)
            if not body:
                # Nothing emitted: hand this one instruction to the interpreter
                # rather than emit wrong code.
                body = [f"gb_interp(gb, 0x{insn.addr:04X}); /* {op.mnem} */"]
            comment = insn.raw.hex()
            out.append(f"    /* {insn.addr:04X}: {comment:<6} {op.mnem} */")
            out += [f"    {line}" for line in body]
            out.append(f"    gb->cycles += {op.cycles};")
            if op.flow != sm83.NEXT:
                out += [f"    {line}" for line in self.terminator(insn, blk)]
        return out

    def terminator(self, insn, blk) -> list:
        op, tgt = insn.op, insn.target
        local = tgt >= 0 and (blk.bank, tgt) in self.prog.blocks and \
            _same_window(tgt, blk.bank)

        # A target at or before this instruction closes a loop.
        backward = tgt >= 0 and tgt <= insn.addr

        if op.flow == sm83.JUMP:
            if local:
                return ([GB_LOOP_SYNC, f"goto {_label(tgt)};"] if backward
                        else [f"goto {_label(tgt)};"])
            return [f"gb_dispatch(gb, {blk.bank}, 0x{tgt:04X}); return;"]
        if op.flow == sm83.CJUMP:
            sync = (GB_LOOP_SYNC + " ") if backward else ""
            inner = (f"{sync}goto {_label(tgt)};" if local
                     else f"{{ gb_dispatch(gb, {blk.bank}, 0x{tgt:04X}); return; }}")
            return [f"if ({COND_EXPR[op.cond]}) {{ gb->cycles += "
                    f"{op.cycles_taken - op.cycles}; {inner} }}"]
        if op.flow == sm83.CALL:
            return [f"gb_call(gb, {blk.bank}, 0x{tgt:04X}, 0x{insn.end:04X});"]
        if op.flow == sm83.CCALL:
            return [f"if ({COND_EXPR[op.cond]}) {{ gb->cycles += "
                    f"{op.cycles_taken - op.cycles}; "
                    f"gb_call(gb, {blk.bank}, 0x{tgt:04X}, 0x{insn.end:04X}); }}"]
        if op.flow == sm83.RST:
            return [f"gb_call(gb, 0, 0x{op.bit:04X}, 0x{insn.end:04X});"]
        if op.flow == sm83.RET:
            return ["return;"]
        if op.flow == sm83.CRET:
            return [f"if ({COND_EXPR[op.cond]}) {{ gb->cycles += "
                    f"{op.cycles_taken - op.cycles}; return; }}"]
        if op.flow == sm83.RETI:
            return ["gb->ime = 1; return;"]
        if op.flow == sm83.IJUMP:
            # Target is in HL and only known now. The dispatcher will find a
            # recompiled entry or fall back to interpreting.
            return [f"gb_dispatch(gb, {blk.bank}, HL); return;"]
        if op.flow in (sm83.HALT, sm83.STOP):
            return []
        return ["return;"]

    # -- whole program ------------------------------------------------------
    def bank_function(self, bank: int) -> str:
        blocks = sorted((a, b) for (bk, a), b in self.prog.blocks.items() if bk == bank)
        if not blocks:
            return ""
        lines = [
            f"/* ROM bank {bank} - {len(blocks)} recompiled blocks */",
            f"void gb_bank_{bank:02X}(gb_t *gb, uint16_t entry)",
            "{",
            "    switch (entry) {",
        ]
        for addr, _ in blocks:
            lines.append(f"    case 0x{addr:04X}: goto {_label(addr)};")
        lines += [
            "    default:",
            f"        gb_no_entry(gb, {bank}, entry);",
            "        return;",
            "    }",
            "",
        ]
        for _, blk in blocks:
            lines += self.block(blk)
            lines.append("")
        lines.append("}")
        return "\n".join(lines)

    def entry_table(self) -> str:
        """Maps (bank, addr) pairs to their recompiled bank function."""
        banks = sorted({bk for bk, _ in self.prog.blocks})
        lines = ["/* Generated dispatch table. */",
                 "const gb_bank_fn gb_bank_table[GB_MAX_BANKS] = {"]
        for b in banks:
            lines.append(f"    [{b}] = gb_bank_{b:02X},")
        lines += ["};", ""]
        lines.append("const uint16_t gb_entry_count = "
                     f"{len(self.prog.blocks)};")
        return "\n".join(lines)


def _same_window(addr: int, bank: int) -> bool:
    """A goto is only valid if the target lives in the same emitted function."""
    return (addr < 0x4000) == (bank == 0)


def generate(prog) -> dict:
    """Returns {filename: contents} for the whole recompiled program."""
    em = Emitter(prog)
    files = {}
    banks = sorted({bk for bk, _ in prog.blocks})

    for b in banks:
        body = em.bank_function(b)
        if body:
            files[f"bank_{b:02X}.c"] = f'#include "gb.h"\n\n{body}\n'

    decls = "\n".join(f"void gb_bank_{b:02X}(gb_t *gb, uint16_t entry);" for b in banks)
    files["banks.h"] = (
        "/* Generated - do not edit. */\n#ifndef GB_BANKS_H\n#define GB_BANKS_H\n"
        f'#include "gb.h"\n\n{decls}\n\n#endif\n')
    files["dispatch.c"] = f'#include "gb.h"\n#include "banks.h"\n\n{em.entry_table()}\n'
    return files, em.unhandled
