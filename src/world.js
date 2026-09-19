/* The playable level: tilemap, collision, camera and HUD. */
var VIEW_W = 400, VIEW_H = 224;
var T_EMPTY = 0, T_SOLID = 1, T_PLAT = 2, T_SPIKE = 3, T_HAZ = 4, T_CRATE = 5, T_SPRING = 6;

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
  this.parts = []; this.texts = []; this.movers = [];
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
        case 'P': spawn = { x: px + 2, y: py + 2 }; break;
        case 'D': this.door = { x: px, y: py - TILE, w: TILE, h: TILE * 2 }; break;
        case 'o': this.pickups.push(new Pickup(px + 4, py + 4, 'coin')); break;
        case 'B': this.pickups.push(new Pickup(px + 3, py + 5, 'bone')); break;
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
      if (!this.isSolidCode(this.tiles[y][x])) { drow.push(-1); continue; }
      drow.push((y > 0 && this.depth[y - 1][x] >= 0) ? this.depth[y - 1][x] + 1 : 0);
    }
    this.depth.push(drow);
  }

  this.decor = this.buildDecor();

  this.cam = { x: 0, y: 0 };
  this.centerCamera();
  this.shakeAmt = 0;
  this.t = 0;
  this.state = 'play';           /* play | dying | clear */
  this.stateTimer = 0;
  this.coinsRun = 0;
  this.boneGot = false;
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
  return c === T_SOLID || c === T_CRATE || c === T_SPRING;
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
      if (!this.isSolidCode(this.tileAt(tx, ty))) continue;
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
      if (this.isSolidCode(code)) {
        if (e.vy > 0) {
          e.y = ty * TILE - e.h; e.vy = 0; e.grounded = true;
          if (code === T_SPRING && e === this.player) {
            e.vy = -8.7; e.grounded = false;
            Sfx.spring(); this.puff(e.x + e.w / 2, e.y + e.h, '#ff8a92', 6);
          }
        } else if (e.vy < 0) { e.y = (ty + 1) * TILE; e.vy = 0; }
        y1 = Math.floor(e.y / TILE); y2 = Math.floor((e.y + e.h - 1) / TILE);
      } else if (code === T_PLAT && !flyer && e.vy >= 0) {
        var top = ty * TILE;
        var dropping = (e === this.player) && Input.down('down');
        if (!dropping && prevBottom <= top + 2 && e.y + e.h >= top) {
          e.y = top - e.h; e.vy = 0; e.grounded = true;
          y1 = Math.floor(e.y / TILE); y2 = Math.floor((e.y + e.h - 1) / TILE);
        }
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
  if (e.grounded && !Util.aabb(e, { x: e.x, y: e.y, w: e.w, h: e.h + 0 })) e.riding = null;
};

/* ---------------- spawning helpers ---------------- */
World.prototype.puff = function (x, y, color, n) {
  for (var i = 0; i < n; i++) {
    this.parts.push(new Particle(x, y, Util.rand(-1.5, 1.5), Util.rand(-1.8, 0.4),
      Util.randInt(14, 30), color, Math.random() < 0.3 ? 2 : 1));
  }
};
World.prototype.shake = function (n) { this.shakeAmt = Math.max(this.shakeAmt, n); };
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
World.prototype.breakCrate = function (tx, ty) {
  if (this.tileAt(tx, ty) !== T_CRATE) return;
  this.tiles[ty][tx] = T_EMPTY;
  var cx = tx * TILE + 8, cy = ty * TILE + 8;
  Sfx.kill(); this.shake(3);
  for (var i = 0; i < 10; i++) {
    this.parts.push(new Particle(cx, cy, Util.rand(-2, 2), Util.rand(-2.6, 0.4),
      Util.randInt(18, 34), Util.pick(['#a97a45', '#c99a5f', '#5e3f21']), 2));
  }
  var n = Util.randInt(2, 4);
  for (var k = 0; k < n; k++) this.dropCoin(cx, cy);
};

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

  if (this.hitStop > 0) { this.hitStop--; return; }
  if (this.bannerTimer > 0) this.bannerTimer--;
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

    /* --- the swing --- */
    var hb = p.hitbox();
    if (hb) {
      for (i = 0; i < this.enemies.length; i++) {
        e = this.enemies[i];
        if (e.dead || p.attackHit.indexOf(e) >= 0) continue;
        if (Util.aabb(hb, e)) {
          p.attackHit.push(e);
          e.hurt(p.damage(), p.facing, this);
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
      /* crates in the swing arc */
      var tx0 = Math.floor(hb.x / TILE), tx1 = Math.floor((hb.x + hb.w) / TILE);
      var ty0 = Math.floor(hb.y / TILE), ty1 = Math.floor((hb.y + hb.h) / TILE);
      for (var ty = ty0; ty <= ty1; ty++) {
        for (var tx = tx0; tx <= tx1; tx++) if (this.tileAt(tx, ty) === T_CRATE) this.breakCrate(tx, ty);
      }
      /* deflect incoming shots */
      for (i = 0; i < this.shots.length; i++) {
        var s = this.shots[i];
        if (s.kind !== 'shock' && Util.aabb(hb, s)) {
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
    if (this.state === 'play' && !p.dead && Util.aabb(p, sh)) {
      p.hurt(this, sh.x + sh.w / 2);
      if (sh.kind !== 'shock') sh.dead = true;
    }
    if (sh.dead) this.shots.splice(i, 1);
  }

  /* --- pickups --- */
  for (i = this.pickups.length - 1; i >= 0; i--) {
    var pk = this.pickups[i];
    pk.update(this);
    if (!p.dead && Util.aabb(p, pk)) {
      if (pk.kind === 'coin') {
        var val = Save.has('lucky') ? 2 : 1;
        this.coinsRun += val; Save.addCoins(val);
        Sfx.coin();
      } else if (pk.kind === 'bone') {
        this.boneGot = true;
        Sfx.bone();
        this.texts.push(new FloatText(pk.x - 10, pk.y - 8, 'GOLDEN BONE', '#ffd75e'));
        this.puff(pk.x + 5, pk.y + 3, '#ffd75e', 14);
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
  var tx, ty;

  for (tx = 1; tx < this.cols - 1; tx++) {
    for (ty = 1; ty < this.rows; ty++) {
      var c = this.tileAt(tx, ty);
      if (c === T_PLAT && this.tileAt(tx, ty + 1) === T_EMPTY && rnd() < 0.20) {
        vines.push({ x: tx * TILE + 3 + Math.floor(rnd() * 9), y: ty * TILE + 6,
                     len: 5 + Math.floor(rnd() * 11), seed: rnd() * 6 });
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
  return { props: props, vines: vines, torches: torches, banners: banners };
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
      if (c === T_SOLID) {
        /* autotile: body or dressed cap, then trim on every exposed face */
        var openUp = !this.isSolidCode(this.tileAt(tx, ty - 1));
        g.drawImage(openUp ? Art.variant(ts.cap, tx, ty) : Art.variant(ts.solid, tx, ty), dx, dy);
        var dep = this.depth[ty][tx];
        if (dep > 0) {
          g.fillStyle = 'rgba(6,4,12,' + Math.min(0.62, dep * 0.17) + ')';
          g.fillRect(dx, dy, TILE, TILE);
        }
        if (!this.isSolidCode(this.tileAt(tx - 1, ty))) g.drawImage(ts.trimL[ty % 2], dx, dy);
        if (!this.isSolidCode(this.tileAt(tx + 1, ty))) g.drawImage(ts.trimR[ty % 2], dx, dy);
        if (!this.isSolidCode(this.tileAt(tx, ty + 1))) g.drawImage(ts.trimB, dx, dy);
      } else if (c === T_PLAT) {
        var pl = this.tileAt(tx - 1, ty) === T_PLAT, pr = this.tileAt(tx + 1, ty) === T_PLAT;
        g.drawImage(!pl ? ts.platL : (!pr ? ts.platR : ts.platM), dx, dy);
      }
      else if (c === T_SPIKE) g.drawImage(ts.spike, dx, dy);
      else if (c === T_HAZ) g.drawImage(ts.hazard[Math.floor(this.t / 12) % 3], dx, dy);
      else if (c === T_CRATE) g.drawImage(ts.crate, dx, dy);
      else if (c === T_SPRING) g.drawImage(ts.spring, dx, dy);
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

  for (i = 0; i < this.pickups.length; i++) this.pickups[i].draw(g, cam);
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
  /* coins */
  g.drawImage(Art.COIN, VIEW_W - 52, 5);
  Text.shadow(g, String(Save.get().coins), VIEW_W - 42, 6, '#ffe27a', 1);
  /* bone token */
  g.drawImage(this.boneGot ? Art.BONE_GOLD : Art.BONE_GREY, VIEW_W - 16, 6);

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

  if (this.state === 'dying') {
    g.fillStyle = 'rgba(10,6,14,' + Math.min(0.75, (96 - this.stateTimer) / 96) + ')';
    g.fillRect(0, 0, VIEW_W, VIEW_H);
  }
};
