"""Builds a small synthetic ROM exercising the control-flow shapes discovery must handle.

Written by hand rather than taken from any commercial cartridge, so the test
suite stays self-contained.
"""
import sys

BANK_SIZE = 0x4000


def build(banks=4):
    rom = bytearray(b"\x00" * (BANK_SIZE * banks))

    # Entry point at 0x100: nop; jp 0x0150
    rom[0x100:0x104] = bytes([0x00, 0xC3, 0x50, 0x01])

    # Header: title, CGB flag, MBC5 + RAM, 64 KiB
    rom[0x134:0x13F] = b"FIXTURE\x00\x00\x00\x00"
    rom[0x143] = 0x80              # CGB compatible
    rom[0x147] = 0x1B              # MBC5 + RAM + battery
    rom[0x148] = 0x01              # 64 KiB => 4 banks
    rom[0x149] = 0x02              # 8 KiB cartridge RAM

    # Main routine at 0x150: set up, call a helper, switch bank, call into it.
    code = [
        0xF3,                       # di
        0x31, 0xFE, 0xFF,           # ld sp, 0xFFFE
        0xAF,                       # xor a, a
        0xCD, 0x00, 0x02,           # call 0x0200        (helper in bank 0)
        0x3E, 0x02,                 # ld a, 2
        0xEA, 0x00, 0x20,           # ld (0x2000), a     (select bank 2)
        0xCD, 0x00, 0x40,           # call 0x4000        (lands in bank 2)
        0x18, 0xFE,                 # jr -2              (self loop)
    ]
    rom[0x150:0x150 + len(code)] = bytes(code)

    # Helper at 0x200: a conditional branch, an indirect jump, and a return.
    helper = [
        0x06, 0x0A,                 # ld b, 10
        0x05,                       # dec b          <- 0x0202 loop head
        0x20, 0xFD,                 # jr nz, -3      (back to 0x0202)
        0x21, 0x10, 0x02,           # ld hl, 0x0210
        0xE9,                       # jp hl          (indirect - unresolvable)
    ]
    rom[0x200:0x200 + len(helper)] = bytes(helper)
    rom[0x210:0x212] = bytes([0xCB, 0x7C])   # bit 7, h
    rom[0x212] = 0xC9                         # ret

    # VBlank interrupt handler at 0x40.
    rom[0x40:0x43] = bytes([0xF5, 0xF1, 0xD9])   # push af; pop af; reti

    # Bank 2 routine, mapped at 0x4000 when selected.
    b2 = 2 * BANK_SIZE
    rom[b2:b2 + 5] = bytes([0x3E, 0x42, 0xEA, 0x00, 0xC0])  # ld a,0x42; ld (0xC000),a
    rom[b2 + 5] = 0xC9                                       # ret

    # Header checksum, then global checksum.
    chk = 0
    for b in rom[0x134:0x14D]:
        chk = (chk - b - 1) & 0xFF
    rom[0x14D] = chk
    total = (sum(rom) - rom[0x14E] - rom[0x14F]) & 0xFFFF
    rom[0x14E], rom[0x14F] = total >> 8, total & 0xFF
    return bytes(rom)


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "tests/fixture.gb"
    with open(out, "wb") as fh:
        fh.write(build())
    print(f"wrote {out}")
