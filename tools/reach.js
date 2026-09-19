/* Reachability audit: can the hero actually get from spawn to exit? */
const fs = require('fs');
eval(fs.readFileSync(require('path').join(__dirname, '..', 'src', 'levels.js'), 'utf8'));

/* Physics is read straight out of src/entities.js so this audit can never
   drift from the game. The jump is simulated (including the hold lift)
   rather than approximated, then the reachable horizontal distance is
   measured at each rise. */
const ENT = fs.readFileSync(require('path').join(__dirname, '..', 'src', 'entities.js'), 'utf8');
function konst(name, fallback) {
  const m = ENT.match(new RegExp('var\\s+' + name + '\\s*=\\s*([0-9.]+)'));
  if (!m && fallback === undefined) throw new Error('missing constant ' + name);
  return m ? Number(m[1]) : fallback;
}
const GRAV = konst('GRAV');
const MAXFALL = konst('MAXFALL');
const SPEED = Number(process.argv[3] || konst('RUN_SPEED'));
const JUMP = Number(process.argv[2] || konst('JUMP_IMPULSE'));
const LIFT = konst('JUMP_LIFT');
const LIFT_FRAMES = konst('JUMP_LIFT_FRAMES');

/* Simulate a full held jump followed by the air jump, which the hero
   always has. The second impulse fires at the apex, where a player would
   naturally use it, giving the true reachable envelope. */
const DBL = konst('DBL_JUMP_IMPULSE', 0);
const BASE_JUMPS = konst('BASE_JUMPS', 1);
function arc() {
  let vy = -JUMP, y = 0, lift = LIFT_FRAMES, used = 1;
  const path = [];
  for (let f = 0; f < 400; f++) {
    if (lift > 0 && vy < 0) { vy -= LIFT; lift--; }
    if (used < BASE_JUMPS && vy >= 0) {      // apex: spend the air jump
      vy = -DBL; lift = LIFT_FRAMES; used++;
    }
    vy = Math.min(vy + GRAV, MAXFALL);
    y += vy;
    path.push(y);
    if (y > 16 * 22) break;
  }
  return path;
}
const PATH = arc();
const PEAK = -Math.min(...PATH);

/* Horizontal tiles reachable when landing `rise` tiles above the start
   (negative rise = landing lower down, which buys extra airtime). */
function reachAt(rise) {
  const targetY = -rise * 16;
  let last = -1;
  for (let f = 0; f < PATH.length; f++) if (PATH[f] <= targetY + 0.001) last = f;
  if (rise > 0 && last < 0) return -1;         // cannot climb that high
  if (rise <= 0) {
    for (let f = 0; f < PATH.length; f++) if (PATH[f] >= targetY) { last = f; break; }
  }
  // a tile delta of n clears (n-1) tiles of air, so add one
  return Math.floor((SPEED * last) / 16) + 1;
}
const air = PATH.length;
const JUMP_UP = Math.floor(PEAK / 16);
const JUMP_DX = reachAt(0);

function analyze(L) {
  const w = Math.max(...L.rows.map(r => r.length));
  const rows = L.rows.map(r => r.padEnd(w, ' '));
  const H = rows.length;
  const at = (x, y) => (y < 0 || y >= H || x < 0 || x >= w) ? ' ' : rows[y][x];
  const solid = (x, y) => '#KSB'.includes(at(x, y));
  const stand = (x, y) => (solid(x, y) || at(x, y) === '=') && !solid(x, y - 1) && at(x, y - 1) !== '^';

  // movers act as standable surfaces too
  const movers = (MOVERS[L.id] || []);
  const moverCells = new Set();
  for (const m of movers) {
    const steps = Math.ceil(m.span / 16) + 1;
    for (let s = 0; s <= steps; s++) {
      for (let i = 0; i < m.tw; i++) {
        const mx = m.tx + i + (m.dx ? s : 0), my = m.ty + (m.dy ? s : 0);
        moverCells.add(mx + ',' + my);
      }
    }
  }
  const standable = (x, y) => stand(x, y) || moverCells.has(x + ',' + y);

  const nodes = [];
  for (let y = 0; y < H; y++) for (let x = 0; x < w; x++) if (standable(x, y)) nodes.push([x, y]);
  const key = (x, y) => x + ',' + y;
  const nodeSet = new Set(nodes.map(n => key(n[0], n[1])));

  // spawn + door
  let spawn = null, door = null, boss = null;
  for (let y = 0; y < H; y++) for (let x = 0; x < w; x++) {
    if (at(x, y) === 'P') spawn = [x, y];
    if (at(x, y) === 'D') door = [x, y];
    if (at(x, y) === 'X') boss = [x, y];
  }
  const goal = door || boss;
  if (!spawn || !goal) return { id: L.id, ok: false, why: 'missing spawn or goal' };

  // snap to nearest standable at/below
  function snap(p) {
    for (let y = p[1]; y < H; y++) if (nodeSet.has(key(p[0], y))) return [p[0], y];
    for (let dx = -2; dx <= 2; dx++) for (let y = p[1]; y < H; y++)
      if (nodeSet.has(key(p[0] + dx, y))) return [p[0] + dx, y];
    return null;
  }
  const s = snap(spawn), gl = snap(goal);
  if (!s || !gl) return { id: L.id, ok: false, why: 'spawn/goal has no floor' };

  // springs launch much higher
  const springUp = Math.floor((8.7 * 8.7 / (2 * GRAV)) / 16);

  const seen = new Set([key(s[0], s[1])]);
  const q = [s];
  while (q.length) {
    const [x, y] = q.shift();
    const up = at(x, y + 1) === 'S' || at(x, y) === 'S' ? springUp : JUMP_UP;
    for (let dx = -8; dx <= 8; dx++) {
      for (let dy = -up; dy <= 12; dy++) {
        const nx = x + dx, ny = y + dy;
        if (!nodeSet.has(key(nx, ny))) continue;
        // horizontal reach shrinks the higher you have to climb,
        // and grows when you get to fall on the way
        const rise = -dy;
        if (rise > up) continue;
        const maxdx = (up > JUMP_UP) ? reachAt(rise) + 3 : reachAt(rise);  // springs carry further
        if (maxdx < 0 || Math.abs(dx) > maxdx) continue;
        // don't path through hazard columns at the landing spot
        if (at(nx, ny - 1) === '%' || at(nx, ny - 1) === '^') continue;
        const k = key(nx, ny);
        if (!seen.has(k)) { seen.add(k); q.push([nx, ny]); }
      }
    }
  }
  const ok = seen.has(key(gl[0], gl[1]));

  // which collectibles are stranded
  let stranded = [];
  for (let y = 0; y < H; y++) for (let x = 0; x < w; x++) {
    if ('oGJQHV'.includes(at(x, y))) {
      let reachable = false;
      for (let dy = 0; dy <= 4 && !reachable; dy++)
        for (let dx = -2; dx <= 2 && !reachable; dx++)
          if (seen.has(key(x + dx, y + dy))) reachable = true;
      if (!reachable) stranded.push(at(x, y) + '@' + x + ',' + y);
    }
  }
  /* Every chest must sit in a pocket that cannot be entered without
     passing through a false wall. Flood from the chest and from the spawn
     with false walls treated as SOLID: if the two ever meet, the chest is
     standing in open level and is not a secret at all. */
  function flood(sx0, sy0) {
    const hit = new Set([key(sx0, sy0)]);
    const q = [[sx0, sy0]];
    while (q.length) {
      const [cx, cy] = q.shift();
      for (const [dx, dy] of [[1,0],[-1,0],[0,1],[0,-1]]) {
        const nx = cx + dx, ny = cy + dy;
        if (nx < 0 || ny < 0 || nx >= w || ny >= H) continue;
        const t = at(nx, ny);
        if ('#KS=FB'.includes(t)) continue;       // false wall and barrier are rock
        const k2 = key(nx, ny);
        if (!hit.has(k2)) { hit.add(k2); q.push([nx, ny]); }
      }
    }
    return hit;
  }
  const openArea = flood(spawn[0], spawn[1]);
  const exposedChests = [];
  for (let y = 0; y < H; y++) for (let x = 0; x < w; x++) {
    if (at(x, y) !== 'C') continue;
    const pocket = flood(x, y);
    let leaks = false;
    pocket.forEach(k2 => { if (openArea.has(k2)) leaks = true; });
    if (leaks) exposedChests.push(x + ',' + y);
  }

  return { id: L.id, ok, stranded, exposedChests, reached: seen.size, total: nodes.length };
}

console.log('physics (read from src/entities.js): impulse ' + JUMP + ', lift ' + LIFT +
  ' x' + LIFT_FRAMES + 'f, speed ' + SPEED);
console.log('simulated held jump: ' + PEAK.toFixed(1) + 'px peak (' + (PEAK / 16).toFixed(2) +
  ' tiles), ' + JUMP_DX + ' tile delta across level ground');
let fails = 0;
for (const L of LEVELS) {
  const r = analyze(L);
  if (!r.ok) fails++;
  const st = r.stranded && r.stranded.length ? '  STRANDED: ' + r.stranded.slice(0, 6).join(' ') : '';
  const ex = r.exposedChests && r.exposedChests.length
    ? '  CHEST IN THE OPEN: ' + r.exposedChests.join(' ') : '';
  if (ex) fails++;
  console.log(((r.ok && !ex) ? 'PASS ' : 'FAIL ') + r.id + (r.why ? ' (' + r.why + ')' : '') + st + ex);
}
console.log(fails ? fails + ' stage(s) not completable' : 'all stages completable');
