/* On-screen controls.
 *
 * A pad's worth of buttons drawn into the composed picture, laid out from the
 * window's own shape so they land under the thumbs at any size. They are not
 * a separate input path: a touch sets the same bits a gamepad sets, so every
 * menu the game has - and the display chooser over it - is driven by them
 * without knowing they exist.
 */
#ifndef GB_TOUCH_H
#define GB_TOUCH_H

#include <stdint.h>

typedef enum {
    GB_TOUCH_UP, GB_TOUCH_DOWN, GB_TOUCH_LEFT, GB_TOUCH_RIGHT,
    GB_TOUCH_A, GB_TOUCH_B, GB_TOUCH_START, GB_TOUCH_SELECT,
    GB_TOUCH_ZOOM_OUT, GB_TOUCH_ZOOM_IN,
    GB_TOUCH_COUNT
} gb_touch_id_t;

/* The joypad bit each control presses, or -1 for the two that do not press
 * one. The order matches the runtime's own: A, B, Select, Start, then right,
 * left, up, down. */
int gb_touch_button(gb_touch_id_t id);

typedef struct { float x, y, r; } gb_touch_spot_t;

/* Where each control sits, in pixels of a window this size. */
void gb_touch_layout(int win_w, int win_h, gb_touch_spot_t out[GB_TOUCH_COUNT]);

/* Which control a point is on, or -1 for none. Coordinates are the window's
 * own, as a touch reports them. */
int gb_touch_hit(int win_w, int win_h, float x, float y);

/* Draws the controls into a picture of any size - the layout is worked out
 * again at that size, so it lands in the same place on screen however the
 * picture is scaled. `held` is a bit per control, `alpha` its opacity. */
void gb_touch_draw(uint32_t *dst, int dst_w, int dst_h,
                   uint32_t held, float alpha);

#endif
