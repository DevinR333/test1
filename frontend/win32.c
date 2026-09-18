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
    int           map_mode;
    float         map_scale;
    float         map_cam_x, map_cam_y;
    uint32_t     *map_pixels;
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
        InvalidateRect(app.hwnd, NULL, app.map_mode ? TRUE : FALSE);

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

/* The scale at which the map shows the world at the same size the game does. */
static float game_scale(HWND hwnd)
{
    RECT rc;
    GetClientRect(hwnd, &rc);
    float sx = (float)(rc.right - rc.left) / GB_SCREEN_W;
    float sy = (float)(rc.bottom - rc.top) / GB_SCREEN_H;
    return sx < sy ? sx : sy;
}

/* Opens the map where the player is, at the size the game was showing. */
static void enter_map(HWND hwnd)
{
    RECT rc;
    GetClientRect(hwnd, &rc);

    int room = app.gb ? gb_world_active_room(app.gb) : -1;
    if (room >= 0) {
        app.map_cam_x = (room % GB_WORLD_COLS) * 160.0f + 80.0f;
        app.map_cam_y = (room / GB_WORLD_COLS) * 128.0f + 64.0f;
    } else {
        app.map_cam_x = GB_WORLD_W * 0.5f;
        app.map_cam_y = GB_WORLD_H * 0.5f;
    }

    app.map_scale = game_scale(hwnd);
    float fit = gb_world_fit_scale(rc.right, rc.bottom);
    if (app.map_scale < fit) app.map_scale = fit;
    app.map_mode = 1;
}

static void paint(HWND hwnd)
{
    PAINTSTRUCT ps;
    HDC dc = BeginPaint(hwnd, &ps);

    RECT rc;
    GetClientRect(hwnd, &rc);
    int cw = rc.right - rc.left, ch = rc.bottom - rc.top;

    gb_viewport_t v = gb_fit_viewport(cw, ch, app.fit, app.zoom,
                                     app.pan_x, app.pan_y);

    /* Fill the letterbox, or old pixels stay visible when the window grows. */
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

    /* Nearest-neighbour keeps pixel art crisp; the default would blur it. */
    SetStretchBltMode(dc, COLORONCOLOR);

    if (app.map_mode) {
        /* Render the world at the window's own resolution, so zooming is
         * genuine detail rather than a magnified 160x144. */
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
            if (app.map_scale <= 0.0f)
                app.map_scale = gb_world_fit_scale(cw, ch);
        }
        if (app.map_pixels) {
            EnterCriticalSection(&app.lock);
            gb_world_render(app.gb, app.map_pixels, cw, ch,
                            app.map_cam_x, app.map_cam_y, app.map_scale);
            gb_world_mark_room(app.map_pixels, cw, ch, app.map_cam_x,
                               app.map_cam_y, app.map_scale,
                               gb_world_active_room(app.gb));
            LeaveCriticalSection(&app.lock);
            StretchDIBits(dc, 0, 0, cw, ch, 0, 0, cw, ch,
                          app.map_pixels, &app.map_bmi, DIB_RGB_COLORS, SRCCOPY);
        }
        EndPaint(hwnd, &ps);
        return;
    }

    EnterCriticalSection(&app.lock);
    /* The source rect narrows as the zoom rises, so magnifying shows less of
     * the screen rather than stretching what is there. */
    StretchDIBits(dc,
                  v.dst_x, v.dst_y, v.dst_w, v.dst_h,
                  (int)(v.src_x + 0.5f), (int)(v.src_y + 0.5f),
                  (int)(v.src_w + 0.5f), (int)(v.src_h + 0.5f),
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
        if (msg == WM_KEYDOWN && wp == VK_TAB) {
            if (app.map_mode) app.map_mode = 0;
            else              enter_map(hwnd);
            InvalidateRect(hwnd, NULL, TRUE);
            return 0;
        }

        if (app.map_mode && msg == WM_KEYDOWN) {
            RECT rc; GetClientRect(hwnd, &rc);
            float fit = gb_world_fit_scale(rc.right, rc.bottom);
            float step = 24.0f / app.map_scale;
            switch (wp) {
            case VK_OEM_PLUS: case VK_ADD:
                app.map_scale *= 1.25f;
                /* Zooming in past the game's own scale returns to playing. */
                if (app.map_scale > game_scale(hwnd)) {
                    app.map_mode = 0;
                    app.zoom = 1.0f;
                }
                break;
            case VK_OEM_MINUS: case VK_SUBTRACT:
                app.map_scale /= 1.25f;
                /* Stop at the point where the whole world is on screen. */
                if (app.map_scale < fit) app.map_scale = fit;
                break;
            case VK_ESCAPE:
                app.map_mode = 0;
                break;
            case '0':
                app.map_scale = fit;
                app.map_cam_x = GB_WORLD_W * 0.5f;
                app.map_cam_y = GB_WORLD_H * 0.5f;
                break;
            case VK_LEFT:  app.map_cam_x -= step; break;
            case VK_RIGHT: app.map_cam_x += step; break;
            case VK_UP:    app.map_cam_y -= step; break;
            case VK_DOWN:  app.map_cam_y += step; break;
            default: break;
            }
            if (app.map_cam_x < 0) app.map_cam_x = 0;
            if (app.map_cam_y < 0) app.map_cam_y = 0;
            if (app.map_cam_x > GB_WORLD_W) app.map_cam_x = GB_WORLD_W;
            if (app.map_cam_y > GB_WORLD_H) app.map_cam_y = GB_WORLD_H;
            InvalidateRect(hwnd, NULL, TRUE);
            return 0;
        }

        /* Zoom and pan. The Game Boy only ever renders 160x144, so zooming in
         * magnifies and crops; there is nothing outside that to zoom out to,
         * and 1.0 is the whole screen. */
        if (msg == WM_KEYDOWN && (wp == VK_OEM_PLUS || wp == VK_ADD)) {
            app.zoom = gb_clamp_zoom(app.zoom * 1.25f);
            InvalidateRect(hwnd, NULL, TRUE);
            return 0;
        }
        if (msg == WM_KEYDOWN && (wp == VK_OEM_MINUS || wp == VK_SUBTRACT)) {
            if (app.zoom > 1.001f) {
                app.zoom = gb_clamp_zoom(app.zoom / 1.25f);
                if (app.zoom <= 1.001f) { app.zoom = 1.0f; app.pan_x = app.pan_y = 0; }
            } else {
                /* Already showing the whole screen. Beyond this the hardware
                 * has nothing more to give, so continue into the world map,
                 * which does - one zoom range from the character to the
                 * whole of the world. */
                enter_map(hwnd);
                app.map_scale = game_scale(hwnd) / 1.25f;
            }
            InvalidateRect(hwnd, NULL, TRUE);
            return 0;
        }
        if (msg == WM_KEYDOWN && wp == '0') {
            app.zoom = 1.0f; app.pan_x = app.pan_y = 0;
            InvalidateRect(hwnd, NULL, TRUE);
            return 0;
        }
        /* Ctrl with the arrows pans instead of moving the character. */
        if (msg == WM_KEYDOWN && (GetKeyState(VK_CONTROL) & 0x8000)) {
            float step = 8.0f / app.zoom;
            int panned = 1;
            switch (wp) {
            case VK_LEFT:  app.pan_x -= step; break;
            case VK_RIGHT: app.pan_x += step; break;
            case VK_UP:    app.pan_y -= step; break;
            case VK_DOWN:  app.pan_y += step; break;
            default: panned = 0; break;
            }
            if (panned) { InvalidateRect(hwnd, NULL, TRUE); return 0; }
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

    RECT want = { 0, 0, GB_SCREEN_W * DEFAULT_SCALE, GB_SCREEN_H * DEFAULT_SCALE };
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
