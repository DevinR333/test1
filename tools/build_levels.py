# -*- coding: utf-8 -*-
"""Builds the stages. Every stage has its own FORM.

The old generator gave all nine stages the same skeleton - a two-deck grid
of 16x11 rooms, a left-to-right route, four room archetypes shuffled - so
they all played the same however the seed fell. Form is what makes a level
memorable, so each stage now has a builder of its own:

  1-1 terrace   rolling ground under open sky
  1-2 warren    three stacked lanes you weave between
  1-3 well      a narrow vertical descent
  2-1 cave      an irregular cavern grown by smoothing noise
  2-2 chasm     one enormous room crossed on a lattice of ledges
  2-3 maze      a branching warren of rooms with real dead ends
  3-1 ascent    a tower climbed floor by floor
  3-2 halls     a regular keep floorplan walked in a serpentine
  3-3 grand     a very long single deck of escalating set-pieces

The three boss rooms differ too. What the forms share is only the parts
that must be shared: secrets are pockets carved out of solid rock, loot is
buried deeper than the way in, and every stage is proved completable by
tools/reach.js, which reads the real jump constants from src/entities.js.
"""
import os, random

SOLID, AIR = '#', ' '
JUMP_UP = 4          # tiles we let a required climb ask for (the arc gives 5)
JUMP_DX = 4          # tiles of gap we let a required jump ask for


class Stage:
    def __init__(self, w, h, theme, seed):
        self.W, self.H = w, h
        self.theme = theme
        self.rng = random.Random(seed)
        self.g = [[SOLID] * w for _ in range(h)]
        self.floors = []        # (x0, x1, row) - row is the surface you stand ON
        self.spawn = None
        self.exit = None

    # ---------------- raw access ----------------
    def inside(self, r, c):
        return 0 <= r < self.H and 0 <= c < self.W

    def set(self, r, c, ch):
        if self.inside(r, c):
            self.g[r][c] = ch

    def get(self, r, c):
        return self.g[r][c] if self.inside(r, c) else SOLID

    def carve(self, r0, r1, c0, c1, ch=AIR):
        for r in range(max(0, r0), min(self.H, r1 + 1)):
            for c in range(max(0, c0), min(self.W, c1 + 1)):
                self.g[r][c] = ch

    def ledge(self, row, c0, c1, ch='='):
        """A standable shelf, registered so loot and enemies can use it."""
        self.carve(row, row, c0, c1, ch)
        self.floors.append((max(0, c0), min(self.W - 1, c1), row))

    def ground(self, row, c0, c1):
        """Solid land from `row` down to the bottom of the map."""
        self.carve(row, self.H - 1, c0, c1, SOLID)
        self.floors.append((max(0, c0), min(self.W - 1, c1), row))

    def put_free(self, r, c, ch):
        if self.inside(r, c) and self.g[r][c] == AIR:
            self.g[r][c] = ch
            return True
        return False

    def rows(self):
        return [''.join(r) for r in self.g]

    # ---------------- secrets ----------------
    def carve_rock(self, r0, r1, c0, c1):
        """Turn solid rock into false wall. Anything that is not rock is left
        alone, so a pocket can never open into the level next door."""
        cells = []
        for r in range(max(0, r0), min(self.H, r1 + 1)):
            for c in range(max(0, c0), min(self.W, c1 + 1)):
                if self.g[r][c] == SOLID:
                    self.g[r][c] = 'F'
                    cells.append((r, c))
        return cells

    def unseal(self, cells):
        for (r, c) in cells:
            self.g[r][c] = SOLID

    def burial_depth(self, cells):
        """How far each pocket cell sits from open air, in tiles. The way in
        necessarily touches air; loot has to be deeper than that."""
        inside = set(cells)
        depth, frontier = {}, []
        for (r, c) in cells:
            for (dr, dc) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nr, nc = r + dr, c + dc
                if (nr, nc) in inside or not self.inside(nr, nc):
                    continue
                if self.g[nr][nc] not in (SOLID, 'F', 'B'):
                    depth[(r, c)] = 1
                    frontier.append((r, c))
                    break
        if not frontier:
            return dict((cell, 9) for cell in cells)
        while frontier:
            nxt = []
            for (r, c) in frontier:
                for (dr, dc) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    n = (r + dr, c + dc)
                    if n in inside and n not in depth:
                        depth[n] = depth[(r, c)] + 1
                        nxt.append(n)
            frontier = nxt
        return depth


# ============================================================ secrets
def try_vault(st, rng, x0, x1, row, style, index):
    """One sealed pocket hung off a place you can stand. Each style hunts
    outward for real rock first, so it works the same in a carved cave, a
    built keep and an open field."""
    mid = (x0 + x1) // 2
    if mid < 4 or mid > st.W - 5:
        return False
    cells = []

    if style == 'under':
        # find the ground, then bury the pocket a tile below its surface
        r, n = row, 0
        while r < st.H and st.get(r, mid) != SOLID and n < 4:
            r += 1; n += 1
        if r >= st.H - 3 or st.get(r, mid) != SOLID:
            return False
        top, bot = r + 2, min(st.H - 2, r + 4)
        if bot - top < 1:
            return False
        c0, c1 = mid - 2, mid + 3
        cells = st.carve_rock(top, bot, c0, c1)
        hole = rng.randint(c0 + 1, c1 - 1)       # the tile that gives way
        for rr in range(r, top):
            if st.inside(rr, hole) and st.get(rr, hole) == SOLID:
                st.set(rr, hole, 'F'); cells.append((rr, hole))

    elif style == 'side':
        step = 1 if rng.random() < 0.5 else -1
        c, n = (x1 if step > 0 else x0), 0
        while st.inside(row - 1, c) and st.get(row - 1, c) != SOLID and n < 8:
            c += step; n += 1
        if not st.inside(row - 1, c) or st.get(row - 1, c) != SOLID:
            return False
        wall = c
        a, b = wall + step, wall + step * 7
        cells = st.carve_rock(row - 3, row - 1, min(a, b), max(a, b))
        for rr in range(row - 2, row):
            if st.inside(rr, wall) and st.get(rr, wall) == SOLID:
                st.set(rr, wall, 'F'); cells.append((rr, wall))

    else:                                        # 'above'
        r, n = row - 1, 0
        while r > 2 and st.get(r, mid) != SOLID and n < 10:
            r -= 1; n += 1
        if r <= 3 or st.get(r, mid) != SOLID or row - r > 5:
            return False                         # too high to jump into
        top, bot = max(1, r - 4), r - 1
        if bot - top < 1:
            return False
        c0, c1 = mid - 3, mid + 2
        cells = st.carve_rock(top, bot, c0, c1)
        climb = rng.randint(c0 + 1, c1 - 1)
        for rr in range(bot + 1, r + 1):
            if st.inside(rr, climb) and st.get(rr, climb) == SOLID:
                st.set(rr, climb, 'F'); cells.append((rr, climb))

    if len(cells) < 7:
        st.unseal(cells)
        return False

    dmap = st.burial_depth(cells)
    buried = [cell for cell in cells if dmap.get(cell, 0) >= 2]
    if len(buried) < 2:
        st.unseal(cells)
        return False

    # loot rests on the pocket's own floor rather than floating in rock
    def grounded(cell):
        r, c = cell
        return st.get(r + 1, c) in (SOLID, 'F', 'B')
    standing = [cell for cell in buried if grounded(cell)]
    if len(standing) >= 2:
        buried = standing
    buried.sort(key=lambda cell: (-cell[0], -dmap[cell]))
    pick = buried[:max(2, len(buried) // 2)]
    rng.shuffle(pick)
    (cr, cc) = pick[0]
    (gr, gc) = pick[-1] if len(pick) > 1 else pick[0]
    if (gr, gc) == (cr, cc):
        for cell in buried:
            if cell != (cr, cc):
                (gr, gc) = cell
                break
    st.set(cr, cc, 'C')
    if (gr, gc) != (cr, cc):
        st.set(gr, gc, 'q' if index == 0 else 'j')
    return True


def dig_vaults(st, rng, want=2):
    """Hang `want` pockets off the level, each in a different style and none
    of them near the ones already placed."""
    # a run that reaches the edge of the map still has usable middle, so
    # trim rather than discard - most forms carve wall to wall
    runs = []
    for (x0, x1, row) in st.floors:
        a, b = max(x0, 5), min(x1, st.W - 6)
        if b - a >= 5:
            runs.append((a, b, row))
    # long runs offer several separate spots; cut them into bays
    bays = []
    for (a, b, row) in runs:
        if b - a > 22:
            x = a
            while x + 10 <= b:
                bays.append((x, min(b, x + 10), row))
                x += rng.randint(12, 20)
        else:
            bays.append((a, b, row))
    runs = bays
    rng.shuffle(runs)
    styles = ['under', 'side', 'above']
    rng.shuffle(styles)
    used, done = [], 0
    for (x0, x1, row) in runs:
        if done >= want:
            break
        mid = (x0 + x1) // 2
        if any(abs(mid - u[0]) < 18 and abs(row - u[1]) < 12 for u in used):
            continue
        order = [styles[done % 3]] + styles
        for style in order:
            if try_vault(st, rng, x0, x1, row, style, done):
                used.append((mid, row))
                done += 1
                break
    return done


# ============================================================ forms
def form_terrace(st, rng):
    """1-1: rolling ground under open sky. Wide, generous, nothing hidden
    behind geometry - the stage that teaches you to run and swing."""
    st.carve(1, st.H - 1, 1, st.W - 2)
    LO, HI = st.H - 18, st.H - 5            # how far the ground may roam
    y = st.H - 6
    x = 1
    first = True
    while x < st.W - 3:
        seg = min(rng.randint(5, 9), st.W - 2 - x)
        st.ground(y, x, x + seg - 1)
        if first:
            st.spawn = (y - 1, x + 2)
            first = False
        x += seg
        # a dip with water in it, never wider than one hop
        if x < st.W - 14 and rng.random() < 0.45:
            gap = rng.randint(2, JUMP_DX)
            st.carve(st.H - 3, st.H - 1, x, x + gap - 1, SOLID)
            st.carve(st.H - 4, st.H - 4, x, x + gap - 1, '%')
            x += gap
            step = rng.choice([-1, 0, 1])
        else:
            # a real step, up or down - a terrace that never moves is a field
            step = rng.choice([-3, -3, -2, -2, -1, 1, 2, 2, 3, 3])
        y = max(LO, min(HI, y + step))
    # outcrops standing proud of the ground, to climb over or go around
    for _ in range(5):
        ox = rng.randint(10, st.W - 14)
        oy = rng.randint(LO - 3, HI - 6)
        ow = rng.randint(3, 6)
        if all(st.get(oy + 3, c) == AIR for c in range(ox, ox + ow)):
            st.carve(oy, oy + 2, ox, ox + ow - 1, SOLID)
            st.floors.append((ox, ox + ow - 1, oy))
    # a scatter of shelves overhead for the braver route
    for _ in range(8):
        lw = rng.randint(3, 5)
        lx = rng.randint(6, st.W - lw - 6)
        ly = rng.randint(3, LO - 2)
        if all(st.get(ly, c) == AIR for c in range(lx - 1, lx + lw + 1)):
            st.ledge(ly, lx, lx + lw - 1)
    last = st.floors[0]
    for f in st.floors:
        if f[1] > last[1]:
            last = f
    st.exit = (last[2] - 1, min(st.W - 3, last[1] - 1))


def form_warren(st, rng):
    """1-2: three hedge lanes stacked up the map. Each is blocked somewhere,
    so the way on is always a change of lane, not a longer run."""
    lanes = [10, 22, 34]                       # surface row of each lane
    for f in lanes:
        st.carve(f - 6, f - 1, 1, st.W - 2)
        st.carve(f, f, 1, st.W - 2, SOLID)
        st.floors.append((1, st.W - 2, f))

    def climb(x, lo, hi):
        """A chimney from the lower lane up through to the higher one."""
        st.carve(hi - 1, lo - 1, x, x + 5)
        st.carve(hi, hi, x, x + 5)             # break the upper floor open
        r = lo - 2
        side = 0
        while r > hi - 1:
            lx = x if side else x + 3
            st.ledge(r, lx, lx + 2)
            side ^= 1
            r -= 3

    def block(lane, x):
        st.carve(lane - 6, lane - 1, x, x + 2, SOLID)

    # bottom -> middle -> top, left to right, with the shortcuts walled off
    c1 = rng.randint(18, 26)
    c2 = rng.randint(52, 62)
    climb(c1, lanes[2], lanes[1])
    climb(c2, lanes[1], lanes[0])
    block(lanes[2], c1 + 9)
    block(lanes[1], c1 - 8)
    block(lanes[1], c2 + 9)
    block(lanes[0], c2 - 8)

    # blind pockets off the lanes, for hiding things in
    for lane in lanes:
        for _ in range(2):
            px = rng.randint(6, st.W - 12)
            st.carve(lane - 3, lane - 1, px, px + 4)

    st.spawn = (lanes[2] - 1, 3)
    st.exit = (lanes[0] - 1, st.W - 4)


def form_well(st, rng):
    """1-3: a narrow shaft going down. You start at the rim and the only way
    on is further into the dark."""
    st.carve(2, st.H - 3, 5, st.W - 6)
    st.carve(st.H - 3, st.H - 3, 5, st.W - 6, SOLID)
    st.floors.append((5, st.W - 6, st.H - 3))

    st.ledge(6, 4, 12)                          # the rim you step off
    st.spawn = (5, 6)
    r, side = 10, 0
    while r < st.H - 6:
        lw = rng.randint(7, 11)
        lx = 6 if side else st.W - 6 - lw
        st.ledge(r, lx, lx + lw - 1)
        # an alcove cut into the wall the shelf is against, so you can
        # actually step into it
        if rng.random() < 0.55:
            if side:
                st.carve(r - 3, r - 1, 2, 5)
                st.carve(r, r, 2, 5, SOLID)
                st.floors.append((2, 5, r))
            else:
                st.carve(r - 3, r - 1, st.W - 6, st.W - 3)
                st.carve(r, r, st.W - 6, st.W - 3, SOLID)
                st.floors.append((st.W - 6, st.W - 3, r))
        side ^= 1
        r += rng.randint(4, 5)
    st.carve(st.H - 4, st.H - 4, 12, 18, '%')   # a puddle at the bottom
    st.exit = (st.H - 4, st.W - 8)


def form_cave(st, rng):
    """2-1: noise, smoothed until it reads as rock. A wandering corridor is
    cut first and protected through every pass, so the cave is always
    walkable however the noise falls."""
    spine = {}
    y = st.H - 8
    for x in range(st.W):
        if x % 4 == 0:
            y = max(12, min(st.H - 5, y + rng.choice([-1, -1, 0, 1, 1])))
        spine[x] = y

    keep = set()
    for x in range(1, st.W - 1):
        for r in range(spine[x] - 5, spine[x]):
            keep.add((r, x))

    for r in range(2, st.H - 2):
        for c in range(2, st.W - 2):
            if (r, c) in keep or rng.random() < 0.46:
                st.g[r][c] = AIR
    for _ in range(4):
        snap = [row[:] for row in st.g]
        for r in range(2, st.H - 2):
            for c in range(2, st.W - 2):
                if (r, c) in keep:
                    continue
                walls = 0
                for dr in (-1, 0, 1):
                    for dc in (-1, 0, 1):
                        if dr or dc:
                            rr, cc = r + dr, c + dc
                            if not (0 <= rr < st.H and 0 <= cc < st.W) or snap[rr][cc] == SOLID:
                                walls += 1
                st.g[r][c] = SOLID if walls >= 5 else AIR
    for (r, c) in keep:
        st.g[r][c] = AIR
    for x in range(1, st.W - 1):
        st.g[spine[x]][x] = SOLID

    # anything the noise cut off from the corridor is filled back in
    seen = set()
    stack = [(spine[2] - 1, 2)]
    while stack:
        (r, c) = stack.pop()
        if (r, c) in seen or not st.inside(r, c) or st.g[r][c] != AIR:
            continue
        seen.add((r, c))
        for (dr, dc) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            stack.append((r + dr, c + dc))
    for r in range(st.H):
        for c in range(st.W):
            if st.g[r][c] == AIR and (r, c) not in seen:
                st.g[r][c] = SOLID

    # every shelf the rock happens to offer counts as floor
    for r in range(2, st.H - 1):
        run = None
        for c in range(1, st.W):
            ok = st.g[r][c] == SOLID and st.get(r - 1, c) == AIR and st.get(r - 2, c) == AIR
            if ok and run is None:
                run = c
            elif not ok and run is not None:
                if c - run >= 3:
                    st.floors.append((run, c - 1, r))
                run = None
    # dripstone: spikes hanging where there is headroom to pass under
    for _ in range(26):
        c = rng.randint(4, st.W - 5)
        for r in range(3, st.H - 6):
            if st.g[r][c] == SOLID and st.get(r + 1, c) == AIR and st.get(r + 4, c) == AIR:
                st.set(r + 1, c, '^')
                break
    st.spawn = (spine[3] - 1, 3)
    st.exit = (spine[st.W - 5] - 1, st.W - 5)


def form_chasm(st, rng):
    """2-2: one enormous room over a drop, crossed on a lattice of shelves.
    Almost no solid ground - the level is the air between the ledges."""
    st.carve(6, st.H - 1, 1, st.W - 2)          # a band of rock left overhead
    st.carve(st.H - 3, st.H - 1, 1, st.W - 2, SOLID)
    st.carve(st.H - 4, st.H - 4, 12, st.W - 13, '%')
    # the two banks are deep enough to hollow something out of
    st.carve(st.H - 7, st.H - 1, 1, 11, SOLID)
    st.carve(st.H - 7, st.H - 1, st.W - 12, st.W - 2, SOLID)
    st.floors.append((1, 11, st.H - 7))
    st.floors.append((st.W - 12, st.W - 2, st.H - 7))
    # a buttress of rock jutting from each wall, with a shelf on top
    for (bx, by) in ((14, st.H - 13), (st.W - 22, st.H - 17)):
        st.carve(by, st.H - 8, bx, bx + 7, SOLID)
        st.floors.append((bx, bx + 7, by))

    tiers = [st.H - 7, st.H - 11, st.H - 15, st.H - 19]
    for i, row in enumerate(tiers):
        x = 4 + (i * 5)
        while x < st.W - 8:
            lw = rng.randint(4, 6)
            st.ledge(row, x, x + lw - 1)
            x += lw + rng.randint(3, JUMP_DX)
    # crystal columns standing out of the water
    for _ in range(5):
        cx = rng.randint(14, st.W - 16)
        top = rng.choice(tiers[:2])
        st.carve(top, st.H - 4, cx, cx + 1, SOLID)
        st.floors.append((cx, cx + 1, top))
    st.spawn = (st.H - 8, 3)
    st.exit = (st.H - 8, st.W - 5)


def form_maze(st, rng):
    """2-3: real branching. A spanning tree over a grid of chambers with a
    few loops added, and the way out at the end of the longest branch, not
    on the right-hand edge."""
    RW, RH = 16, 11
    cols, rows = st.W // RW, st.H // RH

    def bounds(rx, ry):
        return (ry * RH, ry * RH + RH - 1, rx * RW, rx * RW + RW - 1)

    def floor_of(ry):
        return ry * RH + RH - 1

    # A quarter of the grid stays solid rock, so the level reads as tunnels
    # bored through stone rather than a block of boxes - and so there is
    # somewhere left to hide things.
    start = (0, rows - 1)

    def open_after(rockset):
        """Would the grid still be one piece with this cell walled off?"""
        q, hit = [start], {start}
        while q:
            (cx, cy) = q.pop()
            for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                n = (cx + dx, cy + dy)
                if (0 <= n[0] < cols and 0 <= n[1] < rows
                        and n not in rockset and n not in hit):
                    hit.add(n); q.append(n)
        return len(hit) == cols * rows - len(rockset)

    rock, tries = set(), 0
    while len(rock) < (cols * rows) // 4 and tries < 80:
        tries += 1
        cand = (rng.randrange(cols), rng.randrange(rows))
        if cand == start or cand in rock:
            continue
        rock.add(cand)
        if not open_after(rock):
            rock.discard(cand)

    # depth-first spanning tree over what is left
    seen, edges, stack = {start}, [], [start]
    while stack:
        (cx, cy) = stack[-1]
        nb = []
        for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (cx + dx, cy + dy)
            if (0 <= n[0] < cols and 0 <= n[1] < rows
                    and n not in seen and n not in rock):
                nb.append(n)
        if not nb:
            stack.pop()
            continue
        n = rng.choice(nb)
        seen.add(n); edges.append(((cx, cy), n)); stack.append(n)
    # whatever the walk could not reach is rock too
    for cy in range(rows):
        for cx in range(cols):
            if (cx, cy) not in seen:
                rock.add((cx, cy))
    # a few loops, so it is a warren and not a tree you can feel
    for _ in range(3):
        a = rng.choice(sorted(seen))
        for (dx, dy) in ((1, 0), (0, 1)):
            b = (a[0] + dx, a[1] + dy)
            if (b[0] < cols and b[1] < rows and b in seen and a in seen
                    and (a, b) not in edges and (b, a) not in edges):
                edges.append((a, b))
                break

    for (rx, ry) in seen:
        r0, r1, c0, c1 = bounds(rx, ry)
        st.carve(r0 + 1, r1 - 1, c0 + 1, c1 - 1)
        st.floors.append((c0 + 1, c1 - 1, r1))
        # something to stand on part way up, so a room is never a bare box
        if rng.random() < 0.7:
            lw = rng.randint(3, 5)
            lx = c0 + rng.randint(2, RW - lw - 3)
            st.ledge(r1 - rng.choice([3, 4]), lx, lx + lw - 1)

    for (a, b) in edges:
        if a[1] == b[1]:                                    # doorway
            edge = max(a[0], b[0]) * RW
            fl = floor_of(a[1])
            st.carve(fl - 4, fl - 1, edge - 2, edge + 1)
        else:                                               # chimney
            lo, hi = max(a[1], b[1]), min(a[1], b[1])
            c = a[0] * RW + 5
            top, bot = floor_of(hi), floor_of(lo)
            st.carve(top - 1, bot - 1, c, c + 5)
            st.carve(top, top, c, c + 5)
            r, side = bot - 3, 0
            while r > top - 1:
                lx = c if side else c + 3
                st.ledge(r, lx, lx + 2)
                side ^= 1
                r -= 3
            st.ledge(top, c - 2, c - 1)
            st.ledge(top, c + 6, c + 7)

    # the exit goes at the end of the longest branch
    adj = {}
    for (a, b) in edges:
        adj.setdefault(a, []).append(b)
        adj.setdefault(b, []).append(a)
    dist, q = {start: 0}, [start]
    while q:
        cur = q.pop(0)
        for n in adj.get(cur, []):
            if n not in dist:
                dist[n] = dist[cur] + 1
                q.append(n)
    far = max(dist, key=lambda k: dist[k])
    st.spawn = (floor_of(start[1]) - 1, start[0] * RW + 3)
    st.exit = (floor_of(far[1]) - 1, far[0] * RW + RW - 4)


def form_ascent(st, rng):
    """3-1: a tower. One room wide, storey on storey, climbed through a hole
    in each floor that never sits above the one below it."""
    IW0, IW1 = 7, st.W - 8              # a wall thick enough to hide a room in
    st.carve(2, st.H - 2, IW0, IW1)
    st.carve(st.H - 2, st.H - 2, IW0, IW1, SOLID)
    st.floors.append((IW0, IW1, st.H - 2))
    st.spawn = (st.H - 3, IW0 + 2)

    hx = st.W // 2
    r = st.H - 8
    while r > 6:
        hx = rng.randint(IW0 + 1, IW1 - 6) if rng.random() < 0.8 else hx
        st.carve(r, r + 1, IW0, IW1, SOLID)
        st.carve(r, r + 1, hx, hx + 4)                 # the way up
        st.floors.append((IW0, hx - 1, r))
        st.floors.append((hx + 5, IW1, r))
        # two steps under the hole, so the climb is always in reach
        st.ledge(r + 2, hx - 1, hx + 2)
        st.ledge(r + 4, hx + 3, hx + 6)
        # a parapet to break the room up
        if rng.random() < 0.6:
            px = rng.randint(IW0 + 1, IW1 - 3)
            st.carve(r - 3, r - 1, px, px + 1, SOLID)
            st.floors.append((px, px + 1, r - 3))
        r -= 6
    st.exit = (7, IW1 - 2)
    st.ledge(8, IW1 - 7, IW1)


def form_halls(st, rng):
    """3-2: built, not carved. A regular keep floorplan of chambers on three
    storeys, walked as a serpentine - right along the bottom, up at the end,
    back along the middle, up again, out along the top."""
    RW, RH = 16, 11
    cols, rows = st.W // RW, st.H // RH

    def floor_of(ry):
        return ry * RH + RH - 1

    for ry in range(rows):
        fl = floor_of(ry)
        # seven rows of chamber leaves four of masonry above the next one
        st.carve(fl - 7, fl - 1, 4, st.W - 5)
        st.carve(fl, fl, 1, st.W - 2, SOLID)
        st.floors.append((4, st.W - 5, fl))
        # piers between the chambers, with a doorway through each
        for rx in range(1, cols):
            c = rx * RW
            st.carve(fl - 7, fl - 1, c, c + 2, SOLID)
            st.carve(fl - 4, fl - 1, c, c + 2)
            if ry == 0 and rng.random() < 0.5:
                st.carve(fl - 3, fl - 1, c + 1, c + 1, 'B')  # a gate for a heavy blade
        # stores against the piers
        for _ in range(2):
            kx = rng.randint(6, st.W - 10)
            if st.get(fl - 1, kx) == AIR:
                st.carve(fl - 2, fl - 1, kx, kx + 1, 'K')

    # stairwells at alternate ends turn the floorplan into one long route
    for ry in range(rows - 1, 0, -1):
        c = (st.W - 12) if (ry % 2 == (rows - 1) % 2) else 6
        top, bot = floor_of(ry - 1), floor_of(ry)
        st.carve(top - 1, bot - 1, c, c + 5)
        st.carve(top, top, c, c + 5)
        r, side = bot - 3, 0
        while r > top - 1:
            lx = c if side else c + 3
            st.ledge(r, lx, lx + 2)
            side ^= 1
            r -= 3
    st.spawn = (floor_of(rows - 1) - 1, 6)
    st.exit = (floor_of(0) - 1, st.W - 7)


def form_grand(st, rng):
    """3-3: the long walk in. A single deck twice the length of anything
    else, laid out as a run of named set-pieces rather than a shuffle."""
    st.carve(1, st.H - 1, 1, st.W - 2)
    fl = st.H - 8                       # deep ground: there is rock to bury in
    st.carve(fl, st.H - 1, 1, st.W - 2, SOLID)
    st.floors.append((1, st.W - 2, fl))
    st.spawn = (fl - 1, 3)

    x = 14
    beats = ['pillars', 'spikes', 'water', 'stair', 'arena',
             'pillars', 'spikes', 'water', 'arena', 'stair']
    for beat in beats:
        if x > st.W - 26:
            break
        if beat == 'pillars':
            for k in range(4):
                px = x + k * 5
                h = rng.randint(3, 5)
                st.carve(fl - h, fl - 1, px, px + 1, SOLID)
                st.floors.append((px, px + 1, fl - h))
            x += 24
        elif beat == 'spikes':
            st.carve(fl - 1, fl - 1, x, x + 13, '^')
            sx = x
            up = True
            while sx < x + 12:
                st.ledge(fl - (4 if up else 3), sx, sx + 2)
                sx += rng.randint(4, 5)
                up = not up
            x += 20
        elif beat == 'water':
            st.carve(fl, fl, x, x + 11, '%')
            wx = x + 1
            while wx < x + 10:
                st.ledge(fl - 2, wx, wx + 2)
                wx += rng.randint(4, 5)
            x += 18
        elif beat == 'stair':
            for k in range(4):
                st.ledge(fl - 2 - k * 2, x + k * 4, x + k * 4 + 3)
            for k in range(3):
                st.ledge(fl - 8 + k * 2, x + 16 + k * 4, x + 19 + k * 4)
            x += 32
        else:                                   # arena
            for px in (x + 4, x + 13):
                st.carve(fl - 3, fl - 1, px, px, SOLID)
                st.floors.append((px, px, fl - 3))
            st.ledge(fl - 6, x + 6, x + 11)
            x += 22
    st.exit = (fl - 1, st.W - 4)
    st.ledge(fl - 4, st.W - 12, st.W - 3)


FORMS = {
    'terrace': form_terrace, 'warren': form_warren, 'well': form_well,
    'cave': form_cave, 'chasm': form_chasm, 'maze': form_maze,
    'ascent': form_ascent, 'halls': form_halls, 'grand': form_grand,
}

# ============================================================ population
"""What every chest in the game holds, in the order the chests appear when
the stage is read top-to-bottom. Authored rather than rolled, because the
four vessel pieces have to land in four widely separated stages to add up
to exactly one extra heart, and because a blade or an upgrade is only a
reward if you can be sure it is out there somewhere.

  vessel        a quarter of a heart container
  blade:<id>    a sword, and one you cannot buy
  relic:<id>    an upgrade, likewise
  outfit        whichever costume is still missing
  gems / coins  the consolation prizes
"""
CHEST_LOOT = {
    '1-1': ['outfit',         'coins'],
    '1-2': ['vessel',         'outfit'],
    '1-3': ['blade:cleaver',  'gems'],
    '2-1': ['vessel',         'relic:spring'],
    '2-2': ['blade:whip',     'outfit'],
    '2-3': ['vessel',         'gems'],
    '3-1': ['relic:guard',    'outfit'],
    '3-2': ['vessel',         'gems'],
    '3-3': ['relic:lucky',    'coins'],
}


def reachable(st):
    """Every surface tile you can actually get to from the spawn, using the
    same envelope tools/reach.js checks with (five up, a long fall down,
    four across). Loose loot is only ever placed on these."""
    def standable(r, c):
        if not st.inside(r, c):
            return False
        here = st.get(r, c)
        if here not in '#=KSB':
            return False
        above = st.get(r - 1, c)
        return above not in '#KSB^'

    nodes = set()
    for r in range(st.H):
        for c in range(st.W):
            if standable(r, c):
                nodes.add((r, c))
    # drop onto the first surface under the spawn
    start = None
    sr, sc = st.spawn
    for r in range(sr, st.H):
        if (r, sc) in nodes:
            start = (r, sc); break
    if start is None:
        return nodes

    seen, q = {start}, [start]
    while q:
        (r, c) = q.pop()
        for dy in range(-5, 13):
            for dx in range(-5, 6):
                n = (r + dy, c + dx)
                if n in seen or n not in nodes:
                    continue
                rise = -dy
                if rise > 5:
                    continue
                span = 5 if rise <= 0 else (5 - rise + 1)
                if abs(dx) > span:
                    continue
                if st.get(n[0] - 1, n[1]) in '%^':
                    continue
                seen.add(n); q.append(n)
    return seen


def scatter(st, rng, spec, ok):
    slots = []
    for (x0, x1, row) in st.floors:
        for c in range(x0, x1 + 1):
            if (row, c) in ok and st.get(row - 1, c) == AIR:
                slots.append((row - 1, c))
    rng.shuffle(slots)
    i = 0
    for ch, count in spec:
        placed = 0
        while placed < count and i < len(slots):
            r, c = slots[i]; i += 1
            if c < 4:
                continue
            if st.put_free(r, c, ch):
                placed += 1


def build(sid, world, name, theme, hint, form, w, h, seed, spec, flyers):
    rng = random.Random(seed)
    st = Stage(w, h, theme, seed)
    FORMS[form](st, rng)

    dig_vaults(st, rng, 2)

    st.put_free(st.spawn[0], st.spawn[1], 'P')
    if not st.put_free(st.exit[0], st.exit[1], 'D'):
        st.set(st.exit[0], st.exit[1], 'D')

    ok = reachable(st)
    # the third gem goes somewhere awkward but in plain sight, and the
    # vessel piece somewhere else entirely
    perch = []
    for (x0, x1, row) in st.floors:
        for c in range(x0, x1 + 1):
            if (row, c) in ok and st.get(row - 1, c) == AIR:
                perch.append((row, c))
                break
    perch.sort(key=lambda p: p[0])
    for (row, c) in perch:
        if st.put_free(row - 1, c, 'G'):
            break

    scatter(st, rng, spec, ok)

    slots = []
    for c in range(4, st.W - 4, 5):
        for r in range(2, st.H - 3):
            if st.get(r, c) == AIR and st.get(r + 1, c) == AIR and st.get(r - 1, c) == AIR:
                slots.append((r, c)); break
    rng.shuffle(slots)
    i = 0
    for ch, n in flyers:
        placed = 0
        while placed < n and i < len(slots):
            r, c = slots[i]; i += 1
            if st.put_free(r, c, ch):
                placed += 1

    return dict(id=sid, world=world, name=name, theme=theme, hint=hint,
                boss=None, rows=st.rows(), w=st.W)


# ---------------------------------------------------------------- bosses
def boss_yard(sid, world, name, theme, hint):
    """1-4: a walled yard. Flat, so the charge has room, with two low
    mounds to break the line of it."""
    st = Stage(50, 22, theme, 7)
    st.carve(2, st.H - 3, 2, st.W - 3)
    st.carve(st.H - 3, st.H - 1, 1, st.W - 2, SOLID)
    st.ledge(st.H - 6, 8, 14)
    st.ledge(st.H - 6, st.W - 15, st.W - 9)
    st.ledge(st.H - 10, 21, 28)
    st.set(st.H - 4, 4, 'P')
    st.set(st.H - 4, st.W - 8, 'X')
    return dict(id=sid, world=world, name=name, theme=theme, hint=hint,
                boss='boar', rows=st.rows(), w=st.W)


def boss_roost(sid, world, name, theme, hint):
    """2-4: a tall roost. The floor is small and the perches are high, so a
    diving thing has somewhere to dive from."""
    st = Stage(46, 30, theme, 8)
    st.carve(2, st.H - 3, 2, st.W - 3)
    st.carve(st.H - 3, st.H - 1, 1, st.W - 2, SOLID)
    st.floors.append((2, st.W - 3, st.H - 3))
    for i, (row, x0, x1) in enumerate([(st.H - 7, 4, 11), (st.H - 7, st.W - 12, st.W - 5),
                                       (st.H - 12, 14, 22), (st.H - 12, st.W - 23, st.W - 15),
                                       (st.H - 17, 18, 27)]):
        st.ledge(row, x0, x1)
    st.set(st.H - 4, 4, 'P')
    st.set(st.H - 20, st.W - 10, 'X')
    return dict(id=sid, world=world, name=name, theme=theme, hint=hint,
                boss='gloomwing', rows=st.rows(), w=st.W)


def boss_throne(sid, world, name, theme, hint):
    """3-4: the throne room. A dais at the far end, stepped, with pillars
    down the hall to hide behind."""
    st = Stage(58, 24, theme, 9)
    st.carve(2, st.H - 3, 2, st.W - 3)
    st.carve(st.H - 3, st.H - 1, 1, st.W - 2, SOLID)
    for k in range(3):                              # steps up to the dais
        st.carve(st.H - 4 - k, st.H - 4 - k, st.W - 16 + k * 4, st.W - 3, SOLID)
    st.floors.append((st.W - 16, st.W - 3, st.H - 6))
    for px in (14, 24, 34):
        st.carve(st.H - 9, st.H - 4, px, px + 1, SOLID)
        st.floors.append((px, px + 1, st.H - 9))
    st.ledge(st.H - 9, 5, 11)
    st.set(st.H - 4, 4, 'P')
    st.set(st.H - 7, st.W - 8, 'X')
    return dict(id=sid, world=world, name=name, theme=theme, hint=hint,
                boss='king', rows=st.rows(), w=st.W)


STAGES = [
    build('1-1', 1, 'Backyard Gate', 'meadow', 'X swings the blade. Z jumps.',
          'terrace', 96, 30, 11, [('o', 16), ('g', 4), ('K', 2), ('H', 1)], [('b', 2)]),
    build('1-2', 1, 'Thistle Run', 'meadow', 'Push into walls. Some of them give.',
          'warren', 104, 40, 22, [('o', 16), ('g', 4), ('K', 2), ('H', 1)], [('b', 3)]),
    build('1-3', 1, 'The Old Well', 'cavern', 'Only one way from here: down.',
          'well', 44, 64, 33, [('o', 15), ('g', 3), ('h', 1), ('t', 1), ('K', 2), ('H', 1)],
          [('b', 4)]),
    boss_yard('1-4', 1, 'Grumblegut', 'meadow', 'He charges. Let him hit the wall.'),

    build('2-1', 2, 'Dripstone Deep', 'cavern', 'Dripstone bites. Watch the ceiling.',
          'cave', 112, 34, 44, [('o', 16), ('g', 4), ('t', 2), ('K', 2), ('H', 1)], [('b', 4)]),
    build('2-2', 2, 'Crystal Drop', 'cavern', 'There is no floor. Keep moving.',
          'chasm', 120, 32, 55, [('o', 18), ('g', 4), ('h', 2), ('t', 2), ('H', 1)], [('b', 5)]),
    build('2-3', 2, 'Gnaw Tunnels', 'cavern', 'It branches. Not every way out is one.',
          'maze', 112, 44, 66, [('o', 18), ('h', 4), ('g', 3), ('t', 2), ('K', 3), ('H', 1)],
          [('b', 4)]),
    boss_roost('2-4', 2, 'Gloomwing', 'cavern', 'It dives. Swing when it drops low.'),

    build('3-1', 3, 'The Ramparts', 'keep', 'Up. All the way up.',
          'ascent', 48, 66, 77, [('o', 16), ('g', 4), ('h', 4), ('t', 2), ('K', 3), ('H', 1)],
          [('b', 4)]),
    build('3-2', 3, 'Iron Halls', 'keep', 'Break the crates. Some gates need a heavier blade.',
          'halls', 112, 33, 88, [('o', 18), ('g', 4), ('h', 4), ('t', 3), ('K', 4), ('H', 1)],
          [('b', 4)]),
    build('3-3', 3, 'Throne Approach', 'keep', 'Last stretch. Everything at once.',
          'grand', 184, 24, 99, [('o', 20), ('g', 5), ('h', 5), ('t', 3), ('K', 3), ('H', 1)],
          [('b', 5)]),
    boss_throne('3-4', 3, 'The Kennel King', 'keep', 'Jump his shockwaves.')
]

# ---------------------------------------------------------------- emit
# Chests are numbered in the order a stage is read, which is the order the
# game finds them in, so the plan above lines up with the tiles on the map.
loot_lines = []
for L in STAGES:
    found = sum(row.count('C') for row in L['rows'])
    plan = CHEST_LOOT.get(L['id'], [])
    if found > len(plan):
        raise SystemExit('%s has %d chests but only %d planned'
                         % (L['id'], found, len(plan)))
    if found:
        loot_lines.append("  '%s': [%s]"
                          % (L['id'], ', '.join("'%s'" % q for q in plan[:found])))

chunks = []
for L in STAGES:
    extra = (" boss: '%s'," % L['boss']) if L['boss'] else ''
    body = ',\n'.join("      '%s'" % r.replace("\\", "\\\\").replace("'", "\\'") for r in L['rows'])
    chunks.append(
"""  {
    id: '%s', world: %d, name: '%s', theme: '%s',%s
    hint: '%s',
    rows: [
%s
    ]
  }""" % (L['id'], L['world'], L['name'], L['theme'], extra, L['hint'], body))

header = """/* Stage data - generated by tools/build_levels.py and proved completable
   by tools/reach.js, which reads the jump constants from src/entities.js.

   Legend:  # solid   = ledge (solid on every face)   ^ spikes
            %% hazard water   K crate   S bounce pad   F false wall
            D exit   P spawn   X boss
            o coin   G gem   C chest   H meat   V vessel piece
            g grub   b batling   h bonehound   t thorn turret

   Every stage has its own form - a terrace, a warren of lanes, a well, a
   grown cave, a chasm, a branching maze, a tower, a keep floorplan and one
   very long approach - so no two of them play alike. Secrets are pockets
   cut out of solid rock whose only way in is a false wall. */
var LEVELS = [
"""
footer = """
];

/* What each stage's chests hold, in the order the stage is read. */
var CHEST_LOOT = {
%CHESTS%
};

var MOVERS = {};

var WORLDS = [
  { n: 1, name: 'Sunken Garden', theme: 'meadow' },
  { n: 2, name: 'Root Caverns', theme: 'cavern' },
  { n: 3, name: 'Kennel Keep', theme: 'keep' }
];
"""

dest = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'levels.js')
open(dest, 'w').write(header + ',\n'.join(chunks) +
                     footer.replace('%CHESTS%', ',\n'.join(loot_lines)))
print('wrote levels.js')
for L in STAGES:
    f = ''.join(L['rows'])
    print('  %-4s %3dx%-3d  P=%d D=%d X=%d  gems=%d chests=%d false=%-4d enemies=%d'
          % (L['id'], L['w'], len(L['rows']), f.count('P'), f.count('D'), f.count('X'),
             f.count('G') + f.count('q') + f.count('j'), f.count('C'), f.count('F'),
             sum(f.count(c) for c in 'gbht')))
