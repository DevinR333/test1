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
STATS_TOP_GAP = 30.0
VALUE_BASE = 40.0
LABEL_BASE = 72.0
PICKUP_BASE = 110.0
BANK_BASE = 148.0
STATS_FULL_H = 158.0
STATS_ONE_LINE_H = 120.0
STATS_BARE_H = 80.0

# Text is drawn from a BASELINE, so a line reaches upward by roughly its cap height. This is
# what the first version of the check missed: it agreed with the block heights while the code
# drew the first figure from a baseline where the layout meant a top, which put 46-unit digits
# about 37 units above the y they were given - on top of the rank pill. Anything that claims a
# height here must also say where its ink actually lands.
CAP = 0.82


def ink_top(baseline, size):
    return baseline - size * CAP


def ink_bottom(baseline, size):
    return baseline + size * 0.25


def layout(h, w, lives, ranked):
    y = 0.0
    rank_h = 68.0 if ranked else 0.0
    stack_h = BUTTON_STACK_H + (SECOND_LIFE_ROW_H if lives else 0.0)
    stack_top = y + h - 44.0 - stack_h

    score_room = (stack_top - STATS_FULL_H - 38.0 - STATS_TOP_GAP) - (y + 138.0) - (44.0 + rank_h)
    score_size = min(min(w * 0.30, 200.0), max(score_room / 1.24, 56.0))

    ty = y + 104.0
    ty += 34.0
    ty += score_size
    ty += score_size * 0.24 + 10.0
    ty += 24.0
    points_base = ty
    ty += 20.0
    pill_top = None
    if ranked:
        pill_top = ty + 16.0
        ty += 16.0 + 52.0

    gap = stack_top - 8.0 - (ty + STATS_TOP_GAP)
    if gap >= STATS_FULL_H:
        block = STATS_FULL_H
    elif gap >= STATS_ONE_LINE_H:
        block = STATS_ONE_LINE_H
    elif gap >= STATS_BARE_H:
        block = STATS_BARE_H
    else:
        block = 0.0
    stats_y = ty + STATS_TOP_GAP + max(gap - block, 0.0) * 0.4

    # where the ink of each line in the block really lands
    lines = []
    if block > 0:
        lines.append(("value", ink_top(stats_y + VALUE_BASE, 44.0), ink_bottom(stats_y + VALUE_BASE, 44.0)))
        lines.append(("label", ink_top(stats_y + LABEL_BASE, 24.0), ink_bottom(stats_y + LABEL_BASE, 24.0)))
    if block >= STATS_ONE_LINE_H:
        lines.append(("pickup", ink_top(stats_y + PICKUP_BASE, 25.0), ink_bottom(stats_y + PICKUP_BASE, 25.0)))
    if block >= STATS_FULL_H:
        lines.append(("bank", ink_top(stats_y + BANK_BASE, 27.0), ink_bottom(stats_y + BANK_BASE, 27.0)))

    above_bottom = (pill_top + 52.0) if pill_top is not None else ink_bottom(points_base, 28.0)
    return dict(score_bottom=ty, stats_y=stats_y, stats_bottom=stats_y + block,
                stack_top=stack_top, card_bottom=y + h, score_size=score_size,
                bank_line=block >= STATS_FULL_H, block=block,
                lines=lines, above_bottom=above_bottom)


def main():
    bad = 0
    tightest = [1e9, ""]
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
                    # and the ink: nothing may reach above the pill / POINTS, below the
                    # buttons, or past the height its own block claims
                    for name, top, bot in L["lines"]:
                        if name == "value":
                            clear = top - L["above_bottom"]
                            if clear < tightest[0]:
                                tightest = [clear, f"h={h:.0f} w={w:.0f} lives={lives} rank={int(ranked)}"]
                        if top < L["above_bottom"] + 1e-6:
                            ok = False
                            print(f"    {name} ink top {top:.0f} is above "
                                  f"{L['above_bottom']:.0f} (h={h:.0f} lives={lives} rank={int(ranked)})")
                        if bot > L["stack_top"] + 1e-6:
                            ok = False
                            print(f"    {name} ink bottom {bot:.0f} is under the buttons at "
                                  f"{L['stack_top']:.0f} (h={h:.0f} lives={lives} rank={int(ranked)})")
                        if bot > L["stats_bottom"] + 1e-6:
                            ok = False
                            print(f"    {name} ink bottom {bot:.0f} runs past the block end "
                                  f"{L['stats_bottom']:.0f} (h={h:.0f} lives={lives} rank={int(ranked)})")
                    if not ok:
                        bad += 1
                    if w == 760.0:
                        print(f"{h:6.0f} {lives:5d} {int(ranked):5d} {L['score_size']:6.0f} "
                              f"{L['score_bottom']:8.0f} {L['stats_y']:7.0f} {L['stats_bottom']:8.0f} "
                              f"{L['stack_top']:8.0f}  {int(L['bank_line'])}     "
                              + ("ok" if ok else "OVERLAP"))
    print()
    print(f"tightest clearance above the stats block: {tightest[0]:.0f} units ({tightest[1]})")
    print("clean" if bad == 0 else f"{bad} overlapping combination(s)")
    return 1 if bad else 0


if __name__ == "__main__":
    raise SystemExit(main())
