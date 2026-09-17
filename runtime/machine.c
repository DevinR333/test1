/* IO registers, timers, interrupts, and the machine's run loop.
 *
 * This is the half of the hardware the generated code talks to through
 * gb_read and gb_write, plus the entry points it calls when control flow
 * leaves a recompiled block.
 */
#include <stdlib.h>
#include <string.h>
#include "gb.h"
#include "banks.h"

#define R_JOYP 0x00
#define R_DIV  0x04
#define R_TIMA 0x05
#define R_TMA  0x06
#define R_TAC  0x07
#define R_IF   0x0F
#define R_LCDC 0x40
#define R_STAT 0x41
#define R_LY   0x44
#define R_DMA  0x46
#define R_VBK  0x4F
#define R_BCPS 0x68
#define R_BCPD 0x69
#define R_OCPS 0x6A
#define R_OCPD 0x6B
#define R_SVBK 0x70
#define R_IE   0x7F          /* stored at the end of the IO block */

#define INT_VBLANK 0x01
#define INT_STAT   0x02
#define INT_TIMER  0x04
#define INT_SERIAL 0x08
#define INT_JOYPAD 0x10

/* Joypad bits as the frontend sets them. */
enum { BTN_A, BTN_B, BTN_SELECT, BTN_START,
       BTN_RIGHT, BTN_LEFT, BTN_UP, BTN_DOWN };

/* TAC's low two bits pick the timer's divisor, in M-cycles per tick. */
static const uint16_t TIMER_PERIOD[4] = { 256, 4, 16, 64 };

void gb_io_write(gb_t *gb, uint16_t addr, uint8_t value)
{
    int r = addr - 0xFF00;

    switch (r) {
    case R_DIV:
        /* Any write resets the divider, whatever the value. */
        gb->io[R_DIV] = 0;
        gb->div_cycles = 0;
        return;

    case R_LY:
        return;                              /* read-only */

    case R_STAT:
        /* The low three bits are the PPU's; only the enable bits are writable. */
        gb->io[R_STAT] = (gb->io[R_STAT] & 0x07) | (value & 0x78);
        return;

    case R_DMA: {
        /* Copies 160 bytes into OAM. The hardware takes 160 M-cycles and
         * locks most of memory; games wait it out in HRAM, so doing it at
         * once is safe here. */
        uint16_t src = (uint16_t)value << 8;
        for (int i = 0; i < 0xA0; i++)
            gb->oam[i] = gb_read(gb, src + i);
        gb->io[R_DMA] = value;
        return;
    }

    case R_VBK:
        gb->vram_bank = gb->cgb ? (value & 1) : 0;
        gb->io[R_VBK] = value | 0xFE;
        return;

    case R_SVBK:
        gb->wram_bank = gb->cgb ? (value & 7) : 1;
        gb->io[R_SVBK] = value;
        return;

    /* CGB palette access: an index register with an auto-increment bit, and
     * a data register that reads and writes through it. */
    case R_BCPD:
        if (gb->cgb) {
            uint8_t spec = gb->io[R_BCPS];
            gb->bg_palette[spec & 0x3F] = value;
            if (spec & 0x80)
                gb->io[R_BCPS] = (spec & 0x80) | ((spec + 1) & 0x3F);
        }
        gb->io[R_BCPD] = value;
        return;

    case R_OCPD:
        if (gb->cgb) {
            uint8_t spec = gb->io[R_OCPS];
            gb->obj_palette[spec & 0x3F] = value;
            if (spec & 0x80)
                gb->io[R_OCPS] = (spec & 0x80) | ((spec + 1) & 0x3F);
        }
        gb->io[R_OCPD] = value;
        return;

    case R_JOYP:
        /* Only the two select bits are writable; the rest reads the buttons. */
        gb->io[R_JOYP] = (gb->io[R_JOYP] & 0x0F) | (value & 0x30);
        return;

    default:
        gb->io[r] = value;
        return;
    }
}

/* The joypad register reports whichever half the game selected, active low. */
static uint8_t read_joypad(gb_t *gb)
{
    uint8_t sel = gb->io[R_JOYP] & 0x30;
    uint8_t bits = 0x0F;
    if (!(sel & 0x10)) {                     /* direction keys selected */
        if (gb->joypad & (1 << BTN_RIGHT)) bits &= ~0x01;
        if (gb->joypad & (1 << BTN_LEFT))  bits &= ~0x02;
        if (gb->joypad & (1 << BTN_UP))    bits &= ~0x04;
        if (gb->joypad & (1 << BTN_DOWN))  bits &= ~0x08;
    }
    if (!(sel & 0x20)) {                     /* action buttons selected */
        if (gb->joypad & (1 << BTN_A))      bits &= ~0x01;
        if (gb->joypad & (1 << BTN_B))      bits &= ~0x02;
        if (gb->joypad & (1 << BTN_SELECT)) bits &= ~0x04;
        if (gb->joypad & (1 << BTN_START))  bits &= ~0x08;
    }
    return 0xC0 | sel | bits;
}

static void step_timers(gb_t *gb, uint32_t cycles)
{
    /* DIV counts M-cycles and exposes the top eight bits of a 16-bit counter. */
    gb->div_cycles += cycles;
    gb->io[R_DIV] = (gb->div_cycles >> 6) & 0xFF;

    if (!(gb->io[R_TAC] & 0x04))
        return;

    uint16_t period = TIMER_PERIOD[gb->io[R_TAC] & 3];
    gb->tima_cycles += cycles;
    while (gb->tima_cycles >= period) {
        gb->tima_cycles -= period;
        if (++gb->io[R_TIMA] == 0) {
            /* On overflow TIMA reloads from TMA and raises the interrupt. */
            gb->io[R_TIMA] = gb->io[R_TMA];
            gb->io[R_IF] |= INT_TIMER;
        }
    }
}

void gb_sync(gb_t *gb)
{
    uint64_t elapsed = gb->cycles - gb->last_sync;
    if (!elapsed)
        return;
    gb->last_sync = gb->cycles;

    step_timers(gb, (uint32_t)elapsed);
    gb_ppu_step(gb, (uint32_t)elapsed);

    if (gb->frame_ready) {
        gb->frame_ready = 0;
        gb_on_frame(gb);
    }

    /* EI takes effect after the instruction following it. */
    if (gb->ime_pending) {
        gb->ime_pending = 0;
        gb->ime = 1;
    }
}

/* Dispatch a pending interrupt, if one is enabled and armed. Returns the
 * vector taken, or zero. */
static uint16_t take_interrupt(gb_t *gb)
{
    uint8_t pending = gb->io[R_IF] & gb->io[R_IE] & 0x1F;
    if (!pending)
        return 0;

    gb->halted = 0;                          /* an armed interrupt wakes HALT */
    if (!gb->ime)
        return 0;

    for (int i = 0; i < 5; i++) {
        if (pending & (1 << i)) {
            gb->io[R_IF] &= ~(1 << i);
            gb->ime = 0;
            return 0x40 + i * 8;
        }
    }
    return 0;
}

/* --- control flow out of recompiled code ------------------------------ */

void gb_call(gb_t *gb, uint16_t bank, uint16_t target, uint16_t ret_addr)
{
    /* The return address goes on the game's own stack, because the game can
     * and does inspect and modify it. The native return path is the C stack. */
    gb_push(gb, ret_addr);
    gb->call_depth++;
    gb_dispatch(gb, bank, target);
    gb->call_depth--;
    gb->sp += 2;                             /* balance the pushed address */
}

void gb_dispatch(gb_t *gb, uint16_t bank, uint16_t target)
{
    gb_sync(gb);

    /* Bank 0 is fixed; anything above 0x4000 comes from the mapped bank. */
    uint16_t b = (target < 0x4000) ? 0 : (bank ? bank : gb->rom_bank);
    if (b < GB_MAX_BANKS && gb_bank_table[b]) {
        gb_bank_table[b](gb, target);
        return;
    }
    gb_no_entry(gb, b, target);
}

void gb_no_entry(gb_t *gb, uint16_t bank, uint16_t entry)
{
    /* Interpreting is correct here, but reaching an address with no block at
     * all in a bank that was recompiled usually means a bad dispatch rather
     * than a genuine jump table, so it is worth recording. */
    gb->no_entry_count++;

    /* Code the discovery pass never reached: a jump table the analysis could
     * not resolve, or a routine copied into RAM. Hand it to the interpreter
     * rather than guessing. */
    gb->pc = entry;
    gb->rom_bank = bank ? bank : gb->rom_bank;
    gb_interp(gb, entry);
}

void gb_halt(gb_t *gb)
{
    gb->halted = 1;
    /* Advance to the next interrupt rather than spinning. */
    while (gb->halted && !gb->frame_ready) {
        gb->cycles += 4;
        gb_sync(gb);
        take_interrupt(gb);
    }
}

void gb_stop(gb_t *gb)
{
    gb->stopped = 1;
    gb->stop_reason = GB_STOP_OPCODE;
    gb->stop_pc = gb->pc;
    gb->stop_bank = gb->rom_bank;
}

void gb_illegal(gb_t *gb, uint8_t opcode)
{
    /* The hardware locks up. Record it rather than pretending it is a NOP,
     * since reaching one means something upstream went wrong. */
    gb->illegal_opcode = opcode;
    gb->stopped = 1;
    gb->stop_reason = GB_STOP_ILLEGAL;
    gb->stop_pc = gb->pc;
    gb->stop_bank = gb->rom_bank;
}

/* --- lifecycle --------------------------------------------------------- */

int gb_init(gb_t *gb, const uint8_t *rom, size_t size)
{
    if (!rom || size < 0x150)
        return -1;

    memset(gb, 0, sizeof(*gb));
    gb->rom = rom;
    gb->rom_size = size;
    gb->cgb = (rom[0x143] == 0x80 || rom[0x143] == 0xC0);

    switch (rom[0x147]) {
    case 0x01: case 0x02: case 0x03: gb->mapper = GB_MAPPER_MBC1; break;
    case 0x05: case 0x06:            gb->mapper = GB_MAPPER_MBC2; break;
    case 0x0F: case 0x10: case 0x11:
    case 0x12: case 0x13:            gb->mapper = GB_MAPPER_MBC3; break;
    case 0x19: case 0x1A: case 0x1B:
    case 0x1C: case 0x1D: case 0x1E: gb->mapper = GB_MAPPER_MBC5; break;
    default:                         gb->mapper = GB_MAPPER_NONE; break;
    }

    static const size_t RAM_SIZES[] = { 0, 0x800, 0x2000, 0x8000, 0x20000, 0x10000 };
    gb->cart_ram_size = (rom[0x149] < 6) ? RAM_SIZES[rom[0x149]] : 0;
    if (gb->cart_ram_size) {
        gb->cart_ram = calloc(1, gb->cart_ram_size);
        if (!gb->cart_ram)
            return -1;
    }

    gb_reset(gb);
    return 0;
}

void gb_reset(gb_t *gb)
{
    gb->a = gb->cgb ? 0x11 : 0x01;
    gb->f = 0xB0;
    gb->b = 0x00; gb->c = 0x13;
    gb->d = 0x00; gb->e = 0xD8;
    gb->h = 0x01; gb->l = 0x4D;
    gb->sp = 0xFFFE;
    gb->pc = 0x0100;
    gb->rom_bank = 1;
    gb->wram_bank = 1;
    gb->ime = 0;
    gb->cycles = 0;
    gb->last_sync = 0;

    memset(gb->io, 0, sizeof(gb->io));
    gb->io[R_LCDC] = 0x91;
    gb->io[0x47] = 0xFC;                     /* BGP */
    gb->io[0x48] = 0xFF;                     /* OBP0 */
    gb->io[0x49] = 0xFF;                     /* OBP1 */
    gb->io[R_JOYP] = 0xCF;

    /* Until the game writes its own, make every CGB palette entry white so a
     * frame drawn before setup is legible rather than black on black. */
    memset(gb->bg_palette, 0xFF, sizeof(gb->bg_palette));
    memset(gb->obj_palette, 0xFF, sizeof(gb->obj_palette));

    gb_ppu_reset(gb);
}

/* Statically recompiled code does not return from the game's main loop, so
 * frames cannot be driven by calling in once per frame. The game runs
 * continuously and reports each completed frame through a callback, which the
 * frontend uses to present and to feed input back in. */
void gb_run(gb_t *gb)
{
    gb->pc = 0x0100;
    while (!gb->stopped) {
        uint16_t vector = take_interrupt(gb);
        if (vector)
            gb_call(gb, 0, vector, gb->pc);
        else
            gb_dispatch(gb, gb->rom_bank, gb->pc);
        gb_sync(gb);
    }
}

/* Called from gb_sync when the PPU completes a frame. */
void gb_on_frame(gb_t *gb)
{
    gb->io[R_JOYP] = read_joypad(gb);
    gb->frames++;
    if (gb->frame_cb)
        gb->frame_cb(gb, gb->frame_cb_user);
}

void gb_free(gb_t *gb)
{
    free(gb->cart_ram);
    gb->cart_ram = NULL;
}
