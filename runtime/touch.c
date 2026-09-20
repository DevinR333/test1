#include "touch.h"
#include "display.h"

#include <math.h>
#include <stddef.h>

/* The runtime's joypad bits: A, B, Select, Start, right, left, up, down. */
int gb_touch_button(gb_touch_id_t id)
{
    switch (id) {
    case GB_TOUCH_A:      return 0;
    case GB_TOUCH_B:      return 1;
    case GB_TOUCH_SELECT: return 2;
    case GB_TOUCH_START:  return 3;
    case GB_TOUCH_RIGHT:  return 4;
    case GB_TOUCH_LEFT:   return 5;
    case GB_TOUCH_UP:     return 6;
    case GB_TOUCH_DOWN:   return 7;
    default:              return -1;   /* the zoom pair press nothing */
    }
}

/* Sized from the shorter side, so the controls are the same size in the hand
 * whatever the panel's shape, and placed at the corners a thumb reaches
 * without the hand leaving the edge. */
void gb_touch_layout(int win_w, int win_h, gb_touch_spot_t out[GB_TOUCH_COUNT])
{
    float w = (float)win_w, h = (float)win_h;
    float u = (w < h ? w : h) * 0.155f;       /* one control's reach */
    if (u < 18.0f) u = 18.0f;

    float dx = u * 1.40f, dy = h - u * 1.50f;    /* the d-pad's centre */
    float br = u * 0.44f;

    out[GB_TOUCH_UP]    = (gb_touch_spot_t){ dx,            dy - u * 0.72f, br };
    out[GB_TOUCH_DOWN]  = (gb_touch_spot_t){ dx,            dy + u * 0.72f, br };
    out[GB_TOUCH_LEFT]  = (gb_touch_spot_t){ dx - u * 0.72f, dy,            br };
    out[GB_TOUCH_RIGHT] = (gb_touch_spot_t){ dx + u * 0.72f, dy,            br };

    /* A above and right of B, the way a handheld sets them, so the thumb
     * rolls between the two rather than reaching across. */
    out[GB_TOUCH_A] = (gb_touch_spot_t){ w - u * 0.95f, h - u * 1.70f, br * 1.08f };
    out[GB_TOUCH_B] = (gb_touch_spot_t){ w - u * 2.00f, h - u * 1.05f, br * 1.08f };

    /* The small pair, where they are on the hardware: middle, at the bottom. */
    out[GB_TOUCH_START]  = (gb_touch_spot_t){ w * 0.5f + u * 0.75f, h - u * 0.62f,
                                              br * 0.80f };
    out[GB_TOUCH_SELECT] = (gb_touch_spot_t){ w * 0.5f - u * 0.75f, h - u * 0.62f,
                                              br * 0.80f };

    /* Clear of the status bar, which is the top eighth or so of the picture. */
    out[GB_TOUCH_ZOOM_IN]  = (gb_touch_spot_t){ w - u * 0.70f, h * 0.24f, br * 0.80f };
    out[GB_TOUCH_ZOOM_OUT] = (gb_touch_spot_t){ w - u * 1.75f, h * 0.24f, br * 0.80f };
}

int gb_touch_hit(int win_w, int win_h, float x, float y)
{
    gb_touch_spot_t s[GB_TOUCH_COUNT];
    gb_touch_layout(win_w, win_h, s);

    int best = -1;
    float best_d = 0.0f;
    for (int i = 0; i < GB_TOUCH_COUNT; i++) {
        float ddx = x - s[i].x, ddy = y - s[i].y;
        float d = ddx * ddx + ddy * ddy;

        /* A generous margin on the four directions: a thumb on a sheet of
         * glass has no edge to find, and missing a step of a d-pad is worse
         * than pressing one slightly early. The buttons keep their own size,
         * so A and B cannot be hit by accident. */
        float r = s[i].r * (i <= GB_TOUCH_RIGHT ? 1.30f : 1.05f);
        if (d > r * r) continue;
        if (best < 0 || d < best_d) { best = i; best_d = d; }
    }
    return best;
}

static void blend(uint32_t *p, uint32_t colour, float a)
{
    if (a <= 0.0f) return;
    if (a > 1.0f) a = 1.0f;
    uint32_t d = *p;
    int dr = (d >> 16) & 0xFF, dg = (d >> 8) & 0xFF, db = d & 0xFF;
    int sr = (colour >> 16) & 0xFF, sg = (colour >> 8) & 0xFF, sb = colour & 0xFF;
    int r = (int)(dr + (sr - dr) * a);
    int g = (int)(dg + (sg - dg) * a);
    int b = (int)(db + (sb - db) * a);
    *p = ((uint32_t)r << 16) | ((uint32_t)g << 8) | (uint32_t)b;
}

/* A filled disc with a lighter rim. The edge is softened over a pixel so the
 * controls do not look like cut paper against the picture behind them. */
static void disc(uint32_t *dst, int w, int h, float cx, float cy, float r,
                 uint32_t fill, uint32_t rim, float alpha)
{
    int x0 = (int)(cx - r - 2), x1 = (int)(cx + r + 2);
    int y0 = (int)(cy - r - 2), y1 = (int)(cy + r + 2);
    if (x0 < 0) x0 = 0;
    if (y0 < 0) y0 = 0;
    if (x1 > w - 1) x1 = w - 1;
    if (y1 > h - 1) y1 = h - 1;

    float inner = r - r * 0.16f;
    for (int y = y0; y <= y1; y++) {
        uint32_t *row = dst + (size_t)y * w;
        for (int x = x0; x <= x1; x++) {
            float ddx = x + 0.5f - cx, ddy = y + 0.5f - cy;
            float d = sqrtf(ddx * ddx + ddy * ddy);
            if (d > r + 1.0f) continue;
            float edge = r + 1.0f - d;
            if (edge > 1.0f) edge = 1.0f;
            if (d >= inner) blend(&row[x], rim, alpha * 0.85f * edge);
            else            blend(&row[x], fill, alpha * 0.34f);
        }
    }
}

/* A solid triangle pointing one of four ways, for the d-pad. */
static void arrow(uint32_t *dst, int w, int h, float cx, float cy, float r,
                  int dir, uint32_t colour, float alpha)
{
    float s = r * 0.52f;
    for (int y = (int)(cy - s); y <= (int)(cy + s); y++) {
        if (y < 0 || y >= h) continue;
        uint32_t *row = dst + (size_t)y * w;
        for (int x = (int)(cx - s); x <= (int)(cx + s); x++) {
            if (x < 0 || x >= w) continue;
            float px = (x + 0.5f - cx) / s, py = (y + 0.5f - cy) / s;
            float along, across;
            switch (dir) {
            case GB_TOUCH_UP:    along = -py; across = px; break;
            case GB_TOUCH_DOWN:  along =  py; across = px; break;
            case GB_TOUCH_LEFT:  along = -px; across = py; break;
            default:             along =  px; across = py; break;
            }
            if (along < -0.5f || along > 0.7f) continue;
            float half = (0.7f - along) * 0.62f;
            if (across < -half || across > half) continue;
            blend(&row[x], colour, alpha * 0.92f);
        }
    }
}

/* A bar, and optionally a crossing one: minus and plus for the zoom pair. */
static void plus(uint32_t *dst, int w, int h, float cx, float cy, float r,
                 int vertical_too, uint32_t colour, float alpha)
{
    float len = r * 0.5f, thick = r * 0.15f;
    if (thick < 1.0f) thick = 1.0f;
    for (int y = (int)(cy - len); y <= (int)(cy + len); y++) {
        if (y < 0 || y >= h) continue;
        uint32_t *row = dst + (size_t)y * w;
        for (int x = (int)(cx - len); x <= (int)(cx + len); x++) {
            if (x < 0 || x >= w) continue;
            float ddx = x + 0.5f - cx, ddy = y + 0.5f - cy;
            int on = (ddy > -thick && ddy < thick && ddx > -len && ddx < len);
            if (vertical_too && ddx > -thick && ddx < thick
                             && ddy > -len && ddy < len)
                on = 1;
            if (on) blend(&row[x], colour, alpha * 0.92f);
        }
    }
}

/* A word centred in a control, at whatever size the control can hold. */
static void label(uint32_t *dst, int w, int h, float cx, float cy, float r,
                  const char *text, uint32_t colour, float alpha)
{
    int scale = (int)(r / 13.0f);
    if (scale < 1) scale = 1;
    while (scale > 1 && gb_text_width(text, scale) > r * 1.7f)
        scale--;
    int tw = gb_text_width(text, scale);
    gb_text_blend(dst, w, h, (int)(cx - tw / 2.0f), (int)(cy - 3.5f * scale),
                  text, colour, scale, alpha * 0.95f);
}

void gb_touch_draw(uint32_t *dst, int dst_w, int dst_h,
                   uint32_t held, float alpha)
{
    if (alpha <= 0.0f || !dst) return;

    gb_touch_spot_t s[GB_TOUCH_COUNT];
    gb_touch_layout(dst_w, dst_h, s);

    for (int i = 0; i < GB_TOUCH_COUNT; i++) {
        int down = (held >> i) & 1;
        uint32_t fill = down ? 0xFFFFFF : 0x101418;
        uint32_t mark = down ? 0x101418 : 0xF2F4F6;
        uint32_t rim  = down ? 0xFFFFFF : 0xD8DCE0;

        disc(dst, dst_w, dst_h, s[i].x, s[i].y, s[i].r, fill, rim, alpha);

        switch (i) {
        case GB_TOUCH_UP: case GB_TOUCH_DOWN:
        case GB_TOUCH_LEFT: case GB_TOUCH_RIGHT:
            arrow(dst, dst_w, dst_h, s[i].x, s[i].y, s[i].r, i, mark, alpha);
            break;
        case GB_TOUCH_A:
            label(dst, dst_w, dst_h, s[i].x, s[i].y, s[i].r, "A", mark, alpha);
            break;
        case GB_TOUCH_B:
            label(dst, dst_w, dst_h, s[i].x, s[i].y, s[i].r, "B", mark, alpha);
            break;
        case GB_TOUCH_START:
            /* Named, not drawn as a shape. A pill for START and a pill for
             * SELECT are the same pill, and a minus for zooming out is the
             * same again - three controls the player would have to learn by
             * pressing. The words cost nothing. */
            label(dst, dst_w, dst_h, s[i].x, s[i].y, s[i].r, "START", mark, alpha);
            break;
        case GB_TOUCH_SELECT:
            label(dst, dst_w, dst_h, s[i].x, s[i].y, s[i].r, "MAP", mark, alpha);
            break;
        case GB_TOUCH_ZOOM_IN:
            plus(dst, dst_w, dst_h, s[i].x, s[i].y, s[i].r, 1, mark, alpha);
            break;
        case GB_TOUCH_ZOOM_OUT:
            plus(dst, dst_w, dst_h, s[i].x, s[i].y, s[i].r, 0, mark, alpha);
            break;
        default:
            break;
        }
    }
}
