/* Runs the jump-table ROM and checks both kinds of RET took effect. */
#include <stdio.h>
#include <stdlib.h>
#include "gb.h"

static void cb(gb_t *gb, void *u) { (void)u; if (gb->frames >= 10) gb->stopped = 1; }

int main(void)
{
    FILE *f = fopen("tests/jumptest.gb", "rb");
    if (!f) { perror("rom"); return 2; }
    fseek(f, 0, SEEK_END); long n = ftell(f); fseek(f, 0, SEEK_SET);
    uint8_t *rom = malloc(n);
    if (fread(rom, 1, n, f) != (size_t)n) return 2;
    fclose(f);

    static gb_t gb;
    gb_init(&gb, rom, n);
    gb.frame_cb = cb;
    gb_run(&gb);

    uint8_t call_ran = gb.wram[0x000];      /* C000 */
    uint8_t jump_ran = gb.wram[0x001];      /* C001 */

    printf("  ordinary call/ret        %s (C000 = %02X, want 11)\n",
           call_ran == 0x11 ? "ok" : "FAILED", call_ran);
    printf("  push+ret computed jump   %s (C001 = %02X, want 22)\n",
           jump_ran == 0x22 ? "ok" : "FAILED", jump_ran);

    int bad = (call_ran != 0x11) || (jump_ran != 0x22);
    printf("  %s\n", bad ? "FAILURE" : "both forms of RET work");
    return bad;
}
