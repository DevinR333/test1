/* Picture processing unit.
 *
 * Recompiling the CPU does not recompile the display. The game's code still
 * writes hardware registers and depends on them changing at the right time,
 * so the PPU is emulated even though the instructions around it are native.
 *
 * Rendering happens once per scanline rather than per dot. That is accurate
 * enough for everything except mid-scanline register writes, and it is an
 * order of magnitude cheaper.
 */
#include <string.h>
#include "gb.h"

/* Register offsets within the IO block at 0xFF00. */
#define R_LCDC 0x40
#define R_STAT 0x41
#define R_SCY  0x42
#define R_SCX  0x43
#define R_LY   0x44
#define R_LYC  0x45
#define R_DMA  0x46
#define R_BGP  0x47
#define R_OBP0 0x48
#define R_OBP1 0x49
#define R_WY   0x4A
#define R_WX   0x4B
#define R_VBK  0x4F
#define R_BCPS 0x68
#define R_BCPD 0x69
#define R_OCPS 0x6A
#define R_OCPD 0x6B
#define R_IF   0x0F

/* LCDC bits. */
#define LCDC_BG_ON      0x01
#define LCDC_OBJ_ON     0x02
#define LCDC_OBJ_TALL   0x04
#define LCDC_BG_MAP     0x08
#define LCDC_TILE_DATA  0x10
#define LCDC_WIN_ON     0x20
#define LCDC_WIN_MAP    0x40
#define LCDC_ON         0x80

/* Interrupt flags. */
#define INT_VBLANK 0x01
#define INT_STAT   0x02

/* Scanline timing, in M-cycles (one M-cycle is four dots). */
#define LINE_CYCLES   114
#define OAM_CYCLES    20
#define DRAW_CYCLES   43
#define LINES_VISIBLE 144
#define LINES_TOTAL   154

/* Background attribute bits, CGB only, stored in VRAM bank 1. */
#define ATTR_PALETTE  0x07
#define ATTR_BANK     0x08
#define ATTR_XFLIP    0x20
#define ATTR_YFLIP    0x40
#define ATTR_PRIORITY 0x80

/* Object attribute bits. */
#define OBJ_DMG_PAL   0x10
#define OBJ_XFLIP     0x20
#define OBJ_YFLIP     0x40
#define OBJ_BEHIND_BG 0x80

/* The original hardware's four shades, darkest last. */
static const uint32_t DMG_SHADES[4] = {
    0xFFE0F8D0, 0xFF88C070, 0xFF346856, 0xFF081820
};

static inline uint8_t io(gb_t *gb, int r)      { return gb->io[r]; }
static inline void set_io(gb_t *gb, int r, uint8_t v) { gb->io[r] = v; }

/* VRAM is two banks on CGB; tile and map reads name the bank explicitly. */
static inline uint8_t vram(gb_t *gb, int bank, uint16_t addr)
{
    return gb->vram[(bank ? 0x2000 : 0) + (addr - 0x8000)];
}

static uint32_t cgb_colour(const uint8_t *pal, int index)
{
    uint16_t v = pal[index * 2] | ((uint16_t)pal[index * 2 + 1] << 8);
    int r = v & 0x1F, g = (v >> 5) & 0x1F, b = (v >> 10) & 0x1F;
    /* Widen five bits to eight by replicating the top bits. */
    r = (r << 3) | (r >> 2);
    g = (g << 3) | (g >> 2);
    b = (b << 3) | (b >> 2);
    return 0xFF000000u | ((uint32_t)r << 16) | ((uint32_t)g << 8) | b;
}

static uint32_t bg_colour(gb_t *gb, int palette, int shade)
{
    if (gb->cgb)
        return cgb_colour(gb->bg_palette, palette * 4 + shade);
    return DMG_SHADES[(io(gb, R_BGP) >> (shade * 2)) & 3];
}

static uint32_t obj_colour(gb_t *gb, int palette, int shade, int dmg_pal)
{
    if (gb->cgb)
        return cgb_colour(gb->obj_palette, palette * 4 + shade);
    uint8_t reg = dmg_pal ? io(gb, R_OBP1) : io(gb, R_OBP0);
    return DMG_SHADES[(reg >> (shade * 2)) & 3];
}

/* Tile data lives in one of two overlapping regions, selected by LCDC bit 4.
 * The lower region indexes signed from 0x9000. */
static uint16_t tile_addr(gb_t *gb, uint8_t index, int y)
{
    if (io(gb, R_LCDC) & LCDC_TILE_DATA)
        return 0x8000 + index * 16 + y * 2;
    return 0x9000 + (int8_t)index * 16 + y * 2;
}

static void render_line(gb_t *gb, int ly)
{
    uint32_t *out = gb->framebuffer + ly * GB_SCREEN_W;
    uint8_t lcdc = io(gb, R_LCDC);

    /* Per-pixel record of what the background put down, so sprite priority
     * can be resolved without re-reading the tile maps. */
    uint8_t bg_shade[GB_SCREEN_W];
    uint8_t bg_priority[GB_SCREEN_W];
    memset(bg_shade, 0, sizeof(bg_shade));
    memset(bg_priority, 0, sizeof(bg_priority));

    /* --- background and window ------------------------------------- */
    /* On DMG, LCDC bit 0 blanks the background. On CGB the same bit instead
     * demotes background priority, and the background still draws. */
    if ((lcdc & LCDC_BG_ON) || gb->cgb) {
        uint8_t scy = io(gb, R_SCY), scx = io(gb, R_SCX);
        uint8_t wy = io(gb, R_WY), wx = io(gb, R_WX);
        int win_active = (lcdc & LCDC_WIN_ON) && ly >= wy && wx <= 166;

        for (int x = 0; x < GB_SCREEN_W; x++) {
            int use_window = win_active && x + 7 >= wx;
            uint16_t map_base;
            int mx, my;

            if (use_window) {
                map_base = (lcdc & LCDC_WIN_MAP) ? 0x9C00 : 0x9800;
                mx = x + 7 - wx;
                my = gb->window_line;
            } else {
                map_base = (lcdc & LCDC_BG_MAP) ? 0x9C00 : 0x9800;
                mx = (x + scx) & 0xFF;
                my = (ly + scy) & 0xFF;
            }

            uint16_t map_addr = map_base + (my / 8) * 32 + (mx / 8);
            uint8_t index = vram(gb, 0, map_addr);
            uint8_t attr = gb->cgb ? vram(gb, 1, map_addr) : 0;

            int ty = my % 8, tx = mx % 8;
            if (attr & ATTR_YFLIP) ty = 7 - ty;
            if (attr & ATTR_XFLIP) tx = 7 - tx;

            uint16_t addr = tile_addr(gb, index, ty);
            int bank = (attr & ATTR_BANK) ? 1 : 0;
            uint8_t lo = vram(gb, bank, addr);
            uint8_t hi = vram(gb, bank, addr + 1);
            int bit = 7 - tx;
            int shade = (((hi >> bit) & 1) << 1) | ((lo >> bit) & 1);

            bg_shade[x] = shade;
            bg_priority[x] = (attr & ATTR_PRIORITY) ? 1 : 0;
            out[x] = bg_colour(gb, attr & ATTR_PALETTE, shade);
        }

        if (win_active && wx <= 166)
            gb->window_line++;
    } else {
        for (int x = 0; x < GB_SCREEN_W; x++)
            out[x] = DMG_SHADES[0];
    }

    /* --- sprites ---------------------------------------------------- */
    if (!(lcdc & LCDC_OBJ_ON))
        return;

    int height = (lcdc & LCDC_OBJ_TALL) ? 16 : 8;

    /* The hardware draws at most ten sprites per line, taking them in OAM
     * order. Games rely on the cutoff, so it is reproduced rather than
     * drawing everything. */
    int chosen[10], count = 0;
    for (int i = 0; i < 40 && count < 10; i++) {
        int sy = gb->oam[i * 4] - 16;
        if (ly >= sy && ly < sy + height)
            chosen[count++] = i;
    }

    /* Later entries draw first so earlier ones end up on top. On DMG a
     * smaller X wins instead, which is handled by the overwrite order. */
    for (int n = count - 1; n >= 0; n--) {
        int i = chosen[n];
        int sy = gb->oam[i * 4] - 16;
        int sx = gb->oam[i * 4 + 1] - 8;
        uint8_t index = gb->oam[i * 4 + 2];
        uint8_t attr = gb->oam[i * 4 + 3];

        if (height == 16) index &= 0xFE;     /* tall sprites ignore bit 0 */

        int ty = ly - sy;
        if (attr & OBJ_YFLIP) ty = height - 1 - ty;

        int bank = (gb->cgb && (attr & ATTR_BANK)) ? 1 : 0;
        uint16_t addr = 0x8000 + index * 16 + ty * 2;
        uint8_t lo = vram(gb, bank, addr);
        uint8_t hi = vram(gb, bank, addr + 1);

        for (int px = 0; px < 8; px++) {
            int x = sx + px;
            if (x < 0 || x >= GB_SCREEN_W) continue;

            int bit = (attr & OBJ_XFLIP) ? px : 7 - px;
            int shade = (((hi >> bit) & 1) << 1) | ((lo >> bit) & 1);
            if (shade == 0) continue;        /* colour 0 is transparent */

            /* Background priority wins unless the background pixel is
             * colour 0, which never covers a sprite. */
            if (bg_shade[x]) {
                if (attr & OBJ_BEHIND_BG) continue;
                if (gb->cgb && bg_priority[x] && (lcdc & LCDC_BG_ON)) continue;
            }

            int pal = gb->cgb ? (attr & ATTR_PALETTE) : 0;
            out[x] = obj_colour(gb, pal, shade, attr & OBJ_DMG_PAL);
        }
    }
}

/* Keep STAT's mode bits and the LY==LYC flag current, and raise the STAT
 * interrupt on the enabled transitions. */
static void update_stat(gb_t *gb, int mode)
{
    uint8_t stat = io(gb, R_STAT);
    uint8_t ly = io(gb, R_LY), lyc = io(gb, R_LYC);
    int coincide = (ly == lyc);

    stat = (stat & 0xF8) | (coincide ? 0x04 : 0) | (mode & 3);
    set_io(gb, R_STAT, stat);

    int fire = 0;
    if (coincide && (stat & 0x40)) fire = 1;
    if (mode == 0 && (stat & 0x08)) fire = 1;
    if (mode == 1 && (stat & 0x10)) fire = 1;
    if (mode == 2 && (stat & 0x20)) fire = 1;

    /* The interrupt triggers on a rising edge of the combined condition, so
     * holding a condition true does not fire repeatedly. */
    if (fire && !gb->stat_line)
        gb->io[R_IF] |= INT_STAT;
    gb->stat_line = fire;
}

void gb_ppu_step(gb_t *gb, uint32_t cycles)
{
    if (!(io(gb, R_LCDC) & LCDC_ON)) {
        /* With the display off the PPU is idle and LY reads zero. */
        gb->ppu_cycles = 0;
        gb->window_line = 0;
        set_io(gb, R_LY, 0);
        set_io(gb, R_STAT, io(gb, R_STAT) & 0xFC);
        return;
    }

    gb->ppu_cycles += cycles;

    while (gb->ppu_cycles >= LINE_CYCLES) {
        gb->ppu_cycles -= LINE_CYCLES;

        uint8_t ly = io(gb, R_LY);
        if (ly < LINES_VISIBLE)
            render_line(gb, ly);

        /* An HBlank transfer moves one block at the end of each visible
         * line. */
        if (ly < LINES_VISIBLE)
            gb_hdma_hblank(gb);

        ly++;
        if (ly == LINES_VISIBLE) {
            gb->io[R_IF] |= INT_VBLANK;
            gb->frame_ready = 1;
            gb->window_line = 0;
        }
        if (ly >= LINES_TOTAL)
            ly = 0;
        set_io(gb, R_LY, ly);
    }

    /* Mode follows position within the line, except during VBlank. */
    uint8_t ly = io(gb, R_LY);
    int mode;
    if (ly >= LINES_VISIBLE)          mode = 1;
    else if (gb->ppu_cycles < OAM_CYCLES)               mode = 2;
    else if (gb->ppu_cycles < OAM_CYCLES + DRAW_CYCLES) mode = 3;
    else                                                mode = 0;
    update_stat(gb, mode);
}

void gb_ppu_reset(gb_t *gb)
{
    gb->ppu_cycles = 0;
    gb->window_line = 0;
    gb->stat_line = 0;
    gb->frame_ready = 0;
    for (int i = 0; i < GB_SCREEN_W * GB_SCREEN_H; i++)
        gb->framebuffer[i] = DMG_SHADES[0];
}
