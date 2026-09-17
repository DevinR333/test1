"""SM83 (Game Boy / Game Boy Color CPU) instruction tables and decoder.

The tables here are the ground truth for every later stage: code discovery
needs instruction lengths and control-flow behaviour, and the C emitter needs
operand structure. Cycle counts are M-cycles (1 M-cycle = 4 T-states).
"""

from dataclasses import dataclass

# Operand register orders as encoded in the opcode bit fields.
R8 = ["b", "c", "d", "e", "h", "l", "(hl)", "a"]
R16 = ["bc", "de", "hl", "sp"]
R16_STACK = ["bc", "de", "hl", "af"]
ALU = ["add", "adc", "sub", "sbc", "and", "xor", "or", "cp"]
CB_SHIFT = ["rlc", "rrc", "rl", "rr", "sla", "sra", "swap", "srl"]
CONDITIONS = ["nz", "z", "nc", "c"]

# Control-flow classes. Code discovery branches on these, so they matter more
# than the mnemonic itself.
NEXT = "next"          # falls through to the following instruction
JUMP = "jump"          # unconditional, static target
CJUMP = "cjump"        # conditional, static target, also falls through
IJUMP = "ijump"        # jp hl - target only known at run time
CALL = "call"
CCALL = "ccall"
RST = "rst"
RET = "ret"
CRET = "cret"
RETI = "reti"
HALT = "halt"
STOP = "stop"
ILLEGAL = "illegal"

# Operand encodings that consume bytes after the opcode.
IMM_NONE = ""
IMM_N8 = "n8"      # unsigned byte
IMM_N16 = "n16"    # little-endian word
IMM_E8 = "e8"      # signed byte, relative
IMM_A8 = "a8"      # unsigned byte, high-page address (0xFF00 + a8)
IMM_A16 = "a16"    # little-endian absolute address


@dataclass(frozen=True)
class Op:
    """One decoded opcode slot."""
    mnem: str
    length: int            # total instruction size in bytes, including opcode
    cycles: int            # M-cycles; for conditionals this is the not-taken cost
    cycles_taken: int      # M-cycles when a conditional branch is taken
    flow: str
    imm: str = IMM_NONE
    dst: str = ""
    src: str = ""
    cond: str = ""
    bit: int = -1

    @property
    def conditional(self) -> bool:
        return self.flow in (CJUMP, CCALL, CRET)


def _illegal(code: int) -> Op:
    return Op(f"illegal_{code:02x}", 1, 1, 1, ILLEGAL)


def _build_main() -> list:
    t = [None] * 256

    def put(code, mnem, length, cycles, flow, cycles_taken=None,
            imm=IMM_NONE, dst="", src="", cond="", bit=-1):
        t[code] = Op(mnem, length, cycles,
                     cycles if cycles_taken is None else cycles_taken,
                     flow, imm, dst, src, cond, bit)

    # 0x00-0x3F: misc, 16-bit loads/arith, 8-bit inc/dec and immediate loads.
    put(0x00, "nop", 1, 1, NEXT)
    put(0x08, "ld_a16_sp", 3, 5, NEXT, imm=IMM_A16)
    put(0x10, "stop", 2, 1, STOP)
    put(0x76, "halt", 1, 1, HALT)

    for i, rr in enumerate(R16):
        base = i << 4
        put(base + 0x01, f"ld_{rr}_n16", 3, 3, NEXT, imm=IMM_N16, dst=rr)
        put(base + 0x03, f"inc_{rr}", 1, 2, NEXT, dst=rr)
        put(base + 0x09, f"add_hl_{rr}", 1, 2, NEXT, dst="hl", src=rr)
        put(base + 0x0B, f"dec_{rr}", 1, 2, NEXT, dst=rr)

    # Indirect A loads/stores: (bc), (de), (hl+), (hl-)
    for i, ptr in enumerate(["bc", "de", "hl+", "hl-"]):
        base = i << 4
        put(base + 0x02, f"ld_{ptr}_a", 1, 2, NEXT, dst=ptr, src="a")
        put(base + 0x0A, f"ld_a_{ptr}", 1, 2, NEXT, dst="a", src=ptr)

    # 8-bit inc/dec and immediate load, one per r8 slot.
    for i, r in enumerate(R8):
        cost = 3 if r == "(hl)" else 1
        put(0x04 + i * 8, f"inc_{r}", 1, cost, NEXT, dst=r)
        put(0x05 + i * 8, f"dec_{r}", 1, cost, NEXT, dst=r)
        put(0x06 + i * 8, f"ld_{r}_n8", 2, 3 if r == "(hl)" else 2, NEXT,
            imm=IMM_N8, dst=r)

    for code, mnem in ((0x07, "rlca"), (0x0F, "rrca"), (0x17, "rla"),
                       (0x1F, "rra"), (0x27, "daa"), (0x2F, "cpl"),
                       (0x37, "scf"), (0x3F, "ccf")):
        put(code, mnem, 1, 1, NEXT)

    # Relative jumps. jr e8 is unconditional; the other four are conditional.
    put(0x18, "jr_e8", 2, 3, JUMP, imm=IMM_E8)
    for i, cc in enumerate(CONDITIONS):
        put(0x20 + i * 8, f"jr_{cc}_e8", 2, 2, CJUMP, cycles_taken=3,
            imm=IMM_E8, cond=cc)

    # 0x40-0x7F: ld r8, r8 (0x76 is HALT, already placed).
    for d, dst in enumerate(R8):
        for s, src in enumerate(R8):
            code = 0x40 + d * 8 + s
            if code == 0x76:
                continue
            cost = 2 if "(hl)" in (dst, src) else 1
            put(code, f"ld_{dst}_{src}", 1, cost, NEXT, dst=dst, src=src)

    # 0x80-0xBF: 8-bit ALU against A.
    for o, op in enumerate(ALU):
        for s, src in enumerate(R8):
            cost = 2 if src == "(hl)" else 1
            put(0x80 + o * 8 + s, f"{op}_a_{src}", 1, cost, NEXT,
                dst="a", src=src)

    # 0xC0-0xFF: stack, control flow, high-page IO, immediate ALU.
    for i, cc in enumerate(CONDITIONS):
        put(0xC0 + i * 8, f"ret_{cc}", 1, 2, CRET, cycles_taken=5, cond=cc)
        put(0xC2 + i * 8, f"jp_{cc}_a16", 3, 3, CJUMP, cycles_taken=4,
            imm=IMM_A16, cond=cc)
        put(0xC4 + i * 8, f"call_{cc}_a16", 3, 3, CCALL, cycles_taken=6,
            imm=IMM_A16, cond=cc)

    for i, rr in enumerate(R16_STACK):
        put(0xC1 + i * 16, f"pop_{rr}", 1, 3, NEXT, dst=rr)
        put(0xC5 + i * 16, f"push_{rr}", 1, 4, NEXT, src=rr)

    for i, op in enumerate(ALU):
        put(0xC6 + i * 8, f"{op}_a_n8", 2, 2, NEXT, imm=IMM_N8, dst="a")

    for i in range(8):
        put(0xC7 + i * 8, f"rst_{i * 8:02x}", 1, 4, RST, bit=i * 8)

    put(0xC3, "jp_a16", 3, 4, JUMP, imm=IMM_A16)
    put(0xC9, "ret", 1, 4, RET)
    put(0xCD, "call_a16", 3, 6, CALL, imm=IMM_A16)
    put(0xD9, "reti", 1, 4, RETI)
    put(0xE9, "jp_hl", 1, 1, IJUMP)

    put(0xE0, "ldh_a8_a", 2, 3, NEXT, imm=IMM_A8, src="a")
    put(0xF0, "ldh_a_a8", 2, 3, NEXT, imm=IMM_A8, dst="a")
    put(0xE2, "ld_ff00c_a", 1, 2, NEXT, src="a")
    put(0xF2, "ld_a_ff00c", 1, 2, NEXT, dst="a")
    put(0xEA, "ld_a16_a", 3, 4, NEXT, imm=IMM_A16, src="a")
    put(0xFA, "ld_a_a16", 3, 4, NEXT, imm=IMM_A16, dst="a")

    put(0xE8, "add_sp_e8", 2, 4, NEXT, imm=IMM_E8, dst="sp")
    put(0xF8, "ld_hl_sp_e8", 2, 3, NEXT, imm=IMM_E8, dst="hl")
    put(0xF9, "ld_sp_hl", 1, 2, NEXT, dst="sp", src="hl")
    put(0xF3, "di", 1, 1, NEXT)
    put(0xFB, "ei", 1, 1, NEXT)
    put(0xCB, "prefix_cb", 2, 1, NEXT)

    for code in range(256):
        if t[code] is None:
            t[code] = _illegal(code)
    return t


def _build_cb() -> list:
    t = [None] * 256
    for code in range(256):
        reg = R8[code & 7]
        hl = reg == "(hl)"
        block = code >> 6
        if block == 0:
            op = CB_SHIFT[(code >> 3) & 7]
            t[code] = Op(f"{op}_{reg}", 2, 4 if hl else 2, 4 if hl else 2,
                         NEXT, dst=reg)
        else:
            bit = (code >> 3) & 7
            name = {1: "bit", 2: "res", 3: "set"}[block]
            # BIT on (hl) reads but does not write back, so it is a cycle cheaper.
            cost = (3 if name == "bit" else 4) if hl else 2
            t[code] = Op(f"{name}_{bit}_{reg}", 2, cost, cost, NEXT,
                         dst=reg, bit=bit)
    return t


MAIN = _build_main()
CB = _build_cb()


@dataclass(frozen=True)
class Insn:
    """A decoded instruction at a concrete address."""
    addr: int              # address within the 16-bit CPU address space
    bank: int              # ROM bank the bytes were read from
    op: Op
    raw: bytes
    value: int = 0         # decoded immediate, if any
    target: int = -1       # resolved branch/call target, or -1 if not static

    @property
    def length(self) -> int:
        return self.op.length

    @property
    def end(self) -> int:
        return self.addr + self.op.length


def decode(data: bytes, offset: int, addr: int, bank: int = 0) -> Insn:
    """Decode one instruction from `data` at `offset`, presented as living at `addr`."""
    code = data[offset]
    if code == 0xCB:
        if offset + 1 >= len(data):
            return Insn(addr, bank, _illegal(code), data[offset:offset + 1])
        op = CB[data[offset + 1]]
        return Insn(addr, bank, op, data[offset:offset + 2])

    op = MAIN[code]
    raw = data[offset:offset + op.length]
    if len(raw) < op.length:
        return Insn(addr, bank, _illegal(code), raw)

    value, target = 0, -1
    if op.imm in (IMM_N8, IMM_A8):
        value = raw[1]
        if op.imm == IMM_A8:
            target = 0xFF00 + value
    elif op.imm == IMM_E8:
        value = raw[1] - 256 if raw[1] > 127 else raw[1]
        if op.flow in (JUMP, CJUMP):
            target = (addr + op.length + value) & 0xFFFF
    elif op.imm in (IMM_N16, IMM_A16):
        value = raw[1] | (raw[2] << 8)
        if op.flow in (JUMP, CJUMP, CALL, CCALL):
            target = value
    elif op.flow == RST:
        target = op.bit

    return Insn(addr, bank, op, raw, value, target)
