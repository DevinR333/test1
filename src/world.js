/* The playable level: tilemap, collision, camera and HUD. */
/* VIEW_H is fixed so the vertical framing is identical everywhere.
   VIEW_W is recomputed from the real screen aspect at boot and on every
   resize, so the picture fills the display instead of being letterboxed
   inside black bars. */
var VIEW_W = 400, VIEW_H = 224;
var T_EMPTY = 0, T_SOLID = 1, T_PLAT = 2, T_SPIKE = 3, T_HAZ = 4, T_CRATE = 5, T_SPRING = 6;
/* Looks exactly like solid masonry, but you walk straight through it. */
var T_FAKE = 7;
/* Reinforced block: needs blade tier 2 (Emberblade) or better. */
var T_BARRIER = 8;

function World(stageIndex) {
  var def = LEVELS[stageIndex];
  this.index = stageIndex;
  this.def = def;
  this.theme = def.theme;
  this.tileset = Art.tiles(def.theme);

  var w = 0, i, y, x;
  for (i = 0; i < def.rows.length; i++) w = Math.max(w, def.rows[i].length);
  this.cols = w; this.rows = def.rows.length;
  this.pixelW = w * TILE; this.pixelH = this.rows * TILE;

  this.tiles = [];
  this.enemies = []; this.pickups = []; this.shots = [];
  this.parts = []; this.texts = []; this.movers = []; this.chests = [];
  this.revealed = {};
  this.gemsGot = 0; this.chestsGot = 0; this.gemScore = 0; this.vesselGot = 0;
  this.door = null; this.boss = null; this.bossMelee = null;
  var spawn = { x: TILE * 2, y: TILE * 2 };

  for (y = 0; y < this.rows; y++) {
    var row = def.rows[y];
    while (row.length < w) row += ' ';
    var line = [];
    for (x = 0; x < w; x++) {
      var ch = row[x], code = T_EMPTY;
      var px = x * TILE, py = y * TILE;
      switch (ch) {
        case '#': code = T_SOLID; break;
        case '=': code = T_PLAT; break;
        case '^': code = T_SPIKE; break;
        case '%': code = T_HAZ; break;
        case 'K': code = T_CRATE; break;
        case 'S': code = T_SPRING; break;
        case 'F': code = T_FAKE; break;
        case 'B': code = T_BARRIER; break;
        case 'V':
          this.pickups.push(new Pickup(px + 5, py + 5, 'vessel'));
          break;
        case 'C':
          /* the tile itself stays wall, so nothing shows from outside */
          code = T_FAKE;
          var ch2 = new Chest(px + 1, py + 3);
          ch2.buried = true; ch2.tx = x; ch2.ty = y;
          this.chests.push(ch2);
          break;
        case 'q': case 'j':
          code = T_FAKE;
          var bg = new Pickup(px + 4, py + 4, 'gem');
          bg.grade = (ch === 'q') ? 'crown' : 'jewel';
          bg.buried = true; bg.tx = x; bg.ty = y;
          this.pickups.push(bg);
          break;
        case 'G': case 'J': case 'Q':
          var grade = ch === 'G' ? 'shard' : (ch === 'J' ? 'jewel' : 'crown');
          var gem = new Pickup(px + 4, py + 4, 'gem');
          gem.grade = grade;
          this.pickups.push(gem);
          break;
        case 'P': spawn = { x: px + 2, y: py + 2 }; break;
        case 'D': this.door = { x: px, y: py - TILE, w: TILE, h: TILE * 2 }; break;
        case 'o': this.pickups.push(new Pickup(px + 4, py + 4, 'coin')); break;

        case 'H': this.pickups.push(new Pickup(px + 4, py + 4, 'meat')); break;
        case 'g': this.enemies.push(new Enemy(px + 2, py + 4, 'grub', def.world)); break;
        case 'b': this.enemies.push(new Enemy(px + 2, py + 4, 'bat', def.world)); break;
        case 'h': this.enemies.push(new Enemy(px + 1, py + 1, 'hound', def.world)); break;
        case 't': this.enemies.push(new Enemy(px + 2, py + 2, 'thorn', def.world)); break;
        case 'X': this.bossSpawn = { x: px, y: py }; break;
      }
      line.push(code);
    }
    this.tiles.push(line);
  }

  var mv = MOVERS[def.id] || [];
  for (i = 0; i < mv.length; i++) {
    var m = mv[i];
    this.movers.push({
      x: m.tx * TILE, y: m.ty * TILE, w: m.tw * TILE, h: 6,
      ox: m.tx * TILE, oy: m.ty * TILE,
      dx: m.dx, dy: m.dy, span: m.span, dir: 1, mx: 0, my: 0
    });
  }

  this.player = new Player(spawn.x, spawn.y);
  this.spawn = spawn;
  this.lastSafe = { x: spawn.x, y: spawn.y };

  if (def.boss && this.bossSpawn) {
    this.boss = new Boss(this.bossSpawn.x, this.bossSpawn.y, def.boss);
    this.bossIntro = 90;
  }

  /* depth below the surface, so buried rock can fall into shadow */
  this.depth = [];
  for (y = 0; y < this.rows; y++) {
    var drow = [];
    for (x = 0; x < this.cols; x++) {
      if (!this.isWallLooking(this.tiles[y][x])) { drow.push(-1); continue; }
      drow.push((y > 0 && this.depth[y - 1][x] >= 0) ? this.depth[y - 1][x] + 1 : 0);
    }
    this.depth.push(drow);
  }

  /* Label each connected pocket of false wall. A secret is one whole
     region, so stepping inside opens all of it at once instead of
     dissolving a circle around the hero. */
  this.fakeRegion = [];
  for (y = 0; y < this.rows; y++) {
    this.fakeRegion.push(new Array(this.cols).fill(-1));
  }
  this.regionCount = 0;
  for (y = 0; y < this.rows; y++) {
    for (x = 0; x < this.cols; x++) {
      if (this.tiles[y][x] !== T_FAKE || this.fakeRegion[y][x] !== -1) continue;
      var id = this.regionCount++;
      var stack = [[x, y]];
      this.fakeRegion[y][x] = id;
      while (stack.length) {
        var cur = stack.pop();
        var steps = [[1, 0], [-1, 0], [0, 1], [0, -1]];
        for (var si = 0; si < steps.length; si++) {
          var nx2 = cur[0] + steps[si][0], ny2 = cur[1] + steps[si][1];
          if (nx2 < 0 || ny2 < 0 || nx2 >= this.cols || ny2 >= this.rows) continue;
          if (this.tiles[ny2][nx2] !== T_FAKE || this.fakeRegion[ny2][nx2] !== -1) continue;
          this.fakeRegion[ny2][nx2] = id;
          stack.push([nx2, ny2]);
        }
      }
    }
  }
  this.regionOpen = new Array(this.regionCount).fill(0);
  this.activeRegion = -1;

  this.decor = this.buildDecor();

  this.cam = { x: 0, y: 0 };
  this.centerCamera();
  this.shakeAmt = 0;
  this.t = 0;
  this.state = 'play';           /* play | dying | clear */
  this.stateTimer = 0;
  this.coinsRun = 0;
  this.secretShown = false;
  this.bannerTimer = 150;
  this.hitStop = 0;
  Sfx.playSong(def.boss ? 'boss' : def.theme);
}

/* ---------------- tiles ---------------- */
World.prototype.tileAt = function (tx, ty) {
  if (ty < 0 || ty >= this.rows) return T_EMPTY;
  if (tx < 0 || tx >= this.cols) return T_SOLID;   /* level edges are walls */
  return this.tiles[ty][tx];
};
World.prototype.isSolidCode = function (c) {
  /* Ledges collide on every face. Nothing in this game passes through
     floors - you go around, or you find another way up. */
  return c === T_SOLID || c === T_CRATE || c === T_SPRING || c === T_PLAT ||
         c === T_BARRIER;
};
/* Drawn as masonry, so it has to be hashed like masonry too. */
World.prototype.isWallLooking = function (c) {
  return c === T_SOLID || c === T_FAKE;
};
/* Only the hero slips through a false wall. For everything else it is
   ordinary stone - otherwise a patrolling enemy strolls through the
   secret and advertises exactly where it is. */
World.prototype.solidFor = function (e, c) {
  if (c === T_FAKE) return e !== this.player;
  return this.isSolidCode(c);
};
World.prototype.solidAt = function (px, py) {
  return this.isSolidCode(this.tileAt(Math.floor(px / TILE), Math.floor(py / TILE)));
};
World.prototype.codeAtPx = function (px, py) {
  return this.tileAt(Math.floor(px / TILE), Math.floor(py / TILE));
};

/* ---------------- movement ---------------- */
World.prototype.moveActor = function (e, loose, flyer) {
  e.hitWall = false;
  var prevBottom = e.y + e.h;
  var prevTop = e.y;
  var tx, ty, x1, x2, y1, y2, i, m;

  /* --- horizontal --- */
  e.x += e.vx;
  x1 = Math.floor(e.x / TILE); x2 = Math.floor((e.x + e.w - 1) / TILE);
  y1 = Math.floor(e.y / TILE); y2 = Math.floor((e.y + e.h - 1) / TILE);
  for (ty = y1; ty <= y2; ty++) {
    for (tx = x1; tx <= x2; tx++) {
      if (!this.solidFor(e, this.tileAt(tx, ty))) continue;
      if (e.vx > 0) { e.x = tx * TILE - e.w; e.vx = 0; e.hitWall = true; }
      else if (e.vx < 0) { e.x = (tx + 1) * TILE; e.vx = 0; e.hitWall = true; }
      x1 = Math.floor(e.x / TILE); x2 = Math.floor((e.x + e.w - 1) / TILE);
    }
  }
  for (i = 0; i < this.movers.length; i++) {
    m = this.movers[i];
    if (Util.aabb(e, m) && prevBottom > m.y + 3) {
      if (e.vx > 0) { e.x = m.x - e.w; e.vx = 0; e.hitWall = true; }
      else if (e.vx < 0) { e.x = m.x + m.w; e.vx = 0; e.hitWall = true; }
    }
  }

  /* --- vertical --- */
  e.y += e.vy;
  e.grounded = false;
  x1 = Math.floor(e.x / TILE); x2 = Math.floor((e.x + e.w - 1) / TILE);
  y1 = Math.floor(e.y / TILE); y2 = Math.floor((e.y + e.h - 1) / TILE);
  for (ty = y1; ty <= y2; ty++) {
    for (tx = x1; tx <= x2; tx++) {
      var code = this.tileAt(tx, ty);
      if (this.solidFor(e, code)) {
        if (e.vy > 0) {
          e.y = ty * TILE - e.h; e.vy = 0; e.grounded = true;
          if (code === T_SPRING && e === this.player) {
            e.vy = -8.7; e.grounded = false;
            Sfx.spring(); this.puff(e.x + e.w / 2, e.y + e.h, '#ff8a92', 6);
          }
        } else if (e.vy < 0) { e.y = (ty + 1) * TILE; e.vy = 0; }
        y1 = Math.floor(e.y / TILE); y2 = Math.floor((e.y + e.h - 1) / TILE);
      }
    }
  }
  for (i = 0; i < this.movers.length; i++) {
    m = this.movers[i];
    if (!Util.aabb(e, m)) continue;
    if (e.vy >= 0 && prevBottom <= m.y + 3) {
      e.y = m.y - e.h; e.vy = 0; e.grounded = true; e.riding = m;
    } else if (e.vy < 0 && prevTop >= m.y + m.h - 3) {
      e.y = m.y + m.h; e.vy = 0;
    }
  }
  if (!e.grounded && e.vy >= 0 && this.onGround(e)) e.grounded = true;

  /* Settle exactly on the surface. Gravity adds a fraction every frame and
     the collision pass only snaps it back once it overlaps, so a standing
     actor drifted up and down by half a pixel - enough to flip the camera
     a pixel each frame and make the hero look like he was vibrating. */
  if (e.grounded && e.vy > 0) {
    var foot = e.y + e.h;
    var surf = Math.round(foot / TILE) * TILE;
    if (Math.abs(foot - surf) <= e.vy + 0.5 &&
        this.solidFor(e, this.codeAtPx(e.x + e.w / 2, surf + 1))) {
      e.y = surf - e.h;
    }
    e.vy = 0;
  }
};

/* Is there footing directly under this actor? Checked separately from the
   collision snap: an actor resting exactly on a tile boundary does not
   overlap the floor tile, so the snap alone reports it as airborne and the
   grounded flag flickers frame to frame. */
/* Which pocket of false wall the hero is standing in, or -1. He has to
   actually be inside it - nothing opens on approach. */
World.prototype.regionUnder = function () {
  var p = this.player;
  var x1 = Math.floor((p.x + 2) / TILE), x2 = Math.floor((p.x + p.w - 3) / TILE);
  var y1 = Math.floor((p.y + 2) / TILE), y2 = Math.floor((p.y + p.h - 3) / TILE);
  for (var ty = y1; ty <= y2; ty++) {
    for (var tx = x1; tx <= x2; tx++) {
      if (tx < 0 || ty < 0 || tx >= this.cols || ty >= this.rows) continue;
      if (this.tiles[ty][tx] === T_FAKE) return this.fakeRegion[ty][tx];
    }
  }
  return -1;
};

/* 0 = looks like solid rock, 1 = fully open. The whole pocket shares one
   value, so a secret chamber opens and closes as a single room. */
World.prototype.fakeOpen = function (tx, ty) {
  var id = this.fakeRegion[ty] ? this.fakeRegion[ty][tx] : -1;
  if (id < 0) return 0;
  return this.regionOpen[id];
};

/* Buried treasure shows only while the wall around it is open. */
World.prototype.uncovered = function (e) {
  if (!e.buried) return true;
  if (e.open) return true;                 /* an opened chest stays put */
  var id = this.fakeRegion[e.ty] ? this.fakeRegion[e.ty][e.tx] : -1;
  return id >= 0 && id === this.activeRegion;
};

World.prototype.onGround = function (e) {
  var probe = e.y + e.h + 1;
  var xl = e.x + 1, xr = e.x + e.w - 2;
  if (this.solidFor(e, this.codeAtPx(xl, probe)) ||
      this.solidFor(e, this.codeAtPx(xr, probe))) return true;
  for (var i = 0; i < this.movers.length; i++) {
    var m = this.movers[i];
    if (e.x + e.w > m.x && e.x < m.x + m.w &&
        e.y + e.h >= m.y - 2 && e.y + e.h <= m.y + 3) return true;
  }
  return false;
};

/* ---------------- spawning helpers ---------------- */
/* A short panel explaining what just dropped, so a chest is not just
   something flinging off the screen. */
World.prototype.showPopup = function (title, lines, color, blocking) {
  this.popup = {
    title: title, lines: lines || [], color: color || '#e8c45c',
    t: 200, blocking: blocking !== false
  };
  if (this.popup.blocking) {
    /* Nothing updates while a blocking popup is up, so a shake started by
       whatever dropped this loot would otherwise judder the picture for
       as long as the panel is on screen. */
    this.shakeAmt = 0;
    this.hitStop = 0;
  }
};
World.prototype.dismissPopup = function () { this.popup = null; };
World.prototype.popupBlocking = function () {
  return !!(this.popup && this.popup.blocking);
};

World.prototype.drawPopup = function (g) {
  var pu = this.popup;
  if (!pu) return;
  if (!pu.blocking && pu.t <= 0) { this.popup = null; return; }
  var fade = (pu.blocking || pu.t > 30) ? 1 : pu.t / 30;
  var w = 168, h = 30 + pu.lines.length * 11;
  var x = Math.round(VIEW_W / 2 - w / 2), y = 30;
  g.save();
  g.globalAlpha = fade;
  g.fillStyle = 'rgba(12,9,20,.92)';
  g.fillRect(x, y, w, h);
  g.fillStyle = pu.color;
  g.fillRect(x, y, w, 1); g.fillRect(x, y + h - 1, w, 1);
  g.fillRect(x, y, 1, h); g.fillRect(x + w - 1, y, 1, h);
  Text.center(g, pu.title, VIEW_W / 2, y + 7, pu.color, 1);
  for (var i = 0; i < pu.lines.length; i++) {
    Text.center(g, pu.lines[i], VIEW_W / 2, y + 21 + i * 11, '#e6dcf7', 1);
  }
  if (pu.blocking) {
    Text.center(g, 'PRESS TO CARRY ON', VIEW_W / 2, y + h + 6, '#8d80ad', 1);
  }
  g.restore();
  if (!pu.blocking) pu.t--;
};

World.prototype.puff = function (x, y, color, n) {
  for (var i = 0; i < n; i++) {
    this.parts.push(new Particle(x, y, Util.rand(-1.5, 1.5), Util.rand(-1.8, 0.4),
      Util.randInt(14, 30), color, Math.random() < 0.3 ? 2 : 1));
  }
};
World.prototype.shake = function (n) { this.shakeAmt = Math.max(this.shakeAmt, n); };
World.prototype.dropOrb = function (x, y) {
  var o = new Pickup(x - 4, y - 4, 'orb');
  o.loose = true;
  o.vx = Util.rand(-0.8, 0.8); o.vy = -2.2;
  this.pickups.push(o);
};
World.prototype.dropCoin = function (x, y) {
  var p = new Pickup(x - 4, y - 4, 'coin');
  p.loose = true;
  p.vx = Util.rand(-1.2, 1.2); p.vy = Util.rand(-2.6, -1.2);
  this.pickups.push(p);
};
World.prototype.spawnEnemy = function (x, y, type) {
  var e = new Enemy(x, y, type, this.def.world);
  this.enemies.push(e);
  this.puff(x + 6, y + 6, '#c9a2f0', 8);
};
World.prototype.breakCrate = function (tx, ty, stone) {
  var was = this.tileAt(tx, ty);
  if (was !== T_CRATE && was !== T_BARRIER) return;
  this.tiles[ty][tx] = T_EMPTY;
  var cx = tx * TILE + 8, cy = ty * TILE + 8;
  Sfx.kill(); this.shake(3);
  for (var i = 0; i < 10; i++) {
    this.parts.push(new Particle(cx, cy, Util.rand(-2, 2), Util.rand(-2.6, 0.4),
      Util.randInt(18, 34), stone ? Util.pick(['#98a2b0', '#5e525c', '#c9d3e0'])
                                  : Util.pick(['#a97a45', '#c99a5f', '#5e3f21']), 2));
  }
  var n = Util.randInt(2, 4);
  for (var k = 0; k < n; k++) this.dropCoin(cx, cy);
};

/* Each blade spends a charge differently. */
World.prototype.fireSpecial = function (p) {
  var f = p.facing;
  var cx = p.x + p.w / 2 + f * 8, cy = p.y + 4;
  var kind = Save.blade().special;
  var dmg = p.damage();
  var sh;

  if (kind === 'splinter') {
    /* a shallow fan: a steeper spread buried the outer two in the floor */
    for (var i = -1; i <= 1; i++) {
      sh = new Shot(cx, cy - 1, f * 3.4, i * 0.35, 'splinter', true);
      sh.dmg = dmg; sh.life = 70;
      this.shots.push(sh);
    }
  } else if (kind === 'cross') {
    /* the swing lands twice AND throws the slash a short way forward, so
       the special reads as one even when nothing is in arm's reach */
    p.crossHit = true;
    p.fxCross = 16;
    sh = new Shot(cx, cy - 5, f * 3.0, 0, 'crossw', true);
    sh.dmg = dmg * 2; sh.life = 24; sh.pierce = true;
    this.shots.push(sh);
    this.puff(cx, cy + 4, '#eef4fa', 10);
  } else if (kind === 'quake') {
    this.shots.push(quake(this, p, -1, dmg));
    this.shots.push(quake(this, p, 1, dmg));
    this.shake(7);
  } else if (kind === 'flame') {
    sh = new Shot(cx, p.y + p.h - 12, f * 1.9, 0, 'flame', true);
    sh.dmg = dmg; sh.life = 120;
    this.shots.push(sh);
  } else if (kind === 'lash') {
    p.lashHit = true;
    p.fxLash = 14;
    for (var li = 0; li < 5; li++) {
      this.puff(cx + f * (10 + li * 9), cy + 4 + (li % 2 ? 2 : -2), '#e8fff0', 4);
    }
  } else {                                   /* thunder */
    sh = new Shot(cx, cy, f * 4.2, 0, 'bolt', true);
    sh.dmg = dmg + 2; sh.pierce = true; sh.life = 70;
    this.shots.push(sh);
  }
};
function quake(w, p, dir, dmg) {
  var sh = new Shot(p.x + (dir > 0 ? p.w : -10), p.y + p.h - 10, dir * 2.2, 0, 'shock', true);
  sh.dmg = dmg; sh.life = 90;
  return sh;
}

World.prototype.onPlayerDead = function () {
  this.state = 'dying'; this.stateTimer = 96;
  Sfx.stopSong();
};
World.prototype.bossDefeated = function () {
  this.state = 'clear'; this.stateTimer = 90;
  Sfx.stopSong(); Sfx.fanfare();
  for (var i = 0; i < 26; i++) this.dropCoin(this.boss ? this.boss.x + 20 : 100, 60);
};

World.prototype.centerCamera = function () {
  var p = this.player;
  this.cam.x = Util.clamp(p.x + p.w / 2 - VIEW_W / 2, 0, Math.max(0, this.pixelW - VIEW_W));
  this.cam.y = Util.clamp(p.y + p.h / 2 - VIEW_H / 2 + 8, 0, Math.max(0, this.pixelH - VIEW_H));
};

/* ---------------- hazards & respawn ---------------- */
World.prototype.respawnPlayer = function () {
  var p = this.player;
  p.x = this.lastSafe.x; p.y = this.lastSafe.y - 2;
  p.vx = 0; p.vy = 0;
  p.invuln = 70;
  this.centerCamera();
};
World.prototype.hazardHit = function () {
  var p = this.player;
  if (p.invuln > 0 || p.dead) return;
  p.hp--;
  Sfx.hurt(); this.shake(6);
  this.puff(p.x + p.w / 2, p.y + p.h / 2, '#ffffff', 10);
  if (p.hp <= 0) { p.hp = 0; p.die(this); }
  else { this.respawnPlayer(); }
};

/* ---------------- main update ---------------- */
World.prototype.update = function () {
  this.t++;
  var p = this.player, i, e;

  if (this.popupBlocking()) {
    /* the loot popup holds the action - and the picture, which kept
       juddering because the shake decays further down this function */
    this.shakeAmt = 0;
    this.hitStop = 0;
    return;
  }
  if (this.hitStop > 0) { this.hitStop--; return; }
  if (this.bannerTimer > 0) this.bannerTimer--;

  /* open the pocket he is standing in, close every other */
  this.activeRegion = p.dead ? -1 : this.regionUnder();
  for (var ri = 0; ri < this.regionCount; ri++) {
    var target = (ri === this.activeRegion) ? 1 : 0;
    var cur2 = this.regionOpen[ri];
    this.regionOpen[ri] = cur2 + (target - cur2) * 0.34;
    if (Math.abs(this.regionOpen[ri] - target) < 0.02) this.regionOpen[ri] = target;
  }
  if (this.shakeAmt > 0) this.shakeAmt = Math.max(0, this.shakeAmt - 0.6);

  /* moving platforms carry whatever stands on them */
  for (i = 0; i < this.movers.length; i++) {
    var m = this.movers[i];
    var stepX = m.dx * m.dir, stepY = m.dy * m.dir;
    m.mx += Math.abs(stepX) + Math.abs(stepY);
    if (m.mx >= m.span) { m.mx = 0; m.dir *= -1; }
    var riders = [p].concat(this.enemies);
    for (var r = 0; r < riders.length; r++) {
      var a = riders[r];
      if (a.dead) continue;
      var feet = a.y + a.h;
      if (feet >= m.y - 3 && feet <= m.y + 4 && a.x + a.w > m.x && a.x < m.x + m.w) {
        a.x += stepX; a.y += stepY;
      }
    }
    m.x += stepX; m.y += stepY;
  }

  if (this.state === 'play' || this.state === 'clear') {
    p.update(this);
  } else if (this.state === 'dying') {
    p.update(this);
    if (--this.stateTimer <= 0) { Game.onLevelFailed(); return; }
  }

  /* boss intro pause */
  if (this.bossIntro > 0) { this.bossIntro--; }

  if (this.state === 'play') {
    /* --- hazards & falling out of the world --- */
    if (p.y > this.pixelH + 20 && !p.dead) this.hazardHit();
    if (!p.dead) {
      var cx = p.x + p.w / 2, cy = p.y + p.h / 2;
      var c = this.codeAtPx(cx, cy);
      var cFeet = this.codeAtPx(cx, p.y + p.h - 2);
      if (c === T_HAZ || cFeet === T_HAZ) this.hazardHit();
      else if (c === T_SPIKE || cFeet === T_SPIKE) {
        if (p.invuln <= 0) { p.hurt(this, cx); if (!p.dead) p.vy = -3.4; }
      }
      /* remember the last safe footing for respawns */
      if (p.grounded && p.vy === 0) {
        var below = this.codeAtPx(cx, p.y + p.h + 2);
        if (below === T_SOLID || below === T_PLAT) {
          this.lastSafe.x = p.x; this.lastSafe.y = p.y;
        }
      }
    }

    /* --- push into a false wall and it gives way --- */
    var fx0 = Math.floor(p.x / TILE), fx1 = Math.floor((p.x + p.w - 1) / TILE);
    var fy0 = Math.floor(p.y / TILE), fy1 = Math.floor((p.y + p.h - 1) / TILE);
    for (var fty = fy0; fty <= fy1; fty++) {
      for (var ftx = fx0; ftx <= fx1; ftx++) {
        if (this.tileAt(ftx, fty) !== T_FAKE) continue;
        var fkey = ftx + ',' + fty;
        if (this.revealed[fkey]) continue;
        this.revealed[fkey] = true;
        Sfx.spring();
        this.puff(ftx * TILE + 8, fty * TILE + 8, '#d8cbb8', 10);
        if (!this.secretShown) {
          this.secretShown = true;
          this.texts.push(new FloatText(p.x - 12, p.y - 10, 'SECRET FOUND', '#e8c45c'));
        }
      }
    }

    /* --- chests --- */
    for (i = 0; i < this.chests.length; i++) this.chests[i].update(this);

    /* --- the swing --- */
    var hb = p.hitbox();
    if (hb) {
      /* the cross slash cuts back across on the return, so everything
         already struck is fair game once more */
      if (p.crossHit && !p.crossAgain && p.attack <= 8) {
        p.attackHit = [];
        p.crossAgain = true;
        this.puff(p.x + p.w / 2 + p.facing * 12, p.y + 8, '#eef4fa', 6);
      }
      for (i = 0; i < this.enemies.length; i++) {
        e = this.enemies[i];
        if (e.dead || p.attackHit.indexOf(e) >= 0) continue;
        if (Util.aabb(hb, e)) {
          p.attackHit.push(e);
          var mult = p.special && Save.blade().special === 'cross' ? 2 : 1;
          e.hurt(p.damage() * mult, p.facing, this);
          this.hitStop = 2;
          this.texts.push(new FloatText(e.x + e.w / 2 - 3, e.y - 4, String(p.damage()), '#ffe27a'));
        }
      }
      if (this.boss && !this.boss.dead && p.attackHit.indexOf(this.boss) < 0 &&
          Util.aabb(hb, this.boss) && this.boss.hurtLock <= 0) {
        p.attackHit.push(this.boss);
        this.boss.hurt(p.damage(), p.facing, this);
        this.hitStop = 3; this.shake(4);
      }
      /* the blade is the only key: a buried chest stays shut until its
         pocket is open and you actually swing at it */
      for (i = 0; i < this.chests.length; i++) {
        var chH = this.chests[i];
        if (chH.open || p.attackHit.indexOf(chH) >= 0) continue;
        if (this.uncovered(chH) && Util.aabb(hb, chH)) {
          p.attackHit.push(chH);
          chH.hit = 10;
          this.hitStop = 2; this.shake(3);
          chH.pop(this);
        }
      }
      /* crates in the swing arc */
      var tx0 = Math.floor(hb.x / TILE), tx1 = Math.floor((hb.x + hb.w) / TILE);
      var ty0 = Math.floor(hb.y / TILE), ty1 = Math.floor((hb.y + hb.h) / TILE);
      for (var ty = ty0; ty <= ty1; ty++) {
        for (var tx = tx0; tx <= tx1; tx++) {
          var bt = this.tileAt(tx, ty);
          if (bt === T_CRATE) this.breakCrate(tx, ty);
          else if (bt === T_BARRIER) {
            if (Save.bestDamage() >= 3) this.breakCrate(tx, ty, true);
            else if (!this.barrierHinted) {
              this.barrierHinted = true;
              Sfx.deny();
              this.texts.push(new FloatText(tx * TILE - 12, ty * TILE - 6,
                'NEEDS A HEAVIER BLADE', '#c9d3e0'));
            }
          }
        }
      }
      /* deflect incoming shots - never your own, which spawn inside the
         very swing that fired them and were being cancelled on frame one */
      for (i = 0; i < this.shots.length; i++) {
        var s = this.shots[i];
        if (!s.friendly && s.kind !== 'shock' && Util.aabb(hb, s)) {
          s.dead = true;
          this.puff(s.x, s.y, '#ffe27a', 5);
        }
      }
    }
  }

  /* --- enemies --- */
  for (i = 0; i < this.enemies.length; i++) {
    e = this.enemies[i];
    if (e.dead) continue;
    if (this.state === 'play') e.update(this);
    if (this.state === 'play' && !p.dead && Util.aabb(p, e)) p.hurt(this, e.x + e.w / 2);
  }

  /* --- boss --- */
  if (this.boss && !this.boss.dead) {
    if (this.state === 'play' && this.bossIntro <= 0) this.boss.update(this);
    if (this.state === 'play' && !p.dead && !this.boss.contactHarmless() && Util.aabb(p, this.boss)) {
      p.hurt(this, this.boss.x + this.boss.w / 2);
    }
  }
  if (this.bossMelee) {
    if (this.state === 'play' && !p.dead && Util.aabb(p, this.bossMelee)) p.hurt(this, this.bossMelee.x + 8);
    if (--this.bossMelee.life <= 0) this.bossMelee = null;
  }

  /* --- shots --- */
  for (i = this.shots.length - 1; i >= 0; i--) {
    var sh = this.shots[i];
    sh.update(this);
    if (sh.friendly) {
      for (var ei = 0; ei < this.enemies.length; ei++) {
        var en = this.enemies[ei];
        if (en.dead || sh.hits.indexOf(en) >= 0) continue;
        if (Util.aabb(sh, en)) {
          sh.hits.push(en);
          en.hurt(sh.dmg, sh.vx > 0 ? 1 : -1, this);
          if (!sh.pierce) sh.dead = true;
        }
      }
      if (this.boss && !this.boss.dead && sh.hits.indexOf(this.boss) < 0 &&
          Util.aabb(sh, this.boss) && this.boss.hurtLock <= 0) {
        sh.hits.push(this.boss);
        this.boss.hurt(sh.dmg, sh.vx > 0 ? 1 : -1, this);
        if (!sh.pierce) sh.dead = true;
      }
    } else if (this.state === 'play' && !p.dead && Util.aabb(p, sh)) {
      p.hurt(this, sh.x + sh.w / 2);
      if (sh.kind !== 'shock') sh.dead = true;
    }
    if (sh.dead) this.shots.splice(i, 1);
  }

  /* --- pickups --- */
  for (i = this.pickups.length - 1; i >= 0; i--) {
    var pk = this.pickups[i];
    pk.update(this);
    if (!p.dead && this.uncovered(pk) && Util.aabb(p, pk)) {
      if (pk.kind === 'coin') {
        var val = Save.has('lucky') ? 2 : 1;
        this.coinsRun += val; Save.addCoins(val);
        Sfx.coin();
      } else if (pk.kind === 'gem') {
        var val = Art.GEM_VALUE[pk.grade] || 1;
        this.gemsGot++;
        this.gemScore += val;
        Save.addGems(val);
        Sfx.bone();
        var col = pk.grade === 'crown' ? '#ffc8f4' : (pk.grade === 'jewel' ? '#a8ffd0' : '#c8f4ff');
        this.texts.push(new FloatText(pk.x - 4, pk.y - 8, '+' + val, col));
        this.puff(pk.x + 3, pk.y + 3, col, 14);
      } else if (pk.kind === 'orb') {
        p.maxCharges = 4;
        p.charges = p.maxCharges;
        Sfx.buy();
        var sp = Art.SPECIALS[Save.blade().special];
        var d0 = Save.get();
        if (!d0.seenOrbTip) {
          d0.seenOrbTip = true; Save.flush();
          this.showPopup('SPELL ORB', [
            sp.name.toUpperCase() + '  x' + p.maxCharges,
            sp.desc.toUpperCase()
          ], '#b9a0ff', true);
        } else {
          /* after the first one it just tops you up, no interruption */
          this.showPopup('SPECIAL READY', [sp.name.toUpperCase()], '#b9a0ff', false);
          this.popup.t = 90;
        }
        this.puff(pk.x + 3, pk.y + 3, '#b9a0ff', 16);
      } else if (pk.kind === 'vessel') {
        this.vesselGot++;
        var total = Save.addHeartPiece(this.def.id);
        Sfx.fanfare();
        this.puff(pk.x + 3, pk.y + 3, '#ff8a92', 18);
        if (total % 4 === 0) {
          p.maxHp = Save.maxHearts();
          p.hp = p.maxHp;
          this.texts.push(new FloatText(pk.x - 24, pk.y - 10, 'NEW HEART CONTAINER!', '#ff8a92'));
        } else {
          this.texts.push(new FloatText(pk.x - 16, pk.y - 8,
            'HEART PIECE ' + (total % 4) + '/4', '#ff8a92'));
        }
      } else {
        if (p.hp < p.maxHp) { p.hp++; this.texts.push(new FloatText(pk.x, pk.y - 6, '+1', '#e0424f')); }
        Sfx.buy();
      }
      this.pickups.splice(i, 1);
    }
  }

  /* --- door --- */
  if (this.state === 'play' && this.door && !p.dead && Util.aabb(p, this.door)) {
    this.state = 'clear'; this.stateTimer = 40;
    Sfx.stopSong(); Sfx.fanfare();
  }
  if (this.state === 'clear' && --this.stateTimer <= 0) { Game.onLevelClear(); return; }

  /* --- fx --- */
  for (i = this.parts.length - 1; i >= 0; i--) {
    this.parts[i].update();
    if (this.parts[i].life <= 0) this.parts.splice(i, 1);
  }
  for (i = this.texts.length - 1; i >= 0; i--) {
    this.texts[i].update();
    if (this.texts[i].life <= 0) this.texts.splice(i, 1);
  }
  for (i = this.enemies.length - 1; i >= 0; i--) if (this.enemies[i].dead) this.enemies.splice(i, 1);

  /* --- camera --- */
  var tx2 = Util.clamp(p.x + p.w / 2 - VIEW_W / 2 + p.facing * 14, 0, Math.max(0, this.pixelW - VIEW_W));
  var ty2 = Util.clamp(p.y + p.h / 2 - VIEW_H / 2 + 8, 0, Math.max(0, this.pixelH - VIEW_H));
  this.cam.x = Util.lerp(this.cam.x, tx2, 0.12);
  this.cam.y = Util.lerp(this.cam.y, ty2, 0.10);
};

/* ---------------- draw ---------------- */
/* Deterministic set dressing: ground clutter, hanging vines and
   wall torches, seeded off the stage id so it never shifts. */
World.prototype.buildDecor = function () {
  var id = this.def.id;
  var rnd = Util.seeded(id.charCodeAt(0) * 911 + id.charCodeAt(2) * 37 + 7);
  var P = Art.PROPS, theme = this.theme;
  var ground = theme === 'meadow'
      ? [P.TUFT, P.TUFT, P.TUFT, P.SHROOM, P.BARREL, P.RUBBLE]
      : theme === 'cavern'
      ? [P.CRYSTAL, P.CRYSTAL, P.SHROOM, P.RUBBLE, P.BONEPILE, P.SKULL]
      : [P.POT, P.SKULL, P.BARREL, P.VASE, P.RUBBLE, P.BONEPILE, P.CANDELABRA];
  var props = [], vines = [], torches = [], banners = [];
  var hangers = [], growths = [];
  var tx, ty;

  for (tx = 1; tx < this.cols - 1; tx++) {
    for (ty = 1; ty < this.rows; ty++) {
      var c = this.tileAt(tx, ty);
      if (c === T_PLAT && this.tileAt(tx, ty + 1) === T_EMPTY && rnd() < 0.20) {
        vines.push({ x: tx * TILE + 3 + Math.floor(rnd() * 9), y: ty * TILE + 6,
                     len: 5 + Math.floor(rnd() * 11), seed: rnd() * 6 });
      }
      /* rock overhead with open air beneath it gets something hanging */
      if (this.isWallLooking(c) && this.tileAt(tx, ty + 1) === T_EMPTY && rnd() < 0.30) {
        hangers.push({ x: tx * TILE + 4 + Math.floor(rnd() * 8), y: ty * TILE + TILE,
                       len: 3 + Math.floor(rnd() * 6), seed: Math.floor(rnd() * 9) });
      }
      /* exposed vertical faces grow moss */
      if (this.isWallLooking(c)) {
        if (this.tileAt(tx - 1, ty) === T_EMPTY && rnd() < 0.22) {
          growths.push({ x: tx * TILE, y: ty * TILE + 2, dir: -1, seed: Math.floor(rnd() * 9) });
        }
        if (this.tileAt(tx + 1, ty) === T_EMPTY && rnd() < 0.22) {
          growths.push({ x: tx * TILE + TILE, y: ty * TILE + 2, dir: 1, seed: Math.floor(rnd() * 9) });
        }
      }
      if (!this.isSolidCode(c) || c === T_CRATE || c === T_SPRING) continue;
      if (this.tileAt(tx, ty - 1) !== T_EMPTY) continue;
      if (rnd() < 0.34) {
        var img = ground[Math.floor(rnd() * ground.length)];
        props.push({ img: img, x: tx * TILE + 1 + Math.floor(rnd() * 5), y: ty * TILE - img.height });
      }
      /* rubble banks up where the ground steps down */
      var drop = this.tileAt(tx + 1, ty) === T_EMPTY && this.isSolidCode(this.tileAt(tx, ty));
      if (drop && rnd() < 0.5) {
        props.push({ img: P.RUBBLE, x: tx * TILE + 6, y: ty * TILE - P.RUBBLE.height });
      }
      break;  /* only the topmost surface of each column */
    }
  }

  if (theme !== 'meadow') {
    for (tx = 3; tx < this.cols - 3; tx += 6 + Math.floor(rnd() * 5)) {
      for (ty = 2; ty < this.rows; ty++) {
        if (this.isSolidCode(this.tileAt(tx, ty)) && this.tileAt(tx, ty - 1) === T_EMPTY) {
          torches.push({ x: tx * TILE + 4, y: ty * TILE - 40, seed: Math.floor(rnd() * 9) });
          break;
        }
      }
    }
    if (theme === 'keep') {
      for (tx = 6; tx < this.cols - 6; tx += 17 + Math.floor(rnd() * 9)) {
        banners.push({ x: tx * TILE + 4, y: 14 + Math.floor(rnd() * 10) });
      }
    }
  }
  return { props: props, vines: vines, torches: torches, banners: banners,
           hangers: hangers, growths: growths };
};

World.prototype.draw = function (g) {
  var cam = { x: Math.round(this.cam.x), y: Math.round(this.cam.y) };
  var T = Art.THEMES[this.theme];
  Art.drawBackground(g, this.theme, cam.x, cam.y, this.t, VIEW_W, VIEW_H);

  g.save();
  if (this.shakeAmt > 0.2) {
    g.translate(Math.round(Util.rand(-this.shakeAmt, this.shakeAmt)),
                Math.round(Util.rand(-this.shakeAmt, this.shakeAmt)));
  }

  var i, ts = this.tileset;
  /* banners hang on the back wall, behind everything */
  for (i = 0; i < this.decor.banners.length; i++) {
    var bn = this.decor.banners[i];
    var bx = Math.round(bn.x - cam.x * 0.85), by = Math.round(bn.y - cam.y);
    if (bx > -20 && bx < VIEW_W + 20) {
      Art.drawChain(g, bx + 3, by - 8, 8);
      g.drawImage(Art.PROPS.BANNER, bx, by);
    }
  }
  /* torches sit on the wall behind the play surface */
  for (i = 0; i < this.decor.torches.length; i++) {
    var tc = this.decor.torches[i];
    var tcx = Math.round(tc.x - cam.x), tcy = Math.round(tc.y - cam.y);
    if (tcx > -20 && tcx < VIEW_W + 20) Art.drawTorch(g, tcx, tcy, this.t, tc.seed);
  }

  /* tiles */
  var x0 = Math.max(0, Math.floor(cam.x / TILE)), x1 = Math.min(this.cols - 1, Math.ceil((cam.x + VIEW_W) / TILE));
  var y0 = Math.max(0, Math.floor(cam.y / TILE)), y1 = Math.min(this.rows - 1, Math.ceil((cam.y + VIEW_H) / TILE));
  for (var ty = y0; ty <= y1; ty++) {
    for (var tx = x0; tx <= x1; tx++) {
      var c = this.tiles[ty][tx];
      if (c === T_EMPTY) continue;
      var dx = tx * TILE - cam.x, dy = ty * TILE - cam.y;
      if (c === T_SOLID || c === T_FAKE) {
        /* Autotiled masonry. A false wall uses the identical variant hash,
           depth shading and trim as the real thing, so nothing about it
           reads as different until you walk into it. */
        var fade = 0;
        if (c === T_FAKE) {
          fade = this.fakeOpen(tx, ty);
          if (fade >= 0.98) continue;      /* fully open: draw nothing */
          if (fade > 0) g.globalAlpha = 1 - fade;
        }
        var openUp = !this.isWallLooking(this.tileAt(tx, ty - 1));
        g.drawImage(openUp ? Art.variant(ts.cap, tx, ty) : Art.variant(ts.solid, tx, ty), dx, dy);
        var dep = this.depth[ty][tx];
        if (dep > 0) {
          g.fillStyle = 'rgba(6,4,12,' + Math.min(0.62, dep * 0.17) + ')';
          g.fillRect(dx, dy, TILE, TILE);
        }
        if (!this.isWallLooking(this.tileAt(tx - 1, ty))) g.drawImage(ts.trimL[ty % 2], dx, dy);
        if (!this.isWallLooking(this.tileAt(tx + 1, ty))) g.drawImage(ts.trimR[ty % 2], dx, dy);
        if (!this.isWallLooking(this.tileAt(tx, ty + 1))) g.drawImage(ts.trimB, dx, dy);
        if (fade > 0) g.globalAlpha = 1;
      } else if (c === T_PLAT) {
        var pl = this.tileAt(tx - 1, ty) === T_PLAT, pr = this.tileAt(tx + 1, ty) === T_PLAT;
        g.drawImage(!pl ? ts.platL : (!pr ? ts.platR : ts.platM), dx, dy);
      }
      else if (c === T_SPIKE) g.drawImage(ts.spike, dx, dy);
      else if (c === T_HAZ) g.drawImage(ts.hazard[Math.floor(this.t / 12) % 3], dx, dy);
      else if (c === T_CRATE) g.drawImage(ts.crate, dx, dy);
      else if (c === T_BARRIER) g.drawImage(ts.barrier, dx, dy);
      else if (c === T_SPRING) g.drawImage(ts.spring, dx, dy);
    }
  }

  /* things hanging from the rock, and moss on the faces */
  for (i = 0; i < this.decor.hangers.length; i++) {
    var hg2 = this.decor.hangers[i];
    var hx = Math.round(hg2.x - cam.x);
    if (hx > -10 && hx < VIEW_W + 10) {
      Art.drawHanger(g, hx, Math.round(hg2.y - cam.y), hg2.len, this.theme, hg2.seed);
    }
  }
  for (i = 0; i < this.decor.growths.length; i++) {
    var gw = this.decor.growths[i];
    var gx2 = Math.round(gw.x - cam.x);
    if (gx2 > -8 && gx2 < VIEW_W + 8) {
      Art.drawGrowth(g, gx2, Math.round(gw.y - cam.y), gw.dir, this.theme, gw.seed);
    }
  }

  /* ground clutter */
  for (i = 0; i < this.decor.props.length; i++) {
    var pr = this.decor.props[i];
    var prx = Math.round(pr.x - cam.x);
    if (prx > -20 && prx < VIEW_W + 20) g.drawImage(pr.img, prx, Math.round(pr.y - cam.y));
  }

  /* door */
  if (this.door) {
    g.drawImage(ts.door, Math.round(this.door.x - cam.x) - 4, Math.round(this.door.y - cam.y) - 6);
  }

  /* moving platforms */
  for (i = 0; i < this.movers.length; i++) {
    var m = this.movers[i];
    var mx = Math.round(m.x - cam.x), my = Math.round(m.y - cam.y);
    g.fillStyle = '#3a3448'; g.fillRect(mx, my, m.w, m.h);
    g.fillStyle = '#8d84a8'; g.fillRect(mx, my, m.w, 2);
    g.fillStyle = '#c9c0dd'; g.fillRect(mx, my, m.w, 1);
    g.fillStyle = '#1b1826'; g.fillRect(mx, my + m.h - 1, m.w, 1);
    g.fillStyle = '#e8c45c'; g.fillRect(mx + 1, my + 2, 1, 1); g.fillRect(mx + m.w - 2, my + 2, 1, 1);
  }

  for (i = 0; i < this.chests.length; i++) {
    if (this.uncovered(this.chests[i])) this.chests[i].draw(g, cam);
  }
  for (i = 0; i < this.pickups.length; i++) {
    if (this.uncovered(this.pickups[i])) this.pickups[i].draw(g, cam);
  }
  for (i = 0; i < this.enemies.length; i++) this.enemies[i].draw(g, cam);
  if (this.boss && !this.boss.dead) this.boss.draw(g, cam);
  for (i = 0; i < this.shots.length; i++) this.shots[i].draw(g, cam);
  this.player.draw(g, cam);
  for (i = 0; i < this.parts.length; i++) this.parts[i].draw(g, cam);

  /* vines hang in front of the platforms they grow on */
  for (i = 0; i < this.decor.vines.length; i++) {
    var v = this.decor.vines[i];
    var vx = Math.round(v.x - cam.x);
    if (vx > -8 && vx < VIEW_W + 8) Art.drawVine(g, vx, Math.round(v.y - cam.y), v.len, this.t, v.seed);
  }
  for (i = 0; i < this.texts.length; i++) this.texts[i].draw(g, cam);

  Art.drawMotes(g, this.theme, cam.x, cam.y, this.t, VIEW_W, VIEW_H);
  Art.drawForeground(g, this.theme, cam.x, cam.y, this.t, VIEW_W, VIEW_H);

  g.restore();

  /* ---- lighting ---- */
  g.save();
  g.globalCompositeOperation = 'lighter';
  for (i = 0; i < this.decor.torches.length; i++) {
    var lt = this.decor.torches[i];
    var lx = Math.round(lt.x - cam.x), ly = Math.round(lt.y - cam.y) + 4;
    if (lx > -60 && lx < VIEW_W + 60) {
      var flick = 40 + Math.sin(this.t * 0.2 + lt.seed) * 3;
      Art.light(g, lx + 2, ly, flick, T.glow);
    }
  }
  if (this.door) {
    Art.light(g, this.door.x - cam.x + 8, this.door.y - cam.y + 16, 34, 'rgba(255,220,140,.5)');
  }
  g.restore();
  Art.vignette(g, VIEW_W, VIEW_H, Art.THEMES[this.theme].vignette);

  this.drawHud(g);
};

World.prototype.drawHud = function (g) {
  var p = this.player, i;
  /* hearts */
  for (i = 0; i < p.maxHp; i++) {
    g.drawImage(i < p.hp ? Art.HEART_FULL : Art.HEART_EMPTY, 6 + i * 10, 6);
  }
  /* progress toward the next heart container */
  var hp4 = Save.heartPieceCount() % 4;
  if (hp4) {
    for (i = 0; i < hp4; i++) g.drawImage(Art.HEART_PIECE, 8 + p.maxHp * 10 + i * 7, 6);
  }
  /* Special gauge. It only exists while charged: it appears full when you
     pick up an orb, drains a segment per swing, and vanishes at empty. */
  if (p.charges > 0) {
    var bl = Save.blade();
    var gy = 6 + 12;                      /* directly under the hearts */
    var segW = 13, gap = 2, gx0 = 6;
    for (i = 0; i < p.maxCharges; i++) {
      var sx2 = gx0 + i * (segW + gap);
      g.fillStyle = 'rgba(0,0,0,.55)';
      g.fillRect(sx2 - 1, gy - 1, segW + 2, 8);
      if (i < p.charges) {
        g.fillStyle = '#7d5ad6'; g.fillRect(sx2, gy, segW, 6);
        g.fillStyle = '#b9a0ff'; g.fillRect(sx2, gy, segW, 3);
        g.fillStyle = '#e8dcff'; g.fillRect(sx2, gy, segW, 1);
      } else {
        g.fillStyle = '#241c38'; g.fillRect(sx2, gy, segW, 6);
      }
    }
    Text.shadow(g, Art.SPECIALS[bl.special].name.toUpperCase(),
      gx0 + p.maxCharges * (segW + gap) + 4, gy, '#b9a0ff', 1);
  }

  /* Purse and gem total live on the right, clear of the gauge: they used
     to be printed under the hearts, which is exactly where it draws. */
  g.drawImage(Art.COIN, VIEW_W - 52, 5);
  Text.shadow(g, String(Save.get().coins), VIEW_W - 42, 6, '#ffe27a', 1);
  var gemTxt = String(Save.gemScore());
  var gemX = VIEW_W - 62 - Text.width(gemTxt, 1);
  g.drawImage(Art.GEMS.jewel, gemX - 9, 5);
  Text.shadow(g, gemTxt, gemX, 6, '#a8ffd0', 1);
  /* What this stage still owes you: three gems and two chests, filled
     in as you find them. */
  var slotY = 18, sx0 = VIEW_W - 96;
  for (i = 0; i < 3; i++) {
    var gx = sx0 + i * 10;
    if (i < this.gemsGot) {
      g.drawImage(Art.GEMS.jewel, gx, slotY);
    } else {
      g.fillStyle = 'rgba(0,0,0,.50)'; g.fillRect(gx + 1, slotY + 1, 5, 5);
      g.fillStyle = 'rgba(168,255,208,.35)';
      g.fillRect(gx + 1, slotY + 1, 5, 1); g.fillRect(gx + 1, slotY + 5, 5, 1);
      g.fillRect(gx + 1, slotY + 1, 1, 5); g.fillRect(gx + 5, slotY + 1, 1, 5);
    }
  }
  for (i = 0; i < 2; i++) {
    var kx = sx0 + 36 + i * 13;
    if (i < this.chestsGot) {
      g.drawImage(Art.CHEST, kx, slotY - 2, 11, 10);
    } else {
      g.save(); g.globalAlpha = 0.28;
      g.drawImage(Art.CHEST, kx, slotY - 2, 11, 10);
      g.restore();
    }
  }

  /* boss bar */
  if (this.boss && !this.boss.dead) {
    var bw = 160, bx = (VIEW_W - bw) / 2, by = VIEW_H - 14;
    g.fillStyle = 'rgba(0,0,0,.55)'; g.fillRect(bx - 2, by - 2, bw + 4, 10);
    g.fillStyle = '#3a2030'; g.fillRect(bx, by, bw, 6);
    var frac = Math.max(0, this.boss.hp / this.boss.maxHp);
    g.fillStyle = this.boss.phase === 2 ? '#ff6b4a' : '#e0424f';
    g.fillRect(bx, by, Math.round(bw * frac), 6);
    g.fillStyle = 'rgba(255,255,255,.35)'; g.fillRect(bx, by, Math.round(bw * frac), 1);
    Text.centerShadow(g, this.boss.name, VIEW_W / 2, by - 11, '#ffd0c0', 1);
  }

  /* stage banner */
  if (this.bannerTimer > 0) {
    var a = Math.min(1, this.bannerTimer / 40);
    g.save(); g.globalAlpha = a;
    Text.centerShadow(g, this.def.id + '  ' + this.def.name, VIEW_W / 2, 30, '#ffffff', 1);
    if (this.def.hint) Text.centerShadow(g, this.def.hint, VIEW_W / 2, 42, '#c9c0e0', 1);
    g.restore();
  }

  this.drawPopup(g);

  if (this.state === 'dying') {
    g.fillStyle = 'rgba(10,6,14,' + Math.min(0.75, (96 - this.stateTimer) / 96) + ')';
    g.fillRect(0, 0, VIEW_W, VIEW_H);
  }
};
