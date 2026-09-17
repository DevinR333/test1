"""Builds a ROM exercising both meanings of RET.

A normal call returns to its caller. Pushing an address and executing RET
instead performs a computed jump, which is how Game Boy code implements jump
tables. Both must work, and a recompiler that treats the second as an ordinary
return sends control back to the caller and loops forever.

Written by hand, so the test carries no commercial code.
"""
import sys

BANK_SIZE = 0x4000


def build():
    rom = bytearray(b"\x00" * (BANK_SIZE * 4))

    rom[0x100:0x104] = bytes([0x00, 0xC3, 0x50, 0x01])   # nop; jp $0150
    rom[0x134:0x13F] = b"JUMPTEST\x00\x00\x00"
    rom[0x143] = 0x80
    rom[0x147] = 0x1B
    rom[0x148] = 0x01
    rom[0x149] = 0x02

    main = [
        0xF3,                       # di
        0x31, 0xFE, 0xFF,           # ld sp, $FFFE
        0xCD, 0x00, 0x02,           # call $0200    -> ordinary call
        0x21, 0x00, 0x03,           # ld hl, $0300
        0xE5,                       # push hl
        0xC9,                       # ret           -> computed jump to $0300
        0x76,                       # halt          (must never be reached)
    ]
    rom[0x150:0x150 + len(main)] = bytes(main)

    # An ordinary subroutine: records that it ran, then returns normally.
    sub = bytes([
        0x3E, 0x11,                 # ld a, $11
        0xEA, 0x00, 0xC0,           # ld ($C000), a
        0xC9,                       # ret
    ])
    rom[0x200:0x200 + len(sub)] = sub

    # The computed-jump target: records that it ran, then spins.
    target = bytes([
        0x3E, 0x22,                 # ld a, $22
        0xEA, 0x01, 0xC0,           # ld ($C001), a
        0x18, 0xFE,                 # jr -2
    ])
    rom[0x300:0x300 + len(target)] = target

    chk = 0
    for b in rom[0x134:0x14D]:
        chk = (chk - b - 1) & 0xFF
    rom[0x14D] = chk
    total = (sum(rom) - rom[0x14E] - rom[0x14F]) & 0xFFFF
    rom[0x14E], rom[0x14F] = total >> 8, total & 0xFF
    return bytes(rom)


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "tests/jumptest.gb"
    with open(out, "wb") as fh:
        fh.write(build())
    print(f"wrote {out}")
