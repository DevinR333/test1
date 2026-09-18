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
void gb_world_render(const gb_t *gb, uint32_t *dst, int dst_w, int dst_h,
                     float cam_x, float cam_y, float scale);

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

/* Outlines a room, so the player can see where they are on the map. */
void gb_world_mark_room(uint32_t *dst, int dst_w, int dst_h,
                        float cam_x, float cam_y, float scale, int room);

/* Draws the objects the game currently has active, in the room it is
 * simulating, at their live positions. */
void gb_world_draw_objects(const gb_t *gb, uint32_t *dst, int dst_w, int dst_h,
                           float cam_x, float cam_y, float scale, int room);

#endif
