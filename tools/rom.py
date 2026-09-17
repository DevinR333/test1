"""Cartridge header parsing and ROM bank mapping."""

from dataclasses import dataclass, field

BANK_SIZE = 0x4000

# Only the mapper families that matter for bank-aware code discovery.
CARTRIDGE_TYPES = {
    0x00: ("rom_only", "none"), 0x01: ("mbc1", "mbc1"),
    0x02: ("mbc1_ram", "mbc1"), 0x03: ("mbc1_ram_battery", "mbc1"),
    0x05: ("mbc2", "mbc2"), 0x06: ("mbc2_battery", "mbc2"),
    0x0F: ("mbc3_timer_battery", "mbc3"), 0x10: ("mbc3_timer_ram_battery", "mbc3"),
    0x11: ("mbc3", "mbc3"), 0x12: ("mbc3_ram", "mbc3"),
    0x13: ("mbc3_ram_battery", "mbc3"),
    0x19: ("mbc5", "mbc5"), 0x1A: ("mbc5_ram", "mbc5"),
    0x1B: ("mbc5_ram_battery", "mbc5"), 0x1C: ("mbc5_rumble", "mbc5"),
    0x1D: ("mbc5_rumble_ram", "mbc5"), 0x1E: ("mbc5_rumble_ram_battery", "mbc5"),
}

RAM_SIZES = {0x00: 0, 0x01: 0x800, 0x02: 0x2000, 0x03: 0x8000,
             0x04: 0x20000, 0x05: 0x10000}


@dataclass
class Rom:
    data: bytes
    title: str = ""
    cgb: bool = False
    cart_type: int = 0
    mapper: str = "none"
    rom_banks: int = 2
    ram_size: int = 0
    header_checksum_ok: bool = False
    global_checksum_ok: bool = False

    @property
    def bank_count(self) -> int:
        return max(2, len(self.data) // BANK_SIZE)

    def bank(self, n: int) -> bytes:
        """Raw bytes of ROM bank `n`."""
        off = (n % self.bank_count) * BANK_SIZE
        return self.data[off:off + BANK_SIZE]

    def read(self, bank: int, addr: int) -> int:
        """Read a byte as the CPU would see it with `bank` mapped at 0x4000."""
        if addr < BANK_SIZE:
            return self.data[addr]
        off = (bank % self.bank_count) * BANK_SIZE + (addr - BANK_SIZE)
        return self.data[off] if off < len(self.data) else 0xFF

    def window(self, bank: int) -> tuple:
        """(bytes, base_addr) for the bank as mapped into the CPU address space."""
        return (self.bank(0), 0x0000) if bank == 0 else (self.bank(bank), 0x4000)


def load(path: str) -> Rom:
    with open(path, "rb") as fh:
        data = fh.read()
    if len(data) < 0x150:
        raise ValueError(f"{path}: too small to be a Game Boy ROM ({len(data)} bytes)")
    if len(data) % BANK_SIZE:
        raise ValueError(f"{path}: size {len(data)} is not a multiple of 16 KiB")

    raw_title = data[0x134:0x143]
    title = raw_title.split(b"\x00")[0].decode("ascii", "replace").strip()
    cart_type = data[0x147]
    name, mapper = CARTRIDGE_TYPES.get(cart_type, (f"unknown_{cart_type:02x}", "unknown"))

    checksum = 0
    for b in data[0x134:0x14D]:
        checksum = (checksum - b - 1) & 0xFF

    total = (sum(data) - data[0x14E] - data[0x14F]) & 0xFFFF

    return Rom(
        data=data,
        title=title,
        cgb=data[0x143] in (0x80, 0xC0),
        cart_type=cart_type,
        mapper=mapper,
        rom_banks=2 << data[0x148] if data[0x148] <= 8 else len(data) // BANK_SIZE,
        ram_size=RAM_SIZES.get(data[0x149], 0),
        header_checksum_ok=checksum == data[0x14D],
        global_checksum_ok=total == (data[0x14E] << 8 | data[0x14F]),
    )


def describe(r: Rom) -> str:
    name, _ = CARTRIDGE_TYPES.get(r.cart_type, (f"unknown_{r.cart_type:02x}", ""))
    return "\n".join([
        f"  title          {r.title!r}",
        f"  target         {'Game Boy Color' if r.cgb else 'Game Boy (DMG)'}",
        f"  cartridge      {name} (0x{r.cart_type:02x}), mapper {r.mapper}",
        f"  rom            {len(r.data) // 1024} KiB, {r.bank_count} banks",
        f"  cartridge ram  {r.ram_size // 1024} KiB" if r.ram_size else "  cartridge ram  none",
        f"  checksums      header {'ok' if r.header_checksum_ok else 'BAD'}, "
        f"global {'ok' if r.global_checksum_ok else 'BAD'}",
    ])
