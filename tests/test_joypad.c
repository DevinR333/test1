/* The joypad register reports one of two button rows, active low, chosen by
 * the two selection bits. A game writes the selection then reads it back, so
 * the value must reflect the selection in force at the moment of the read. */
#include <stdio.h>
#include <string.h>
#include "gb.h"

enum { BTN_A, BTN_B, BTN_SELECT, BTN_START, BTN_RIGHT, BTN_LEFT, BTN_UP, BTN_DOWN };

static int failures;

static void check(const char *what, int got, int want)
{
    if (got != want) {
        printf("  FAIL: %s: got %02X, want %02X\n", what, got, want);
        failures++;
    } else {
        printf("  ok: %-42s %02X\n", what, got);
    }
}

int main(void)
{
    static gb_t gb;
    static uint8_t rom[0x8000];
    rom[0x147] = 0x00;
    memset(&gb, 0, sizeof gb);
    gb_init(&gb, rom, sizeof rom);

    /* Nothing pressed: every line reads high. */
    gb_write(&gb, 0xFF00, 0x20);                 /* select directions */
    check("no buttons, directions selected", gb_read(&gb, 0xFF00), 0xEF);

    /* Direction row selected, Right and Up held. */
    gb.joypad = (1 << BTN_RIGHT) | (1 << BTN_UP);
    gb_write(&gb, 0xFF00, 0x20);
    check("right+up held, directions selected", gb_read(&gb, 0xFF00), 0xEA);

    /* Same buttons, other row selected: they must not appear. */
    gb_write(&gb, 0xFF00, 0x10);                 /* select actions */
    check("right+up held, actions selected", gb_read(&gb, 0xFF00), 0xDF);

    /* Action row, A and Start held. */
    gb.joypad = (1 << BTN_A) | (1 << BTN_START);
    gb_write(&gb, 0xFF00, 0x10);
    check("a+start held, actions selected", gb_read(&gb, 0xFF00), 0xD6);

    /* Switching selection must change the answer immediately, not next frame:
     * this is what a cached value got wrong. */
    gb_write(&gb, 0xFF00, 0x20);
    check("a+start held, directions selected", gb_read(&gb, 0xFF00), 0xEF);
    gb_write(&gb, 0xFF00, 0x10);
    check("switched straight back to actions", gb_read(&gb, 0xFF00), 0xD6);

    /* Repeated reads without an intervening write stay stable. */
    int first = gb_read(&gb, 0xFF00);
    check("repeated read is stable", gb_read(&gb, 0xFF00), first);

    printf("\n  %s\n", failures ? "FAILURES" : "joypad reports the selected row correctly");
    return failures != 0;
}
