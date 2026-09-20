# -*- coding: utf-8 -*-
"""Builds the stages as carved rock rather than a flat ribbon.

The level starts as SOLID STONE and rooms are cut out of it. That single
choice fixes the two things a ribbon can never do:

  * secrets are enclosed by construction - a vault is a pocket carved in
    rock whose only opening is a false-wall tile, so a chest can never end
    up sitting in open air, and
  * levels stop being a straight line - rooms sit on a grid two deep, the
    route wanders up and down through vertical shafts, and side rooms hang
    off the path.

Layout follows the usual platformer pacing ideas: a teach beat before a
test beat, action rooms alternating with quieter ones, verticality through
shafts, and dedicated combat arenas rather than enemies sprinkled evenly.

Every stage is then proved completable by tools/reach.js, which reads the
real jump constants out of src/entities.js.
"""
import os, random

RW, RH = 16, 11          # room size in tiles
SOLID, AIR = '#', ' '


class Stage:
    def __init__(self, rcols, rrows, theme, seed):
        self.rc, self.rr = rcols, rrows
        self.W, self.H = rcols * RW, rrows * RH
        self.theme = theme
        self.rng = random.Random(seed)
        self.g = [[SOLID] * self.W for _ in range(self.H)]
        self.floors = []        # (x0, x1, row) walkable runs, for placement
        self.rooms = {}

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

    def put_free(self, r, c, ch):
        """Place only into empty space, so actors never replace geometry."""
        if self.inside(r, c) and self.g[r][c] == AIR:
            self.g[r][c] = ch
            return True
        return False

    # ---------------- room geometry ----------------
    def room_bounds(self, rx, ry):
        return (ry * RH, ry * RH + RH - 1, rx * RW, rx * RW + RW - 1)

    def room_floor(self, rx, ry):
        return ry * RH + RH - 1

    def open_room(self, rx, ry, top_pad=1):
        """Hollow a room out, leaving its bottom row as floor."""
        r0, r1, c0, c1 = self.room_bounds(rx, ry)
        self.carve(r0 + top_pad, r1 - 1, c0 + 1, c1 - 1)
        self.floors.append((c0 + 1, c1 - 1, r1))
        self.rooms[(rx, ry)] = True

    # ---------------- connections ----------------
    def door(self, rx, ry, rx2, ry2):
        """A walkable opening between two horizontally adjacent rooms."""
        r1 = self.room_floor(rx, ry)
        r2 = self.room_floor(rx2, ry2)
        edge = max(rx, rx2) * RW
        lo, hi = min(r1, r2), max(r1, r2)
        self.carve(lo - 4, hi - 1, edge - 2, edge + 1)
        # step the sill if the two floors sit at different heights
        if r1 != r2:
            for c in range(edge - 2, edge + 2):
                self.set(hi - 1, c, SOLID)

    def shaft(self, rx, ry_low, ry_high):
        """A vertical passage climbed in zig-zag. Ledges sit two rows
        apart, inside the jump arc, and alternate sides so each hop is a
        short diagonal rather than a straight vertical wall."""
        c0 = rx * RW + 4
        top = self.room_floor(rx, ry_high)
        bottom = self.room_floor(rx, ry_low)
        # break clean through the upper room's floor as well
        self.carve(top - 1, bottom - 1, c0, c0 + 7)
        side = 0
        r = bottom - 2
        while r > top - 1:
            lx = c0 if side else c0 + 4
            self.carve(r, r, lx, lx + 3, '=')
            self.floors.append((lx, lx + 3, r))
            side ^= 1
            r -= 2
        # a lip to step off onto at the top, both sides of the opening
        self.carve(top, top, c0 - 2, c0 - 1, '=')
        self.carve(top, top, c0 + 8, c0 + 9, '=')
        self.floors.append((c0 - 2, c0 - 1, top))
        self.floors.append((c0 + 8, c0 + 9, top))

    # ---------------- room archetypes ----------------
    def arch_corridor(self, rx, ry, hard):
        """Traversal: a couple of ledges and a hazard to time."""
        r0, r1, c0, c1 = self.room_bounds(rx, ry)
        floor = self.room_floor(rx, ry)
        if self.rng.random() < 0.7:
            w = self.rng.randint(3, 4)
            x = c0 + self.rng.randint(4, RW - w - 5)        # clear of the doorways
            self.carve(floor, floor, x, x + w - 1, '%')     # water to clear
        for _ in range(self.rng.randint(1, 2)):
            lw = self.rng.randint(3, 4)
            lx = c0 + self.rng.randint(1, RW - lw - 2)
            ly = floor - self.rng.choice([3, 4])
            self.carve(ly, ly, lx, lx + lw - 1, '=')
            self.floors.append((lx, lx + lw - 1, ly))
        if hard:
            sx = c0 + self.rng.randint(2, RW - 5)
            self.carve(floor - 1, floor - 1, sx, sx + 2, '^')

    def arch_arena(self, rx, ry, hard):
        """Combat space: open floor, cover pillars, a high ledge to retreat to."""
        r0, r1, c0, c1 = self.room_bounds(rx, ry)
        floor = self.room_floor(rx, ry)
        for px in (c0 + 4, c0 + 11):
            h = self.rng.choice([2, 3])
            for r in range(floor - h, floor):
                self.set(r, px, SOLID)
            self.floors.append((px, px, floor - h))
        ly = floor - 5
        self.carve(ly, ly, c0 + 6, c0 + 10, '=')
        self.floors.append((c0 + 6, c0 + 10, ly))
        self.rooms[(rx, ry, 'arena')] = True

    def arch_gauntlet(self, rx, ry, hard):
        """Precision: stepping ledges over spikes."""
        r0, r1, c0, c1 = self.room_bounds(rx, ry)
        floor = self.room_floor(rx, ry)
        self.carve(floor - 1, floor - 1, c0 + 4, c1 - 4, '^')
        x = c0 + 1
        up = True
        while x < c1 - 3:
            ly = floor - (4 if up else 3)
            self.carve(ly, ly, x, x + 2, '=')
            self.floors.append((x, x + 2, ly))
            x += self.rng.randint(4, 5)
            up = not up

    def arch_cistern(self, rx, ry, hard):
        """Water room crossed on ledges, with headroom above."""
        r0, r1, c0, c1 = self.room_bounds(rx, ry)
        floor = self.room_floor(rx, ry)
        self.carve(floor, floor, c0 + 4, c1 - 4, '%')
        x = c0 + 4
        while x < c1 - 3:
            self.carve(floor - 2, floor - 2, x, x + 2, '=')
            self.floors.append((x, x + 2, floor - 2))
            x += self.rng.randint(4, 5)

    # ---------------- secrets ----------------
    def vault(self, rx, ry, from_side):
        """A sealed pocket inside the rock. The only way in is one false
        tile, so the chest cannot be visible from the open level."""
        r0, r1, c0, c1 = self.room_bounds(rx, ry)
        floor = self.room_floor(rx, ry)
        top = floor - 3
        vc0, vc1 = c0 + 3, c0 + RW - 4
        self.carve(top, floor - 1, vc0, vc1)
        self.carve(floor, floor, vc0, vc1, SOLID)       # give it a floor
        # the false tile, on the wall facing the route
        if from_side == 'left':
            for r in range(top + 1, floor):
                self.set(r, vc0 - 1, 'F')
        elif from_side == 'right':
            for r in range(top + 1, floor):
                self.set(r, vc1 + 1, 'F')
        else:                                            # from above
            self.set(top - 1, vc0 + 2, 'F')
            self.carve(top - 1, top - 1, vc0 + 2, vc0 + 2, 'F')
        self.put_free(floor - 1, vc0 + 2, 'C')
        self.put_free(floor - 1, vc1 - 1, 'G')
        return True

    # ---------------- population ----------------
    def scatter(self, spec):
        slots = []
        for (x0, x1, row) in self.floors:
            for c in range(x0, x1 + 1):
                if self.get(row - 1, c) == AIR and self.get(row, c) in '#=':
                    slots.append((row - 1, c))
        self.rng.shuffle(slots)
        i = 0
        for ch, count in spec:
            placed = 0
            while placed < count and i < len(slots):
                r, c = slots[i]; i += 1
                if c < 4:
                    continue
                if self.put_free(r, c, ch):
                    placed += 1

    def rows(self):
        return [''.join(r) for r in self.g]


# ============================================================ assembly
# One piece each in four widely separated stages; four makes a heart.
HEART_PIECE_STAGES = ('1-2', '2-1', '2-3', '3-2')


def route(rc, rr, rng):
    """A wandering path: right across the grid, changing deck as it goes,
    so the level is never a straight line."""
    x, y = 0, rr - 1
    path = [(x, y)]
    while x < rc - 1:
        if rr > 1 and 0 < x < rc - 1 and rng.random() < 0.5:
            ny = 0 if y == rr - 1 else rr - 1
            if (x, ny) not in path:
                path.append((x, ny))
                y = ny
        x += 1
        path.append((x, y))
    return path


def build(sid, world, name, theme, hint, rcols, seed, spec, flyers):
    rng = random.Random(seed)
    st = Stage(rcols, 2, theme, seed)
    path = route(rcols, 2, rng)

    # open every room on the route
    for (rx, ry) in path:
        st.open_room(rx, ry)

    # archetypes, paced teach -> test -> twist
    kinds = []
    for i, (rx, ry) in enumerate(path):
        if i == 0:
            kinds.append('teach')
        elif i == len(path) - 1:
            kinds.append('exit')
        else:
            kinds.append(rng.choice(['corridor', 'arena', 'gauntlet', 'cistern',
                                     'corridor', 'arena']))
    arenas = []
    for i, (rx, ry) in enumerate(path):
        k, hard = kinds[i], i > len(path) // 2
        if k == 'corridor':
            st.arch_corridor(rx, ry, hard)
        elif k == 'arena':
            st.arch_arena(rx, ry, hard); arenas.append((rx, ry))
        elif k == 'gauntlet':
            st.arch_gauntlet(rx, ry, hard)
        elif k == 'cistern':
            st.arch_cistern(rx, ry, hard)

    # Connections are punched LAST. Room features paint over floors (water
    # pits, spike beds), and a door or shaft carved earlier would simply be
    # sealed up again by them.
    for i in range(len(path) - 1):
        ax, ay = path[i]
        bx, by = path[i + 1]
        if ax == bx:
            st.shaft(ax, max(ay, by), min(ay, by))
        else:
            st.door(ax, ay, bx, by)

    # vaults in rooms OFF the route, reached only through false wall
    onpath = set(path)
    vaults = 0
    for (rx, ry) in path:
        if vaults >= 2:
            break
        for nx in (rx + 1, rx - 1):
            if vaults >= 2:
                break
            if 0 <= nx < rcols and (nx, ry) not in onpath:
                r0, r1, c0, c1 = st.room_bounds(nx, ry)
                floor = st.room_floor(nx, ry)
                top = floor - 3
                # The whole chamber is packed with false wall, not left
                # hollow: from outside it is indistinguishable from solid
                # rock, and it only opens up as you push into it.
                if nx > rx:
                    vc0, vc1 = c0 + 3, c1 - 2
                    st.carve(top, floor - 1, vc0, vc1, 'F')
                    for c in range(c0 - 2, vc0):
                        for r in range(floor - 2, floor):
                            st.set(r, c, 'F')
                else:
                    vc0, vc1 = c0 + 2, c1 - 3
                    st.carve(top, floor - 1, vc0, vc1, 'F')
                    for c in range(vc1 + 1, c1 + 3):
                        for r in range(floor - 2, floor):
                            st.set(r, c, 'F')
                st.set(floor - 1, vc0 + 2, 'C')
                # the deeper vault is sealed behind reinforced stone, so it
                # stays shut until the Emberblade is bought - a reason to
                # come back to an early stage later
                if vaults == 1:
                    bx = (c0 - 3) if nx > rx else (c1 + 3)
                    for r in range(floor - 2, floor):
                        st.set(r, bx, 'B')
                    st.set(floor - 1, vc1 - 1, 'q')     # buried crown gem
                else:
                    st.set(floor - 1, vc1 - 1, 'j')     # buried jewel
                st.floors.append((vc0, vc1, floor))
                onpath.add((nx, ry))
                vaults += 1

    # spawn and exit
    sx, sy = path[0]
    st.put_free(st.room_floor(sx, sy) - 1, sx * RW + 3, 'P')
    ex, ey = path[-1]
    st.put_free(st.room_floor(ex, ey) - 1, ex * RW + RW - 4, 'D')

    # the third gem goes somewhere awkward but visible: a high ledge
    high = sorted(st.floors, key=lambda f: f[2])
    for (x0, x1, row) in high:
        if st.put_free(row - 1, (x0 + x1) // 2, 'G'):
            break

    # Heart vessel pieces sit far apart, so four of them span the game
    if sid in HEART_PIECE_STAGES:
        mid = sorted(st.floors, key=lambda f: f[2])
        placed = False
        for (x0, x1, row) in mid[2:]:
            if st.put_free(row - 1, (x0 + x1) // 2, 'V'):
                placed = True
                break
        if not placed:
            for (x0, x1, row) in st.floors:
                if st.put_free(row - 1, x0 + 1, 'V'):
                    break

    # arenas get the crowd; the rest is sprinkled
    for (rx, ry) in arenas:
        floor = st.room_floor(rx, ry)
        for k in range(3):
            st.put_free(floor - 1, rx * RW + 3 + k * 4, rng.choice(['g', 'h', 'g']))
    st.scatter(spec)

    # flyers ride the open air
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


def arena_stage(sid, world, name, theme, boss, hint):
    st = Stage(3, 2, theme, 99)
    st.open_room(0, 1); st.open_room(1, 1); st.open_room(2, 1)
    floor = st.room_floor(0, 1)
    st.carve(floor - 9, floor - 1, 1, st.W - 2)
    st.carve(floor, floor, 0, st.W - 1, SOLID)
    # stepped so every ledge is within one jump of the one below it
    st.carve(floor - 2, floor - 2, 5, 10, '=')
    st.carve(floor - 2, floor - 2, st.W - 11, st.W - 6, '=')
    st.carve(floor - 4, floor - 4, 14, 19, '=')
    st.carve(floor - 4, floor - 4, st.W - 20, st.W - 15, '=')
    st.carve(floor - 6, floor - 6, 21, 26, '=')
    st.put_free(floor - 1, 3, 'P')
    # the flyer starts high, the others on the floor; force it so a ledge
    # tile can never silently swallow the spawn marker
    st.set(floor - 1 if boss != 'gloomwing' else floor - 8, st.W - 8, 'X')
    return dict(id=sid, world=world, name=name, theme=theme, hint=hint,
                boss=boss, rows=st.rows(), w=st.W)


STAGES = [
    build('1-1', 1, 'Backyard Gate', 'meadow', 'X swings the blade. Z jumps.',
          5, 11, [('o', 12), ('g', 2), ('K', 2), ('H', 1)], []),
    build('1-2', 1, 'Thistle Run', 'meadow', 'Push into walls. Some of them give.',
          6, 22, [('o', 14), ('g', 3), ('K', 2), ('H', 1)], [('b', 3)]),
    build('1-3', 1, 'The Old Well', 'meadow', 'Mind the water.',
          6, 33, [('o', 14), ('g', 3), ('h', 1), ('t', 1), ('K', 2), ('H', 1)], [('b', 3)]),
    arena_stage('1-4', 1, 'Grumblegut', 'meadow', 'boar', 'He charges. Let him hit the wall.'),

    build('2-1', 2, 'Dripstone Deep', 'cavern', 'Spikes hurt. Everything down here hurts.',
          6, 44, [('o', 15), ('g', 4), ('t', 2), ('K', 2), ('H', 1)], [('b', 4)]),
    build('2-2', 2, 'Crystal Drop', 'cavern', 'Look up. And look down.',
          7, 55, [('o', 16), ('g', 4), ('h', 2), ('t', 2), ('K', 2), ('H', 1)], [('b', 4)]),
    build('2-3', 2, 'Gnaw Tunnels', 'cavern', 'Bonehounds leap when you get close.',
          7, 66, [('o', 16), ('h', 4), ('g', 3), ('t', 2), ('K', 3), ('H', 1)], [('b', 4)]),
    arena_stage('2-4', 2, 'Gloomwing', 'cavern', 'gloomwing', 'It dives. Swing when it drops low.'),

    build('3-1', 3, 'The Ramparts', 'keep', 'The keep is awake.',
          7, 77, [('o', 16), ('g', 4), ('h', 4), ('t', 2), ('K', 3), ('H', 1)], [('b', 4)]),
    build('3-2', 3, 'Iron Halls', 'keep', 'Break the crates. Some hide coin.',
          7, 88, [('o', 16), ('g', 4), ('h', 4), ('t', 3), ('K', 4), ('H', 1)], [('b', 4)]),
    build('3-3', 3, 'Throne Approach', 'keep', 'Last stretch. Everything at once.',
          8, 99, [('o', 18), ('g', 5), ('h', 5), ('t', 3), ('K', 3), ('H', 1)], [('b', 5)]),
    arena_stage('3-4', 3, 'The Kennel King', 'keep', 'king', 'Jump his shockwaves.')
]

# ---------------------------------------------------------------- emit
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
            % hazard water   K crate   S bounce pad   F false wall
            D exit   P spawn   X boss
            o coin   G gem   C chest   H meat
            g grub   b batling   h bonehound   t thorn turret

   Levels are carved out of solid rock on a two-deck room grid, so the
   route wanders up and down instead of running in a straight line, and
   secret vaults are sealed pockets whose only way in is a false wall. */
var LEVELS = [
"""
footer = """
];

var MOVERS = {};

var WORLDS = [
  { n: 1, name: 'Sunken Garden', theme: 'meadow' },
  { n: 2, name: 'Root Caverns', theme: 'cavern' },
  { n: 3, name: 'Kennel Keep', theme: 'keep' }
];
"""

dest = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'levels.js')
open(dest, 'w').write(header + ',\n'.join(chunks) + footer)
print('wrote levels.js')
for L in STAGES:
    f = ''.join(L['rows'])
    print('  %-4s %dx%d  P=%d D=%d X=%d  gems=%d chests=%d false=%d  enemies=%d'
          % (L['id'], L['w'], len(L['rows']), f.count('P'), f.count('D'), f.count('X'),
             f.count('G'), f.count('C'), f.count('F'),
             sum(f.count(c) for c in 'gbht')))
