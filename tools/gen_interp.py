#!/usr/bin/env python3
"""Generate the interpreter fallback from the same tables as the recompiler.

Recompiled code covers everything the discovery pass could reach statically.
What it cannot reach - jump tables, computed branches, routines copied into
HRAM - has to be interpreted instead.

Generating that interpreter from sm83.py rather than writing it by hand means
the two paths cannot drift: an operand or flag fixed in one is fixed in both.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import sm83

R8_FIELD = {"a": "gb->a", "b": "gb->b", "c": "gb->c", "d": "gb->d",
            "e": "gb->e", "h": "gb->h", "l": "gb->l"}
ALU_CALL = {"add": "alu_add", "adc": "alu_adc", "sub": "alu_sub",
            "sbc": "alu_sbc", "and": "alu_and", "xor": "alu_xor",
            "or": "alu_or", "cp": "alu_cp"}
CB_CALL = {"rlc": "alu_rlc", "rrc": "alu_rrc", "rl": "alu_rl", "rr": "alu_rr",
           "sla": "alu_sla", "sra": "alu_sra", "swap": "alu_swap", "srl": "alu_srl"}
COND = {"nz": "!(gb->f & FZ)", "z": "(gb->f & FZ)",
        "nc": "!(gb->f & FC)", "c": "(gb->f & FC)"}


def get8(r):
    return "gb_read(gb, HL)" if r == "(hl)" else R8_FIELD[r]


def set8(r, expr):
    return f"gb_write(gb, HL, {expr});" if r == "(hl)" else f"{R8_FIELD[r]} = {expr};"


def set16(r, expr):
    r = r.lower()
    return f"gb->sp = {expr};" if r == "sp" else f"gb_set_{r}(gb, {expr});"


def body(op):
    """C statements for one opcode, with immediates fetched at run time."""
    m = op.mnem

    if m == "nop":        return ["/* nop */"]
    if m == "di":         return ["gb->ime = 0;"]
    if m == "ei":         return ["gb->ime_pending = 1;"]
    if m == "halt":       return ["gb->halted = 1;"]
    if m == "stop":       return ["gb->stopped = 1;", "gb->pc++;"]

    # Immediates are read from the instruction stream as the PC advances.
    fetch = {
        sm83.IMM_N8:  "uint8_t n = gb_read(gb, gb->pc++);",
        sm83.IMM_A8:  "uint8_t n = gb_read(gb, gb->pc++);",
        sm83.IMM_E8:  "int8_t e = (int8_t)gb_read(gb, gb->pc++);",
        sm83.IMM_N16: "uint16_t nn = gb_read16(gb, gb->pc); gb->pc += 2;",
        sm83.IMM_A16: "uint16_t nn = gb_read16(gb, gb->pc); gb->pc += 2;",
    }.get(op.imm)
    pre = [fetch] if fetch else []

    # --- control flow ---
    if op.flow == sm83.JUMP and op.imm == sm83.IMM_E8:
        return pre + ["gb->pc += e;"]
    if op.flow == sm83.CJUMP and op.imm == sm83.IMM_E8:
        return pre + [f"if ({COND[op.cond]}) {{ gb->pc += e; return {op.cycles_taken}; }}"]
    if op.flow == sm83.JUMP:
        return pre + ["gb->pc = nn;"]
    if op.flow == sm83.CJUMP:
        return pre + [f"if ({COND[op.cond]}) {{ gb->pc = nn; return {op.cycles_taken}; }}"]
    if op.flow == sm83.IJUMP:
        return ["gb->pc = HL;"]
    if op.flow == sm83.CALL:
        return pre + ["gb_push(gb, gb->pc);", "gb->pc = nn;"]
    if op.flow == sm83.CCALL:
        return pre + [f"if ({COND[op.cond]}) {{ gb_push(gb, gb->pc); gb->pc = nn;"
                      f" return {op.cycles_taken}; }}"]
    if op.flow == sm83.RST:
        return ["gb_push(gb, gb->pc);", f"gb->pc = 0x{op.bit:02X};"]
    if op.flow == sm83.RET:
        return ["gb->pc = gb_pop(gb);"]
    if op.flow == sm83.CRET:
        return [f"if ({COND[op.cond]}) {{ gb->pc = gb_pop(gb); return {op.cycles_taken}; }}"]
    if op.flow == sm83.RETI:
        return ["gb->pc = gb_pop(gb);", "gb->ime = 1;"]
    if op.flow == sm83.ILLEGAL:
        return [f"gb_illegal(gb, 0x{int(m.split('_')[1], 16):02X});"]

    # --- loads ---
    if m.startswith("ld_") and op.imm == sm83.IMM_N8 and op.dst:
        return pre + [set8(op.dst, "n")]
    if m.startswith("ld_") and op.imm == sm83.IMM_N16:
        return pre + [set16(op.dst, "nn")]
    if m.startswith("ld_") and op.dst in R8_FIELD and op.src in list(R8_FIELD) + ["(hl)"]:
        return [set8(op.dst, get8(op.src))]
    if m.startswith("ld_") and op.dst == "(hl)" and op.src in R8_FIELD:
        return [set8("(hl)", get8(op.src))]

    for ptr, step in (("bc", 0), ("de", 0), ("hl+", 1), ("hl-", -1)):
        reg = ptr[:2].upper()
        sign = "+" if step > 0 else "-"
        if m == f"ld_{ptr}_a":
            out = [f"gb_write(gb, {reg}, gb->a);"]
            if step: out.append(set16(reg, f"({reg} {sign} 1) & 0xFFFF"))
            return out
        if m == f"ld_a_{ptr}":
            out = [f"gb->a = gb_read(gb, {reg});"]
            if step: out.append(set16(reg, f"({reg} {sign} 1) & 0xFFFF"))
            return out

    if m == "ldh_a8_a":   return pre + ["gb_write(gb, 0xFF00 + n, gb->a);"]
    if m == "ldh_a_a8":   return pre + ["gb->a = gb_read(gb, 0xFF00 + n);"]
    if m == "ld_ff00c_a": return ["gb_write(gb, 0xFF00 + gb->c, gb->a);"]
    if m == "ld_a_ff00c": return ["gb->a = gb_read(gb, 0xFF00 + gb->c);"]
    if m == "ld_a16_a":   return pre + ["gb_write(gb, nn, gb->a);"]
    if m == "ld_a_a16":   return pre + ["gb->a = gb_read(gb, nn);"]
    if m == "ld_a16_sp":  return pre + ["gb_write16(gb, nn, gb->sp);"]
    if m == "ld_sp_hl":   return ["gb->sp = HL;"]
    if m == "ld_hl_sp_e8":return pre + [set16("hl", "alu_add_sp(gb, e)")]
    if m == "add_sp_e8":  return pre + ["gb->sp = alu_add_sp(gb, e);"]

    # --- arithmetic ---
    if m.startswith("inc_") and op.dst in ("bc", "de", "hl", "sp"):
        return [set16(op.dst, f"({op.dst.upper()} + 1) & 0xFFFF")]
    if m.startswith("dec_") and op.dst in ("bc", "de", "hl", "sp"):
        return [set16(op.dst, f"({op.dst.upper()} - 1) & 0xFFFF")]
    if m.startswith("inc_"):
        return [set8(op.dst, f"alu_inc(gb, {get8(op.dst)})")]
    if m.startswith("dec_"):
        return [set8(op.dst, f"alu_dec(gb, {get8(op.dst)})")]
    if m.startswith("add_hl_"):
        return [set16("hl", f"alu_add16(gb, HL, {op.src.upper()})")]

    for name, fn in ALU_CALL.items():
        if m == f"{name}_a_n8":
            return pre + [f"{fn}(gb, n);"]
        if m.startswith(f"{name}_a_") and op.src:
            return [f"{fn}(gb, {get8(op.src)});"]

    for m2, fn in (("rlca","alu_rlca"),("rrca","alu_rrca"),("rla","alu_rla"),
                   ("rra","alu_rra"),("daa","alu_daa"),("cpl","alu_cpl"),
                   ("scf","alu_scf"),("ccf","alu_ccf")):
        if m == m2:
            return [f"{fn}(gb);"]

    if m.startswith("push_"): return [f"gb_push(gb, {op.src.upper()});"]
    if m.startswith("pop_"):
        return ["gb_pop_af(gb);"] if op.dst == "af" else [set16(op.dst, "gb_pop(gb)")]

    return None


def cb_body(op):
    parts = op.mnem.split("_")
    if parts[0] in CB_CALL:
        r = "_".join(parts[1:])
        return [set8(r, f"{CB_CALL[parts[0]]}(gb, {get8(r)})")]
    if parts[0] == "bit":
        return [f"alu_bit(gb, {get8('_'.join(parts[2:]))}, {parts[1]});"]
    if parts[0] in ("res", "set"):
        r, b = "_".join(parts[2:]), int(parts[1])
        mask = 1 << b
        expr = (f"{get8(r)} & 0x{0xFF ^ mask:02X}" if parts[0] == "res"
                else f"{get8(r)} | 0x{mask:02X}")
        return [set8(r, expr)]
    return None


def generate():
    out = ['/* Generated by tools/gen_interp.py - do not edit.',
           ' *',
           ' * Interpreter for code the static recompiler could not reach.',
           ' * Built from the same opcode tables as the emitter so the two',
           ' * cannot disagree about an operand or a flag.',
           ' */',
           '#include "gb.h"',
           '',
           'static uint32_t interp_cb(gb_t *gb)',
           '{',
           '    uint8_t op = gb_read(gb, gb->pc++);',
           '    switch (op) {']

    missing = []
    for code in range(256):
        op = sm83.CB[code]
        b = cb_body(op)
        if b is None:
            missing.append(("cb", code, op.mnem))
            continue
        out.append(f"    case 0x{code:02X}: {{ /* {op.mnem} */")
        out += [f"        {line}" for line in b]
        out.append(f"        return {op.cycles}; }}")
    out += ['    }', '    return 2;', '}', '',
            'uint32_t gb_interp_step(gb_t *gb)',
            '{',
            '    uint8_t op = gb_read(gb, gb->pc++);',
            '    switch (op) {',
            '    case 0xCB: return interp_cb(gb);']

    for code in range(256):
        if code == 0xCB:
            continue
        op = sm83.MAIN[code]
        b = body(op)
        if b is None:
            missing.append(("main", code, op.mnem))
            continue
        out.append(f"    case 0x{code:02X}: {{ /* {op.mnem} */")
        out += [f"        {line}" for line in b]
        out.append(f"        return {op.cycles}; }}")

    out += ['    }',
            '    return 1;',
            '}',
            '']
    return "\n".join(out), missing


def main():
    dest = sys.argv[1] if len(sys.argv) > 1 else "runtime/interp_gen.c"
    text, missing = generate()
    with open(dest, "w") as fh:
        fh.write(text)
    print(f"  wrote {dest} ({text.count(chr(10)) + 1} lines)")
    if missing:
        print(f"  {len(missing)} opcodes unhandled:")
        for kind, code, mnem in missing[:20]:
            print(f"    {kind} 0x{code:02X}  {mnem}")
        return 1
    print("  all 512 opcodes covered")
    return 0


if __name__ == "__main__":
    sys.exit(main())
