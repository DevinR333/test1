#!/usr/bin/env python3
"""
Checks that nothing on the death card overlaps anything else.

The card is built from two ends - the button stack owns the foot, the score
owns the top, the stats strip goes between - and the failure mode is silent:
the stack used to be shoved upward to make room for a SECOND LIFE button and
simply painted over the stats strip and the two coin lines under it.

Mirror of GameOverScreen.draw. Run: python3 tools/gameover/layout.py
"""

BUTTON_STACK_H = 224.0
SECOND_LIFE_ROW_H = 124.0
SCREEN_H = 1600.0
STATS_FULL_H = 122.0
STATS_ONE_LINE_H = 88.0
STATS_BARE_H = 46.0


def layout(h, w, lives, ranked):
    y = 0.0
    rank_h = 68.0 if ranked else 0.0
    stack_h = BUTTON_STACK_H + (SECOND_LIFE_ROW_H if lives else 0.0)
    stack_top = y + h - 44.0 - stack_h

    score_room = (stack_top - STATS_FULL_H - 38.0 - 24.0) - (y + 138.0) - (44.0 + rank_h)
    score_size = min(min(w * 0.30, 200.0), max(score_room / 1.24, 56.0))

    ty = y + 104.0
    ty += 34.0
    ty += score_size
    ty += score_size * 0.24 + 10.0
    ty += 24.0
    ty += 20.0
    if ranked:
        ty += 16.0 + 52.0

    gap = stack_top - 8.0 - (ty + 24.0)
    if gap >= STATS_FULL_H:
        block = STATS_FULL_H
    elif gap >= STATS_ONE_LINE_H:
        block = STATS_ONE_LINE_H
    elif gap >= STATS_BARE_H:
        block = STATS_BARE_H
    else:
        block = 0.0
    stats_y = ty + 24.0 + max(gap - block, 0.0) * 0.4
    return dict(score_bottom=ty, stats_y=stats_y, stats_bottom=stats_y + block,
                stack_top=stack_top, card_bottom=y + h, score_size=score_size,
                bank_line=block >= STATS_FULL_H, block=block)


def main():
    bad = 0
    print(f"{'h':>6} {'lives':>5} {'rank':>5} {'score':>6} {'scoreEnd':>8} "
          f"{'statsY':>7} {'statsEnd':>8} {'stackTop':>8}  bank  verdict")
    # h is min(1600 - insets - 100, 1020); 760 covers a very tall gesture bar.
    for h in (1020.0, 940.0, 860.0, 780.0):
        for w in (760.0, 520.0):
            for lives in (0, 1):
                for ranked in (False, True):
                    L = layout(h, w, lives, ranked)
                    ok = (L["stats_y"] >= L["score_bottom"] + 1e-6
                          and L["stats_bottom"] <= L["stack_top"] + 1e-6
                          and L["stack_top"] + (BUTTON_STACK_H
                                                + (SECOND_LIFE_ROW_H if lives else 0)) <= L["card_bottom"])
                    if not ok:
                        bad += 1
                    if w == 760.0:
                        print(f"{h:6.0f} {lives:5d} {int(ranked):5d} {L['score_size']:6.0f} "
                              f"{L['score_bottom']:8.0f} {L['stats_y']:7.0f} {L['stats_bottom']:8.0f} "
                              f"{L['stack_top']:8.0f}  {int(L['bank_line'])}     "
                              + ("ok" if ok else "OVERLAP"))
    print()
    print("clean" if bad == 0 else f"{bad} overlapping combination(s)")
    return 1 if bad else 0


if __name__ == "__main__":
    raise SystemExit(main())
