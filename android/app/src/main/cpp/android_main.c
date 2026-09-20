/* Android frontend.
 *
 * The same runtime, the same world renderer and the same display modes as the
 * desktop build; only the window, the blit and the input are different.
 *
 * The translated game never returns from the game's own loop, so it runs on
 * its own thread and hands the main thread a copy of the whole machine once
 * per frame. Composing happens on the CPU into one buffer, which is uploaded
 * as a single texture and stretched over the display - so the picture is put
 * together exactly as it is on the desktop, and a mode means the same thing
 * in both places.
 */
#include <android_native_app_glue.h>
#include <android/asset_manager.h>
#include <android/keycodes.h>
#include <android/log.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <pthread.h>
#include <stdlib.h>
#include <time.h>
#include <string.h>

#include "gb.h"
#include "worldmap.h"
#include "display.h"
#include "touch.h"

#define LOG(...) __android_log_print(ANDROID_LOG_INFO, "oracle", __VA_ARGS__)
#define ERR(...) __android_log_print(ANDROID_LOG_ERROR, "oracle", __VA_ARGS__)

/* Composing at the panel's own resolution on a handheld is a lot of pixels to
 * push every frame for a 160x144 game. This is plenty to look sharp and the
 * display stretches it. */
#define MAX_W 1280
#define MAX_H 720

/* Named at length on purpose: gb.h defines A, B, C, D, E, H and L as the
 * processor registers, so a short name here quietly becomes something else. */
static struct {
    struct android_app *app;

    EGLDisplay display;
    EGLSurface surface;
    EGLContext context;
    GLuint     program, texture;
    GLint      a_pos, a_uv, u_tex;
    int        win_w, win_h;

    /* The machine, handed over a frame at a time. */
    gb_t            *gb;
    gb_t             snapshot, view;
    gb_world_view_t  track, snapshot_track, view_track;
    gb_world_memory_t memory;
    pthread_mutex_t  lock;
    volatile int     have_frame, running;

    uint32_t *pixels;            /* the composed picture */
    int       pix_w, pix_h;

    uint8_t   prev_oam[160];
    float     prev_screen_x, prev_screen_y;
    int       prev_room, prev_objects;

    volatile uint8_t buttons;    /* what the pad and the glass hold between them */
    uint8_t   pad_mask;          /* ... and each of them on its own, so that */
    uint8_t   touch_mask;        /*     letting go of one keeps the other */
    uint32_t  touch_held;        /* a bit per on-screen control, for drawing */
    long long touch_at_ns;       /* when the glass was last touched */
    volatile uint8_t latched;    /* and anything tapped since the last frame */

    gb_view_mode_t view_mode;
    int       menu_open, menu_sel;
    float     zoom;

    /* Pacing a crossing by how far Link moves, as the desktop build does. */
    int shown_x, shown_y, burst, settling;
    int hurried;              /* frames run unshown in this crossing */

    pthread_t thread;
    uint8_t  *rom;
    size_t    rom_size;
} ORA;

/* ---------------------------------------------------------------- the game */

/* One frame of the hardware, in nanoseconds: 70224 cycles at 4.194304MHz. */
#define FRAME_NS 16742706L

/* Hold the machine to the speed the cartridge ran at.
 *
 * This used to say the display paced it, which was wrong: the display paces
 * the thread that draws, and the game runs on its own thread, so nothing
 * held it back at all and it ran as fast as the processor could carry it.
 * The deadline below moves on by one frame for every frame that is shown,
 * and ordinary play shows every frame, so ordinary play runs at exactly the
 * rate the cartridge did.
 *
 * Crossing between rooms is the one place frames go unshown, and those are
 * deliberately not paced: the game moves Link three eighths of a pixel a
 * frame there, so most frames have nothing new in them, and running those
 * without waiting is what makes a boundary take as long as walking across it
 * rather than nearly a second of sliding. It is bounded - only a crossing of
 * the overworld qualifies, a burst is capped at thirty-two frames and an
 * episode at a hundred and twenty - so it cannot become the runaway above.
 *
 * Falling behind is not repaid. If the machine stalls - the app is in the
 * background, the phone throttles - the lost frames are simply lost, because
 * the alternative is running at double speed to catch up, which is the very
 * thing this is here to prevent.
 */
static void pace(void)
{
    static struct timespec due;
    static int started;
    struct timespec now;

    clock_gettime(CLOCK_MONOTONIC, &now);
    if (!started) { due = now; started = 1; }

    due.tv_nsec += FRAME_NS;
    while (due.tv_nsec >= 1000000000L) { due.tv_nsec -= 1000000000L; due.tv_sec++; }

    long late = (long)(now.tv_sec - due.tv_sec) * 1000000000L
              + (now.tv_nsec - due.tv_nsec);
    if (late > 4 * FRAME_NS) { due = now; return; }   /* too far behind to chase */
    if (late >= 0) return;                            /* late already: no wait */

    clock_nanosleep(CLOCK_MONOTONIC, TIMER_ABSTIME, &due, NULL);
}

static int frame_body(gb_t *gb);

static void on_frame(gb_t *gb, void *user)
{
    (void)user;
    if (frame_body(gb))
        pace();
}

/* Returns whether this frame is one the window will show. */
static int frame_body(gb_t *gb)
{

    gb_world_track(&ORA.track, gb);

    pthread_mutex_lock(&ORA.lock);
    memcpy(&ORA.snapshot, gb, sizeof(ORA.snapshot));
    ORA.snapshot_track = ORA.track;
    ORA.have_frame = 1;
    pthread_mutex_unlock(&ORA.lock);

    gb->joypad = (uint8_t)(ORA.buttons | __atomic_exchange_n(&ORA.latched, 0,
                                                           __ATOMIC_SEQ_CST));

    if (!ORA.running) { gb->stopped = 1; return 1; }

    /* Crossing between rooms, show a frame for each whole pixel Link moves
     * and run the rest as fast as they compute - one frame per pixel being
     * exactly his walking rate, so a boundary takes as long as walking it. */
    int step_x, step_y;

    /* Only a crossing of the overworld. The flag is set for every screen
     * change the game makes - a door, a warp, a scene - and in those Link
     * does not move at all, which would look to the rule below like a
     * crossing that never advances. */
    int crossing = gb_world_scrolling(gb) && ORA.track.in_world;

    if (crossing) ORA.settling = 12;
    else if (ORA.settling > 0) ORA.settling--;
    if (!crossing && ORA.settling == 0) ORA.hurried = 0;

    if ((crossing || ORA.settling > 0) && ORA.hurried < 120
        && gb_world_link_step(gb, &step_x, &step_y)) {
        if (step_x == ORA.shown_x && step_y == ORA.shown_y && ORA.burst < 32) {
            ORA.burst++;
            ORA.hurried++;
            return 0;                      /* nothing new to show: no wait */
        }
        int wrapped = abs(step_x - ORA.shown_x) > 8 || abs(step_y - ORA.shown_y) > 8;
        ORA.shown_x = step_x; ORA.shown_y = step_y; ORA.burst = 0;
        if (!crossing && !wrapped) ORA.settling = 0;
    } else if (gb_world_link_step(gb, &step_x, &step_y)) {
        ORA.shown_x = step_x; ORA.shown_y = step_y; ORA.burst = 0;
    }

    return 1;
}

static void *game_thread(void *unused)
{
    (void)unused;
    gb_run(ORA.gb);
    ORA.running = 0;
    return NULL;
}

/* Reads the cartridge out of the package. Keeping it there means the app is
 * one file with nothing to place by hand. */
static uint8_t *load_rom(struct android_app *app, size_t *size)
{
    static const char *names[] = { "seasons.gbc", "rom.gbc", "seasons.gb", "rom.gb" };
    for (unsigned i = 0; i < sizeof(names) / sizeof(names[0]); i++) {
        AAsset *a = AAssetManager_open(app->activity->assetManager, names[i],
                                       AASSET_MODE_BUFFER);
        if (!a) continue;
        off_t n = AAsset_getLength(a);
        uint8_t *buf = malloc((size_t)n);
        if (buf && AAsset_read(a, buf, (size_t)n) == (int)n) {
            AAsset_close(a);
            *size = (size_t)n;
            LOG("loaded %s from the package, %ld KiB", names[i], (long)n / 1024);
            return buf;
        }
        free(buf);
        AAsset_close(a);
    }
    return NULL;
}

/* ------------------------------------------------------------------ the pad */

/* Bit per button: 0 A, 1 B, 2 select, 3 start, 4 right, 5 left, 6 up, 7 down.
 * Mapped for the pads people actually use - Xbox layout, which is what a
 * Retroid and most handhelds report. */
static int button_for(int32_t code)
{
    switch (code) {
    case AKEYCODE_BUTTON_A: case AKEYCODE_DPAD_CENTER: return 0;
    case AKEYCODE_BUTTON_B: case AKEYCODE_BUTTON_X:    return 1;
    case AKEYCODE_BUTTON_SELECT: case AKEYCODE_BUTTON_THUMBL: return 2;
    case AKEYCODE_BUTTON_START: case AKEYCODE_BUTTON_THUMBR:  return 3;
    case AKEYCODE_DPAD_RIGHT: return 4;
    case AKEYCODE_DPAD_LEFT:  return 5;
    case AKEYCODE_DPAD_UP:    return 6;
    case AKEYCODE_DPAD_DOWN:  return 7;
    default: return -1;
    }
}

/* The pad and the glass are held separately and combined, so that lifting a
 * thumb off a drawn button does not release what a real pad is holding, and
 * the other way round. */
static void settle_buttons(void)
{
    ORA.buttons = (uint8_t)(ORA.pad_mask | ORA.touch_mask);
}

static void press(int bit, int down)
{
    if (bit < 0) return;
    if (down) {
        ORA.pad_mask |= (uint8_t)(1u << bit);
        /* A tap shorter than a frame would otherwise never be seen. */
        __atomic_or_fetch(&ORA.latched, (uint8_t)(1u << bit), __ATOMIC_SEQ_CST);
    } else {
        ORA.pad_mask &= (uint8_t)~(1u << bit);
    }
    settle_buttons();
}

/* ------------------------------------------------------------ the glass */

#define TOUCH_HOLD_NS  3000000000LL   /* shown for three seconds after a touch */
#define TOUCH_FADE_NS   600000000LL   /* then half a second going */

static long long now_ns(void)
{
    struct timespec t;
    clock_gettime(CLOCK_MONOTONIC, &t);
    return (long long)t.tv_sec * 1000000000LL + t.tv_nsec;
}

/* Full while they are in use, gone a few seconds after the last touch. The
 * player who is using a pad never sees them at all. */
static float touch_alpha(void)
{
    long long age = now_ns() - ORA.touch_at_ns;
    if (ORA.touch_at_ns == 0) return 0.0f;
    if (age <= TOUCH_HOLD_NS) return 1.0f;
    if (age >= TOUCH_HOLD_NS + TOUCH_FADE_NS) return 0.0f;
    return 1.0f - (float)(age - TOUCH_HOLD_NS) / (float)TOUCH_FADE_NS;
}

static void zoom_by(float factor)
{
    ORA.zoom *= factor;
    if (ORA.zoom > 4.0f) ORA.zoom = 4.0f;
    if (ORA.zoom < 0.08f) ORA.zoom = 0.08f;
}

static void menu_move(int delta)
{
    ORA.menu_sel = (ORA.menu_sel + GB_VIEW_MODES + delta) % GB_VIEW_MODES;
}

/* What the drawn controls are holding, worked out from every finger on the
 * glass at once - so a thumb on the d-pad and a thumb on B are both heard,
 * which walking while attacking needs.
 *
 * They press the same joypad bits a gamepad presses and nothing else, so the
 * inventory, the map, the save prompt, a text box and the display chooser are
 * all driven by them without any of those knowing they exist. The chooser is
 * the one exception, because it is this program's own and reads its keys
 * rather than the joypad.
 */
static int32_t on_touch(AInputEvent *e)
{
    int32_t action = AMotionEvent_getAction(e);
    int32_t kind = action & AMOTION_EVENT_ACTION_MASK;
    size_t going = (size_t)((action & AMOTION_EVENT_ACTION_POINTER_INDEX_MASK)
                            >> AMOTION_EVENT_ACTION_POINTER_INDEX_SHIFT);

    /* The first touch after they have faded only brings them back. Pressing
     * a button the player cannot see is not something they asked for. */
    int was_visible = touch_alpha() > 0.0f;
    ORA.touch_at_ns = now_ns();
    if (!was_visible) {
        ORA.touch_mask = 0;
        ORA.touch_held = 0;
        settle_buttons();
        return 1;
    }

    uint32_t held = 0;
    if (kind != AMOTION_EVENT_ACTION_UP && kind != AMOTION_EVENT_ACTION_CANCEL) {
        size_t n = AMotionEvent_getPointerCount(e);
        for (size_t i = 0; i < n; i++) {
            if (kind == AMOTION_EVENT_ACTION_POINTER_UP && i == going)
                continue;                  /* this one is on its way off */
            int hit = gb_touch_hit(ORA.win_w, ORA.win_h,
                                   AMotionEvent_getX(e, i),
                                   AMotionEvent_getY(e, i));
            if (hit >= 0) held |= 1u << hit;
        }
    }

    uint32_t began = held & ~ORA.touch_held;   /* newly pressed this event */
    ORA.touch_held = held;

    /* The chooser is ours, so it is worked here rather than through the pad. */
    if (ORA.menu_open) {
        ORA.touch_mask = 0;
        settle_buttons();
        if (began & (1u << GB_TOUCH_UP))    menu_move(-1);
        if (began & (1u << GB_TOUCH_DOWN))  menu_move(+1);
        if (began & ((1u << GB_TOUCH_A) | (1u << GB_TOUCH_START))) {
            ORA.view_mode = (gb_view_mode_t)ORA.menu_sel;
            ORA.menu_open = 0;
        }
        if (began & (1u << GB_TOUCH_B)) ORA.menu_open = 0;
        return 1;
    }

    if (began & (1u << GB_TOUCH_ZOOM_OUT)) zoom_by(1.0f / 1.25f);
    if (began & (1u << GB_TOUCH_ZOOM_IN))  zoom_by(1.25f);

    uint8_t mask = 0;
    for (int i = 0; i < GB_TOUCH_COUNT; i++) {
        int bit = gb_touch_button((gb_touch_id_t)i);
        if (bit >= 0 && (held & (1u << i))) mask |= (uint8_t)(1u << bit);
    }
    /* Latch what was newly pressed, so a tap between two frames still counts
     * - the same rule the pad gets. */
    uint8_t fresh = 0;
    for (int i = 0; i < GB_TOUCH_COUNT; i++) {
        int bit = gb_touch_button((gb_touch_id_t)i);
        if (bit >= 0 && (began & (1u << i))) fresh |= (uint8_t)(1u << bit);
    }
    if (fresh) __atomic_or_fetch(&ORA.latched, fresh, __ATOMIC_SEQ_CST);

    ORA.touch_mask = mask;
    settle_buttons();
    return 1;
}

static int32_t on_input(struct android_app *app, AInputEvent *e)
{
    (void)app;
    int32_t type = AInputEvent_getType(e);

    if (type == AINPUT_EVENT_TYPE_KEY) {
        int32_t code = AKeyEvent_getKeyCode(e);
        int down = AKeyEvent_getAction(e) == AKEY_EVENT_ACTION_DOWN;

        /* The chooser. It is up when the game starts, and Y brings it back
         * at any point. */
        if (code == AKEYCODE_BUTTON_Y || code == AKEYCODE_BUTTON_MODE) {
            if (down) {
                ORA.menu_open = !ORA.menu_open;
                ORA.menu_sel = ORA.view_mode;
            }
            return 1;
        }
        if (ORA.menu_open) {
            if (!down) return 1;
            switch (code) {
            case AKEYCODE_DPAD_UP:   menu_move(-1); return 1;
            case AKEYCODE_DPAD_DOWN: menu_move(+1); return 1;
            case AKEYCODE_BUTTON_A: case AKEYCODE_BUTTON_START:
            case AKEYCODE_DPAD_CENTER:
                ORA.view_mode = (gb_view_mode_t)ORA.menu_sel;
                ORA.menu_open = 0;
                return 1;
            case AKEYCODE_BACK: case AKEYCODE_BUTTON_B:
                ORA.menu_open = 0;
                return 1;
            default: return 1;
            }
        }

        /* Shoulders pull the camera back and push it in, while playing. */
        if (code == AKEYCODE_BUTTON_L1) { if (down) zoom_by(1.0f / 1.25f); return 1; }
        if (code == AKEYCODE_BUTTON_R1) { if (down) zoom_by(1.25f); return 1; }

        int bit = button_for(code);
        if (bit >= 0) { press(bit, down); return 1; }
        return 0;                          /* back, volume: let Android have it */
    }

    if (type == AINPUT_EVENT_TYPE_MOTION
        && (AInputEvent_getSource(e) & AINPUT_SOURCE_TOUCHSCREEN))
        return on_touch(e);

    if (type == AINPUT_EVENT_TYPE_MOTION
        && (AInputEvent_getSource(e) & (AINPUT_SOURCE_JOYSTICK | AINPUT_SOURCE_DPAD))) {
        /* The hat and the left stick both steer. A pad that reports its
         * d-pad as an axis is common enough that ignoring it would look like
         * the d-pad not working at all. */
        float hx = AMotionEvent_getAxisValue(e, AMOTION_EVENT_AXIS_HAT_X, 0);
        float hy = AMotionEvent_getAxisValue(e, AMOTION_EVENT_AXIS_HAT_Y, 0);
        float sx = AMotionEvent_getAxisValue(e, AMOTION_EVENT_AXIS_X, 0);
        float sy = AMotionEvent_getAxisValue(e, AMOTION_EVENT_AXIS_Y, 0);
        float x = (hx != 0.0f) ? hx : sx;
        float y = (hy != 0.0f) ? hy : sy;
        const float edge = 0.45f;          /* past the slop of a worn stick */

        if (ORA.menu_open) {
            static int held = 0;
            if (y < -edge && !held) { menu_move(-1); held = 1; }
            else if (y > edge && !held) { menu_move(+1); held = 1; }
            else if (y > -edge && y < edge) held = 0;
            return 1;
        }
        press(5, x < -edge);
        press(4, x >  edge);
        press(6, y < -edge);
        press(7, y >  edge);
        return 1;
    }
    return 0;
}

/* --------------------------------------------------------------- the screen */

static const char *VERT =
    "attribute vec2 pos; attribute vec2 uv; varying vec2 v;"
    "void main(){ v = uv; gl_Position = vec4(pos, 0.0, 1.0); }";

/* The composed picture is 0xAARRGGBB in memory, which arrives here as BGRA,
 * so the channels come back in that order and are put right at no cost. */
static const char *FRAG =
    "precision mediump float; varying vec2 v; uniform sampler2D tex;"
    "void main(){ gl_FragColor = texture2D(tex, v).bgra; }";

static GLuint compile(GLenum kind, const char *src)
{
    GLuint s = glCreateShader(kind);
    glShaderSource(s, 1, &src, NULL);
    glCompileShader(s);
    GLint ok = 0;
    glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[512];
        glGetShaderInfoLog(s, sizeof(log), NULL, log);
        ERR("shader: %s", log);
    }
    return s;
}

static int gl_start(void)
{
    const EGLint attribs[] = {
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_BLUE_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_RED_SIZE, 8,
        EGL_NONE
    };
    const EGLint ctx_attribs[] = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE };

    ORA.display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    eglInitialize(ORA.display, NULL, NULL);

    EGLConfig config;
    EGLint n = 0;
    eglChooseConfig(ORA.display, attribs, &config, 1, &n);
    if (n < 1) { ERR("no usable EGL config"); return 0; }

    ORA.surface = eglCreateWindowSurface(ORA.display, config, ORA.app->window, NULL);
    ORA.context = eglCreateContext(ORA.display, config, EGL_NO_CONTEXT, ctx_attribs);
    if (!eglMakeCurrent(ORA.display, ORA.surface, ORA.surface, ORA.context)) {
        ERR("eglMakeCurrent failed");
        return 0;
    }
    eglQuerySurface(ORA.display, ORA.surface, EGL_WIDTH, &ORA.win_w);
    eglQuerySurface(ORA.display, ORA.surface, EGL_HEIGHT, &ORA.win_h);

    ORA.program = glCreateProgram();
    glAttachShader(ORA.program, compile(GL_VERTEX_SHADER, VERT));
    glAttachShader(ORA.program, compile(GL_FRAGMENT_SHADER, FRAG));
    glLinkProgram(ORA.program);
    ORA.a_pos = glGetAttribLocation(ORA.program, "pos");
    ORA.a_uv  = glGetAttribLocation(ORA.program, "uv");
    ORA.u_tex = glGetUniformLocation(ORA.program, "tex");

    glGenTextures(1, &ORA.texture);
    glBindTexture(GL_TEXTURE_2D, ORA.texture);
    /* Nearest, so the picture stays as crisp as the pixels it is made of. */
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    /* Composed at most this large however big the panel is: a 160x144 game
     * does not need four million pixels pushed at it every frame. */
    ORA.pix_w = ORA.win_w < MAX_W ? ORA.win_w : MAX_W;
    ORA.pix_h = ORA.win_h < MAX_H ? ORA.win_h : MAX_H;
    free(ORA.pixels);
    ORA.pixels = malloc((size_t)ORA.pix_w * ORA.pix_h * sizeof(uint32_t));
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, ORA.pix_w, ORA.pix_h, 0,
                 GL_RGBA, GL_UNSIGNED_BYTE, NULL);
    LOG("display %dx%d, composing %dx%d", ORA.win_w, ORA.win_h, ORA.pix_w, ORA.pix_h);
    return 1;
}

static void gl_stop(void)
{
    if (ORA.display == EGL_NO_DISPLAY) return;
    eglMakeCurrent(ORA.display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    if (ORA.context != EGL_NO_CONTEXT) eglDestroyContext(ORA.display, ORA.context);
    if (ORA.surface != EGL_NO_SURFACE) eglDestroySurface(ORA.display, ORA.surface);
    eglTerminate(ORA.display);
    ORA.display = EGL_NO_DISPLAY;
    ORA.context = EGL_NO_CONTEXT;
    ORA.surface = EGL_NO_SURFACE;
}

/* Exactly the desktop composition: world, then whoever was in the other
 * rooms, then the live objects, then the hardware's own screen, then the
 * status bar - or, in a fixed shape, the screen alone in its frame. */
static void compose(void)
{
    const int w = ORA.pix_w, h = ORA.pix_h;
    uint32_t *out = ORA.pixels;
    if (!out) return;

    pthread_mutex_lock(&ORA.lock);
    memcpy(&ORA.view, &ORA.snapshot, sizeof(ORA.view));
    ORA.view_track = ORA.snapshot_track;
    int have = ORA.have_frame;
    pthread_mutex_unlock(&ORA.lock);

    if (!have) { memset(out, 0, (size_t)w * h * sizeof(uint32_t)); return; }

    const gb_t *view = &ORA.view;
    const gb_world_view_t *where = &ORA.view_track;

    if (ORA.view_mode != GB_VIEW_WORLD || !where->in_world) {
        gb_view_mode_t m = ORA.view_mode == GB_VIEW_WORLD ? GB_VIEW_16_9 : ORA.view_mode;
        gb_rect_t r = gb_view_screen(m, w, h);
        memset(out, 0, (size_t)w * h * sizeof(uint32_t));
        for (int y = 0; y < r.h; y++) {
            int dy = r.y + y;
            if (dy < 0 || dy >= h) continue;
            const uint32_t *src =
                view->framebuffer + (y * GB_SCREEN_H / r.h) * GB_SCREEN_W;
            uint32_t *row = out + (size_t)dy * w;
            for (int x = 0; x < r.w; x++) {
                int dx = r.x + x;
                if (dx >= 0 && dx < w) row[dx] = src[x * GB_SCREEN_W / r.w];
            }
        }
        return;
    }

    float base = (float)h / GB_SCREEN_H;
    float scale = base * ORA.zoom;
    float screen_x = where->screen_x, screen_y = where->screen_y;
    float cam_x = where->have_link ? where->link_x : screen_x + 80.0f;
    float cam_y = where->have_link ? where->link_y : screen_y + 64.0f;
    int loaded = gb_world_active_room(view);

    gb_world_render(view, out, w, h, cam_x, cam_y, scale, 1);

    gb_world_remember(&ORA.memory, view, screen_x, screen_y, cam_x, cam_y,
                      where->crossing ? -1 : loaded);
    gb_world_draw_remembered(&ORA.memory, out, w, h, cam_x, cam_y, scale,
                             where->crossing ? -1 : loaded);

    if (ORA.prev_objects && ORA.prev_room == loaded)
        gb_world_draw_objects(view, ORA.prev_oam, out, w, h, cam_x, cam_y, scale,
                              ORA.prev_screen_x, ORA.prev_screen_y);
    gb_world_draw_objects(view, NULL, out, w, h, cam_x, cam_y, scale,
                          screen_x, screen_y);
    memcpy(ORA.prev_oam, view->oam, sizeof(ORA.prev_oam));
    ORA.prev_screen_x = screen_x;
    ORA.prev_screen_y = screen_y;
    ORA.prev_room = loaded;
    ORA.prev_objects = 1;

    if (!where->crossing)
        gb_world_draw_screen(view, out, w, h, cam_x, cam_y, scale,
                             screen_x, screen_y);
    gb_world_draw_status(view, out, w, h, base);
}

static void draw(void)
{
    if (ORA.display == EGL_NO_DISPLAY || !ORA.pixels) return;

    compose();
    if (ORA.menu_open)
        gb_menu_draw(ORA.pixels, ORA.pix_w, ORA.pix_h, ORA.menu_sel);

    /* Over everything, including the chooser, because they work it too. */
    gb_touch_draw(ORA.pixels, ORA.pix_w, ORA.pix_h, ORA.touch_held, touch_alpha());

    glBindTexture(GL_TEXTURE_2D, ORA.texture);
    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, ORA.pix_w, ORA.pix_h,
                    GL_RGBA, GL_UNSIGNED_BYTE, ORA.pixels);

    glViewport(0, 0, ORA.win_w, ORA.win_h);
    glClearColor(0, 0, 0, 1);
    glClear(GL_COLOR_BUFFER_BIT);

    static const GLfloat quad[] = { -1,-1, 1,-1, -1, 1, 1, 1 };
    static const GLfloat uv[]   = {  0, 1, 1, 1,  0, 0, 1, 0 };

    glUseProgram(ORA.program);
    glUniform1i(ORA.u_tex, 0);
    glActiveTexture(GL_TEXTURE0);
    glEnableVertexAttribArray(ORA.a_pos);
    glVertexAttribPointer(ORA.a_pos, 2, GL_FLOAT, GL_FALSE, 0, quad);
    glEnableVertexAttribArray(ORA.a_uv);
    glVertexAttribPointer(ORA.a_uv, 2, GL_FLOAT, GL_FALSE, 0, uv);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    eglSwapBuffers(ORA.display, ORA.surface);
}

static void on_cmd(struct android_app *app, int32_t cmd)
{
    switch (cmd) {
    case APP_CMD_INIT_WINDOW:
        if (app->window) gl_start();
        break;
    case APP_CMD_TERM_WINDOW:
        gl_stop();
        break;
    case APP_CMD_GAINED_FOCUS:
    case APP_CMD_LOST_FOCUS:
        /* Nothing held down carries across a focus change. */
        ORA.pad_mask = ORA.touch_mask = 0;
        ORA.touch_held = 0;
        settle_buttons();
        break;
    default:
        break;
    }
}

void android_main(struct android_app *app)
{
    memset(&ORA, 0, sizeof(ORA));
    ORA.app = app;
    ORA.display = EGL_NO_DISPLAY;
    ORA.context = EGL_NO_CONTEXT;
    ORA.surface = EGL_NO_SURFACE;
    ORA.zoom = 1.0f;
    ORA.view_mode = GB_VIEW_WORLD;
    ORA.menu_open = 1;                    /* offered once, up front */
    ORA.menu_sel = GB_VIEW_WORLD;
    ORA.running = 1;
    ORA.shown_x = ORA.shown_y = -1;
    pthread_mutex_init(&ORA.lock, NULL);

    app->onAppCmd = on_cmd;
    app->onInputEvent = on_input;

    ORA.rom = load_rom(app, &ORA.rom_size);
    if (!ORA.rom) {
        ERR("no ROM in the package. Put seasons.gbc in app/src/main/assets.");
        return;
    }

    static gb_t machine;
    if (gb_init(&machine, ORA.rom, ORA.rom_size) != 0) {
        ERR("that file is not a Game Boy ROM");
        return;
    }
    machine.frame_cb = on_frame;
    ORA.gb = &machine;
    pthread_create(&ORA.thread, NULL, game_thread, NULL);

    while (1) {
        int events;
        struct android_poll_source *source;
        /* Wait for events only when there is nothing to draw. */
        int timeout = (ORA.display == EGL_NO_DISPLAY) ? -1 : 0;
        while (ALooper_pollAll(timeout, NULL, &events, (void **)&source) >= 0) {
            if (source) source->process(app, source);
            if (app->destroyRequested) {
                ORA.running = 0;
                machine.stopped = 1;
                pthread_join(ORA.thread, NULL);
                gl_stop();
                return;
            }
            timeout = 0;
        }
        draw();
    }
}
