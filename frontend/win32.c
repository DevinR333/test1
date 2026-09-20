/* Windows frontend.
 *
 * Deliberately dependency-free: window, blit and input all come from the Win32
 * API that MinGW already ships headers for, so building needs nothing beyond a
 * compiler.
 *
 * The game runs on its own thread because recompiled code never returns from
 * the game's main loop. It hands each finished frame to the UI thread, which
 * owns the window and does the blit.
 */
#include <windows.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include "gb.h"
#include "present.h"
#include "worldmap.h"
#include "display.h"

/* Stamped in by the build so any report names the build that produced it.
 * Identical numbers from what should have been different code means a stale
 * binary, and that is worth being able to see at a glance. */
#ifndef GB_BUILD_STAMP
#define GB_BUILD_STAMP "unstamped"
#endif
#ifndef GB_BUILD_REV
#define GB_BUILD_REV "unknown"
#endif

#define WINDOW_CLASS "GbRecompWindow"
#define DEFAULT_SCALE 4

/* One frame at the hardware's 59.7275 Hz, in 100ns units for a waitable timer. */
#define FRAME_100NS 167427

static struct {
    gb_t         *gb;
    HWND          hwnd;
    HANDLE        thread;
    HANDLE        timer;
    CRITICAL_SECTION lock;

    /* The machine as of one finished frame, and the window's own copy of it.
     * Copying is cheap; rendering is not, so the game's thread is never left
     * waiting on a redraw. */
    gb_t          snapshot;      /* written by the game thread */
    gb_t          view;          /* read by the window, under no lock */

    /* Where the view is, followed once per finished frame on the game's own
     * thread and carried across with the frame it describes. Working it out
     * when the window happens to redraw instead means working it out from
     * frames several apart, and the position is only known to the nearest
     * 256 pixels - which is how the view used to land a room and a half
     * away, showing somewhere else entirely. */
    gb_world_view_t track;           /* the game thread's running position */
    gb_world_view_t snapshot_track;  /* handed over with the snapshot */
    gb_world_view_t view_track;      /* the window's copy */
    volatile LONG have_frame;
    uint32_t      pixels[GB_SCREEN_W * GB_SCREEN_H];   /* presented copy */
    BITMAPINFO    bmi;
    volatile LONG running;
    volatile LONG keys;        /* currently held */
    volatile LONG keys_latched; /* pressed since the last frame, even if released */
    volatile LONG want_diag;
    int           samples, best_ok;
    double        best_pct;
    volatile LONG seamless;    /* pace room crossings by Link, not by the clock */
    int           burst, shown_x, shown_y, settling;
    gb_fit_mode_t fit;
    gb_view_mode_t view_mode;
    int           menu_open, menu_sel;
    int           integer_scale;
    float         zoom;
    float         pan_x, pan_y;

    /* World map view: the whole world drawn from its room data, at any
     * scale, rather than the hardware's 160x144 window. */
    gb_world_memory_t memory;   /* who was in each room when last seen */
    uint8_t       prev_oam[160]; /* the frame before, to cover for flicker */
    float         prev_screen_x, prev_screen_y;
    int           prev_room, prev_objects;
    uint32_t     *map_pixels;   /* scratch for the pulled-back view */
    float         cam_x, cam_y; /* eased, so a room change glides */
    int           cam_valid;
    int           map_w, map_h;
    BITMAPINFO    map_bmi;
} app;

/* Keyboard mapping. Arrows move, Z and X are B and A, matching the layout
 * most people expect from a handheld. */
static int key_to_button(WPARAM vk)
{
    switch (vk) {
    case VK_UP:    return 6;
    case VK_DOWN:  return 7;
    case VK_LEFT:  return 5;
    case VK_RIGHT: return 4;
    case 'X': case VK_RETURN: return 0;      /* A */
    case 'Z': case VK_BACK:   return 1;      /* B */
    case VK_SHIFT: return 2;                 /* Select */
    case VK_SPACE: return 3;                 /* Start */
    default: return -1;
    }
}

/* Declared ahead of use: the frame callback and the crash handler both need
 * these, and both appear before their definitions. */
static void write_log(const gb_t *gb, const char *note);
static void beside_exe(char *out, size_t n, const char *name);

/* Called from the game thread each time the PPU finishes a frame. */
static void on_frame(gb_t *gb, void *user)
{
    (void)user;

    /* Hand the window a copy of the whole machine, not just the screen.
     *
     * The world drawn around the live screen comes from video memory, work
     * RAM and the palettes, and the live screen comes from the framebuffer.
     * Reading those from a running machine means they can be from different
     * frames - the world a few frames ahead of the screen sitting in it - and
     * that is exactly what "the parts outside the screen do not line up"
     * looks like. One copy, taken at one instant, cannot disagree with
     * itself. */
    gb_world_track(&app.track, gb);

    EnterCriticalSection(&app.lock);
    memcpy(&app.snapshot, gb, sizeof(app.snapshot));
    app.snapshot_track = app.track;
    app.have_frame = 1;
    LeaveCriticalSection(&app.lock);

    /* Held keys, plus anything pressed and released since the last frame.
     *
     * Sampling only what is held at this instant loses a quick tap entirely:
     * the key goes down and up between two frames and the game never sees it.
     * Latching a press until it has been reported once means every tap
     * registers, however briefly it was held. */
    LONG held = InterlockedCompareExchange(&app.keys, 0, 0);
    LONG tapped = InterlockedExchange(&app.keys_latched, 0);
    gb->joypad = (uint8_t)(held | tapped);

    /* Snapshot the machine once the game has had a few seconds to get
     * somewhere. One run then explains a blank screen without needing another
     * build to ask a different question. */
    if (gb->frames == 180 || InterlockedCompareExchange(&app.want_diag, 0, 1)) {
        char path[MAX_PATH];
        beside_exe(path, sizeof(path), "oracle-diag.txt");
        gb_write_diagnostics(gb, path);
    }

    /* Keep an eye on whether the compiled-in world matches this ROM.
     *
     * One frame is no evidence. Measured on a build where the two demonstrably
     * match, a frame during a cutscene disagreed on 92% of its terrain, and
     * one just after a room loaded on 96%, while every settled frame was
     * between 7% and 10%. A single sample accuses good data roughly one time
     * in five.
     *
     * So it is sampled again and again and the best is kept. One frame that
     * agrees proves the data is right; data that is wrong has no such frame,
     * because it is wrong on every one of them. The result goes in a file
     * beside the executable and nowhere else - a dialog on a measurement this
     * noisy is worse than no measurement at all. */
    if (app.samples < 60 && app.track.in_world && !app.track.crossing
        && gb->frames > 240 && gb->frames % 90 == 0) {
        static char report[4096];
        double pct = -1.0;
        int ok = gb_world_self_check(gb, report, sizeof(report), &pct);
        if (pct >= 0.0) {
            app.samples++;
            if (pct < app.best_pct) {
                app.best_pct = pct;
                app.best_ok = ok;
                char path[MAX_PATH];
                beside_exe(path, sizeof(path), "oracle-world.txt");
                FILE *fh = fopen(path, "w");
                if (fh) {
                    fprintf(fh, "oracle.exe  %s  %s\n", GB_BUILD_REV, GB_BUILD_STAMP);
                    fprintf(fh, "ROM: %s (%ld KiB)\n", GB_ROM_PATH,
                            (long)GB_ROM_SIZE / 1024);
                    fprintf(fh, "best of %d samples so far\n\n", app.samples);
                    fputs(report, fh);

                    /* What the world has been told to remember, so an
                     * emptying room can be seen rather than described. */
                    int rooms = 0, total = 0;
                    for (int r = 0; r < GB_WORLD_ROOMS; r++)
                        if (app.memory.known[r]) {
                            rooms++;
                            total += app.memory.count[r];
                        }
                    fprintf(fh, "\nremembered objects: %d in %d rooms\n", total, rooms);
                    for (int r = 0; r < GB_WORLD_ROOMS; r++)
                        if (app.memory.known[r])
                            fprintf(fh, "  room %02x: %d\n", r, app.memory.count[r]);
                    fclose(fh);
                }
            }
        }
    }

    if (!InterlockedCompareExchange(&app.running, 0, 0)) {
        gb->stopped = 1;
        return;
    }

    /* Crossing a room boundary, show a frame per pixel Link moves, and run
     * the rest as fast as they compute.
     *
     * The game spends about fifty frames scrolling from one room to the next,
     * moving Link three eighths of a pixel a frame - fifteen pixels of travel
     * stretched over nearly a second. Nothing about that is wrong; it is how
     * the game was written, for hardware that could hold one room at a time.
     * But it is why a world drawn in one piece still feels like a set of
     * separate screens: the walking stops, the screen slides, the walking
     * resumes.
     *
     * Every frame still runs, in order, with nothing skipped or altered - the
     * game's logic, Link's position and the geometry of the crossing are
     * exactly as written. What changes is which of them the window is shown.
     * Presenting one frame for each whole pixel he moves is precisely the
     * rate he moves at when walking normally, so the crossing takes as long
     * as walking that far would, and looks like it: no pause, no slide, no
     * boundary.
     */
    int step_x, step_y;
    int crossing = gb_world_scrolling(gb);

    /* The game also holds Link still for a few frames after the scroll ends,
     * reloading the room it has arrived in. Left at the game's pace that is a
     * short dead stop right after the crossing, which reads as the boundary
     * all over again, so it is carried through under the same rule - bounded,
     * so standing still at a boundary cannot run the game away. */
    if (crossing)
        app.settling = 12;
    else if (app.settling > 0)
        app.settling--;

    if (InterlockedCompareExchange(&app.seamless, 0, 0)
        && (crossing || app.settling > 0)
        && gb_world_link_step(gb, &step_x, &step_y)) {
        if (step_x == app.shown_x && step_y == app.shown_y && app.burst < 32) {
            app.burst++;
            return;                     /* not moved yet: no repaint, no wait */
        }
        /* Arriving in the new room wraps his coordinates by a whole room
         * width, which is not him moving - counting it as movement ends the
         * window on the very frame it is needed. */
        int wrapped = abs(step_x - app.shown_x) > 8 || abs(step_y - app.shown_y) > 8;
        app.shown_x = step_x;
        app.shown_y = step_y;
        app.burst = 0;
        if (!crossing && !wrapped)
            app.settling = 0;           /* walking again: back to real time */
    } else if (gb_world_link_step(gb, &step_x, &step_y)) {
        app.shown_x = step_x;           /* keep current, ready for the next one */
        app.shown_y = step_y;
        app.burst = 0;
    }

    if (app.hwnd)
        InvalidateRect(app.hwnd, NULL, FALSE);

    /* Pace to real time. A waitable timer is used rather than Sleep because
     * Sleep's granularity is coarser than a frame. */
    if (app.timer)
        WaitForSingleObject(app.timer, 100);
}

/* A fault inside recompiled code would otherwise close the window with no
 * indication of what happened, which is the least useful failure possible. */
static LONG WINAPI on_crash(EXCEPTION_POINTERS *info)
{
    char msg[512];
    snprintf(msg, sizeof(msg),
             "The game crashed.\n\n"
             "Exception:  0x%08lX\n"
             "Address:    %p\n"
             "Frames run: %llu\n"
             "Last bank:  %02X\n"
             "Interpreter fallbacks: %llu\n\n"
             "Written to oracle-log.txt beside the executable.",
             (unsigned long)info->ExceptionRecord->ExceptionCode,
             info->ExceptionRecord->ExceptionAddress,
             app.gb ? (unsigned long long)app.gb->frames : 0ULL,
             app.gb ? app.gb->rom_bank : 0,
             app.gb ? (unsigned long long)app.gb->no_entry_count : 0ULL);
    if (app.gb) write_log(app.gb, "crashed");
    MessageBox(NULL, msg, "Oracle of Seasons - crash", MB_ICONERROR);
    return EXCEPTION_EXECUTE_HANDLER;
}

static const char *stop_reason_text(const gb_t *gb, char *buf, size_t n)
{
    switch (gb->stop_reason) {
    case GB_STOP_ILLEGAL:
        snprintf(buf, n,
                 "The game executed opcode %02X, which does not exist on this "
                 "processor.\n\nThat normally means control reached somewhere "
                 "it should not have, rather than a problem with the ROM.",
                 gb->illegal_opcode);
        return buf;
    case GB_STOP_OPCODE:
        return "The game executed STOP, which halts the processor.";
    case GB_STOP_NO_ENTRY:
        return "The game jumped to an address with no code at it.";
    case GB_STOP_INTERP_RUNAWAY:
        return "Interpreted code ran for two million instructions without "
               "returning, so it was stopped rather than left to hang.";
    case GB_STOP_NO_PROGRESS: {
        /* Name the busiest registers since the last frame: whatever the game
         * is waiting on will be at the top by a wide margin. */
        char top[256] = "";
        for (int rank = 0; rank < 4; rank++) {
            int best = -1;
            uint32_t most = 0;
            for (int r = 0; r < 128; r++) {
                uint32_t hits = gb->io_reads[r] + gb->io_writes[r];
                if (hits > most) {
                    char seen[8];
                    snprintf(seen, sizeof(seen), "FF%02X", r);
                    if (strstr(top, seen)) continue;
                    most = hits; best = r;
                }
            }
            if (best < 0 || most == 0) break;
            char line[64];
            snprintf(line, sizeof(line), "  FF%02X  %u reads, %u writes\n",
                     best, gb->io_reads[best], gb->io_writes[best]);
            strncat(top, line, sizeof(top) - strlen(top) - 1);
        }
        snprintf(buf, n,
                 "The game ran for two seconds without drawing a frame.\n\n"
                 "Busiest registers since the last frame:\n%s\n"
                 "LCDC is %02X, so the display is %s. Speed is %s.",
                 top[0] ? top : "  (none)\n", gb->io[0x40],
                 (gb->io[0x40] & 0x80) ? "on" : "off",
                 gb->double_speed ? "double" : "normal");
        return buf;
    }
    case GB_STOP_USER:
        return NULL;                         /* closing the window is not a fault */
    default:
        return "The game loop exited without recording a reason, which should "
               "not happen.";
    }
}

/* Written beside the executable on every exit. A dialog cannot be shown for
 * every kind of failure - a stack overflow in particular often cannot run any
 * handler at all - so the same detail goes to a file that survives. */
/* Build a path beside the executable, so files land where the dialog says
 * they will even when launched from Explorer. */
static void beside_exe(char *out, size_t n, const char *name)
{
    char path[MAX_PATH + 64];
    DWORD len = GetModuleFileName(NULL, path, MAX_PATH);
    if (len == 0 || len >= MAX_PATH) {
        snprintf(out, n, "%s", name);
        return;
    }
    char *slash = strrchr(path, '\\');
    if (!slash) {
        snprintf(out, n, "%s", name);
        return;
    }
    *slash = 0;
    snprintf(out, n, "%s\\%s", path, name);
}

static void write_log(const gb_t *gb, const char *note)
{
    char path[MAX_PATH];
    beside_exe(path, sizeof(path), "oracle-log.txt");

    FILE *fh = fopen(path, "w");
    if (!fh) return;
    fprintf(fh, "build         %s (%s)\n", GB_BUILD_STAMP, GB_BUILD_REV);
    fprintf(fh, "log           %s\n", path);
    fprintf(fh, "stop reason   %d\n", gb->stop_reason);
    fprintf(fh, "note          %s\n", note ? note : "(none)");
    fprintf(fh, "stopped at    %02X:%04X\n", gb->stop_bank, gb->stop_pc);
    fprintf(fh, "frames        %llu\n", (unsigned long long)gb->frames);
    fprintf(fh, "cycles        %llu\n", (unsigned long long)gb->cycles);
    fprintf(fh, "interp calls  %llu\n", (unsigned long long)gb->no_entry_count);
    fprintf(fh, "illegal op    %02X\n", gb->illegal_opcode);
    fprintf(fh, "rom bank      %02X\n", gb->rom_bank);
    fprintf(fh, "LCDC          %02X\n", gb->io[0x40]);
    fprintf(fh, "LY            %02X\n", gb->io[0x44]);
    fprintf(fh, "IE / IF       %02X / %02X\n", gb->io[0x7F], gb->io[0x0F]);
    fprintf(fh, "busiest io registers since the last frame:\n");
    for (int r = 0; r < 128; r++) {
        if (gb->io_reads[r] || gb->io_writes[r])
            fprintf(fh, "  FF%02X  r=%u w=%u\n", r, gb->io_reads[r], gb->io_writes[r]);
    }
    fprintf(fh, "hdma          %s, %u blocks left\n",
            gb->hdma_active ? "active" : "idle", gb->hdma_left);
    fprintf(fh, "double speed  %s\n", gb->double_speed ? "yes" : "no");
    fclose(fh);
}

static DWORD WINAPI game_thread(LPVOID param)
{
    gb_t *gb = (gb_t *)param;
    gb_run(gb);
    InterlockedExchange(&app.running, 0);

    /* Report a fault before the window goes away. A user-requested stop is
     * not a fault and closes quietly. */
    char detail[512], msg[1024];
    const char *why = stop_reason_text(gb, detail, sizeof(detail));
    write_log(gb, why);
    if (why) {
        snprintf(msg, sizeof(msg),
                 "%s\n\n"
                 "Stopped at:  %02X:%04X\n"
                 "Frames run:  %llu\n"
                 "Cycles:      %llu\n"
                 "Interpreter fallbacks: %llu\n"
                 "Build:       %s (%s)\n\n"
                 "Written to oracle-log.txt beside the executable.",
                 why, gb->stop_bank, gb->stop_pc,
                 (unsigned long long)gb->frames,
                 (unsigned long long)gb->cycles,
                 (unsigned long long)gb->no_entry_count,
                 GB_BUILD_STAMP, GB_BUILD_REV);
        MessageBox(NULL, msg, "Oracle of Seasons - stopped", MB_ICONWARNING);
    }

    if (app.hwnd)
        PostMessage(app.hwnd, WM_CLOSE, 0, 0);
    return 0;
}

/* The scale at which the hardware's screen fills the window. */
static float game_scale(HWND hwnd)
{
    RECT rc;
    GetClientRect(hwnd, &rc);
    float sx = (float)(rc.right - rc.left) / GB_SCREEN_W;
    float sy = (float)(rc.bottom - rc.top) / GB_SCREEN_H;
    return sx < sy ? sx : sy;
}

/* How far back the camera can go: the point where the world still covers the
 * window. Stopping there means a widescreen display shows more world across
 * rather than bars down the sides. */
static float min_zoom(HWND hwnd)
{
    RECT rc;
    GetClientRect(hwnd, &rc);
    float base = game_scale(hwnd);
    if (base <= 0.0f) return 1.0f;
    return gb_world_cover_scale(rc.right - rc.left, rc.bottom - rc.top) / base;
}

/* One view, at any zoom.
 *
 * At zoom 1 the hardware's screen fills the window, exactly as the game
 * intends. Below that the camera pulls back: the surrounding world is drawn
 * from its room data, and the live screen - which is where the game is
 * actually running, enemies and all - is composited over the room the player
 * is in, at the same scale. The game never stops, and input keeps reaching it,
 * so zooming out is something done while playing rather than instead of it.
 */
static void paint(HWND hwnd)
{
    PAINTSTRUCT ps;
    HDC dc = BeginPaint(hwnd, &ps);

    RECT rc;
    GetClientRect(hwnd, &rc);
    int cw = rc.right - rc.left, ch = rc.bottom - rc.top;
    if (cw <= 0 || ch <= 0) { EndPaint(hwnd, &ps); return; }

    /* One frame of the machine, copied out quickly, then drawn at leisure.
     * Drawing used to happen with the lock held, so the game's thread sat
     * waiting on it - at a large window size that alone held the game below
     * its own frame rate. */
    EnterCriticalSection(&app.lock);
    memcpy(&app.view, &app.snapshot, sizeof(app.view));
    memcpy(app.pixels, app.view.framebuffer, sizeof(app.pixels));
    app.view_track = app.snapshot_track;
    int have = app.have_frame;
    LeaveCriticalSection(&app.lock);

    const gb_t *view = have ? &app.view : NULL;
    const gb_world_view_t *where = &app.view_track;

    SetStretchBltMode(dc, COLORONCOLOR);

    /* At zoom 1 the screen is as large as it can be while staying square. */
    float base = (float)cw / GB_SCREEN_W;
    float by = (float)ch / GB_SCREEN_H;
    if (by < base) base = by;

    if (app.map_w != cw || app.map_h != ch) {
        free(app.map_pixels);
        app.map_pixels = malloc((size_t)cw * ch * sizeof(uint32_t));
        app.map_w = cw;
        app.map_h = ch;
        app.map_bmi.bmiHeader.biSize = sizeof(BITMAPINFOHEADER);
        app.map_bmi.bmiHeader.biWidth = cw;
        app.map_bmi.bmiHeader.biHeight = -ch;
        app.map_bmi.bmiHeader.biPlanes = 1;
        app.map_bmi.bmiHeader.biBitCount = 32;
        app.map_bmi.bmiHeader.biCompression = BI_RGB;
    }
    if (!app.map_pixels) { EndPaint(hwnd, &ps); return; }

    /* The two fixed shapes show the hardware's screen and nothing else, in
     * the largest 16:9 or 4:3 rectangle the window will hold, with the screen
     * centred in it at a whole multiple so no row is doubled while its
     * neighbour is not. */
    if (app.view_mode != GB_VIEW_WORLD) {
        gb_rect_t r = gb_view_screen(app.view_mode, cw, ch);
        memset(app.map_pixels, 0, (size_t)cw * ch * sizeof(uint32_t));
        for (int y = 0; y < r.h; y++) {
            int dy = r.y + y;
            if (dy < 0 || dy >= ch) continue;
            const uint32_t *src = app.pixels + (y * GB_SCREEN_H / r.h) * GB_SCREEN_W;
            uint32_t *row = app.map_pixels + (size_t)dy * cw;
            for (int x = 0; x < r.w; x++) {
                int dx = r.x + x;
                if (dx >= 0 && dx < cw)
                    row[dx] = src[x * GB_SCREEN_W / r.w];
            }
        }
        if (app.menu_open)
            gb_menu_draw(app.map_pixels, cw, ch, app.menu_sel);
        StretchDIBits(dc, 0, 0, cw, ch, 0, 0, cw, ch,
                      app.map_pixels, &app.map_bmi, DIB_RGB_COLORS, SRCCOPY);
        EndPaint(hwnd, &ps);
        return;
    }

    /* Above natural size there is no world to put around the screen. Nor is
     * there anywhere the world data does not describe - menus, cutscenes and
     * the opening - where surrounding the screen with overworld scenery would
     * show somewhere the player is not. */
    int world_ok = view && where->in_world;
    if (app.zoom > 1.001f || !world_ok) {
        app.cam_valid = 0;
        float z = app.zoom < 1.0f ? 1.0f : app.zoom;
        gb_viewport_t v = gb_fit_viewport(cw, ch, app.fit, z,
                                          app.pan_x, app.pan_y);
        if (v.dst_w < cw || v.dst_h < ch) {
            HBRUSH bg = (HBRUSH)GetStockObject(BLACK_BRUSH);
            RECT bars[4] = {
                { 0, 0, cw, v.dst_y },
                { 0, v.dst_y + v.dst_h, cw, ch },
                { 0, v.dst_y, v.dst_x, v.dst_y + v.dst_h },
                { v.dst_x + v.dst_w, v.dst_y, cw, v.dst_y + v.dst_h },
            };
            for (int i = 0; i < 4; i++)
                if (bars[i].right > bars[i].left && bars[i].bottom > bars[i].top)
                    FillRect(dc, &bars[i], bg);
        }
        StretchDIBits(dc, v.dst_x, v.dst_y, v.dst_w, v.dst_h,
                      (int)(v.src_x + 0.5f), (int)(v.src_y + 0.5f),
                      (int)(v.src_w + 0.5f), (int)(v.src_h + 0.5f),
                      app.pixels, &app.bmi, DIB_RGB_COLORS, SRCCOPY);
        EndPaint(hwnd, &ps);
        return;
    }

    /* At natural size and below, the window is filled with world and the live
     * screen sits in it at its own scale. At exactly 1 the screen is the size
     * the hardware intends and the rest of a widescreen window shows the world
     * around it, rather than black bars. Nothing is stretched: every pixel is
     * drawn at the same scale. */
    float scale = base * app.zoom;

    /* Where the screen actually is, which during a room crossing is somewhere
     * between two rooms rather than at either one's corner. */
    float screen_x = where->screen_x;
    float screen_y = where->screen_y;

    /* The camera follows Link, not the screen.
     *
     * The screen is what shifts: crossing a boundary it scrolls a whole room
     * over about forty frames and then stops. Link's own position crosses the
     * same boundary as one unbroken line, so a camera on him has nothing to
     * shift about - the world just travels past while he walks, which is what
     * a room boundary should look like when the whole world is drawn.
     *
     * The live screen still goes where the game says it is, so its pixels
     * stay lined up with the world drawn around them throughout. */
    float cam_x = screen_x + 80.0f;
    float cam_y = screen_y + 64.0f;
    if (where->have_link) {
        cam_x = where->link_x;
        cam_y = where->link_y;
    }
    app.cam_x = cam_x;
    app.cam_y = cam_y;
    app.cam_valid = 1;


    /* World, then the live screen into it, then the status bar over the top -
     * all into one image, which then goes to the window in a single blit.
     *
     * The live screen used to be a second blit, positioned separately. Two
     * blits mean two different resamplers over one picture: at a window
     * height that is not a whole multiple of 144 - most of them, and every
     * 16:9 one - the screen landed a fraction of a pixel from the world drawn
     * around it. Worse, the source rectangle of a top-down bitmap is not
     * measured from the top, so asking for the 128 rows below the status bar
     * actually fetched the 128 rows above it: the world was drawn sixteen
     * pixels out of place with the hearts and rupees painted into it, and the
     * status bar travelled around with the player. Sampling both here, by
     * index, through one position and scale, none of that can happen. */
    int loaded = gb_world_active_room(view);

    gb_world_render(view, app.map_pixels, cw, ch, cam_x, cam_y, scale, 1);

    /* Who was in the other rooms.
     *
     * The game simulates one room; objects elsewhere are not slowed down or
     * paused, they are simply not there, so there is nothing live to draw for
     * them. What was seen is remembered and kept on screen, so the world
     * stays populated rather than emptying to bare ground everywhere the
     * player is not. They hold their last pose until their room is loaded
     * again, at which point the live ones take over. */
    gb_world_remember(&app.memory, view, screen_x, screen_y,
                      cam_x, cam_y, where->crossing ? -1 : loaded);
    gb_world_draw_remembered(&app.memory, app.map_pixels, cw, ch,
                             cam_x, cam_y, scale, where->crossing ? -1 : loaded);

    /* Every object the game has active, drawn at its own position across the
     * whole view - not only where the hardware's screen happens to reach.
     * Objects at a room's edge used to be sliced off by that edge, and the
     * hardware drops any beyond ten on a line, so they flickered. Drawn
     * before the live screen, so where that screen is used its own rendering
     * wins and keeps the game's own ordering; the only pixels left are the
     * ones it never covered. */
    /* The frame before's objects first, then this frame's over them.
     *
     * The hardware draws at most ten objects on a line and drops the rest,
     * and the game rotates which ones it drops so that everything is seen
     * some of the time. On the small screen that reads as a shimmer; drawn
     * large it reads as people blinking in and out. Whatever was dropped this
     * frame was almost certainly present last frame, so last frame covers for
     * it, and this frame's copy lands on top with the positions up to date. */
    if (app.prev_objects && app.prev_room == loaded)
        gb_world_draw_objects(view, app.prev_oam, app.map_pixels, cw, ch,
                              cam_x, cam_y, scale,
                              app.prev_screen_x, app.prev_screen_y);
    gb_world_draw_objects(view, NULL, app.map_pixels, cw, ch, cam_x, cam_y, scale,
                          screen_x, screen_y);
    memcpy(app.prev_oam, view->oam, sizeof(app.prev_oam));
    app.prev_screen_x = screen_x;
    app.prev_screen_y = screen_y;
    app.prev_room = loaded;
    app.prev_objects = 1;

    /* Crossing between rooms, the hardware's screen is not to be trusted.
     *
     * Its background is one tilemap 256 pixels wide and a room is 160, so as
     * the game scrolls the next room's columns in, it writes over the columns
     * of the old one still on display. There is no room for both. Measured
     * part way through a crossing, the leftmost sixteen pixels disagreed with
     * the world on 72 rows of 128 - a stripe of the wrong room, sweeping
     * across. The world drawn here has both rooms and is right about each, so
     * for the quarter of a second a crossing lasts, it is used on its own. */
    if (!where->crossing)
        gb_world_draw_screen(view, app.map_pixels, cw, ch, cam_x, cam_y, scale,
                             screen_x, screen_y);

    /* The status bar is the player's, not the world's: it stays across the
     * top of the window at its natural size, whatever the camera is doing and
     * however far out the view is pulled. */
    gb_world_draw_status(view, app.map_pixels, cw, ch, base);

    if (app.menu_open)
        gb_menu_draw(app.map_pixels, cw, ch, app.menu_sel);

    StretchDIBits(dc, 0, 0, cw, ch, 0, 0, cw, ch,
                  app.map_pixels, &app.map_bmi, DIB_RGB_COLORS, SRCCOPY);

    EndPaint(hwnd, &ps);
}

static LRESULT CALLBACK wndproc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp)
{
    switch (msg) {
    case WM_PAINT:
        paint(hwnd);
        return 0;

    case WM_ERASEBKGND:
        return 1;                            /* painted in WM_PAINT */

    case WM_KEYDOWN:
    case WM_KEYUP: {
        if (msg == WM_KEYDOWN && wp == VK_ESCAPE) {
            if (app.menu_open) {             /* back out of the chooser */
                app.menu_open = 0;
                InvalidateRect(hwnd, NULL, TRUE);
                return 0;
            }
            PostMessage(hwnd, WM_CLOSE, 0, 0);
            return 0;
        }

        /* The chooser. It opens on the title screen, and F10 brings it back
         * at any point, so a display that turns out wrong is one key away
         * from being right rather than a rebuild away. */
        if (msg == WM_KEYDOWN && wp == VK_F10) {
            app.menu_open = !app.menu_open;
            app.menu_sel = app.view_mode;
            InvalidateRect(hwnd, NULL, TRUE);
            return 0;
        }
        if (app.menu_open && msg == WM_KEYDOWN) {
            switch (wp) {
            case VK_UP:
                app.menu_sel = (app.menu_sel + GB_VIEW_MODES - 1) % GB_VIEW_MODES;
                break;
            case VK_DOWN:
                app.menu_sel = (app.menu_sel + 1) % GB_VIEW_MODES;
                break;
            case VK_RETURN: case VK_SPACE: case 'X': case 'Z':
                app.view_mode = (gb_view_mode_t)app.menu_sel;
                app.menu_open = 0;
                break;
            default:
                break;
            }
            InvalidateRect(hwnd, NULL, TRUE);
            return 0;                        /* the game hears none of this */
        }
        /* Tab jumps between playing at normal size and pulled right back. */
        if (msg == WM_KEYDOWN && wp == VK_TAB) {
            app.zoom = (app.zoom < 0.999f) ? 1.0f : min_zoom(hwnd);
            InvalidateRect(hwnd, NULL, TRUE);
            return 0;
        }

        /* One continuous range. Above 1 magnifies the hardware's screen;
         * below it the camera pulls back and the surrounding world is drawn
         * around the live one. The game keeps running throughout. */
        if (msg == WM_KEYDOWN && (wp == VK_OEM_PLUS || wp == VK_ADD)) {
            app.zoom *= 1.25f;
            if (app.zoom > 4.0f) app.zoom = 4.0f;
            InvalidateRect(hwnd, NULL, TRUE);
            return 0;
        }
        if (msg == WM_KEYDOWN && (wp == VK_OEM_MINUS || wp == VK_SUBTRACT)) {
            app.zoom /= 1.25f;
            float floor_z = min_zoom(hwnd);
            if (app.zoom < floor_z) app.zoom = floor_z;
            InvalidateRect(hwnd, NULL, TRUE);
            return 0;
        }
        if (msg == WM_KEYDOWN && wp == '0') {
            app.zoom = 1.0f;
            app.pan_x = app.pan_y = 0;
            InvalidateRect(hwnd, NULL, TRUE);
            return 0;
        }

        if (msg == WM_KEYDOWN && wp == VK_F2) {
            InterlockedExchange(&app.want_diag, 1);
            return 0;
        }
        if (msg == WM_KEYDOWN && wp == VK_F1) {
            app.fit = (app.fit == GB_FIT_INTEGER) ? GB_FIT_ASPECT : GB_FIT_INTEGER;
            InvalidateRect(hwnd, NULL, TRUE);
            return 0;
        }
        int button = key_to_button(wp);
        if (button >= 0) {
            LONG old, val;
            do {
                old = app.keys;
                val = (msg == WM_KEYDOWN) ? (old | (1L << button))
                                          : (old & ~(1L << button));
            } while (InterlockedCompareExchange(&app.keys, val, old) != old);

            /* Remember the press so a tap shorter than a frame still counts. */
            if (msg == WM_KEYDOWN) {
                do {
                    old = app.keys_latched;
                    val = old | (1L << button);
                } while (InterlockedCompareExchange(&app.keys_latched, val, old) != old);
            }
        }
        return 0;
    }

    case WM_CLOSE:
        InterlockedExchange(&app.running, 0);
        if (app.gb && !app.gb->stop_reason) {
            app.gb->stopped = 1;
            app.gb->stop_reason = GB_STOP_USER;
        }
        DestroyWindow(hwnd);
        return 0;

    case WM_DESTROY:
        PostQuitMessage(0);
        return 0;
    }
    return DefWindowProc(hwnd, msg, wp, lp);
}

static uint8_t *read_file(const char *path, size_t *size)
{
    FILE *fh = fopen(path, "rb");
    if (!fh) return NULL;
    fseek(fh, 0, SEEK_END);
    long n = ftell(fh);
    fseek(fh, 0, SEEK_SET);
    uint8_t *buf = malloc(n > 0 ? (size_t)n : 1);
    if (!buf || fread(buf, 1, n, fh) != (size_t)n) {
        free(buf); fclose(fh); return NULL;
    }
    fclose(fh);
    *size = (size_t)n;
    return buf;
}

/* The build stamps in which ROM it translated. The game's code is turned into
 * C at build time, so the executable is bound to that exact file: opening a
 * different one means every jump in it lands somewhere that was never there.
 * That used to crash without explanation. */
#ifndef GB_ROM_FINGERPRINT
#define GB_ROM_FINGERPRINT 0ULL
#endif
#ifndef GB_ROM_PATH
#define GB_ROM_PATH ""
#endif
#ifndef GB_ROM_SIZE
#define GB_ROM_SIZE 0
#endif

static uint64_t fingerprint(const uint8_t *p, size_t n)
{
    uint64_t h = 0xcbf29ce484222325ULL;       /* FNV-1a, 64 bit */
    for (size_t i = 0; i < n; i++) {
        h ^= p[i];
        h *= 0x100000001b3ULL;
    }
    return h;
}

int main(int argc, char **argv)
{
    /* With no argument, open the ROM this was built from - so the executable
     * works when it is double-clicked, not only from a command line. */
    const char *rom_path = (argc > 1) ? argv[1]
                         : (GB_ROM_PATH[0] ? GB_ROM_PATH : "rom.gbc");

    size_t size = 0;
    uint8_t *rom = read_file(rom_path, &size);
    if (!rom && argc <= 1 && GB_ROM_PATH[0]) {
        rom_path = "rom.gbc";                 /* moved since the build */
        rom = read_file(rom_path, &size);
    }
    if (!rom) {
        char msg[1024];
        snprintf(msg, sizeof(msg),
                 "Could not open the ROM:\n\n%s\n\n"
                 "This was built from:\n%s\n\n"
                 "Pass one on the command line, or put it beside the "
                 "executable as rom.gbc", rom_path, GB_ROM_PATH);
        MessageBox(NULL, msg, "Oracle of Seasons", MB_ICONERROR);
        return 1;
    }

    /* An argument naming a different cartridge is nearly always a command
     * line left over from an earlier build. The executable knows exactly
     * which ROM it was translated from, so if that file is still there, open
     * it rather than refusing: the argument is a hint, not an instruction. */
    if (GB_ROM_FINGERPRINT && fingerprint(rom, size) != (uint64_t)GB_ROM_FINGERPRINT
        && GB_ROM_PATH[0] && strcmp(rom_path, GB_ROM_PATH) != 0) {
        size_t own_size = 0;
        uint8_t *own = read_file(GB_ROM_PATH, &own_size);
        if (own && fingerprint(own, own_size) == (uint64_t)GB_ROM_FINGERPRINT) {
            free(rom);
            rom = own;
            size = own_size;
            rom_path = GB_ROM_PATH;
        } else {
            free(own);
        }
    }

    if (GB_ROM_FINGERPRINT && fingerprint(rom, size) != (uint64_t)GB_ROM_FINGERPRINT) {
        char msg[1400];
        snprintf(msg, sizeof(msg),
                 "This build was made from a different ROM.\n\n"
                 "Built from:  %s\n"
                 "             %ld KiB\n\n"
                 "You opened:  %s\n"
                 "             %ld KiB\n\n"
                 "The game's code is translated from the ROM when the "
                 "executable is built, so it only runs with the exact file it "
                 "was built from. Running it with another one would crash "
                 "somewhere unhelpful, so it stops here instead.\n\n"
                 "Either open the file above, or rebuild from this one:\n\n"
                 "    bash scripts/build-windows.sh \"%s\"",
                 GB_ROM_PATH, (long)GB_ROM_SIZE / 1024,
                 rom_path, (long)size / 1024, rom_path);
        MessageBox(NULL, msg, "Oracle of Seasons - wrong ROM", MB_ICONERROR);
        return 1;
    }

    static gb_t gb;
    if (gb_init(&gb, rom, size) != 0) {
        MessageBox(NULL, "This file is not a valid Game Boy ROM.",
                   "Oracle of Seasons", MB_ICONERROR);
        return 1;
    }

    SetUnhandledExceptionFilter(on_crash);

    app.gb = &gb;
    app.fit = GB_FIT_INTEGER;
    app.seamless = 1;
    app.best_pct = 1e9;
    app.view_mode = GB_VIEW_WORLD;
    app.menu_open = 1;              /* offered once, up front */
    app.menu_sel = GB_VIEW_WORLD;
    app.zoom = 1.0f;
    app.running = 1;
    InitializeCriticalSection(&app.lock);

    app.bmi.bmiHeader.biSize = sizeof(BITMAPINFOHEADER);
    app.bmi.bmiHeader.biWidth = GB_SCREEN_W;
    app.bmi.bmiHeader.biHeight = -GB_SCREEN_H;   /* negative: top-down */
    app.bmi.bmiHeader.biPlanes = 1;
    app.bmi.bmiHeader.biBitCount = 32;
    app.bmi.bmiHeader.biCompression = BI_RGB;

    app.timer = CreateWaitableTimer(NULL, FALSE, NULL);
    if (app.timer) {
        LARGE_INTEGER due; due.QuadPart = -FRAME_100NS;
        SetWaitableTimer(app.timer, &due, FRAME_100NS / 10000, NULL, NULL, FALSE);
    }

    WNDCLASS wc = {0};
    wc.lpfnWndProc = wndproc;
    wc.hInstance = GetModuleHandle(NULL);
    wc.lpszClassName = WINDOW_CLASS;
    wc.hCursor = LoadCursor(NULL, IDC_ARROW);
    wc.hbrBackground = (HBRUSH)GetStockObject(BLACK_BRUSH);
    RegisterClass(&wc);

    /* Open at 16:9. The screen's own height sets the scale, and the extra
     * width shows more of the world beside it, so the original view is
     * expanded rather than cropped or stretched. */
    int win_h = GB_SCREEN_H * DEFAULT_SCALE;
    int win_w = win_h * 16 / 9;
    RECT want = { 0, 0, win_w, win_h };
    AdjustWindowRect(&want, WS_OVERLAPPEDWINDOW, FALSE);

    /* The build in the title bar. Whether the thing on screen is the build
     * just made is otherwise guesswork, and guessing it wrong wastes a whole
     * round of looking for a fault that was already fixed. */
    char title[256];
    snprintf(title, sizeof(title), "Oracle of Seasons  -  %s  %s",
             GB_BUILD_REV, GB_BUILD_STAMP);

    app.hwnd = CreateWindow(WINDOW_CLASS, title,
                            WS_OVERLAPPEDWINDOW, CW_USEDEFAULT, CW_USEDEFAULT,
                            want.right - want.left, want.bottom - want.top,
                            NULL, NULL, wc.hInstance, NULL);
    if (!app.hwnd) {
        MessageBox(NULL, "Could not create the window.", "Error", MB_ICONERROR);
        return 1;
    }
    ShowWindow(app.hwnd, SW_SHOW);

    gb.frame_cb = on_frame;
    app.thread = CreateThread(NULL, 16 * 1024 * 1024, game_thread, &gb, 0, NULL);

    MSG msg;
    while (GetMessage(&msg, NULL, 0, 0) > 0) {
        TranslateMessage(&msg);
        DispatchMessage(&msg);
    }

    InterlockedExchange(&app.running, 0);
    gb.stopped = 1;
    if (app.thread) {
        WaitForSingleObject(app.thread, 2000);
        CloseHandle(app.thread);
    }
    if (app.timer) CloseHandle(app.timer);
    free(app.map_pixels);
    DeleteCriticalSection(&app.lock);
    gb_free(&gb);
    free(rom);
    return 0;
}
