/* ALU and flag behaviour.
 *
 * Kept out of the generated code so a bug here is fixed in one place rather
 * than in three hundred thousand emitted lines.
 */
#include "gb.h"

static inline void setf(gb_t *gb, int z, int n, int h, int c)
{
    gb->f = (z ? FZ : 0) | (n ? FN : 0) | (h ? FH : 0) | (c ? FC : 0);
}

uint8_t alu_inc(gb_t *gb, uint8_t v)
{
    uint8_t r = v + 1;
    gb->f = (gb->f & FC) | (r ? 0 : FZ) | ((v & 0x0F) == 0x0F ? FH : 0);
    return r;
}

uint8_t alu_dec(gb_t *gb, uint8_t v)
{
    uint8_t r = v - 1;
    gb->f = (gb->f & FC) | (r ? 0 : FZ) | FN | ((v & 0x0F) == 0 ? FH : 0);
    return r;
}

void alu_add(gb_t *gb, uint8_t v)
{
    unsigned r = gb->a + v;
    setf(gb, (r & 0xFF) == 0, 0, ((gb->a & 0x0F) + (v & 0x0F)) > 0x0F, r > 0xFF);
    gb->a = r;
}

void alu_adc(gb_t *gb, uint8_t v)
{
    unsigned carry = (gb->f & FC) ? 1 : 0;
    unsigned r = gb->a + v + carry;
    setf(gb, (r & 0xFF) == 0, 0,
         ((gb->a & 0x0F) + (v & 0x0F) + carry) > 0x0F, r > 0xFF);
    gb->a = r;
}

void alu_sub(gb_t *gb, uint8_t v)
{
    unsigned r = gb->a - v;
    setf(gb, (r & 0xFF) == 0, 1, (gb->a & 0x0F) < (v & 0x0F), gb->a < v);
    gb->a = r;
}

void alu_sbc(gb_t *gb, uint8_t v)
{
    unsigned carry = (gb->f & FC) ? 1 : 0;
    unsigned r = gb->a - v - carry;
    setf(gb, (r & 0xFF) == 0, 1,
         (gb->a & 0x0F) < ((v & 0x0F) + carry), gb->a < (unsigned)(v + carry));
    gb->a = r;
}

void alu_and(gb_t *gb, uint8_t v) { gb->a &= v; setf(gb, !gb->a, 0, 1, 0); }
void alu_xor(gb_t *gb, uint8_t v) { gb->a ^= v; setf(gb, !gb->a, 0, 0, 0); }
void alu_or (gb_t *gb, uint8_t v) { gb->a |= v; setf(gb, !gb->a, 0, 0, 0); }

void alu_cp(gb_t *gb, uint8_t v)
{
    setf(gb, gb->a == v, 1, (gb->a & 0x0F) < (v & 0x0F), gb->a < v);
}

uint16_t alu_add16(gb_t *gb, uint16_t a, uint16_t b)
{
    unsigned r = a + b;
    /* Z is preserved by ADD HL,rr. */
    gb->f = (gb->f & FZ)
          | (((a & 0x0FFF) + (b & 0x0FFF)) > 0x0FFF ? FH : 0)
          | (r > 0xFFFF ? FC : 0);
    return r;
}

uint16_t alu_add_sp(gb_t *gb, int8_t off)
{
    uint16_t sp = gb->sp;
    /* Half-carry and carry come from the low byte, regardless of sign. */
    setf(gb, 0, 0,
         ((sp & 0x0F) + (off & 0x0F)) > 0x0F,
         ((sp & 0xFF) + (uint8_t)off) > 0xFF);
    return sp + off;
}

void alu_rlca(gb_t *gb)
{
    uint8_t c = gb->a >> 7;
    gb->a = (gb->a << 1) | c;
    setf(gb, 0, 0, 0, c);
}

void alu_rrca(gb_t *gb)
{
    uint8_t c = gb->a & 1;
    gb->a = (gb->a >> 1) | (c << 7);
    setf(gb, 0, 0, 0, c);
}

void alu_rla(gb_t *gb)
{
    uint8_t c = gb->a >> 7;
    gb->a = (gb->a << 1) | ((gb->f & FC) ? 1 : 0);
    setf(gb, 0, 0, 0, c);
}

void alu_rra(gb_t *gb)
{
    uint8_t c = gb->a & 1;
    gb->a = (gb->a >> 1) | ((gb->f & FC) ? 0x80 : 0);
    setf(gb, 0, 0, 0, c);
}

/* Decimal adjust. The awkward one: it corrects A after an add or subtract of
 * two BCD values, and which correction applies depends on the N flag. */
void alu_daa(gb_t *gb)
{
    int a = gb->a;
    if (!(gb->f & FN)) {
        if ((gb->f & FH) || (a & 0x0F) > 9)  a += 0x06;
        if ((gb->f & FC) || a > 0x9F)        a += 0x60;
    } else {
        if (gb->f & FH) a = (a - 0x06) & 0xFF;
        if (gb->f & FC) a -= 0x60;
    }
    gb->f &= ~(FH | FZ);
    if (a & 0x100) gb->f |= FC;     /* carry is set, never cleared, by DAA */
    a &= 0xFF;
    if (a == 0) gb->f |= FZ;
    gb->a = a;
}

void alu_cpl(gb_t *gb) { gb->a = ~gb->a; gb->f |= FN | FH; }
void alu_scf(gb_t *gb) { gb->f = (gb->f & FZ) | FC; }
void alu_ccf(gb_t *gb) { gb->f = (gb->f & FZ) | ((gb->f & FC) ? 0 : FC); }

uint8_t alu_rlc(gb_t *gb, uint8_t v)
{
    uint8_t c = v >> 7, r = (v << 1) | c;
    setf(gb, !r, 0, 0, c);
    return r;
}

uint8_t alu_rrc(gb_t *gb, uint8_t v)
{
    uint8_t c = v & 1, r = (v >> 1) | (c << 7);
    setf(gb, !r, 0, 0, c);
    return r;
}

uint8_t alu_rl(gb_t *gb, uint8_t v)
{
    uint8_t c = v >> 7, r = (v << 1) | ((gb->f & FC) ? 1 : 0);
    setf(gb, !r, 0, 0, c);
    return r;
}

uint8_t alu_rr(gb_t *gb, uint8_t v)
{
    uint8_t c = v & 1, r = (v >> 1) | ((gb->f & FC) ? 0x80 : 0);
    setf(gb, !r, 0, 0, c);
    return r;
}

uint8_t alu_sla(gb_t *gb, uint8_t v)
{
    uint8_t c = v >> 7, r = v << 1;
    setf(gb, !r, 0, 0, c);
    return r;
}

uint8_t alu_sra(gb_t *gb, uint8_t v)
{
    uint8_t c = v & 1, r = (v >> 1) | (v & 0x80);   /* arithmetic: sign extends */
    setf(gb, !r, 0, 0, c);
    return r;
}

uint8_t alu_srl(gb_t *gb, uint8_t v)
{
    uint8_t c = v & 1, r = v >> 1;
    setf(gb, !r, 0, 0, c);
    return r;
}

uint8_t alu_swap(gb_t *gb, uint8_t v)
{
    uint8_t r = (v << 4) | (v >> 4);
    setf(gb, !r, 0, 0, 0);
    return r;
}

void alu_bit(gb_t *gb, uint8_t v, uint8_t bit)
{
    gb->f = (gb->f & FC) | ((v & (1 << bit)) ? 0 : FZ) | FH;
}
