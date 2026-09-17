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
    uint16_t entry_sp = gb->sp;
    int guard = 0;

    for (;;) {
        uint32_t cycles = gb_interp_step(gb);
        gb->cycles += cycles;
        gb_sync(gb);

        if (gb->stopped || gb->frame_ready)
            return;
        /* A RET past where we came in means the routine finished. */
        if (gb->sp > entry_sp)
            return;
        if (++guard > 2000000)
            return;                 /* wedged; let the caller carry on */
    }
}
