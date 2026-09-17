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

static DWORD WINAPI game_thread(LPVOID param)
{
    gb_run((gb_t *)param);
    InterlockedExchange(&app.running, 0);
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
        if (app.gb) app.gb->stopped = 1;
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
    app.thread = CreateThread(NULL, 0, game_thread, &gb, 0, NULL);

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
