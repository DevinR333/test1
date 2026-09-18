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
#define GB_MAPPING_BYTES 2048
#define GB_TILESET_VRAM    0x2000
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
extern const int     gb_world_tileset_count;
extern const uint8_t gb_world_tileset_layout[];

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

/* Outlines a room, so the player can see where they are on the map. */
void gb_world_mark_room(uint32_t *dst, int dst_w, int dst_h,
                        float cam_x, float cam_y, float scale, int room);

/* Draws the objects the game currently has active, in the room it is
 * simulating, at their live positions. */
void gb_world_draw_objects(const gb_t *gb, uint32_t *dst, int dst_w, int dst_h,
                           float cam_x, float cam_y, float scale, int room);

#endif
