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

extern const int     gb_world_group;
extern const uint8_t gb_world_rooms[GB_WORLD_ROOMS][GB_ROOM_TILES];
extern const uint8_t gb_world_room_tileset[GB_WORLD_ROOMS];
extern const uint8_t gb_world_mappings[][GB_MAPPING_BYTES];
extern const int     gb_world_mapping_count;
extern const uint8_t gb_world_tileset_vram[][GB_TILESET_VRAM];
extern const uint8_t gb_world_tileset_palette[][GB_TILESET_PALETTE];
extern const uint8_t gb_world_tileset_palette_mask[];
extern const int     gb_world_tileset_count;

/* A tileset's own number indexes neither of these directly: tilesets share
 * metatile definitions, and only the ones this group uses carry graphics. */
extern const uint8_t gb_world_tileset_layout[];
extern const uint8_t gb_world_tileset_asset[];
#define GB_TILESET_NONE 0xFF

/* Draws the world into `dst` at `scale` (1.0 shows every pixel one for one,
 * lower values shrink it). `cam_x` and `cam_y` are world pixels at the centre
 * of the view. */
void gb_world_render(const gb_t *gb, uint32_t *dst, int dst_w, int dst_h,
                     float cam_x, float cam_y, float scale);

/* The scale at which the whole world fits in a view of this size, leaving
 * bars on the wider axis. */
float gb_world_fit_scale(int dst_w, int dst_h);

/* The scale at which the world covers the view completely, with no bars. The
 * world is 5:4, so on a wider display this crops rather than letterboxes. */
float gb_world_cover_scale(int dst_w, int dst_h);

/* The room the game currently has loaded, or -1 if it is not in this group. */
int  gb_world_active_room(const gb_t *gb);

/* Where the hardware's screen sits in the world, in world pixels, following
 * the game through a room transition rather than snapping between rooms.
 * Returns 0 if the position cannot be determined. */
int  gb_world_screen_origin(const gb_t *gb, float *out_x, float *out_y);

/* Where Link is standing, in world pixels. Unlike the screen, this never
 * jumps: it crosses a room boundary as one continuous line. Returns 0 if it
 * cannot be determined. */
int  gb_world_link_position(const gb_t *gb, float *out_x, float *out_y);

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
