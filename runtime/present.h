/* Presentation: screen fitting, zoom, and frame interpolation.
 *
 * Two things live here, both of which sit *outside* the emulated machine and
 * therefore cannot change how the game plays.
 *
 * Fitting    The Game Boy screen is 160x144 (10:9). Phone screens are not.
 *            Fitting scales it up by a whole multiple and pillarboxes the
 *            remainder, so pixels stay square and no row is doubled while its
 *            neighbour is not.
 *
 * Interpolation  The game is a fixed-timestep 59.73Hz simulation; running its
 *            logic faster would speed up every animation counter and cooldown
 *            in it. So logic stays at 59.73Hz and the *presenter* generates
 *            extra frames by interpolating sprite positions and background
 *            scroll between the last two game frames. Motion gets smoother,
 *            game speed does not change.
 *
 *            This costs one game frame of latency: to interpolate toward frame
 *            N+1 you must already have it. Set GB_PRESENT_EXTRAPOLATE to trade
 *            that latency for mild overshoot on direction changes instead.
 */
#ifndef GB_PRESENT_H
#define GB_PRESENT_H

#include <stdint.h>
#include "gb.h"

#define GB_OBJ_COUNT 40

/* A sprite as the PPU sees it, straight out of OAM. */
typedef struct {
    uint8_t y, x, tile, attr;
} gb_obj_t;

/* Everything the presenter needs from one game frame. */
typedef struct {
    gb_obj_t obj[GB_OBJ_COUNT];
    uint8_t  scx, scy, wx, wy, lcdc;
    uint8_t  valid;
} gb_snapshot_t;

typedef enum {
    GB_FIT_INTEGER,   /* largest whole multiple; pillarbox the rest. Sharpest. */
    GB_FIT_ASPECT,    /* fill the smaller axis exactly; may be fractional.     */
} gb_fit_mode_t;

/* Where to draw, and which part of the 160x144 to draw. */
typedef struct {
    int   dst_x, dst_y, dst_w, dst_h;   /* destination rect, screen pixels */
    float src_x, src_y, src_w, src_h;   /* source rect within the GB screen */
    float scale;
    int   integer_scale;                /* nonzero if scale is a whole number */
} gb_viewport_t;

typedef struct {
    gb_snapshot_t prev, cur;
    int   reject_threshold;   /* px of movement above which we snap, not lerp */
    int   extrapolate;
} gb_present_t;

void gb_present_init(gb_present_t *p);

/* Call once per *game* frame, after the frame's logic has run. */
void gb_present_push(gb_present_t *p, const gb_t *gb);

/* Call once per *display* frame. alpha runs 0..1 between the two game frames;
 * at 120Hz output with 60Hz logic you pass 0.0 then 0.5. Writes GB_OBJ_COUNT
 * sprites and the blended scroll registers. */
void gb_present_resolve(const gb_present_t *p, float alpha,
                        gb_obj_t *out_obj, uint8_t *out_scx, uint8_t *out_scy);

/* Screen fitting. zoom 1.0 shows the whole screen; above that crops and
 * magnifies. pan is in Game Boy pixels and is clamped to keep the source rect
 * on screen. */
gb_viewport_t gb_fit_viewport(int screen_w, int screen_h, gb_fit_mode_t mode,
                              float zoom, float pan_x, float pan_y);

/* Clamp a zoom request to something that still shows a usable area. */
float gb_clamp_zoom(float zoom);

#endif /* GB_PRESENT_H */
