/* Boots a ROM through the recompiled code and runs it, so the runtime is
 * exercised end to end rather than only compiled.
 *
 * Recompiled code never returns from the game's main loop, so frames arrive
 * through the callback and the harness stops the machine from inside it.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "gb.h"

struct limit { int max_frames; };

static void on_frame(gb_t *gb, void *user)
{
    struct limit *lim = user;
    if ((int)gb->frames >= lim->max_frames)
        gb->stopped = 1;
}

int main(int argc, char **argv)
{
    if (argc < 2) { fprintf(stderr, "usage: harness ROM [frames]\n"); return 2; }
    struct limit lim = { argc > 2 ? atoi(argv[2]) : 10 };

    FILE *fh = fopen(argv[1], "rb");
    if (!fh) { perror(argv[1]); return 1; }
    fseek(fh, 0, SEEK_END); long size = ftell(fh); fseek(fh, 0, SEEK_SET);
    uint8_t *rom = malloc(size);
    if (!rom || fread(rom, 1, size, fh) != (size_t)size) {
        fprintf(stderr, "read failed\n"); return 1;
    }
    fclose(fh);

    static gb_t gb;
    if (gb_init(&gb, rom, size) != 0) { fprintf(stderr, "gb_init failed\n"); return 1; }
    gb.frame_cb = on_frame;
    gb.frame_cb_user = &lim;

    printf("  %s: %ld KiB, %s, mapper %d\n", argv[1], size / 1024,
           gb.cgb ? "CGB" : "DMG", gb.mapper);

    gb_run(&gb);

    /* Prove the PPU produced pixels rather than leaving the buffer untouched. */
    uint32_t first = gb.framebuffer[0];
    int varied = 0;
    for (int i = 1; i < GB_SCREEN_W * GB_SCREEN_H; i++)
        if (gb.framebuffer[i] != first) { varied = 1; break; }

    printf("  %llu frames, %llu M-cycles, LY=%d, LCDC=%02X\n",
           (unsigned long long)gb.frames, (unsigned long long)gb.cycles,
           gb.io[0x44], gb.io[0x40]);
    printf("  framebuffer: %s\n", varied ? "varied" : "uniform");
    if (gb.illegal_opcode) printf("  illegal opcode: %02X\n", gb.illegal_opcode);

    gb_free(&gb);
    free(rom);
    return gb.frames > 0 ? 0 : 1;
}
