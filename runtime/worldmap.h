/* World map rendering.
 *
 * The hardware draws a 160x144 window and keeps only the current room in
 * video memory, so a camera that shows more than that has to draw the world
 * itself: room layouts and metatile definitions come from generated data, and
 * the tile pixels and colours come from whatever the game currently has
 * loaded.
 */
#ifndef GB_WORLDMAP_H
#define GB_WORLDMAP_H

#include <stdint.h>
#include "gb.h"

#define GB_ROOM_COLS     10
#define GB_ROOM_ROWS     8
#define GB_ROOM_TILES    (GB_ROOM_COLS * GB_ROOM_ROWS)
#define GB_WORLD_COLS    16
#define GB_WORLD_ROWS    16
#define GB_WORLD_ROOMS   (GB_WORLD_COLS * GB_WORLD_ROWS)
#define GB_METATILE_PX   16

/* The game keeps the top of the display for the status bar and scrolls the
 * room down past it, so the room occupies rows 16 to 143. */
#define GB_STATUS_H      16
#define GB_MAPPING_BYTES 2048
#define GB_TILESET_VRAM    0x4000   /* both banks, bank 1 after bank 0 */
#define GB_TILESET_PALETTE 64

#define GB_WORLD_W (GB_WORLD_COLS * GB_ROOM_COLS * GB_METATILE_PX)   /* 2560 */
#define GB_WORLD_H (GB_WORLD_ROWS * GB_ROOM_ROWS * GB_METATILE_PX)   /* 2048 */

#define GB_TILESET_SLOTS 128   /* a room's tileset byte names one of these */
#define GB_SEASONS       4     /* spring, summer, autumn, winter */

extern const int     gb_world_group;

/* The season changes the tiles, the colours and the layout of every room in
 * the overworld, and the game picks it at run time - so all four are here and
 * the renderer chooses, rather than one being baked in. Tables shared between
 * seasons are stored once. */
extern const uint8_t  gb_world_room_tileset[GB_WORLD_ROOMS];
extern const uint8_t  gb_world_layouts[][GB_ROOM_TILES];
extern const int      gb_world_layout_count;
extern const uint16_t gb_world_room_layout[GB_SEASONS][GB_WORLD_ROOMS];
extern const uint8_t  gb_world_mappings[][GB_MAPPING_BYTES];
extern const int      gb_world_mapping_count;
extern const uint16_t gb_world_tileset_mapping[GB_SEASONS][GB_TILESET_SLOTS];
extern const uint8_t  gb_world_tileset_vram[][GB_TILESET_VRAM];
extern const uint8_t  gb_world_tileset_palette[][GB_TILESET_PALETTE];
extern const uint8_t  gb_world_tileset_palette_mask[];
extern const int      gb_world_tileset_count;

/* Which graphics a tileset uses in a given season, or none for one no room in
 * this group ever asks for. Identical graphics share an entry, so two tilesets
 * with the same number here really are interchangeable. */
extern const uint8_t  gb_world_tileset_asset[GB_SEASONS][GB_TILESET_SLOTS];
#define GB_TILESET_NONE 0xFF

/* Link's position within his room, in whole pixels. Needs no tracking state,
 * so it is safe to read while the window is drawing. */
int gb_world_link_step(const gb_t *gb, int *out_x, int *out_y);

/* Whether the game is part way through scrolling from one room to the next. */
int gb_world_scrolling(const gb_t *gb);

/* The season the game is in, 0 to 3. */
int gb_world_season(const gb_t *gb);

/* Draws the world into `dst` at `scale` (1.0 shows every pixel one for one,
 * lower values shrink it). `cam_x` and `cam_y` are world pixels at the centre
 * of the view. */
/* `use_live` takes tile graphics from the machine for whichever tileset it
 * currently holds, so animated tiles keep moving in step with the live
 * screen. Pass 0 to draw purely from the compiled-in data. */
void gb_world_render(const gb_t *gb, uint32_t *dst, int dst_w, int dst_h,
                     float cam_x, float cam_y, float scale, int use_live);

/* Composites the hardware's screen into the world at `screen_x, screen_y`,
 * sampled exactly as the world around it is, so the two cannot disagree. */
void gb_world_draw_screen(const gb_t *gb, uint32_t *dst, int dst_w, int dst_h,
                          float cam_x, float cam_y, float scale,
                          float screen_x, float screen_y);

/* Draws the status bar across the top of the view, where it stays whatever
 * the camera is doing. */
void gb_world_draw_status(const gb_t *gb, uint32_t *dst, int dst_w, int dst_h,
                          float scale);

/* The scale at which the whole world fits in a view of this size, leaving
 * bars on the wider axis. */
float gb_world_fit_scale(int dst_w, int dst_h);

/* The scale at which the world covers the view completely, with no bars. The
 * world is 5:4, so on a wider display this crops rather than letterboxes. */
float gb_world_cover_scale(int dst_w, int dst_h);

/* The room the game currently has loaded, or -1 if it is not in this group. */
int  gb_world_active_room(const gb_t *gb);

/* Where the view is, followed frame by frame.
 *
 * The hardware's scroll registers say where the screen is only to the nearest
 * 256 pixels, so each reading has to be resolved against the last one. That
 * only works if every frame is seen: skip enough of them and a reading
 * resolves to the wrong multiple, putting the view a room and a half from
 * where it belongs. So this is followed on the thread running the game, once
 * per finished frame, and the result travels with the frame it describes -
 * rather than being worked out when the window happens to redraw, which is
 * neither every frame nor at a steady rate.
 */
typedef struct {
    int   valid;
    int   in_world;              /* the readings mean something */
    float screen_x, screen_y;    /* where the hardware's screen is */
    float anchor_x, anchor_y;    /* the room object coordinates are counted in */
    float link_x, link_y;        /* where Link is; crosses rooms unbroken */
    int   have_link;
    int   crossing;              /* part way from one room to the next */

    /* The frame before: what the hardware has actually drawn is one frame
     * behind what the game has computed. */
    float last_screen_x, last_screen_y;
    float last_link_x, last_link_y;
    int   have_last;
} gb_world_view_t;

/* Call once for each finished frame, on the thread running the game. */
void gb_world_track(gb_world_view_t *view, const gb_t *gb);

/* Whether the game is somewhere the world data describes. Menus, cutscenes,
 * dungeons and interiors are not, and drawing overworld rooms around them
 * shows scenery that has nothing to do with what is on screen. */
int  gb_world_in_overworld(const gb_t *gb);

/* Checks the compiled-in world against the hardware's own picture for this
 * one frame and writes a report. Returns nonzero if they agree, and hands
 * back the share of terrain that differed, or -1 if this frame could not be
 * judged at all.
 *
 * One frame is not a verdict. A cutscene, a fade, a room mid-load: any of
 * those disagree completely on data that is perfectly good. Sample it
 * repeatedly and keep the best - one frame that agrees proves the data is
 * right, while data that is wrong disagrees on every frame there is. */
int gb_world_self_check(const gb_t *gb, char *out, size_t n, double *pct_out);

/* Outlines a room, so the player can see where they are on the map. */
void gb_world_mark_room(uint32_t *dst, int dst_w, int dst_h,
                        float cam_x, float cam_y, float scale, int room);

/* What was standing in a room when it was last loaded.
 *
 * The game simulates one room. Objects in the others do not exist in memory
 * at all, so there is nothing live to draw for them - walk away and an NPC
 * does not carry on, it stops being. What can be done is to remember what was
 * there and keep showing it, so the world stays populated instead of emptying
 * out to bare ground everywhere the player is not standing.
 *
 * They are remembered as they looked, so they hold their last pose rather
 * than animating. A remembered figure is replaced by the live one the moment
 * its room is loaded again.
 */
#define GB_REMEMBERED 40             /* objects kept per room */
#define GB_GHOST_PX   (8 * 16)

typedef struct {
    int16_t  x, y;                   /* from the room's corner */
    uint8_t  h;                      /* 8 or 16 */
    uint32_t px[GB_GHOST_PX];        /* 0 where the object is see-through */
} gb_world_ghost_t;

typedef struct {
    gb_world_ghost_t obj[GB_WORLD_ROOMS][GB_REMEMBERED];
    uint8_t          count[GB_WORLD_ROOMS];
    uint8_t          known[GB_WORLD_ROOMS];

    /* The fullest view of each room, rather than the latest. A single look is
     * a poor record: the player standing next to someone hides them, and the
     * object table is briefly empty while a room loads, so a room caught at
     * either moment would be remembered as deserted. */
    int8_t           fullest[GB_WORLD_ROOMS];
    int              watching;      /* the room loaded when last recorded */
} gb_world_memory_t;

/* Records what is in the room the game currently has loaded. */
void gb_world_remember(gb_world_memory_t *mem, const gb_t *gb,
                       float screen_x, float screen_y,
                       float link_x, float link_y, int room);

/* Draws what was remembered of every room except the one that is loaded. */
void gb_world_draw_remembered(const gb_world_memory_t *mem,
                              uint32_t *dst, int dst_w, int dst_h,
                              float cam_x, float cam_y, float scale,
                              int loaded_room);

/* Draws every object the game has active, at its live position, anywhere in
 * the view - so nothing is cut off at the edge of the hardware's screen, and
 * nothing is lost to its limit of ten objects on a line. */
void gb_world_draw_objects(const gb_t *gb, uint32_t *dst, int dst_w, int dst_h,
                           float cam_x, float cam_y, float scale,
                           float screen_x, float screen_y);

#endif
