/* Runtime interface targeted by recompiled code.
 *
 * The recompiler turns the game's CPU instructions into native C, but the code
 * still talks to Game Boy hardware: it writes display registers, waits on
 * timers, reads the joypad. Those writes have to mean something, so the
 * hardware side lives here and is implemented per subsystem.
 */
#ifndef GB_H
#define GB_H

#include <stdint.h>
#include <stddef.h>

#define GB_MAX_BANKS   512      /* MBC5 ceiling: 8 MiB / 16 KiB */
#define GB_BANK_SIZE   0x4000
#define GB_SCREEN_W    160
#define GB_SCREEN_H    144

/* Flag bits in register F. */
#define FZ 0x80
#define FN 0x40
#define FH 0x20
#define FC 0x10

typedef struct gb_s gb_t;
typedef void (*gb_bank_fn)(gb_t *gb, uint16_t entry);

struct gb_s {
    /* CPU registers. Kept as separate bytes because recompiled code touches
     * the halves far more often than the pairs. */
    uint8_t  a, f, b, c, d, e, h, l;
    uint16_t sp, pc;

    uint8_t  ime;            /* interrupt master enable */
    uint8_t  ime_pending;    /* EI takes effect after the next instruction */
    uint8_t  halted;
    uint8_t  stopped;

    uint64_t cycles;         /* M-cycles since reset */
    uint64_t sync_deadline;  /* next point the hardware must catch up */

    /* Memory map. */
    const uint8_t *rom;
    size_t   rom_size;
    uint16_t rom_bank;       /* bank mapped at 0x4000-0x7FFF */
    uint8_t  ram_bank;
    uint8_t  vram_bank;      /* CGB has two */
    uint8_t  wram_bank;      /* CGB banks 1-7 at 0xD000 */

    uint8_t  vram[0x4000];   /* 2 banks on CGB */
    uint8_t  wram[0x8000];   /* 8 banks on CGB */
    uint8_t  oam[0xA0];
    uint8_t  hram[0x7F];
    uint8_t  io[0x80];
    uint8_t *cart_ram;
    size_t   cart_ram_size;

    uint8_t  mapper;         /* GB_MAPPER_* */
    uint8_t  ram_enabled;
    uint8_t  cgb;

    /* Return-address stack for recompiled calls. Recompiled code returns via
     * the C stack, but the game can also manipulate its own SP directly, so
     * both are kept coherent. */
    uint16_t call_depth;

    uint32_t framebuffer[GB_SCREEN_W * GB_SCREEN_H];
    uint8_t  joypad;         /* bit per button, 1 = pressed */
};

enum { GB_MAPPER_NONE, GB_MAPPER_MBC1, GB_MAPPER_MBC2,
       GB_MAPPER_MBC3, GB_MAPPER_MBC5 };

/* Register accessors used verbatim by generated code. */
#define A  (gb->a)
#define B  (gb->b)
#define C  (gb->c)
#define D  (gb->d)
#define E  (gb->e)
#define H  (gb->h)
#define L  (gb->l)
#define SP (gb->sp)
#define BC (((uint16_t)gb->b << 8) | gb->c)
#define DE (((uint16_t)gb->d << 8) | gb->e)
#define HL (((uint16_t)gb->h << 8) | gb->l)
#define AF (((uint16_t)gb->a << 8) | gb->f)

#define FLAG_Z (gb->f & FZ)
#define FLAG_N (gb->f & FN)
#define FLAG_H (gb->f & FH)
#define FLAG_C (gb->f & FC)

/* Assigning to a 16-bit pair has to split back into halves. */
static inline void gb_set_bc(gb_t *gb, uint16_t v) { gb->b = v >> 8; gb->c = v; }
static inline void gb_set_de(gb_t *gb, uint16_t v) { gb->d = v >> 8; gb->e = v; }
static inline void gb_set_hl(gb_t *gb, uint16_t v) { gb->h = v >> 8; gb->l = v; }

/* Memory. */
uint8_t  gb_read(gb_t *gb, uint16_t addr);
void     gb_write(gb_t *gb, uint16_t addr, uint8_t value);
uint16_t gb_read16(gb_t *gb, uint16_t addr);
void     gb_io_write(gb_t *gb, uint16_t addr, uint8_t value);
void     gb_write16(gb_t *gb, uint16_t addr, uint16_t value);

/* Stack. */
void     gb_push(gb_t *gb, uint16_t value);
uint16_t gb_pop(gb_t *gb);
void     gb_pop_af(gb_t *gb);

/* Control flow out of recompiled code. */
void gb_call(gb_t *gb, uint16_t bank, uint16_t target, uint16_t ret_addr);
void gb_dispatch(gb_t *gb, uint16_t bank, uint16_t target);
void gb_no_entry(gb_t *gb, uint16_t bank, uint16_t entry);
void gb_interp(gb_t *gb, uint16_t addr);   /* interpreter fallback, one insn */
void gb_illegal(gb_t *gb, uint8_t opcode);
void gb_halt(gb_t *gb);
void gb_stop(gb_t *gb);

/* ALU helpers. Flag behaviour lives in one place so generated code stays thin. */
uint8_t  alu_inc(gb_t *gb, uint8_t v);
uint8_t  alu_dec(gb_t *gb, uint8_t v);
void     alu_add(gb_t *gb, uint8_t v);
void     alu_adc(gb_t *gb, uint8_t v);
void     alu_sub(gb_t *gb, uint8_t v);
void     alu_sbc(gb_t *gb, uint8_t v);
void     alu_and(gb_t *gb, uint8_t v);
void     alu_xor(gb_t *gb, uint8_t v);
void     alu_or (gb_t *gb, uint8_t v);
void     alu_cp (gb_t *gb, uint8_t v);
uint16_t alu_add16(gb_t *gb, uint16_t a, uint16_t b);
uint16_t alu_add_sp(gb_t *gb, int8_t off);
void     alu_rlca(gb_t *gb); void alu_rrca(gb_t *gb);
void     alu_rla (gb_t *gb); void alu_rra (gb_t *gb);
void     alu_daa (gb_t *gb); void alu_cpl (gb_t *gb);
void     alu_scf (gb_t *gb); void alu_ccf (gb_t *gb);
uint8_t  alu_rlc(gb_t *gb, uint8_t v); uint8_t alu_rrc(gb_t *gb, uint8_t v);
uint8_t  alu_rl (gb_t *gb, uint8_t v); uint8_t alu_rr (gb_t *gb, uint8_t v);
uint8_t  alu_sla(gb_t *gb, uint8_t v); uint8_t alu_sra(gb_t *gb, uint8_t v);
uint8_t  alu_swap(gb_t *gb, uint8_t v); uint8_t alu_srl(gb_t *gb, uint8_t v);
void     alu_bit(gb_t *gb, uint8_t v, uint8_t bit);

/* Hardware catch-up. Generated code accumulates cycles and calls this at
 * block boundaries; the PPU, timers and APU advance to meet it. */
void gb_sync(gb_t *gb);

/* Lifecycle. */
int  gb_init(gb_t *gb, const uint8_t *rom, size_t size);
void gb_reset(gb_t *gb);
void gb_run_frame(gb_t *gb);
void gb_free(gb_t *gb);

extern const gb_bank_fn gb_bank_table[GB_MAX_BANKS];

#endif /* GB_H */
