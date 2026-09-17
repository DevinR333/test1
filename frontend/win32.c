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
    volatile LONG keys;
    gb_fit_mode_t fit;
    int           integer_scale;
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

/* Called from the game thread each time the PPU finishes a frame. */
static void on_frame(gb_t *gb, void *user)
{
    (void)user;

    EnterCriticalSection(&app.lock);
    memcpy(app.pixels, gb->framebuffer, sizeof(app.pixels));
    LeaveCriticalSection(&app.lock);

    gb->joypad = (uint8_t)InterlockedCompareExchange(&app.keys, 0, 0);

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

static void write_log(const gb_t *gb, const char *note);

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
static void write_log(const gb_t *gb, const char *note)
{
    /* Next to the executable, not the working directory: that is what the
     * dialog tells the user, and it is where they will look after launching
     * it from Explorer rather than a shell. */
    char path[MAX_PATH];
    DWORD n = GetModuleFileName(NULL, path, MAX_PATH);
    if (n == 0 || n >= MAX_PATH) {
        strcpy(path, "oracle-log.txt");
    } else {
        char *slash = strrchr(path, '\\');
        if (slash && (size_t)(slash - path) + sizeof("\\oracle-log.txt") < MAX_PATH)
            strcpy(slash + 1, "oracle-log.txt");
        else
            strcpy(path, "oracle-log.txt");
    }

    FILE *fh = fopen(path, "w");
    if (!fh) return;
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
                 "Interpreter fallbacks: %llu\n\n"
                 "Written to oracle-log.txt beside the executable.",
                 why, gb->stop_bank, gb->stop_pc,
                 (unsigned long long)gb->frames,
                 (unsigned long long)gb->cycles,
                 (unsigned long long)gb->no_entry_count);
        MessageBox(NULL, msg, "Oracle of Seasons - stopped", MB_ICONWARNING);
    }

    if (app.hwnd)
        PostMessage(app.hwnd, WM_CLOSE, 0, 0);
    return 0;
}

static void paint(HWND hwnd)
{
    PAINTSTRUCT ps;
    HDC dc = BeginPaint(hwnd, &ps);

    RECT rc;
    GetClientRect(hwnd, &rc);
    int cw = rc.right - rc.left, ch = rc.bottom - rc.top;

    gb_viewport_t v = gb_fit_viewport(cw, ch, app.fit, 1.0f, 0, 0);

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

    EnterCriticalSection(&app.lock);
    StretchDIBits(dc,
                  v.dst_x, v.dst_y, v.dst_w, v.dst_h,
                  0, 0, GB_SCREEN_W, GB_SCREEN_H,
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
    DeleteCriticalSection(&app.lock);
    gb_free(&gb);
    free(rom);
    return 0;
}
