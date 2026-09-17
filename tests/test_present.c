/* Checks the two bits of presentation maths that are easy to get subtly wrong:
 * viewport fitting on real phone resolutions, and wrap-aware scroll blending. */
#include <stdio.h>
#include <string.h>
#include "present.h"

static int failures = 0;

#define CHECK(cond, fmt, ...) do { \
    if (!(cond)) { printf("  FAIL: " fmt "\n", ##__VA_ARGS__); failures++; } \
} while (0)

static void test_fit(void)
{
    struct { int w, h; const char *name; } screens[] = {
        {2400, 1080, "1080p phone, landscape"},
        {2340, 1080, "Pixel-class, landscape"},
        {1080, 2400, "1080p phone, portrait"},
        {2778, 1284, "large phone, landscape"},
        {160,   144, "exactly 1:1"},
    };

    printf("viewport fitting (integer mode):\n");
    for (size_t i = 0; i < sizeof(screens) / sizeof(screens[0]); i++) {
        gb_viewport_t v = gb_fit_viewport(screens[i].w, screens[i].h,
                                          GB_FIT_INTEGER, 1.0f, 0, 0);
        printf("  %-26s %4dx%-4d -> %dx%d at (%d,%d), scale %.0fx\n",
               screens[i].name, screens[i].w, screens[i].h,
               v.dst_w, v.dst_h, v.dst_x, v.dst_y, (double)v.scale);

        CHECK(v.integer_scale, "%s: scale not integral", screens[i].name);
        CHECK(v.dst_w <= screens[i].w && v.dst_h <= screens[i].h,
              "%s: image overflows the screen", screens[i].name);
        CHECK(v.dst_x >= 0 && v.dst_y >= 0, "%s: negative origin", screens[i].name);
        /* Square pixels: the destination must keep 160:144 exactly. */
        CHECK(v.dst_w * GB_SCREEN_H == v.dst_h * GB_SCREEN_W,
              "%s: aspect ratio distorted (%dx%d)", screens[i].name,
              v.dst_w, v.dst_h);
    }
}

static void test_zoom(void)
{
    printf("\nzoom and pan clamping:\n");
    gb_viewport_t a = gb_fit_viewport(2400, 1080, GB_FIT_INTEGER, 1.0f, 0, 0);
    CHECK(a.src_w == GB_SCREEN_W && a.src_h == GB_SCREEN_H,
          "zoom 1.0 should show the whole screen, got %.1fx%.1f",
          (double)a.src_w, (double)a.src_h);

    gb_viewport_t b = gb_fit_viewport(2400, 1080, GB_FIT_INTEGER, 2.0f, 0, 0);
    CHECK(b.src_w == 80.0f && b.src_h == 72.0f,
          "zoom 2.0 should crop to 80x72, got %.1fx%.1f",
          (double)b.src_w, (double)b.src_h);
    CHECK(b.dst_w == a.dst_w && b.dst_h == a.dst_h,
          "zoom must not change the destination rect");

    /* Panning hard against the edge must not walk the crop off the screen. */
    gb_viewport_t c = gb_fit_viewport(2400, 1080, GB_FIT_INTEGER, 2.0f, 999, 999);
    CHECK(c.src_x + c.src_w <= GB_SCREEN_W + 0.01f,
          "pan ran off the right edge: src_x=%.1f", (double)c.src_x);
    CHECK(c.src_y + c.src_h <= GB_SCREEN_H + 0.01f,
          "pan ran off the bottom edge: src_y=%.1f", (double)c.src_y);

    gb_viewport_t d = gb_fit_viewport(2400, 1080, GB_FIT_INTEGER, 99.0f, 0, 0);
    CHECK(d.src_w >= GB_SCREEN_W / 4.0f, "zoom not clamped: src_w=%.1f",
          (double)d.src_w);
    printf("  zoom 1x/2x/clamped, pan clamping: ok\n");
}

static void test_interpolation(void)
{
    printf("\nframe interpolation:\n");
    gb_t gb;
    gb_present_t p;
    gb_obj_t out[GB_OBJ_COUNT];
    uint8_t scx, scy;

    memset(&gb, 0, sizeof(gb));
    gb_present_init(&p);

    /* Frame 1: one sprite at (40,40), scroll 0. */
    gb.oam[0] = 40; gb.oam[1] = 40; gb.oam[2] = 0x10; gb.oam[3] = 0;
    gb.io[0x43] = 0; gb.io[0x42] = 0;
    gb_present_push(&p, &gb);

    /* Frame 2: it moved 4 pixels right, scroll advanced 2. */
    gb.oam[1] = 44;
    gb.io[0x43] = 2;
    gb_present_push(&p, &gb);

    gb_present_resolve(&p, 0.5f, out, &scx, &scy);
    CHECK(out[0].x == 42, "midpoint x should be 42, got %d", out[0].x);
    CHECK(scx == 1, "midpoint scroll should be 1, got %d", scx);
    printf("  halfway between x=40 and x=44 -> x=%d, scroll -> %d\n", out[0].x, scx);

    /* A teleport must snap, not streak across the screen. */
    gb.oam[1] = 140;
    gb_present_push(&p, &gb);
    gb_present_resolve(&p, 0.5f, out, &scx, &scy);
    CHECK(out[0].x == 140, "teleport should snap to 140, got %d", out[0].x);
    printf("  44 -> 140 in one frame snaps to %d (no streak)\n", out[0].x);

    /* OAM slot reused by a different object: also snap. */
    gb.oam[1] = 60; gb.oam[2] = 0x55;
    gb_present_push(&p, &gb);
    gb_present_resolve(&p, 0.5f, out, &scx, &scy);
    CHECK(out[0].x == 60, "slot reuse should snap to 60, got %d", out[0].x);
    printf("  slot reused by a different tile snaps to %d\n", out[0].x);

    /* Scroll wrapping the 256 boundary must take the short way round. */
    memset(&gb, 0, sizeof(gb));
    gb_present_init(&p);
    gb.io[0x43] = 254; gb_present_push(&p, &gb);
    gb.io[0x43] = 2;   gb_present_push(&p, &gb);
    gb_present_resolve(&p, 0.5f, out, &scx, &scy);
    CHECK(scx == 0, "254 -> 2 should pass through 0, got %d", scx);
    printf("  scroll 254 -> 2 interpolates through %d, not backwards\n", scx);
}

int main(void)
{
    test_fit();
    test_zoom();
    test_interpolation();
    printf("\n%s\n", failures ? "FAILURES" : "all presentation tests passed");
    return failures != 0;
}
