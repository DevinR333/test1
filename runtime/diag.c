/* Diagnostic snapshot.
 *
 * A blank screen has several possible causes that look identical from the
 * outside: graphics never reaching VRAM, a tilemap left empty, palettes never
 * written, or the background switched off. Reporting the machine's actual
 * state distinguishes them in one run instead of one guess per rebuild.
 */
#include <stdio.h>
#include <string.h>
#include "gb.h"

static int count_nonzero(const uint8_t *p, size_t n)
{
    int c = 0;
    for (size_t i = 0; i < n; i++)
        if (p[i]) c++;
    return c;
}

static int distinct_bytes(const uint8_t *p, size_t n)
{
    uint8_t seen[256];
    memset(seen, 0, sizeof(seen));
    for (size_t i = 0; i < n; i++)
        seen[p[i]] = 1;
    int c = 0;
    for (int i = 0; i < 256; i++)
        c += seen[i];
    return c;
}

void gb_write_diagnostics(const gb_t *gb, const char *path)
{
    FILE *fh = fopen(path, "w");
    if (!fh) return;

    uint8_t lcdc = gb->io[0x40];
    fprintf(fh, "frames        %llu\n", (unsigned long long)gb->frames);
    fprintf(fh, "cycles        %llu\n", (unsigned long long)gb->cycles);
    fprintf(fh, "speed         %s\n", gb->double_speed ? "double" : "normal");
    fprintf(fh, "rom bank      %02X\n", gb->rom_bank);
    fprintf(fh, "interp calls  %llu\n", (unsigned long long)gb->no_entry_count);

    fprintf(fh, "\nLCDC          %02X  (display %s, bg %s, obj %s, win %s)\n",
            lcdc,
            (lcdc & 0x80) ? "on" : "OFF",
            (lcdc & 0x01) ? "on" : "OFF",
            (lcdc & 0x02) ? "on" : "off",
            (lcdc & 0x20) ? "on" : "off");
    fprintf(fh, "              bg map %s, tile data %s\n",
            (lcdc & 0x08) ? "9C00" : "9800",
            (lcdc & 0x10) ? "8000 (unsigned)" : "8800 (signed)");
    fprintf(fh, "STAT          %02X\n", gb->io[0x41]);
    fprintf(fh, "SCY/SCX       %02X %02X\n", gb->io[0x42], gb->io[0x43]);
    fprintf(fh, "LY / LYC      %02X %02X\n", gb->io[0x44], gb->io[0x45]);
    fprintf(fh, "WY / WX       %02X %02X\n", gb->io[0x4A], gb->io[0x4B]);
    fprintf(fh, "BGP           %02X\n", gb->io[0x47]);
    fprintf(fh, "VBK / SVBK    %02X %02X\n", gb->io[0x4F], gb->io[0x70]);
    fprintf(fh, "IE / IF       %02X %02X   IME %d\n",
            gb->io[0x7F], gb->io[0x0F], gb->ime);

    /* Is there anything to draw at all? */
    fprintf(fh, "\ntile data\n");
    fprintf(fh, "  vram bank 0   %d of 8192 bytes set, %d distinct values\n",
            count_nonzero(gb->vram, 0x2000), distinct_bytes(gb->vram, 0x2000));
    fprintf(fh, "  vram bank 1   %d of 8192 bytes set, %d distinct values\n",
            count_nonzero(gb->vram + 0x2000, 0x2000),
            distinct_bytes(gb->vram + 0x2000, 0x2000));

    /* The two tile maps live at the top of bank 0. */
    const uint8_t *map98 = gb->vram + (0x9800 - 0x8000);
    const uint8_t *map9C = gb->vram + (0x9C00 - 0x8000);
    fprintf(fh, "  map 9800      %d of 1024 entries set, %d distinct tiles\n",
            count_nonzero(map98, 0x400), distinct_bytes(map98, 0x400));
    fprintf(fh, "  map 9C00      %d of 1024 entries set, %d distinct tiles\n",
            count_nonzero(map9C, 0x400), distinct_bytes(map9C, 0x400));
    fprintf(fh, "  oam           %d of 160 bytes set\n",
            count_nonzero(gb->oam, 0xA0));

    fprintf(fh, "\nbackground palettes (CGB)\n");
    for (int p = 0; p < 8; p++) {
        fprintf(fh, "  %d:", p);
        for (int c = 0; c < 4; c++) {
            int i = (p * 4 + c) * 2;
            unsigned v = gb->bg_palette[i] | ((unsigned)gb->bg_palette[i + 1] << 8);
            fprintf(fh, " %04X", v);
        }
        fprintf(fh, "\n");
    }

    fprintf(fh, "\nfirst framebuffer row, every 16th pixel\n ");
    for (int x = 0; x < GB_SCREEN_W; x += 16)
        fprintf(fh, " %08X", gb->framebuffer[x]);
    fprintf(fh, "\n");

    /* A screen that is one flat colour says the pipeline produced nothing. */
    uint32_t first = gb->framebuffer[0];
    int varied = 0;
    for (int i = 1; i < GB_SCREEN_W * GB_SCREEN_H; i++)
        if (gb->framebuffer[i] != first) { varied = 1; break; }
    fprintf(fh, "framebuffer   %s\n", varied ? "varied" : "ONE FLAT COLOUR");

    fprintf(fh, "\nio registers touched since the last frame\n");
    for (int r = 0; r < 128; r++)
        if (gb->io_reads[r] || gb->io_writes[r])
            fprintf(fh, "  FF%02X  r=%u w=%u\n", r, gb->io_reads[r], gb->io_writes[r]);

    fclose(fh);
}
