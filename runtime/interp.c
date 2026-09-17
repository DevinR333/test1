/* Interpreter entry points.
 *
 * The per-opcode work is generated into interp_gen.c from the recompiler's
 * own tables; this file is the loop around it.
 */
#include "gb.h"

uint32_t gb_interp_step(gb_t *gb);

/* Run interpreted until control returns above the depth we entered at, or the
 * frame ends. Entered when recompiled code reaches an address the discovery
 * pass never saw. */
void gb_interp(gb_t *gb, uint16_t addr)
{
    gb->pc = addr;
    int guard = 0;

    for (;;) {
        uint32_t cycles = gb_interp_step(gb);
        gb->cycles += cycles;
        gb_sync(gb);

        if (gb->stopped)
            return;

        /* Hand back as soon as the program counter reaches code that was
         * compiled, so the fast path resumes.
         *
         * Only ROM is ever compiled. Checking the bank table for an address
         * outside ROM matched on the current bank and handed back an address
         * that had no block, which dispatched straight back here: the two
         * bounced off each other one instruction at a time. */
        if (gb->pc < 0x8000) {
            uint16_t b = (gb->pc < 0x4000) ? 0 : gb->rom_bank;
            if (b < GB_MAX_BANKS && gb_bank_table[b]) {
                gb_jump(gb, b, gb->pc);
                return;
            }
        }
        if (++guard > 2000000) {
            /* Not returning after two million instructions means it is not
             * going to. Stopping with a reason beats hanging. */
            gb->stopped = 1;
            gb->stop_reason = GB_STOP_INTERP_RUNAWAY;
            gb->stop_pc = gb->pc;
            gb->stop_bank = gb->rom_bank;
            return;
        }
    }
}
