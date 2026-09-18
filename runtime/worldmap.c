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
#include <limits.h>
#include <string.h>
#include "worldmap.h"

#define ROOM_PX_W (GB_ROOM_COLS * GB_METATILE_PX)   /* 160 */
#define ROOM_PX_H (GB_ROOM_ROWS * GB_METATILE_PX)   /* 128 */

/* Which tileset the machine currently has in video memory, and which season
 * the world is in - the season decides what every room looks like. */
/* The tileset the loaded room uses. Not wLoadedTilesetIndex ($cd28): the
 * expanded-tilesets patch stopped keeping that one up to date, so it lags a
 * room behind. */
#define W_LOADED_TILESET 0xCD20
#define W_SEASON         0xCC4E
#define W_SCROLL_MODE    0xCD00   /* 8 while a screen transition scrolls */

/* Metatile attribute bits, as the hardware reads them. */
#define ATTR_PALETTE 0x07
#define ATTR_BANK    0x08
#define ATTR_XFLIP   0x20
#define ATTR_YFLIP   0x40

static const uint32_t DMG_SHADES[4] = {
    0xFFE0F8D0, 0xFF88C070, 0xFF346856, 0xFF081820
};

/* Tiles come from the tileset's own graphics, not from video memory: the
 * hardware only ever holds the area the player is standing in.
 *
 * Both video memory banks are kept, one after the other. The overworld loads
 * each area's own tiles into bank 1 and reaches them through the metatile
 * attribute's bank bit, so a single bank holds the shared terrain and nothing
 * that makes one place look different from another. */
static inline uint8_t vram_at(const uint8_t *vram, int bank, uint16_t addr)
{
    return vram[(addr - 0x8000) + bank * 0x2000];
}

static uint32_t colour_of(const gb_t *gb, const uint8_t *pal, int mask,
                         int palette, int shade)
{
    if (!gb->cgb)
        return DMG_SHADES[(gb->io[0x47] >> (shade * 2)) & 3];

    /* A tileset defines background palettes 2 to 7. The first two are shared
     * across the whole game - the status bar, and what every area has in
     * common - and are loaded once rather than per area, so they are not in
     * the tileset's own data. Taking them from there anyway leaves them at
     * whatever the table was filled with, which is how parts of the world end
     * up a colour nothing in the game is. */
    if (!(mask & (1 << palette)))
        pal = gb->bg_palette;

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

    const int season = gb_world_season(gb);

    /* Which graphics the machine is holding right now, so tiles it animates -
     * water, flowers, the torches - can be taken from it rather than from a
     * fixed copy that would be frozen on one frame beside a live screen that
     * is not. Only worth trusting once a room is actually loaded. */
    int loaded_assets = -1;
    if (gb_world_in_overworld(gb) && !gb_world_scrolling(gb)) {
        int loaded = gb->wram[W_LOADED_TILESET - 0xC000] & 0x7F;
        loaded_assets = gb_world_tileset_asset[season][loaded];
    }

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

        /* Everything but the pixel within a tile is the same for eight world
         * pixels in a row, and at anything near natural size that is several
         * destination pixels. Working it out once per tile rather than once
         * per pixel, and stepping the source position by addition rather than
         * a multiply and a float conversion, is most of the cost of this
         * loop: at 1920x1080 a frame was taking longer than a frame lasts,
         * which both capped how often the window could be redrawn and -
         * because the game's own thread waits on this - held the game itself
         * to the same rate. That is what made a smooth scroll look like a
         * series of jumps. */
        int column = INT_MIN;          /* which tile column is prepared */
        uint8_t lo = 0, hi = 0, attr = 0;
        uint32_t shade_of[4] = { backdrop, backdrop, backdrop, backdrop };
        int solid = 0;                 /* nothing here; use the backdrop */

        long long wx_fx = (long long)(left * 65536.0f);
        const long long step = (long long)(inv * 65536.0f);

        for (int dx = 0; dx < dst_w; dx++, wx_fx += step) {
            int wx = (int)(wx_fx >> 16);     /* arithmetic: floors, as wanted */
            if (wx < 0 || wx >= GB_WORLD_W) {
                row[dx] = backdrop;
                continue;
            }

            if ((wx >> 3) != column) {
                column = wx >> 3;

                int room = room_row * GB_WORLD_COLS + (wx / ROOM_PX_W);
                int in_room_x = wx % ROOM_PX_W;

                /* The top bit of a room's tileset byte is a flag, not part
                 * of the number. */
                int tileset = gb_world_room_tileset[room] & 0x7F;
                int assets = gb_world_tileset_asset[season][tileset];
                solid = (assets == GB_TILESET_NONE);
                if (!solid) {
                    int layout = gb_world_room_layout[season][room];
                    uint8_t metatile =
                        gb_world_layouts[layout][mt_y * GB_ROOM_COLS
                                                 + in_room_x / GB_METATILE_PX];
                    int mapping = gb_world_tileset_mapping[season][tileset];
                    if (mapping >= gb_world_mapping_count)
                        mapping = 0;

                    /* Each metatile is four tiles: two across, two down.
                     *
                     * Its eight bytes are four tile indices followed by four
                     * attribute bytes, not four pairs of the two. Reading
                     * them as pairs takes an attribute byte as a tile index
                     * for half of every metatile, which draws real tiles in
                     * the wrong places - the tiles look right and the terrain
                     * does not. The distinction is visible in the data: the
                     * last four bytes of each metatile take only a couple of
                     * dozen distinct values across a whole tileset, as
                     * attribute bits do, while the first four span the range. */
                    int sub_x = in_room_x % GB_METATILE_PX;
                    int quadrant = (sub_y / 8) * 2 + (sub_x / 8);
                    const uint8_t *entry = &gb_world_mappings[mapping][metatile * 8];
                    uint8_t index = entry[quadrant];
                    attr = entry[4 + quadrant];

                    /* For the graphics the machine is holding, use the
                     * machine's own copy: the game animates tiles in place -
                     * water, flowers, torches - so a fixed copy is frozen on
                     * one frame beside a live screen that is not, and the
                     * join between them is then plainly visible. Identical
                     * graphics share an entry, so matching numbers here mean
                     * the machine really is holding this room's tiles. */
                    const uint8_t *tvram, *tpal;
                    int tmask;
                    if (assets == loaded_assets) {
                        tvram = gb->vram;
                        tpal  = gb->bg_palette;
                        tmask = 0xFF;
                    } else {
                        tvram = gb_world_tileset_vram[assets];
                        tpal  = gb_world_tileset_palette[assets];
                        tmask = gb_world_tileset_palette_mask[assets];
                    }

                    int py = sub_y % 8;
                    if (attr & ATTR_YFLIP) py = 7 - py;
                    uint16_t addr = tile_row_addr(index, py);
                    int bank = (attr & ATTR_BANK) ? 1 : 0;
                    lo = vram_at(tvram, bank, addr);
                    hi = vram_at(tvram, bank, addr + 1);

                    /* A tile has four colours; looking each up once here
                     * keeps the palette out of the per-pixel path. */
                    for (int k = 0; k < 4; k++)
                        shade_of[k] = colour_of(gb, tpal, tmask,
                                                attr & ATTR_PALETTE, k);
                }
            }

            if (solid) {
                row[dx] = backdrop;
                continue;
            }

            int px = wx & 7;
            if (attr & ATTR_XFLIP) px = 7 - px;
            int bit = 7 - px;
            row[dx] = shade_of[(((hi >> bit) & 1) << 1) | ((lo >> bit) & 1)];
        }
    }
}


/* Composites the hardware's own screen into the world, sampling it exactly as
 * the world around it is sampled.
 *
 * Drawing it with a separate blit meant two different resamplers over one
 * picture: at a window height that is not a whole multiple of 144 - which is
 * most of them, and every 16:9 one - the live screen landed a fraction of a
 * pixel away from the world drawn around it, and the join showed. Sampling it
 * here, through the same position and scale, they cannot disagree.
 *
 * Only the room is taken. The top sixteen rows are the status bar, which is
 * not part of the world and does not belong at the world's position.
 */
void gb_world_draw_screen(const gb_t *gb, uint32_t *dst, int dst_w, int dst_h,
                          float cam_x, float cam_y, float scale,
                          float screen_x, float screen_y)
{
    if (!dst || dst_w <= 0 || dst_h <= 0 || scale <= 0.0f)
        return;

    const float inv = 1.0f / scale;
    const float left = cam_x - (dst_w * 0.5f) * inv;
    const float top  = cam_y - (dst_h * 0.5f) * inv;
    const int room_h = GB_SCREEN_H - GB_STATUS_H;

    for (int dy = 0; dy < dst_h; dy++) {
        int sy = (int)((top + dy * inv) - screen_y);
        if (sy < 0 || sy >= room_h)
            continue;
        const uint32_t *src = gb->framebuffer + (sy + GB_STATUS_H) * GB_SCREEN_W;
        uint32_t *row = dst + (size_t)dy * dst_w;

        long long wx_fx = (long long)((left - screen_x) * 65536.0f);
        const long long step = (long long)(inv * 65536.0f);
        for (int dx = 0; dx < dst_w; dx++, wx_fx += step) {
            int sx = (int)(wx_fx >> 16);
            if (sx >= 0 && sx < GB_SCREEN_W)
                row[dx] = src[sx];
        }
    }
}


/* The status bar belongs to the player, not to the world, so it stays at the
 * top of the window however the camera moves and whatever the zoom - rather
 * than riding along with the screen and wandering off with it.
 */
void gb_world_draw_status(const gb_t *gb, uint32_t *dst, int dst_w, int dst_h,
                          float scale)
{
    if (!dst || dst_w <= 0 || dst_h <= 0 || scale <= 0.0f)
        return;

    int h = (int)(GB_STATUS_H * scale + 0.5f);
    int w = (int)(GB_SCREEN_W * scale + 0.5f);
    if (h < 1 || w < 1) return;
    if (h > dst_h) h = dst_h;

    /* The bar's own background, so the rest of the width belongs with it
     * rather than cutting it off in the middle of the window. */
    uint32_t back = gb->framebuffer[0];
    int x0 = (dst_w - w) / 2;

    for (int dy = 0; dy < h; dy++) {
        int sy = dy * GB_STATUS_H / h;
        const uint32_t *src = gb->framebuffer + sy * GB_SCREEN_W;
        uint32_t *row = dst + (size_t)dy * dst_w;
        for (int dx = 0; dx < dst_w; dx++) {
            int sx = dx - x0;
            row[dx] = (sx >= 0 && sx < w) ? src[sx * GB_SCREEN_W / w] : back;
        }
    }
}


/* The game keeps the room it is standing in at a known address in work RAM.
 * Reading it lets the map open where the player is rather than at the middle
 * of the world. */
#define W_ACTIVE_ROOM 0xCC4C

/* The game crosses a room boundary by scrolling the whole screen over about
 * fifty frames, moving Link at three eighths of a pixel a frame while it does.
 * Nothing else happens in that time, so at the game's own pace it reads as a
 * pause between one screen and the next - the thing that makes a world drawn
 * in one piece still feel like a set of separate screens. */
int gb_world_scrolling(const gb_t *gb)
{
    return gb->wram[W_SCROLL_MODE - 0xC000] == 0x08;
}

/* Link's position within the room he is counted against, in whole pixels.
 *
 * Unlike his world position this needs no tracking state, so the thread
 * running the game can read it safely while the window is drawing. During a
 * crossing it moves steadily and does not wrap; the wrap happens on the frame
 * the crossing finishes, by which point the game is no longer scrolling. */
#define W_LINK_OBJECT_ID 0xCC48

int gb_world_link_step(const gb_t *gb, int *out_x, int *out_y)
{
    int object = gb->wram[W_LINK_OBJECT_ID - 0xC000];
    if (object < 0xD0 || object > 0xDF)
        return 0;
    const uint8_t *o = &gb->wram[0x1000 + ((object << 8) - 0xD000)];
    if (out_x) *out_x = o[0x0D];
    if (out_y) *out_y = o[0x0B];
    return 1;
}

int gb_world_season(const gb_t *gb)
{
    return gb->wram[W_SEASON - 0xC000] & (GB_SEASONS - 1);
}

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
                    ? colour_of(gb, gb->obj_palette, 0xFF, pal, shade)
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
#define W_SCREEN_OFFSET_Y   0xCD08
#define W_SCREEN_OFFSET_X   0xCD09
#define W_TRANSITION_STATE  0xCD04
#define W_TRANSITION_STATE2 0xCD05
#define H_CAMERA_Y          0xFFA8   /* sixteen bits, low byte first */
#define H_CAMERA_X          0xFFAA

/* Where the tracking stands. The offsets only tell us a position to the
 * nearest screen-width, so the full position is carried from frame to frame
 * and each new reading is resolved against it. */
static struct {
    int   valid;
    int   kx, ky;      /* what to add to a reading to get a world position */
    float x, y;
    float anchor_x, anchor_y;   /* the room the objects' coordinates are in */
} origin;

/* The value congruent to `raw` modulo 256 that lies closest to `anchor`. A
 * transition covers less than a whole 256, so there is never a tie. */
static float resolve(float anchor, int raw)
{
    float v = (float)raw;
    while (v - anchor > 128.0f)  v -= 256.0f;
    while (anchor - v > 128.0f)  v += 256.0f;
    return v;
}

int gb_world_screen_origin(const gb_t *gb, float *out_x, float *out_y)
{
    int room = gb_world_active_room(gb);
    if (room < 0 || !gb_world_in_overworld(gb)) {
        origin.valid = 0;
        return 0;
    }

    int base_x = (room % GB_WORLD_COLS) * ROOM_PX_W;
    int base_y = (room / GB_WORLD_COLS) * ROOM_PX_H;

    /* The scroll the hardware is given is the camera's position within the
     * area plus the offset of the area itself, and during a transition the
     * camera moves a few pixels every frame. Reading only the offset gives a
     * position that holds still for the whole transition and then jumps a
     * whole room at the end of it, which is the shift the player sees. */
    int cx = gb->hram[H_CAMERA_X - 0xFF80]
           | ((int)gb->hram[H_CAMERA_X + 1 - 0xFF80] << 8);
    int cy = gb->hram[H_CAMERA_Y - 0xFF80]
           | ((int)gb->hram[H_CAMERA_Y + 1 - 0xFF80] << 8);

    int raw_x = (cx + gb->wram[W_SCREEN_OFFSET_X - 0xC000]) & 0xFF;
    int raw_y = (cy + gb->wram[W_SCREEN_OFFSET_Y - 0xC000]) & 0xFF;

    /* Both are counted from wherever the game last set them, so they say
     * where the screen is only up to a whole multiple of 256 pixels. Standing
     * still in a known room pins that multiple down; a transition then moves
     * from there without ever needing it pinned down again. */
    int settled = gb->wram[W_TRANSITION_STATE - 0xC000] == 0x02
               && gb->wram[W_TRANSITION_STATE2 - 0xC000] == 0x00;

    if (settled || !origin.valid) {
        origin.kx = (base_x - raw_x) & 0xFF;
        origin.ky = (base_y - raw_y) & 0xFF;
        origin.x  = (float)base_x;
        origin.y  = (float)base_y;
        origin.anchor_x = (float)base_x;
        origin.anchor_y = (float)base_y;
        origin.valid = 1;
    } else {
        origin.x = resolve(origin.x, (raw_x + origin.kx) & 0xFF);
        origin.y = resolve(origin.y, (raw_y + origin.ky) & 0xFF);
    }

    if (out_x) *out_x = origin.x;
    if (out_y) *out_y = origin.y;
    return 1;
}


/* Where Link is in the world.
 *
 * His own coordinates are measured within the room he started the transition
 * in, and stay that way for the whole of it: walking east they run past the
 * room's width - 154, 160, 169 - and only wrap to 9 when the transition
 * finishes and the room he is counted against becomes the new one. Added to
 * the room he is counted against, rather than to where the screen currently
 * is, that makes one unbroken line across the boundary: 1274, 1278, ... 1289,
 * and 1289 again on the far side.
 *
 * A camera on that line never jumps, so there is no shift from one screen to
 * the next - the world simply travels past as he walks.
 */
#define W_LINK_OBJECT 0xCC48   /* high byte of whichever object Link is */

int gb_world_link_position(const gb_t *gb, float *out_x, float *out_y)
{
    float sx, sy;
    if (!gb_world_screen_origin(gb, &sx, &sy))   /* also updates the anchor */
        return 0;

    /* Riding an animal makes a different object the one being steered. */
    int object = gb->wram[W_LINK_OBJECT - 0xC000];
    if (object < 0xD0 || object > 0xDF)
        return 0;

    /* Objects live in the second bank of work RAM. */
    const uint8_t *o = &gb->wram[0x1000 + ((object << 8) - 0xD000)];

    if (out_x) *out_x = origin.anchor_x + o[0x0D];   /* x, whole pixels */
    if (out_y) *out_y = origin.anchor_y + o[0x0B];   /* y, whole pixels */
    return 1;
}


/* Group 0 is the overworld. Everywhere else - dungeons, interiors, menus and
 * the opening - is either a different set of rooms or no rooms at all, and the
 * world data describes none of it. Drawing it anyway surrounds the screen with
 * scenery from somewhere the player is not: the file-select and name-entry
 * screens showed a strip of Horon Village beside them, because a group of zero
 * is also what work RAM holds before the game has set anything.
 */
#define W_GAME_STATE  0xC2EE   /* 2 once a room is loaded and running */
#define W_OPENED_MENU 0xCBCB   /* nonzero while a menu covers the screen */
#define W_ACTIVE_GROUP 0xCC49

int gb_world_in_overworld(const gb_t *gb)
{
    /* Anything before the first room - the logos, the title, choosing and
     * naming a file - runs with the game state still at its initial value. */
    if (gb->wram[W_GAME_STATE - 0xC000] != 0x02)
        return 0;

    /* The inventory, the map and the save prompt each take over the whole
     * display while the room stays loaded behind them. */
    if (gb->wram[W_OPENED_MENU - 0xC000] != 0x00)
        return 0;

    if (gb->wram[W_ACTIVE_GROUP - 0xC000] != gb_world_group)
        return 0;

    /* The display being off means the game is between places rather than in
     * one. */
    if (!(gb->io[0x40] & 0x80))
        return 0;

    return 1;
}
