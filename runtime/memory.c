/* Memory map and mapper (MBC) handling. */
#include <string.h>
#include "gb.h"

#define ROM_BANK_MASK(gb) (((gb)->rom_size / GB_BANK_SIZE) - 1)

uint8_t gb_read(gb_t *gb, uint16_t addr)
{
    switch (addr >> 12) {
    case 0x0: case 0x1: case 0x2: case 0x3:
        return gb->rom[addr];
    case 0x4: case 0x5: case 0x6: case 0x7: {
        size_t off = (size_t)(gb->rom_bank & ROM_BANK_MASK(gb)) * GB_BANK_SIZE
                   + (addr - 0x4000);
        return off < gb->rom_size ? gb->rom[off] : 0xFF;
    }
    case 0x8: case 0x9:
        return gb->vram[(gb->vram_bank ? 0x2000 : 0) + (addr - 0x8000)];
    case 0xA: case 0xB: {
        if (!gb->ram_enabled || !gb->cart_ram) return 0xFF;
        size_t off = (size_t)gb->ram_bank * 0x2000 + (addr - 0xA000);
        return off < gb->cart_ram_size ? gb->cart_ram[off] : 0xFF;
    }
    case 0xC:
        return gb->wram[addr - 0xC000];
    case 0xD: {
        uint8_t bank = gb->wram_bank ? gb->wram_bank : 1;   /* bank 0 reads as 1 */
        return gb->wram[bank * 0x1000 + (addr - 0xD000)];
    }
    case 0xE:
        return gb->wram[addr - 0xE000];                     /* echo of C000 */
    default:
        if (addr < 0xFE00) {                                /* echo of D000 */
            uint8_t bank = gb->wram_bank ? gb->wram_bank : 1;
            return gb->wram[bank * 0x1000 + (addr - 0xF000)];
        }
        if (addr < 0xFEA0) return gb->oam[addr - 0xFE00];
        if (addr < 0xFF00) return 0x00;                     /* unusable */
        if (addr < 0xFF80) return gb->io[addr - 0xFF00];
        if (addr < 0xFFFF) return gb->hram[addr - 0xFF80];
        return gb->io[0x7F];                                /* IE */
    }
}

/* Writes below 0x8000 do not reach ROM; they configure the mapper. */
static void mapper_write(gb_t *gb, uint16_t addr, uint8_t value)
{
    switch (gb->mapper) {
    case GB_MAPPER_MBC1:
        if (addr < 0x2000)      gb->ram_enabled = (value & 0x0F) == 0x0A;
        else if (addr < 0x4000) {
            uint8_t lo = value & 0x1F;
            /* Bank 0 is not selectable; the mapper reads it as bank 1. */
            gb->rom_bank = (gb->rom_bank & 0x60) | (lo ? lo : 1);
        } else if (addr < 0x6000) gb->ram_bank = value & 0x03;
        break;

    case GB_MAPPER_MBC2:
        if (addr < 0x4000) {
            if (addr & 0x0100) gb->rom_bank = (value & 0x0F) ? (value & 0x0F) : 1;
            else               gb->ram_enabled = (value & 0x0F) == 0x0A;
        }
        break;

    case GB_MAPPER_MBC3:
        if (addr < 0x2000)      gb->ram_enabled = (value & 0x0F) == 0x0A;
        else if (addr < 0x4000) gb->rom_bank = (value & 0x7F) ? (value & 0x7F) : 1;
        else if (addr < 0x6000) gb->ram_bank = value & 0x0F;
        break;

    case GB_MAPPER_MBC5:
        /* MBC5 is the one that can address bank 0, and splits the bank number
         * across two registers to reach 9 bits. */
        if (addr < 0x2000)      gb->ram_enabled = (value & 0x0F) == 0x0A;
        else if (addr < 0x3000) gb->rom_bank = (gb->rom_bank & 0x100) | value;
        else if (addr < 0x4000) gb->rom_bank = (gb->rom_bank & 0x0FF) | ((value & 1) << 8);
        else if (addr < 0x6000) gb->ram_bank = value & 0x0F;
        break;

    default:
        break;
    }
}

void gb_write(gb_t *gb, uint16_t addr, uint8_t value)
{
    switch (addr >> 12) {
    case 0x0: case 0x1: case 0x2: case 0x3:
    case 0x4: case 0x5: case 0x6: case 0x7:
        mapper_write(gb, addr, value);
        return;
    case 0x8: case 0x9:
        gb->vram[(gb->vram_bank ? 0x2000 : 0) + (addr - 0x8000)] = value;
        return;
    case 0xA: case 0xB: {
        if (!gb->ram_enabled || !gb->cart_ram) return;
        size_t off = (size_t)gb->ram_bank * 0x2000 + (addr - 0xA000);
        if (off < gb->cart_ram_size) gb->cart_ram[off] = value;
        return;
    }
    case 0xC: gb->wram[addr - 0xC000] = value; return;
    case 0xD: {
        uint8_t bank = gb->wram_bank ? gb->wram_bank : 1;
        gb->wram[bank * 0x1000 + (addr - 0xD000)] = value;
        return;
    }
    case 0xE: gb->wram[addr - 0xE000] = value; return;
    default:
        if (addr < 0xFE00) {
            uint8_t bank = gb->wram_bank ? gb->wram_bank : 1;
            gb->wram[bank * 0x1000 + (addr - 0xF000)] = value;
        } else if (addr < 0xFEA0) {
            gb->oam[addr - 0xFE00] = value;
        } else if (addr < 0xFF00) {
            /* unusable */
        } else if (addr < 0xFF80) {
            gb_io_write(gb, addr, value);
        } else if (addr < 0xFFFF) {
            gb->hram[addr - 0xFF80] = value;
        } else {
            gb->io[0x7F] = value;
        }
        return;
    }
}

uint16_t gb_read16(gb_t *gb, uint16_t addr)
{
    return gb_read(gb, addr) | ((uint16_t)gb_read(gb, addr + 1) << 8);
}

void gb_write16(gb_t *gb, uint16_t addr, uint16_t value)
{
    gb_write(gb, addr, value & 0xFF);
    gb_write(gb, addr + 1, value >> 8);
}

void gb_push(gb_t *gb, uint16_t value)
{
    gb->sp -= 2;
    gb_write16(gb, gb->sp, value);
}

uint16_t gb_pop(gb_t *gb)
{
    uint16_t v = gb_read16(gb, gb->sp);
    gb->sp += 2;
    return v;
}

void gb_pop_af(gb_t *gb)
{
    uint16_t v = gb_pop(gb);
    gb->a = v >> 8;
    gb->f = v & 0xF0;      /* the low nibble of F is not writable */
}
