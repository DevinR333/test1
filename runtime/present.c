#include <string.h>
#include <math.h>
#include "present.h"

#define GB_ZOOM_MIN 1.0f
#define GB_ZOOM_MAX 4.0f

/* Anything faster than this in one frame is a teleport, a screen transition,
 * or OAM slot reuse - not motion. Interpolating across it draws a streak. */
#define DEFAULT_REJECT 16

void gb_present_init(gb_present_t *p)
{
    memset(p, 0, sizeof(*p));
    p->reject_threshold = DEFAULT_REJECT;
}

void gb_present_push(gb_present_t *p, const gb_t *gb)
{
    p->prev = p->cur;
    memcpy(p->cur.obj, gb->oam, sizeof(p->cur.obj));
    p->cur.scx   = gb->io[0x43];
    p->cur.scy   = gb->io[0x42];
    p->cur.wx    = gb->io[0x4B];
    p->cur.wy    = gb->io[0x4A];
    p->cur.lcdc  = gb->io[0x40];
    p->cur.valid = 1;
}

static inline float lerpf(float a, float b, float t) { return a + (b - a) * t; }

/* Scroll registers wrap at 256. Interpolating 254 -> 2 must travel forward
 * through the wrap (4 pixels), not backwards across the whole map (252). */
static uint8_t lerp_wrap8(uint8_t a, uint8_t b, float t)
{
    int d = (int)b - (int)a;
    if (d >  128) d -= 256;
    if (d < -128) d += 256;
    return (uint8_t)lrintf((float)a + (float)d * t);
}

static int is_offscreen(const gb_obj_t *o)
{
    /* The PPU hides a sprite by parking it outside the visible range. */
    return o->y == 0 || o->y >= 160 || o->x == 0 || o->x >= 168;
}

void gb_present_resolve(const gb_present_t *p, float alpha,
                        gb_obj_t *out_obj, uint8_t *out_scx, uint8_t *out_scy)
{
    float t = p->extrapolate ? 1.0f + alpha : alpha;

    if (!p->prev.valid) {
        memcpy(out_obj, p->cur.obj, sizeof(p->cur.obj));
        *out_scx = p->cur.scx;
        *out_scy = p->cur.scy;
        return;
    }

    for (int i = 0; i < GB_OBJ_COUNT; i++) {
        const gb_obj_t *a = &p->prev.obj[i];
        const gb_obj_t *b = &p->cur.obj[i];
        gb_obj_t *o = &out_obj[i];

        *o = *b;   /* tile and attributes always come from the newest frame */

        /* A different tile or a different palette in the same OAM slot means
         * the slot was reused by another object. There is no motion to
         * interpolate between two unrelated sprites. */
        if (a->tile != b->tile || a->attr != b->attr) continue;
        if (is_offscreen(a) || is_offscreen(b))       continue;

        int dx = (int)b->x - (int)a->x;
        int dy = (int)b->y - (int)a->y;
        if (dx > p->reject_threshold || dx < -p->reject_threshold) continue;
        if (dy > p->reject_threshold || dy < -p->reject_threshold) continue;

        o->x = (uint8_t)lrintf(lerpf(a->x, b->x, t));
        o->y = (uint8_t)lrintf(lerpf(a->y, b->y, t));
    }

    *out_scx = lerp_wrap8(p->prev.scx, p->cur.scx, t);
    *out_scy = lerp_wrap8(p->prev.scy, p->cur.scy, t);
}

float gb_clamp_zoom(float zoom)
{
    if (zoom < GB_ZOOM_MIN) return GB_ZOOM_MIN;
    if (zoom > GB_ZOOM_MAX) return GB_ZOOM_MAX;
    return zoom;
}

gb_viewport_t gb_fit_viewport(int screen_w, int screen_h, gb_fit_mode_t mode,
                              float zoom, float pan_x, float pan_y)
{
    gb_viewport_t v;
    memset(&v, 0, sizeof(v));

    if (screen_w <= 0 || screen_h <= 0) return v;

    float fit_w = (float)screen_w / GB_SCREEN_W;
    float fit_h = (float)screen_h / GB_SCREEN_H;
    float scale = fit_w < fit_h ? fit_w : fit_h;

    if (mode == GB_FIT_INTEGER) {
        float whole = floorf(scale);
        scale = whole < 1.0f ? 1.0f : whole;   /* never scale below 1:1 */
    }

    v.scale = scale;
    v.integer_scale = (scale == floorf(scale));

    /* The destination rect is the fitted image, centred. Zoom does not change
     * it - zoom crops the source instead, which is what keeps pixels square. */
    v.dst_w = (int)lrintf(GB_SCREEN_W * scale);
    v.dst_h = (int)lrintf(GB_SCREEN_H * scale);
    v.dst_x = (screen_w - v.dst_w) / 2;
    v.dst_y = (screen_h - v.dst_h) / 2;

    zoom = gb_clamp_zoom(zoom);
    v.src_w = GB_SCREEN_W / zoom;
    v.src_h = GB_SCREEN_H / zoom;

    /* Centre the crop, then apply pan, then clamp so it stays inside. */
    float max_x = GB_SCREEN_W - v.src_w;
    float max_y = GB_SCREEN_H - v.src_h;
    v.src_x = max_x * 0.5f + pan_x;
    v.src_y = max_y * 0.5f + pan_y;
    if (v.src_x < 0)     v.src_x = 0;
    if (v.src_y < 0)     v.src_y = 0;
    if (v.src_x > max_x) v.src_x = max_x;
    if (v.src_y > max_y) v.src_y = max_y;

    return v;
}
