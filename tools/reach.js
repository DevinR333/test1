/* Reachability audit: can the hero actually get from spawn to exit? */
const fs = require('fs');
eval(fs.readFileSync(require('path').join(__dirname, '..', 'src', 'levels.js'), 'utf8'));

const GRAV = 0.42;
const JUMP = Number(process.argv[2] || 6.7);
const SPEED = Number(process.argv[3] || 1.75);
const air = 2 * JUMP / GRAV;
const JUMP_DX = Math.floor((SPEED * air) / 16);        // tiles of horizontal reach
const JUMP_UP = Math.floor((JUMP * JUMP / (2 * GRAV)) / 16); // tiles of height

function analyze(L) {
  const w = Math.max(...L.rows.map(r => r.length));
  const rows = L.rows.map(r => r.padEnd(w, ' '));
  const H = rows.length;
  const at = (x, y) => (y < 0 || y >= H || x < 0 || x >= w) ? ' ' : rows[y][x];
  const solid = (x, y) => '#KS'.includes(at(x, y));
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
        // Horizontal travel still available after climbing `rise` tiles,
        // from the real trajectory. A tile delta of n clears (n-1) tiles
        // of air, so the reachable delta is one more than the tile span.
        let travel;
        if (rise > 0) {
          const disc = JUMP * JUMP - 2 * GRAV * (rise * 16);
          if (disc < 0) continue;
          travel = SPEED * ((JUMP + Math.sqrt(disc)) / GRAV);
        } else {
          travel = SPEED * (air + Math.sqrt(Math.max(0, 2 * (-dy) * 16 / GRAV)));
        }
        const maxdx = Math.floor(travel / 16) + 1;
        if (Math.abs(dx) > maxdx) continue;
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
    if ('oBH'.includes(at(x, y))) {
      let reachable = false;
      for (let dy = 0; dy <= 4 && !reachable; dy++)
        for (let dx = -2; dx <= 2 && !reachable; dx++)
          if (seen.has(key(x + dx, y + dy))) reachable = true;
      if (!reachable) stranded.push(at(x, y) + '@' + x + ',' + y);
    }
  }
  return { id: L.id, ok, stranded, reached: seen.size, total: nodes.length };
}

console.log('physics: jump reach ' + JUMP_DX + ' tiles across, ' + JUMP_UP + ' tiles up');
let fails = 0;
for (const L of LEVELS) {
  const r = analyze(L);
  if (!r.ok) fails++;
  const st = r.stranded && r.stranded.length ? '  STRANDED: ' + r.stranded.slice(0, 6).join(' ') : '';
  console.log((r.ok ? 'PASS ' : 'FAIL ') + r.id + (r.why ? ' (' + r.why + ')' : '') + st);
}
console.log(fails ? fails + ' stage(s) not completable' : 'all stages completable');
