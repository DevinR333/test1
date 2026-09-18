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
#define R_KEY1 0x4D
#define R_HDMA1 0x51
#define R_HDMA5 0x55
#define R_SB    0x01
#define R_SC    0x02
#define R_NR52  0x26
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

/* Records how control reached somewhere unexpected. Kind: 1 a RET whose
 * popped address was not what the call pushed, 2 a dispatch with no compiled
 * block, 3 a stack fixup after a callee that did not return. */
static void note_event(gb_t *gb, uint8_t kind, uint16_t a, uint16_t b)
{
    uint32_t i = gb->ev_pos++ & 63;
    gb->ev_kind[i] = kind;
    gb->ev_a[i] = a;
    gb->ev_b[i] = b;
}

void gb_io_write(gb_t *gb, uint16_t addr, uint8_t value)
{
    int r = addr - 0xFF00;
    if (r >= 0 && r < 128 && gb->io_writes[r] < 0xFFFFFFFFu)
        gb->io_writes[r]++;

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

    case R_KEY1:
        /* Only the arming bit is writable; bit 7 reports the current speed and
         * the unused bits in between read as one. */
        if (gb->cgb)
            gb->io[R_KEY1] = (gb->double_speed ? 0x80 : 0x00) | 0x7E | (value & 0x01);
        return;

    case R_HDMA5: {
        if (!gb->cgb) { gb->io[R_HDMA5] = value; return; }

        /* Writing with bit 7 clear while an HBlank transfer runs cancels it. */
        if (gb->hdma_active && !(value & 0x80)) {
            gb->hdma_active = 0;
            gb->io[R_HDMA5] = 0x80 | ((gb->hdma_left - 1) & 0x7F);
            return;
        }

        /* Source ignores its low four bits; the destination is always in VRAM
         * and ignores everything above the bank. */
        gb->hdma_src = ((uint16_t)gb->io[R_HDMA1] << 8 | gb->io[R_HDMA1 + 1]) & 0xFFF0;
        gb->hdma_dst = (((uint16_t)gb->io[R_HDMA1 + 2] << 8 | gb->io[R_HDMA1 + 3]) & 0x1FF0)
                       + 0x8000;
        gb->hdma_left = (value & 0x7F) + 1;

        if (value & 0x80) {
            gb->hdma_active = 1;                  /* per-HBlank from here on */
            gb->io[R_HDMA5] = (value & 0x7F);     /* bit 7 clear: in progress */
        } else {
            /* General purpose: the whole transfer happens now. */
            while (gb->hdma_left) {
                for (int i = 0; i < 16; i++) {
                    gb_write(gb, gb->hdma_dst++, gb_read(gb, gb->hdma_src++));
                }
                gb->hdma_left--;
                gb->cycles += gb->double_speed ? 16 : 8;
            }
            gb->hdma_active = 0;
            gb->io[R_HDMA5] = 0xFF;               /* complete */
        }
        return;
    }

    /* Sound. No audio is produced yet, but the register file has to behave:
     * games read these back, and NR52's power bit clears the others. */
    case R_NR52:
        if (!(value & 0x80)) {
            /* Powering off clears every sound register except the length
             * counters, and wave RAM is untouched. */
            for (int i = 0x10; i <= 0x25; i++)
                gb->io[i] = 0;
            gb->io[R_NR52] = 0;
        } else {
            gb->io[R_NR52] = 0x80;
        }
        return;

    /* Serial. Nothing is connected, so a transfer started on the internal
     * clock shifts in ones and completes. Without this, a game that waits on
     * the serial interrupt waits forever. */
    case R_SC:
        gb->io[R_SC] = value;
        if ((value & 0x81) == 0x81) {
            gb->io[R_SB] = 0xFF;
            gb->io[R_SC] = value & 0x7F;      /* transfer finished */
            gb->io[R_IF] |= INT_SERIAL;
        }
        return;

    case R_JOYP:
        /* Only the two select bits are writable; the rest reads the buttons. */
        gb->io[R_JOYP] = (gb->io[R_JOYP] & 0x0F) | (value & 0x30);
        return;

    default:
        /* While the sound hardware is off, its registers ignore writes. Wave
         * RAM stays writable. */
        if (r >= 0x10 && r <= 0x25 && !(gb->io[R_NR52] & 0x80))
            return;
        gb->io[r] = value;
        return;
    }
}

/* The joypad register reports whichever half the game selected, active low. */
/* The joypad register reports whichever half the game selected, active low.
 *
 * It has to be computed when read, not cached: a game selects a row by writing
 * the register and reads it back immediately, several times. Refreshing it
 * once per frame meant every read returned a value computed for whichever row
 * happened to be selected at the last VBlank, so no button was ever seen. */
uint8_t gb_joypad_state(gb_t *gb)
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

    /* In double-speed mode the CPU and timers run twice as fast while the PPU
     * and audio keep their original rate, so the display advances by half the
     * CPU cycles counted. */
    gb_ppu_step(gb, (uint32_t)elapsed >> (gb->double_speed ? 1 : 0));

    if (gb->frame_ready) {
        gb->frame_ready = 0;
        gb->last_frame_cycle = gb->cycles;
        memset(gb->io_reads, 0, sizeof(gb->io_reads));
        memset(gb->io_writes, 0, sizeof(gb->io_writes));
        gb_on_frame(gb);
    }

    /* Ten seconds of frames with the display never coming on is a stall, not
     * a long boot. */
    if (gb->blank_frames > 600) {
        gb->stopped = 1;
        gb->stop_reason = GB_STOP_NO_PROGRESS;
        gb->stop_pc = gb->pc;
        gb->stop_bank = gb->rom_bank;
    }

    /* Roughly two seconds of cycles with no frame at all means the PPU is not
     * advancing, which is a different fault from the display being off. */
    if (gb->cycles - gb->last_frame_cycle > 2000000ULL) {
        gb->stopped = 1;
        gb->stop_reason = GB_STOP_NO_PROGRESS;
        gb->stop_pc = gb->pc;
        gb->stop_bank = gb->rom_bank;
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
    uint8_t pending = gb->io[R_IF] & gb->ie & 0x1F;
    if (!pending)
        return 0;

    gb->halted = 0;                          /* an armed interrupt wakes HALT */
    if (!gb->ime)
        return 0;

    for (int i = 0; i < 5; i++) {
        if (pending & (1 << i)) {
            gb->n_int++;
            gb->io[R_IF] &= ~(1 << i);
            gb->ime = 0;
            return 0x40 + i * 8;
        }
    }
    return 0;
}

/* --- control flow out of recompiled code ------------------------------ */

/* Control flow out of recompiled code.
 *
 * The game has one stack and the hardware follows it. An earlier design mapped
 * the game's CALL and RET onto the C call stack, which only works while every
 * path has a matching C frame - and several do not. A routine entered by a
 * jump, a tail call, or an interrupt has no frame to return to, so its RET had
 * nothing to match and was dispatched to whatever happened to be on the stack.
 *
 * So the C stack is no longer used for the game's control flow at all. Every
 * transfer records where to go next and returns, and one loop follows it,
 * exactly as the hardware follows the program counter. Calls cost no C stack,
 * and deep recursion in the game cannot overflow anything.
 */
void gb_call(gb_t *gb, uint16_t bank, uint16_t target, uint16_t ret_addr)
{
    gb_push(gb, ret_addr);
    gb_jump(gb, bank, target);
}

void gb_ret(gb_t *gb)
{
    gb_jump(gb, gb->rom_bank, gb_pop(gb));
}

void gb_jump(gb_t *gb, uint16_t bank, uint16_t target)
{
    gb->jump_bank = bank;
    gb->jump_pc = target;
    gb->jump_pending = 1;
}

/* Called at every loop back-edge.
 *
 * Advancing the hardware here is not enough on its own. A game's idle loop is
 * often two instructions - halt, then jump back - and that jump stays inside
 * the same compiled function, so control never returns to the dispatcher and
 * an enabled, pending interrupt is never taken. The game then waits forever
 * for work its own handler was supposed to do.
 *
 * Returning nonzero means leave the block: the dispatcher will service the
 * interrupt and resume at the address given. */
int gb_poll(gb_t *gb, uint16_t bank, uint16_t resume_pc)
{
    gb_sync(gb);
    if (gb->stopped)
        return 1;

    if (gb->ime && (gb->io[R_IF] & gb->ie & 0x1F)) {
        gb_jump(gb, bank, resume_pc);
        return 1;
    }
    return 0;
}

/* Runs recompiled code until it stops asking to go somewhere else. */
void gb_dispatch(gb_t *gb, uint16_t bank, uint16_t target)
{
    gb_jump(gb, bank, target);

    while (gb->jump_pending && !gb->stopped) {
        gb->jump_pending = 0;

        uint16_t pc = gb->jump_pc;
        uint16_t b = (pc < 0x4000) ? 0
                   : (gb->jump_bank ? gb->jump_bank : gb->rom_bank);

        gb_sync(gb);
        if (gb->stopped)
            return;

        /* Service interrupts here, between blocks. The game's own code keeps
         * asking to go somewhere else, so this loop runs for as long as the
         * game does and control rarely returns to the caller - checking out
         * there meant an enabled, pending interrupt was never taken at all. */
        uint16_t vector = take_interrupt(gb);
        if (vector) {
            gb_push(gb, pc);
            pc = vector;
            b = 0;
        }

        gb->pc = pc;
        if (b < GB_MAX_BANKS && gb_bank_table[b])
            gb_bank_table[b](gb, pc);
        else
            gb_no_entry(gb, b, pc);
    }
}

void gb_no_entry(gb_t *gb, uint16_t bank, uint16_t entry)
{
    (void)bank;
    /* Interpreting from here; nothing is pending until the interpreter says
     * so, or the two would bounce off each other. */
    gb->jump_pending = 0;
    /* Interpreting is correct here, but reaching an address with no block at
     * all in a bank that was recompiled usually means a bad dispatch rather
     * than a genuine jump table, so it is worth recording. */
    gb->no_entry_count++;
    note_event(gb, 2, entry, bank);

    /* Code the discovery pass never reached: a jump table the analysis could
     * not resolve, or a routine copied into RAM. Hand it to the interpreter
     * rather than guessing. */
    gb->pc = entry;
    gb->rom_bank = bank ? bank : gb->rom_bank;
    gb_interp(gb, entry);
}

void gb_halt(gb_t *gb)
{
    gb->n_halt++;
    gb->halted = 1;

    /* HALT suspends the processor until an interrupt is both enabled and
     * pending. It does not service it: that happens afterwards, in the normal
     * way, and only if IME is set.
     *
     * This used to call take_interrupt to decide when to stop waiting, which
     * consumed the interrupt - clearing its pending flag and IME - and threw
     * away the vector it returned, so the handler never ran. A game that waits
     * for its VBlank handler to do something therefore waited forever, having
     * had the interrupt silently taken from it. */
    while (gb->halted && !gb->stopped) {
        gb->cycles += 4;
        gb_sync(gb);

        if (gb->io[R_IF] & gb->ie & 0x1F) {
            gb->halted = 0;             /* resume; the caller services it */
            return;
        }
    }
}

void gb_stop(gb_t *gb)
{
    /* On Game Boy Color, STOP is how a game changes CPU speed. It sets bit 0
     * of KEY1 to arm the switch, then executes STOP; the hardware toggles
     * speed and carries on. Treating that as a halt kills the game one frame
     * into boot, since switching to double speed is among the first things a
     * CGB title does. */
    if (gb->cgb && (gb->io[R_KEY1] & 0x01)) {
        gb->double_speed = !gb->double_speed;
        gb->io[R_KEY1] = (gb->double_speed ? 0x80 : 0x00) | 0x7E;  /* bit 0 clears */
        /* The switch resets the divider. */
        gb->io[R_DIV] = 0;
        gb->div_cycles = 0;
        return;
    }

    /* A genuine STOP halts until a button is pressed. */
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
    gb->last_frame_cycle = 0;

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
    gb_jump(gb, 0, 0x0100);

    while (!gb->stopped) {
        if (!gb->jump_pending)
            gb_jump(gb, gb->rom_bank, gb->pc);

        uint16_t pc = gb->jump_pc, bank = gb->jump_bank;

        /* An interrupt pushes the address execution would otherwise have
         * continued at, not wherever the last block happened to start. */
        uint16_t vector = take_interrupt(gb);
        if (vector) {
            gb_push(gb, pc);
            pc = vector;
            bank = 0;
        }

        gb->jump_pending = 0;
        gb_dispatch(gb, bank, pc);
        gb_sync(gb);
    }
}

/* One 16-byte block per HBlank, which is all the hardware has time for. */
void gb_hdma_hblank(gb_t *gb)
{
    if (!gb->hdma_active || !gb->hdma_left)
        return;

    for (int i = 0; i < 16; i++)
        gb_write(gb, gb->hdma_dst++, gb_read(gb, gb->hdma_src++));

    gb->cycles += gb->double_speed ? 16 : 8;

    if (--gb->hdma_left == 0) {
        gb->hdma_active = 0;
        gb->io[R_HDMA5] = 0xFF;                   /* complete */
    } else {
        gb->io[R_HDMA5] = (gb->hdma_left - 1) & 0x7F;
    }
}

/* Called from gb_sync when the PPU completes a frame. */
void gb_on_frame(gb_t *gb)
{
    if (gb->joypad & ~gb->joypad_prev)
        gb->io[R_IF] |= INT_JOYPAD;
    gb->joypad_prev = gb->joypad;

    gb->frames++;
    if (gb->frame_cb)
        gb->frame_cb(gb, gb->frame_cb_user);
}

void gb_free(gb_t *gb)
{
    free(gb->cart_ram);
    gb->cart_ram = NULL;
}
