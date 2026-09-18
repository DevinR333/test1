/* World map renderer.
 *
 * Draws the world from its room data rather than from the hardware's display,
 * which only ever holds the room the player is standing in. Tile pixels and
 * colours come from whatever the game currently has loaded, so the map matches
 * what the player is seeing.
 *
 * Sampling runs backwards, from each destination pixel to the world position
 * behind it, which handles any scale without a separate path for magnifying
 * and shrinking.
 */
#include <string.h>
#include "worldmap.h"

#define ROOM_PX_W (GB_ROOM_COLS * GB_METATILE_PX)   /* 160 */
#define ROOM_PX_H (GB_ROOM_ROWS * GB_METATILE_PX)   /* 128 */

/* Metatile attribute bits, as the hardware reads them. */
#define ATTR_PALETTE 0x07
#define ATTR_BANK    0x08
#define ATTR_XFLIP   0x20
#define ATTR_YFLIP   0x40

static const uint32_t DMG_SHADES[4] = {
    0xFFE0F8D0, 0xFF88C070, 0xFF346856, 0xFF081820
};

/* Tiles come from the tileset's own graphics, not from video memory: the
 * hardware only ever holds the area the player is standing in. */
static inline uint8_t vram_at(const uint8_t *vram, uint16_t addr)
{
    return vram[addr - 0x8000];
}

static uint32_t colour_of(const gb_t *gb, const uint8_t *pal, int palette, int shade)
{
    if (!gb->cgb)
        return DMG_SHADES[(gb->io[0x47] >> (shade * 2)) & 3];

    int i = (palette * 4 + shade) * 2;
    uint16_t v = pal[i] | ((uint16_t)pal[i + 1] << 8);
    int r = v & 0x1F, g = (v >> 5) & 0x1F, b = (v >> 10) & 0x1F;
    r = (r << 3) | (r >> 2);
    g = (g << 3) | (g >> 2);
    b = (b << 3) | (b >> 2);
    return 0xFF000000u | ((uint32_t)r << 16) | ((uint32_t)g << 8) | b;
}

/* Tile data sits in one of two overlapping regions, chosen by LCDC bit 4; the
 * lower one indexes signed from 0x9000. */
/* The world's tilesets are built for the signed addressing the overworld
 * uses, so indices are read from 0x9000 regardless of what the display is
 * currently configured for. */
static uint16_t tile_row_addr(uint8_t index, int row)
{
    return 0x9000 + (int8_t)index * 16 + row * 2;
}

float gb_world_fit_scale(int dst_w, int dst_h)
{
    float sx = (float)dst_w / GB_WORLD_W;
    float sy = (float)dst_h / GB_WORLD_H;
    return sx < sy ? sx : sy;
}

float gb_world_cover_scale(int dst_w, int dst_h)
{
    float sx = (float)dst_w / GB_WORLD_W;
    float sy = (float)dst_h / GB_WORLD_H;
    return sx > sy ? sx : sy;
}

void gb_world_render(const gb_t *gb, uint32_t *dst, int dst_w, int dst_h,
                     float cam_x, float cam_y, float scale)
{
    if (!dst || dst_w <= 0 || dst_h <= 0 || scale <= 0.0f)
        return;

    /* No world data compiled in. Drawing nothing leaves a blank screen that
     * looks exactly like a rendering bug, so draw something unmistakable
     * instead. */
    if (gb_world_tileset_count <= 0) {
        for (int y = 0; y < dst_h; y++)
            for (int x = 0; x < dst_w; x++)
                dst[(size_t)y * dst_w + x] =
                    (((x >> 4) ^ (y >> 4)) & 1) ? 0xFF803030u : 0xFF202020u;
        return;
    }

    const uint32_t backdrop = 0xFF101014u;
    const float inv = 1.0f / scale;
    const float left = cam_x - (dst_w * 0.5f) * inv;
    const float top  = cam_y - (dst_h * 0.5f) * inv;

    for (int dy = 0; dy < dst_h; dy++) {
        float wy_f = top + dy * inv;
        int wy = (int)wy_f;
        uint32_t *row = dst + (size_t)dy * dst_w;

        if (wy_f < 0 || wy >= GB_WORLD_H) {
            for (int dx = 0; dx < dst_w; dx++)
                row[dx] = backdrop;
            continue;
        }

        int room_row = wy / ROOM_PX_H;
        int in_room_y = wy % ROOM_PX_H;
        int mt_y = in_room_y / GB_METATILE_PX;
        int sub_y = in_room_y % GB_METATILE_PX;

        for (int dx = 0; dx < dst_w; dx++) {
            float wx_f = left + dx * inv;
            int wx = (int)wx_f;
            if (wx_f < 0 || wx >= GB_WORLD_W) {
                row[dx] = backdrop;
                continue;
            }

            int room = room_row * GB_WORLD_COLS + (wx / ROOM_PX_W);
            int in_room_x = wx % ROOM_PX_W;

            uint8_t metatile = gb_world_rooms[room][mt_y * GB_ROOM_COLS
                                                    + in_room_x / GB_METATILE_PX];
            /* The top bit of a room's tileset byte is a flag, not part of
             * the number. */
            int tileset = gb_world_room_tileset[room] & 0x7F;
            int assets = (tileset < gb_world_tileset_count) ? tileset : 0;
            int mapping = (tileset < gb_world_mapping_count) ? tileset : 0;
            const uint8_t *tvram = gb_world_tileset_vram[assets];
            const uint8_t *tpal = gb_world_tileset_palette[assets];

            /* Each metatile is four tiles: two across, two down.
             *
             * A metatile's eight bytes are four tile indices followed by four
             * attribute bytes, not four pairs of the two. Reading them as
             * pairs takes an attribute byte as a tile index for half of every
             * metatile, which draws real tiles in the wrong places - the
             * tiles look right and the terrain does not. The distinction is
             * visible in the data: the last four bytes of each metatile take
             * only a couple of dozen distinct values across a whole tileset,
             * as attribute bits do, while the first four span the range. */
            int sub_x = in_room_x % GB_METATILE_PX;
            int quadrant = (sub_y / 8) * 2 + (sub_x / 8);
            const uint8_t *entry = &gb_world_mappings[mapping][metatile * 8];

            uint8_t index = entry[quadrant];
            uint8_t attr = entry[4 + quadrant];

            int px = sub_x % 8, py = sub_y % 8;
            if (attr & ATTR_XFLIP) px = 7 - px;
            if (attr & ATTR_YFLIP) py = 7 - py;

            uint16_t addr = tile_row_addr(index, py);
            uint8_t lo = vram_at(tvram, addr);
            uint8_t hi = vram_at(tvram, addr + 1);

            int bit = 7 - px;
            int shade = (((hi >> bit) & 1) << 1) | ((lo >> bit) & 1);
            row[dx] = colour_of(gb, tpal, attr & ATTR_PALETTE, shade);
        }
    }
}


/* The game keeps the room it is standing in at a known address in work RAM.
 * Reading it lets the map open where the player is rather than at the middle
 * of the world. */
#define W_ACTIVE_ROOM 0xCC4C

int gb_world_active_room(const gb_t *gb)
{
    int room = gb->wram[W_ACTIVE_ROOM - 0xC000];
    return (room >= 0 && room < GB_WORLD_ROOMS) ? room : -1;
}

void gb_world_mark_room(uint32_t *dst, int dst_w, int dst_h,
                        float cam_x, float cam_y, float scale, int room)
{
    if (!dst || room < 0 || room >= GB_WORLD_ROOMS || scale <= 0.0f)
        return;

    /* Where the room sits in the world, then on screen. */
    float wx = (float)(room % GB_WORLD_COLS) * ROOM_PX_W;
    float wy = (float)(room / GB_WORLD_COLS) * ROOM_PX_H;

    float left = cam_x - (dst_w * 0.5f) / scale;
    float top  = cam_y - (dst_h * 0.5f) / scale;

    int x0 = (int)((wx - left) * scale);
    int y0 = (int)((wy - top) * scale);
    int x1 = (int)((wx + ROOM_PX_W - left) * scale);
    int y1 = (int)((wy + ROOM_PX_H - top) * scale);

    /* A two-pixel outline, thick enough to see when the whole world is in
     * view and not so thick that it hides the room when zoomed in. */
    const uint32_t mark = 0xFFFFFFFFu;
    for (int t = 0; t < 2; t++) {
        for (int x = x0 - t; x <= x1 + t; x++) {
            if (x < 0 || x >= dst_w) continue;
            int ya = y0 - t, yb = y1 + t;
            if (ya >= 0 && ya < dst_h) dst[(size_t)ya * dst_w + x] = mark;
            if (yb >= 0 && yb < dst_h) dst[(size_t)yb * dst_w + x] = mark;
        }
        for (int y = y0 - t; y <= y1 + t; y++) {
            if (y < 0 || y >= dst_h) continue;
            int xa = x0 - t, xb = x1 + t;
            if (xa >= 0 && xa < dst_w) dst[(size_t)y * dst_w + xa] = mark;
            if (xb >= 0 && xb < dst_w) dst[(size_t)y * dst_w + xb] = mark;
        }
    }
}


/* Objects - the player, enemies, anything else the game is running - live in
 * the hardware's object memory at their current positions, and their tiles are
 * in video memory because the game is drawing them right now. Both are read
 * live, so they move on the map as they move in the game.
 *
 * Only the room being simulated has any: the game runs one room at a time, so
 * elsewhere on the map there is genuinely nothing to draw.
 */
void gb_world_draw_objects(const gb_t *gb, uint32_t *dst, int dst_w, int dst_h,
                           float cam_x, float cam_y, float scale, int room)
{
    if (!dst || room < 0 || room >= GB_WORLD_ROOMS || scale <= 0.0f)
        return;

    float room_x = (float)(room % GB_WORLD_COLS) * ROOM_PX_W;
    float room_y = (float)(room / GB_WORLD_COLS) * ROOM_PX_H;
    float left = cam_x - (dst_w * 0.5f) / scale;
    float top  = cam_y - (dst_h * 0.5f) / scale;

    int tall = (gb->io[0x40] & 0x04) ? 16 : 8;

    /* Later entries draw first so earlier ones end up on top, matching the
     * hardware's priority. */
    for (int i = 39; i >= 0; i--) {
        int oy = gb->oam[i * 4] - 16;
        int ox = gb->oam[i * 4 + 1] - 8;
        uint8_t index = gb->oam[i * 4 + 2];
        uint8_t attr = gb->oam[i * 4 + 3];

        /* The hardware hides an object by parking it outside the screen. */
        if (gb->oam[i * 4] == 0 || gb->oam[i * 4] >= 160) continue;
        if (gb->oam[i * 4 + 1] == 0 || gb->oam[i * 4 + 1] >= 168) continue;

        if (tall == 16) index &= 0xFE;

        for (int py = 0; py < tall; py++) {
            for (int px = 0; px < 8; px++) {
                int ty = (attr & ATTR_YFLIP) ? tall - 1 - py : py;
                int tx = (attr & ATTR_XFLIP) ? 7 - px : px;

                uint16_t addr = 0x8000 + index * 16 + ty * 2;
                int bank = (gb->cgb && (attr & ATTR_BANK)) ? 1 : 0;
                uint8_t lo = gb->vram[(bank ? 0x2000 : 0) + (addr - 0x8000)];
                uint8_t hi = gb->vram[(bank ? 0x2000 : 0) + (addr - 0x8000) + 1];

                int bit = 7 - tx;
                int shade = (((hi >> bit) & 1) << 1) | ((lo >> bit) & 1);
                if (shade == 0) continue;          /* transparent */

                /* One world pixel can cover several screen pixels, so fill
                 * the whole footprint rather than leaving gaps when zoomed. */
                float wx = room_x + ox + px;
                float wy = room_y + oy + py;
                int sx0 = (int)((wx - left) * scale);
                int sy0 = (int)((wy - top) * scale);
                int sx1 = (int)((wx + 1 - left) * scale);
                int sy1 = (int)((wy + 1 - top) * scale);
                if (sx1 <= sx0) sx1 = sx0 + 1;
                if (sy1 <= sy0) sy1 = sy0 + 1;

                int pal = gb->cgb ? (attr & ATTR_PALETTE) : 0;
                uint32_t colour = gb->cgb
                    ? colour_of(gb, gb->obj_palette, pal, shade)
                    : DMG_SHADES[((attr & 0x10 ? gb->io[0x49] : gb->io[0x48])
                                  >> (shade * 2)) & 3];

                for (int y = sy0; y < sy1; y++) {
                    if (y < 0 || y >= dst_h) continue;
                    for (int x = sx0; x < sx1; x++) {
                        if (x < 0 || x >= dst_w) continue;
                        dst[(size_t)y * dst_w + x] = colour;
                    }
                }
            }
        }
    }
}


/* Moving between rooms is a transition the game performs over many frames: it
 * scrolls the view while swapping which room is loaded. Placing the live
 * screen at the active room's corner therefore holds it still and then jumps a
 * whole room, which reads as the screen shifting.
 *
 * The game tracks where its screen actually is while that happens, so the
 * screen can be placed at its real position instead and simply travel.
 */
#define W_SCREEN_OFFSET_Y 0xCD08
#define W_SCREEN_OFFSET_X 0xCD09
#define W_TRANSITION_DIR  0xCD02
#define W_LOADING_ROOM    0xCC4B

int gb_world_screen_origin(const gb_t *gb, float *out_x, float *out_y)
{
    int room = gb_world_active_room(gb);
    if (room < 0)
        return 0;

    float x = (float)(room % GB_WORLD_COLS) * ROOM_PX_W;
    float y = (float)(room / GB_WORLD_COLS) * ROOM_PX_H;

    /* The offsets are signed pixel counts, and run opposite to the direction
     * the screen is travelling: the view slides one way as the world slides
     * the other. */
    int ox = (int8_t)gb->wram[W_SCREEN_OFFSET_X - 0xC000];
    int oy = (int8_t)gb->wram[W_SCREEN_OFFSET_Y - 0xC000];

    x -= (float)ox;
    y -= (float)oy;

    if (out_x) *out_x = x;
    if (out_y) *out_y = y;
    return 1;
}
