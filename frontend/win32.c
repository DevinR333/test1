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
    uint32_t      pixels[GB_SCREEN_W * GB_SCREEN_H];   /* presented copy */
    BITMAPINFO    bmi;
    volatile LONG running;
    volatile LONG keys;        /* currently held */
    volatile LONG keys_latched; /* pressed since the last frame, even if released */
    volatile LONG want_diag;
    gb_fit_mode_t fit;
    int           integer_scale;
    float         zoom;
    float         pan_x, pan_y;

    /* World map view: the whole world drawn from its room data, at any
     * scale, rather than the hardware's 160x144 window. */
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

    EnterCriticalSection(&app.lock);
    memcpy(app.pixels, gb->framebuffer, sizeof(app.pixels));
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

    if (app.hwnd)
        InvalidateRect(app.hwnd, NULL, FALSE);

    if (!InterlockedCompareExchange(&app.running, 0, 0)) {
        gb->stopped = 1;
        return;
    }

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

    SetStretchBltMode(dc, COLORONCOLOR);

    /* At zoom 1 the screen is as large as it can be while staying square. */
    float base = (float)cw / GB_SCREEN_W;
    float by = (float)ch / GB_SCREEN_H;
    if (by < base) base = by;

    /* Above natural size there is no world to put around the screen. Nor is
     * there anywhere the world data does not describe - menus, cutscenes and
     * the opening - where surrounding the screen with overworld scenery would
     * show somewhere the player is not. */
    int world_ok = app.gb && gb_world_in_overworld(app.gb);
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
        EnterCriticalSection(&app.lock);
        StretchDIBits(dc, v.dst_x, v.dst_y, v.dst_w, v.dst_h,
                      (int)(v.src_x + 0.5f), (int)(v.src_y + 0.5f),
                      (int)(v.src_w + 0.5f), (int)(v.src_h + 0.5f),
                      app.pixels, &app.bmi, DIB_RGB_COLORS, SRCCOPY);
        LeaveCriticalSection(&app.lock);
        EndPaint(hwnd, &ps);
        return;
    }

    /* At natural size and below, the window is filled with world and the live
     * screen sits in it at its own scale. At exactly 1 the screen is the size
     * the hardware intends and the rest of a widescreen window shows the world
     * around it, rather than black bars. Nothing is stretched: every pixel is
     * drawn at the same scale. */
    float scale = base * app.zoom;

    int room = app.gb ? gb_world_active_room(app.gb) : -1;
    if (room < 0) room = 0;

    /* Where the screen actually is, which during a room transition is
     * somewhere between two rooms rather than at either one's corner. */
    float screen_x = (room % GB_WORLD_COLS) * 160.0f;
    float screen_y = (room / GB_WORLD_COLS) * 128.0f;
    if (app.gb)
        gb_world_screen_origin(app.gb, &screen_x, &screen_y);

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
    if (app.gb) {
        float lx, ly;
        if (gb_world_link_position(app.gb, &lx, &ly)) {
            cam_x = lx;
            cam_y = ly;
        }
    }
    app.cam_x = cam_x;
    app.cam_y = cam_y;
    app.cam_valid = 1;

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

    EnterCriticalSection(&app.lock);
    gb_world_render(app.gb, app.map_pixels, cw, ch, cam_x, cam_y, scale);
    LeaveCriticalSection(&app.lock);

    /* The surrounding world fills the window, so a widescreen display shows
     * more world across rather than bars. */
    StretchDIBits(dc, 0, 0, cw, ch, 0, 0, cw, ch,
                  app.map_pixels, &app.map_bmi, DIB_RGB_COLORS, SRCCOPY);

    /* The live screen goes where the game says it is, so its content lines up
     * with the world around it throughout a transition. */
    float left = cam_x - (cw * 0.5f) / scale;
    float top  = cam_y - (ch * 0.5f) / scale;

    int lx = (int)((screen_x - left) * scale);
    int ly = (int)((screen_y - top) * scale);
    int lw = (int)(160.0f * scale + 0.5f);
    int lh = (int)(128.0f * scale + 0.5f);
    if (lw < 1) lw = 1;
    if (lh < 1) lh = 1;

    EnterCriticalSection(&app.lock);
    /* Only the part of the screen showing the room. The status bar covers the
     * top sixteen rows - the game scrolls the room by sixteen less than its
     * true position to make room for it - so the room starts on row 16, and
     * taking it from row 0 puts the world sixteen pixels out and paints the
     * hearts and rupees into it. */
    StretchDIBits(dc, lx, ly, lw, lh, 0, GB_STATUS_H, GB_SCREEN_W, 128,
                  app.pixels, &app.bmi, DIB_RGB_COLORS, SRCCOPY);
    LeaveCriticalSection(&app.lock);

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
            PostMessage(hwnd, WM_CLOSE, 0, 0);
            return 0;
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

int main(int argc, char **argv)
{
    const char *rom_path = (argc > 1) ? argv[1] : "rom.gbc";

    size_t size = 0;
    uint8_t *rom = read_file(rom_path, &size);
    if (!rom) {
        char msg[512];
        snprintf(msg, sizeof(msg),
                 "Could not open the ROM:\n\n%s\n\n"
                 "Pass one on the command line, or put it beside the "
                 "executable as rom.gbc", rom_path);
        MessageBox(NULL, msg, "Oracle of Seasons", MB_ICONERROR);
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

    app.hwnd = CreateWindow(WINDOW_CLASS, "Oracle of Seasons",
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
