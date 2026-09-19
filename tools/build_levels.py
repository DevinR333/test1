# -*- coding: utf-8 -*-
import os
"""Builds the stage maps as architecture rather than a flat floor.

Terrain is a surface heightmap assembled from segments - plateaus,
steps, mesas, water crossings - and structures are then cut into it:
pillars to hop across, arches to walk through, stepped ledges and
platform tiers. Everything is placed against the hero's measured jump
arc (3 tiles up, 3 tiles of air across) and verified by reach.js.
"""
H = 14
FLOOR = 13          # deepest row
CEIL_THEMES = ('cavern', 'keep')

class Builder:
    def __init__(self, w, theme, base=10):
        self.w = w; self.theme = theme
        self.g = [[' '] * w for _ in range(H)]
        self.surf = [None] * w      # surface row per column; None = open pit
        self.x = 0
        self.y = base
        self.flats = []             # (x0, x1, row) runs safe for actors
        self.jobs = []              # structures painted after the ground

    # ---- raw cell access -------------------------------------------------
    def put(self, r, c, ch, force=False):
        if 0 <= r < H and 0 <= c < self.w:
            if force or self.g[r][c] == ' ':
                self.g[r][c] = ch
                return True
        return False

    def free(self, r, c):
        return 0 <= r < H and 0 <= c < self.w and self.g[r][c] == ' '

    # ---- terrain segments ------------------------------------------------
    def flat(self, n, tag=True):
        x0 = self.x
        for i in range(n):
            if self.x < self.w:
                self.surf[self.x] = self.y
                self.x += 1
        if tag and n >= 3:
            self.flats.append((x0, min(self.x, self.w) - 1, self.y))
        return self

    def step(self, delta, run=3):
        """Change surface height by delta (positive = downhill), in 1-tile stairs."""
        d = 1 if delta > 0 else -1
        for _ in range(abs(delta)):
            self.y = max(6, min(11, self.y + d))
            self.flat(run, tag=False)
        return self

    def pit(self, air=3, water=True):
        air = min(air, 3)                       # never wider than the jump arc
        for i in range(air):
            if self.x < self.w:
                self.surf[self.x] = None
                if water:
                    for r in range(10, H):
                        self.put(r, self.x, '%', force=True)
                self.x += 1
        return self

    def crossing(self, spans=2, air=3):
        """Water with standable stepping platforms, each one tile above."""
        for s in range(spans):
            self.pit(air)
            if self.x < self.w - 2:
                px = self.x
                row = self.y - 1
                self.jobs.append(('plat', px, row, 2))
                for i in range(2):
                    if self.x < self.w:
                        self.surf[self.x] = None
                        for r in range(10, H):
                            self.put(r, self.x, '%', force=True)
                        self.x += 1
                self.flats.append((px, px + 1, row))
        self.pit(air)
        return self

    def mesa(self, n, rise=2):
        """A raised block with a stepped approach on both sides."""
        self.step(-rise, run=2)
        self.flat(n)
        self.step(rise, run=2)
        return self

    def pillars(self, count=3, gap=2, tall=2):
        """Free-standing columns rising from the floor; hop across the tops."""
        for i in range(count):
            px = self.x
            self.jobs.append(('pillar', px, self.y, tall))
            self.flats.append((px, px, self.y - tall))
            self.flat(1, tag=False)
            self.flat(gap, tag=False)
        return self

    def arch(self):
        """Two legs and a lintel - walk under it or stand on top."""
        x0 = self.x
        self.jobs.append(('arch', x0, self.y))
        self.flat(5)
        return self

    def tower(self, tiers=3, bone=False):
        """Platform tiers, each exactly 2 above the one below."""
        x0 = self.x
        self.jobs.append(('tower', x0, self.y, tiers, bone))
        self.flat(7)
        return self

    def spikes(self, n=3):
        x0 = self.x
        self.jobs.append(('spikes', x0, self.y, n))
        self.flat(n + 2, tag=False)
        return self

    # ---- painting --------------------------------------------------------
    def build(self):
        # ground body
        for c in range(self.w):
            s = self.surf[c]
            if s is None:
                continue
            for r in range(s, H):
                self.put(r, c, '#', force=True)
        # ceiling for interiors
        if self.theme in CEIL_THEMES:
            for c in range(self.w):
                self.put(0, c, '#', force=True)
                if (c * 7 + 3) % 11 == 0:
                    self.put(1, c, '#', force=True)
                    if (c * 5) % 13 == 0:
                        self.put(2, c, '#', force=True)
        # structures
        for job in self.jobs:
            kind = job[0]
            if kind == 'pillar':
                _, px, row, tall = job
                for t in range(1, tall + 1):
                    self.put(row - t, px, '#', force=True)
            elif kind == 'arch':
                _, x0, row = job
                for t in range(1, 3):                      # two legs, 2 tall
                    self.put(row - t, x0, '#', force=True)
                    self.put(row - t, x0 + 4, '#', force=True)
                for c in range(x0, x0 + 5):                # lintel
                    self.put(row - 3, c, '#', force=True)
                self.flats.append((x0, x0 + 4, row - 3))
            elif kind == 'plat':
                _, px, row, n = job
                for c in range(px, px + n):
                    self.put(row, c, '=', force=True)
            elif kind == 'tower':
                _, x0, row, tiers, bone = job
                for t in range(tiers):
                    py = row - 2 * (t + 1)
                    px = x0 + t
                    for c in range(px, px + 5):
                        self.put(py, c, '=', force=True)
                    self.flats.append((px, px + 4, py))
                    if t == tiers - 1 and bone:
                        self.put(py - 1, px + 2, 'B', force=True)
                    else:
                        self.put(py - 1, px + 1, 'o', force=True)
                        self.put(py - 1, px + 3, 'o', force=True)
            elif kind == 'spikes':
                _, x0, row, n = job
                for c in range(x0 + 1, x0 + 1 + n):
                    self.put(row - 1, c, '^', force=True)
        return self

    # ---- actors ----------------------------------------------------------
    def scatter(self, spec):
        """spec: list of (char, count). Placed on tagged flat runs, never
        overwriting existing geometry."""
        import random
        rng = random.Random(hash(tuple(sorted(spec))) & 0xffff)
        slots = []
        for (x0, x1, row) in self.flats:
            for c in range(x0, x1 + 1):
                if self.free(row - 1, c) and self.g[row][c] in '#=':
                    slots.append((row - 1, c))
        rng.shuffle(slots)
        i = 0
        for (ch, count) in spec:
            placed = 0
            while placed < count and i < len(slots):
                r, c = slots[i]; i += 1
                # keep a little breathing room around the spawn point
                if c < 4:
                    continue
                if self.put(r, c, ch):
                    placed += 1
        return self

    def spawn(self):
        for c in range(1, self.w):
            if self.surf[c] is not None:
                self.put(self.surf[c] - 1, c, 'P', force=True)
                return self
        return self

    def exit(self):
        for c in range(self.w - 2, 0, -1):
            if self.surf[c] is not None and self.free(self.surf[c] - 1, c):
                self.put(self.surf[c] - 1, c, 'D', force=True)
                return self
        return self

    def rows(self):
        return [''.join(r) for r in self.g]

    def crop(self):
        """Trim to the columns actually used, keeping a solid final column."""
        self.w = self.x
        self.g = [row[:self.w] for row in self.g]
        self.surf = self.surf[:self.w]
        return self

    def air(self, spec):
        """Place flyers in open air above the terrain."""
        import random
        rng = random.Random(len(self.g[0]) * 31 + 7)
        slots = []
        for c in range(6, self.w - 4, 3):
            s = self.surf[c]
            if s is None:
                continue
            for r in range(max(2, s - 6), s - 2):
                if self.free(r, c) and self.free(r, c + 1):
                    slots.append((r, c))
                    break
        rng.shuffle(slots)
        i = 0
        for (ch, count) in spec:
            placed = 0
            while placed < count and i < len(slots):
                r, c = slots[i]; i += 1
                if self.put(r, c, ch):
                    placed += 1
        return self


def stage(sid, world, name, theme, hint, script, ground, flyers, boss=None):
    b = Builder(160, theme)
    script(b)
    b.crop().build().spawn().exit()
    b.scatter(ground)
    b.air(flyers)
    return dict(id=sid, world=world, name=name, theme=theme, hint=hint,
                boss=boss, rows=b.rows(), w=b.w)


def arena(sid, world, name, theme, boss, hint):
    W = 40
    b = Builder(W, theme, base=11)
    b.flat(W)
    b.crop().build()
    for r in range(1, 11):                 # side walls
        b.put(r, 0, '#', force=True); b.put(r, 1, '#', force=True)
        b.put(r, W - 1, '#', force=True); b.put(r, W - 2, '#', force=True)
    for c in range(5, 11):                 # ledges to fight from
        b.put(5, c, '=', force=True)
        b.put(5, W - 1 - c, '=', force=True)
    for c in range(14, 26):
        b.put(8, c, '=', force=True)
    b.put(10, 4, 'P', force=True)
    b.put(10 if boss != 'gloomwing' else 4, W - 8 if boss != 'gloomwing' else 20,
          'X', force=True)
    return dict(id=sid, world=world, name=name, theme=theme, hint=hint,
                boss=boss, rows=b.rows(), w=W)


LEVELS = [
    stage('1-1', 1, 'Backyard Gate', 'meadow', 'X swings the blade. Z jumps.',
          lambda b: (b.flat(6).tower(2).step(-1).flat(3).pit(2).flat(4).mesa(4)
                      .step(1).flat(3).pit(3).flat(3).tower(3, bone=True)
                      .step(-1).flat(3).pit(2).flat(4).step(1).flat(5)),
          [('g', 3), ('o', 9), ('K', 1)], []),

    stage('1-2', 1, 'Thistle Run', 'meadow', 'Hop the pillars. Mind the drop.',
          lambda b: (b.flat(5).pillars(3, 2, 2).step(-1).flat(3).pit(3).flat(3)
                      .tower(3, bone=True).step(1).flat(3).mesa(5).pit(3)
                      .flat(3).arch().step(-1).flat(3).pit(2).flat(5)),
          [('g', 4), ('o', 10), ('K', 2), ('H', 1)], [('b', 3)]),

    stage('1-3', 1, 'The Old Well', 'meadow', 'Mind the water. Some things bite back.',
          lambda b: (b.flat(5).crossing(2).step(-1).flat(3).tower(3, bone=True)
                      .step(1).flat(3).mesa(4).crossing(2).flat(3).arch()
                      .step(-1).flat(5)),
          [('h', 2), ('g', 2), ('t', 1), ('o', 10), ('H', 1)], [('b', 3)]),

    arena('1-4', 1, 'Grumblegut', 'meadow', 'boar', 'He charges. Let him hit the wall.'),

    stage('2-1', 2, 'Dripstone Deep', 'cavern', 'Spikes hurt. Everything down here hurts.',
          lambda b: (b.flat(5).spikes(3).step(-1).flat(3).tower(2).pit(3)
                      .flat(3).pillars(3, 2, 2).step(1).flat(3).mesa(5)
                      .spikes(3).step(-1).flat(3).tower(3, bone=True)
                      .step(1).flat(3).pit(3).flat(5)),
          [('g', 5), ('o', 11), ('K', 2), ('H', 1)], [('b', 4)]),

    stage('2-2', 2, 'Crystal Drop', 'cavern', 'Ride the stones. Do not rush.',
          lambda b: (b.flat(5).crossing(2).step(-1).flat(3).mesa(5).step(1)
                      .flat(3).tower(3, bone=True).crossing(2).step(-1)
                      .flat(3).pillars(4, 2, 2).step(1).flat(5)),
          [('g', 4), ('t', 2), ('o', 11), ('K', 1), ('H', 1)], [('b', 4)]),

    stage('2-3', 2, 'Gnaw Tunnels', 'cavern', 'Bonehounds leap when you get close.',
          lambda b: (b.flat(4).arch().step(-1).flat(3).spikes(3)
                      .pillars(3, 2, 2).step(1).flat(3).tower(3, bone=True)
                      .pit(3).mesa(5).step(-1).flat(3).crossing(1).flat(3)
                      .arch().step(1).flat(5)),
          [('h', 4), ('g', 3), ('t', 2), ('o', 12), ('K', 2), ('H', 1)], [('b', 4)]),

    arena('2-4', 2, 'Gloomwing', 'cavern', 'gloomwing', 'It dives. Swing when it drops low.'),

    stage('3-1', 3, 'The Ramparts', 'keep', 'The keep is awake.',
          lambda b: (b.flat(4).mesa(4).spikes(3).step(-1).flat(3)
                      .tower(3, bone=True).pillars(3, 2, 2).step(1).flat(3)
                      .arch().pit(3).flat(3).crossing(1).step(-1).flat(3)
                      .mesa(5).step(1).flat(5)),
          [('g', 5), ('h', 4), ('o', 12), ('K', 2), ('H', 1)], [('b', 4)]),

    stage('3-2', 3, 'Iron Halls', 'keep', 'Break the crates. Some hide coin.',
          lambda b: (b.flat(4).spikes(3).pillars(4, 2, 2).step(-1).flat(3)
                      .tower(3, bone=True).step(1).flat(3).arch().spikes(3)
                      .mesa(5).crossing(1).step(-1).flat(3).tower(2)
                      .step(1).flat(5)),
          [('g', 5), ('h', 3), ('t', 3), ('o', 11), ('K', 4), ('H', 1)], [('b', 4)]),

    stage('3-3', 3, 'Throne Approach', 'keep', 'Last stretch. Everything at once.',
          lambda b: (b.flat(4).arch().spikes(3).pillars(3, 2, 3).step(-1)
                      .flat(3).tower(3, bone=True).crossing(2).mesa(5)
                      .spikes(3).step(1).flat(3).arch().pillars(3, 2, 2)
                      .step(-1).flat(5)),
          [('g', 6), ('h', 5), ('t', 3), ('o', 13), ('K', 3), ('H', 1)], [('b', 5)]),

    arena('3-4', 3, 'The Kennel King', 'keep', 'king', 'Jump his shockwaves.')
]

# ---- moving platforms: search for genuinely empty air -------------------
def find_mover(L, want_vertical):
    rows = L['rows']; w = L['w']
    def free(r, c):
        return 0 <= r < H and 0 <= c < w and rows[r][c] == ' '
    span = 3
    for c in range(8, w - 10):
        for r in range(4, 8):
            cells = []
            ok = True
            steps = 3
            for s in range(steps + 1):
                for i in range(span):
                    rr = r + (s if want_vertical else 0)
                    cc = c + i + (0 if want_vertical else s)
                    if not free(rr, cc):
                        ok = False; break
                    cells.append((rr, cc))
                if not ok: break
            if ok:
                return dict(tx=c, ty=r, tw=span,
                            dx=0 if want_vertical else 0.5,
                            dy=0.45 if want_vertical else 0,
                            span=48)
    return None

MOVERS = {}
for i, L in enumerate(LEVELS):
    if L['boss'] or L['id'] in ('1-1', '1-2'):
        continue
    m = find_mover(L, want_vertical=(i % 2 == 0))
    if m:
        MOVERS[L['id']] = [m]

# ---- emit ---------------------------------------------------------------
chunks = []
for L in LEVELS:
    extra = (" boss: '%s'," % L['boss']) if L['boss'] else ''
    body = ',\n'.join("      '%s'" % r for r in L['rows'])
    chunks.append(
"""  {
    id: '%s', world: %d, name: '%s', theme: '%s',%s
    hint: '%s',
    rows: [
%s
    ]
  }""" % (L['id'], L['world'], L['name'], L['theme'], extra, L['hint'], body))

mover_js = ',\n'.join(
    "  '%s': [\n%s\n  ]" % (sid, ',\n'.join(
        "    { tx: %d, ty: %d, tw: %d, dx: %s, dy: %s, span: %d }"
        % (s['tx'], s['ty'], s['tw'], s['dx'], s['dy'], s['span']) for s in mv))
    for sid, mv in MOVERS.items())

header = """/* Stage data - generated by tools/build_levels.py, then verified by
   tools/reach.js against the hero's measured jump arc.

   Legend:  # solid   = one-way platform   ^ spikes   % hazard water
            K crate   S bounce pad   D exit   P spawn   X boss
            o coin    B golden bone   H meat
            g grub    b batling   h bonehound   t thorn turret

   Terrain is assembled from segments (plateaus, steps, mesas, water
   crossings) with structures cut in: pillars to hop, arches to pass
   under or stand on, and platform tiers two tiles apart. */
var LEVELS = [
"""
footer = """
];

/* Moving platforms, keyed by stage id. Tile coords; dx/dy are px per
   frame and span is the round-trip distance in pixels. */
var MOVERS = {
%s
};

var WORLDS = [
  { n: 1, name: 'Sunken Garden', theme: 'meadow' },
  { n: 2, name: 'Root Caverns', theme: 'cavern' },
  { n: 3, name: 'Kennel Keep', theme: 'keep' }
];
""" % mover_js

open(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'levels.js'), 'w').write(header + ',\n'.join(chunks) + footer)
print('wrote levels.js')
for L in LEVELS:
    flat = ''.join(L['rows'])
    print(' ', L['id'], 'w=%-3d' % L['w'],
          'P=%d D=%d X=%d' % (flat.count('P'), flat.count('D'), flat.count('X')),
          'coins=%-3d bone=%d' % (flat.count('o'), flat.count('B')),
          'enemies=%d' % sum(flat.count(c) for c in 'gbht'))
