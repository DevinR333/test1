/* How the picture is framed, and the menu that chooses it.
 *
 * Shared by every frontend so that a mode means the same thing on a desktop
 * and on a handheld, and so the menu is one piece of code rather than one per
 * platform.
 */
#ifndef GB_DISPLAY_H
#define GB_DISPLAY_H

#include <stdint.h>

typedef enum {
    GB_VIEW_WORLD = 0,   /* the world fills the frame, drawn around the game */
    GB_VIEW_16_9,        /* the hardware's screen alone, framed 16:9 */
    GB_VIEW_4_3,         /* the hardware's screen alone, framed 4:3 */
    GB_VIEW_MODES
} gb_view_mode_t;

const char *gb_view_name(gb_view_mode_t mode);
const char *gb_view_note(gb_view_mode_t mode);

/* Where the framed picture sits inside a window of this size. For the two
 * fixed shapes this is the largest rectangle of that aspect that fits, and
 * within it the hardware's screen at its own shape, centred. */
typedef struct { int x, y, w, h; } gb_rect_t;
gb_rect_t gb_view_frame(gb_view_mode_t mode, int win_w, int win_h);
gb_rect_t gb_view_screen(gb_view_mode_t mode, int win_w, int win_h);

/* The world renderer's scale for "the screen at its natural size" in this
 * mode's frame. Zoom multiplies it. */
float gb_view_base_scale(gb_view_mode_t mode, int win_w, int win_h);

/* The chooser, drawn into the picture itself so it looks the same wherever it
 * runs and needs nothing from the platform but a buffer. */
void gb_menu_draw(uint32_t *dst, int dst_w, int dst_h, int selected);

/* Five pixels by seven, upper case and digits, which is all the menu asks
 * for. Drawn at whatever size suits the display. */
void gb_text_draw(uint32_t *dst, int dst_w, int dst_h, int x, int y,
                  const char *text, uint32_t colour, int scale);
int  gb_text_width(const char *text, int scale);

/* The same, mixed into the picture instead of replacing it. */
void gb_text_blend(uint32_t *dst, int dst_w, int dst_h, int x, int y,
                   const char *text, uint32_t colour, int scale,
                   float alpha);

#endif /* GB_DISPLAY_H */
