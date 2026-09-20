/* Actors: the hero, the beasts, the bosses and everything they throw. */
var TILE = 16;
var GRAV = 0.62;
var MAXFALL = 8.0;

/* Jump feel. A tap gives a real jump that clears a two-tile ledge on its
   own; holding adds lift for a few frames for roughly half again the
   height. Tuned by simulation - see tools/reach.js, which reads these. */
var RUN_SPEED = 1.75;
var RUN_SPEED_SWIFT = 2.2;
var JUMP_IMPULSE = 6.8;      /* tap  -> 34px, about 2.4x the hero */
var JUMP_LIFT = 0.16;        /* added per frame while held */
var JUMP_LIFT_FRAMES = 10;   /* hold -> 46px (2.8 tiles), 26f airtime */
var DBL_JUMP_IMPULSE = 6.2;
var DBL_LIFT_FRAMES = 6;     /* shorter hold on the air jump, so it snaps */
var BASE_JUMPS = 2;          /* ground jump + one in mid-air, always */
var ATTACK_FRAMES = 17;

/* ------------------------------------------------------------------ */
function Particle(x, y, vx, vy, life, color, size) {
  this.x = x; this.y = y; this.vx = vx; this.vy = vy;
  this.life = life; this.max = life; this.color = color; this.size = size || 1;
  this.grav = 0.12;
}
Particle.prototype.update = function () {
  this.x += this.vx; this.y += this.vy;
  this.vy += this.grav; this.vx *= 0.96;
  this.life--;
};
Particle.prototype.draw = function (g, cam) {
  g.globalAlpha = Math.max(0, this.life / this.max);
  g.fillStyle = this.color;
  g.fillRect(Math.round(this.x - cam.x), Math.round(this.y - cam.y), this.size, this.size);
  g.globalAlpha = 1;
};

function FloatText(x, y, text, color) {
  this.x = x; this.y = y; this.text = text; this.color = color || '#ffe27a';
  this.life = 46;
}
FloatText.prototype.update = function () { this.y -= 0.35; this.life--; };
FloatText.prototype.draw = function (g, cam) {
  g.globalAlpha = Math.min(1, this.life / 20);
  Text.draw(g, this.text, Math.round(this.x - cam.x), Math.round(this.y - cam.y), this.color);
  g.globalAlpha = 1;
};

/* ------------------------------------------------------------------ */
function Pickup(x, y, kind) {
  this.x = x; this.y = y; this.kind = kind;
  this.w = 7; this.h = 7;
  this.grade = 'shard';
  this.vx = 0; this.vy = 0;
  this.t = Math.random() * 6.28;
  this.dead = false;
  this.loose = false;   /* dropped from a crate: obeys gravity */
  this.magnetized = false;
}
Pickup.prototype.update = function (w) {
  this.t += 0.12;
  if (this.loose) {
    this.vy = Math.min(this.vy + GRAV * 0.6, 4);
    w.moveActor(this, true);
    if (this.grounded) { this.vx *= 0.82; this.vy = 0; }
  }
  if (this.kind === 'coin' && Save.has('magnet')) {
    var p = w.player;
    var d = Util.dist(this.x, this.y, p.x, p.y);
    if (d < 62) {
      this.magnetized = true;
      var a = Math.atan2(p.y + p.h / 2 - this.y, p.x + p.w / 2 - this.x);
      this.x += Math.cos(a) * 1.9;
      this.y += Math.sin(a) * 1.9;
    }
  }
};
Pickup.prototype.draw = function (g, cam) {
  var bob = this.loose && !this.grounded ? 0 : Math.round(Math.sin(this.t) * 1.4);
  var sx = Math.round(this.x - cam.x), sy = Math.round(this.y - cam.y + bob);
  if (this.kind === 'coin') g.drawImage(Art.COIN, sx, sy);
  else if (this.kind === 'gem') {
    var img = Art.GEMS[this.grade] || Art.GEMS.shard;
    g.save();
    g.globalAlpha = 0.18 + 0.16 * Math.sin(this.t * 1.1);
    g.fillStyle = this.grade === 'crown' ? '#ffc8f4'
                : (this.grade === 'jewel' ? '#a8ffd0' : '#c8f4ff');
    g.fillRect(sx - 2, sy - 2, img.width + 4, img.height + 4);
    g.restore();
    g.drawImage(img, sx, sy);
  } else if (this.kind === 'orb') {
    g.save();
    g.globalAlpha = 0.25 + 0.2 * Math.sin(this.t * 1.4);
    g.fillStyle = '#b9a0ff';
    g.fillRect(sx - 3, sy - 3, this.w + 6, this.h + 6);
    g.restore();
    g.drawImage(Art.ORB, sx, sy);
  } else if (this.kind === 'vessel') {
    g.save();
    g.globalAlpha = 0.22 + 0.16 * Math.sin(this.t * 0.9);
    g.fillStyle = '#ff8a92';
    g.fillRect(sx - 3, sy - 3, this.w + 6, this.h + 6);
    g.restore();
    g.drawImage(Art.HEART_PIECE, sx, sy);
  } else g.drawImage(Art.MEAT, sx, sy);
};

/* ------------------------------------------------------------------
   A strongbox walled up in a secret chamber. Break it open with the
   blade, or just bump into it.
------------------------------------------------------------------ */
function Chest(x, y) {
  this.x = x; this.y = y;
  this.w = 14; this.h = 13;
  this.open = false;
  this.t = 0;
  this.hit = 0;            /* frames of recoil after a swing lands */
  this.nudge = 0;          /* lid rattle while you stand against it */
  this.dead = false;
  /* What it holds is set from the stage's plan when the level loads.
     'gems' is only ever the fallback for a prize already yours. */
  this.prize = 'gems';
}

/* Spill a handful of gems, the consolation prize. */
Chest.prototype.spillGems = function (w) {
  var cx = this.x + this.w / 2, cy = this.y + 2;
  var grades = ['crown', 'jewel', 'jewel', 'shard'];
  for (var gi = 0; gi < grades.length; gi++) {
    var gem = new Pickup(cx - 4 + (gi - 1.5) * 9, cy - 6, 'gem');
    gem.grade = grades[gi];
    gem.loose = true;
    gem.vx = (gi - 1.5) * 0.7; gem.vy = Util.rand(-3.0, -2.0);
    w.pickups.push(gem);
  }
};

Chest.prototype.pop = function (w) {
  if (this.open) return;
  this.open = true;
  Sfx.bone();
  w.shake(4);
  w.chestsGot++;
  var cx = this.x + this.w / 2, cy = this.y + 2;
  for (var j = 0; j < 14; j++) {
    w.parts.push(new Particle(cx, cy, Util.rand(-2, 2), Util.rand(-3.2, -0.6),
      Util.randInt(20, 40), Util.pick(['#e8c45c', '#fff3bc', '#c08a42']), 2));
  }

  var spec = this.prize || 'gems';
  var got = null;

  if (spec === 'vessel') {
    /* A quarter of a heart. Four of them, four stages apart, make one. */
    var total = Save.addHeartPiece(w.def.id);
    if (total > 0) {
      var part = total % 4;
      w.pickups.push(new Pickup(cx - 3, cy - 10, 'vessel'));
      got = { title: 'A VESSEL PIECE', color: '#ff8a92', lines: [
        part === 0 ? 'THAT IS FOUR - A WHOLE NEW HEART'
                   : 'PIECE ' + part + ' OF 4',
        part === 0 ? 'YOUR MAXIMUM HEALTH IS UP BY ONE'
                   : 'FOUR MAKE A HEART. THEY ARE STAGES APART.'
      ] };
      Sfx.fanfare();
    }
  } else if (spec.indexOf('blade:') === 0 || spec.indexOf('relic:') === 0) {
    got = Save.grant(spec);
    if (got) Sfx.fanfare();
  } else if (spec === 'outfit') {
    var prize = Save.lockedOutfit();
    if (prize) {
      Save.unlockOutfit(prize);
      got = { title: 'AN OUTFIT', color: '#a8ffd0', lines: [
        Art.OUTFITS[prize].name.toUpperCase(),
        'WEAR IT AT THE TRADING POST'
      ] };
    }
  } else if (spec === 'gems') {
    this.spillGems(w);
    got = { title: 'A HOARD OF GEMS', color: '#ffc8f4', lines: [
      'CROWN 10   JEWEL 5   SHARD 1',
      'GEMS COUNT TOWARD THE STAGE TALLY'
    ] };
  } else if (spec === 'coins') {
    for (var i = 0; i < 16; i++) w.dropCoin(cx, cy);
    got = { title: 'A PURSE', color: '#e8c45c', lines: [
      '16 COINS', 'SPEND THEM AT THE TRADING POST'
    ] };
  }

  /* Whatever was planned is already yours - so it pays out instead. */
  if (!got) {
    this.spillGems(w);
    got = { title: 'A HOARD OF GEMS', color: '#ffc8f4', lines: [
      'CROWN 10   JEWEL 5   SHARD 1',
      'THE PRIZE HERE WAS ALREADY YOURS'
    ] };
  }
  w.showPopup(got.title, got.lines, got.color);
};
Chest.prototype.update = function (w) {
  this.t++;
  if (this.hit > 0) this.hit--;
  /* A chest only ever gives way to the blade - walking into it does
     nothing but rattle the lid. */
  if (!this.open && w.uncovered(this) && Util.aabb(this, w.player)) this.nudge = 12;
  if (this.nudge > 0) this.nudge--;
};
Chest.prototype.draw = function (g, cam) {
  var img = this.open ? Art.CHEST_OPEN : Art.CHEST;
  var bob = this.open ? 0 : Math.round(Math.sin(this.t * 0.06) * 0.5);
  /* the lid jumps when you lean on it, so "hit me" reads without words */
  var jx = 0;
  if (this.nudge > 0) jx = Math.round(Math.sin(this.nudge * 1.1) * 1.2);
  if (this.hit > 0) { jx = Math.round(Math.sin(this.hit * 1.9) * 2); bob -= 1; }
  g.drawImage(img, Math.round(this.x - cam.x) + jx, Math.round(this.y - cam.y + bob));
  if (!this.open) {
    g.save();
    g.globalAlpha = 0.18 + 0.12 * Math.sin(this.t * 0.08);
    g.fillStyle = '#e8c45c';
    g.fillRect(Math.round(this.x - cam.x) + 5, Math.round(this.y - cam.y) + 6, 4, 3);
    g.restore();
  }
};

/* ------------------------------------------------------------------ */
function Shot(x, y, vx, vy, kind, friendly) {
  this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.kind = kind;
  this.friendly = !!friendly;
  this.dmg = 1;
  this.pierce = false;
  this.hits = [];
  this.w = (kind === 'shock' || kind === 'flame') ? 10
         : (kind === 'bolt' ? 14 : (kind === 'crossw' ? 16 : 4));
  this.h = (kind === 'shock' || kind === 'flame') ? 10
         : (kind === 'bolt' ? 8 : (kind === 'crossw' ? 22 : 4));
  this.life = kind === 'shock' ? 150 : 260;
  this.dead = false;
  this.t = 0;
}
Shot.prototype.update = function (w) {
  this.t++;
  this.x += this.vx; this.y += this.vy;
  if (this.kind === 'sonic') this.vy += 0.02;
  if (--this.life <= 0) this.dead = true;
  if (this.kind === 'shock') {
    /* hugs the floor; dies at a wall */
    if (w.solidAt(this.x + (this.vx > 0 ? this.w + 1 : -1), this.y + this.h - 2)) this.dead = true;
    while (!w.solidAt(this.x + this.w / 2, this.y + this.h + 1) && this.life > 0) {
      this.y += 1;
      if (this.y > w.pixelH) { this.dead = true; break; }
    }
  } else if (w.solidAt(this.x + this.w / 2, this.y + this.h / 2)) {
    if (!this.pierce) {
      this.dead = true;
      w.puff(this.x, this.y, this.friendly ? '#ffd08a' : '#c9a2d8', 4);
    }
  }
};
Shot.prototype.draw = function (g, cam) {
  var sx = Math.round(this.x - cam.x), sy = Math.round(this.y - cam.y);
  if (this.kind === 'thorn') {
    g.save(); g.translate(sx + 2, sy + 2); g.rotate(this.t * 0.3);
    g.drawImage(Art.THORNBALL, -2, -2); g.restore();
  } else if (this.kind === 'sonic') {
    g.fillStyle = '#c9a2f0';
    g.fillRect(sx, sy + 1, 4, 2); g.fillRect(sx + 1, sy, 2, 4);
  } else if (this.kind === 'flame') {
    var fh = 8 + Math.round(Math.sin(this.t * 0.5) * 2);
    g.fillStyle = 'rgba(255,140,50,.85)'; g.fillRect(sx, sy + 10 - fh, 10, fh);
    g.fillStyle = '#ffd08a'; g.fillRect(sx + 1, sy + 10 - fh, 8, 2);
    g.fillStyle = '#fff3bc'; g.fillRect(sx + 3, sy + 10 - fh, 4, 1);
  } else if (this.kind === 'bolt') {
    g.fillStyle = 'rgba(180,230,255,.9)'; g.fillRect(sx, sy + 2, 14, 4);
    g.fillStyle = '#eafdff'; g.fillRect(sx, sy + 3, 14, 1);
    g.fillStyle = 'rgba(140,200,255,.5)'; g.fillRect(sx - 4, sy + 1, 6, 6);
  } else if (this.kind === 'crossw') {
    /* the thrown cross: two bright strokes, thinning as it goes */
    var cf2 = Math.max(0, Math.min(1, this.life / 24));
    g.save();
    g.translate(sx + this.w / 2, sy + this.h / 2);
    g.globalAlpha = cf2;
    g.strokeStyle = '#eef4fa';
    g.lineWidth = 3;
    var a2 = this.w / 2 + 3;
    g.beginPath();
    g.moveTo(-a2, -a2); g.lineTo(a2, a2);
    g.moveTo(a2, -a2); g.lineTo(-a2, a2);
    g.stroke();
    g.globalAlpha = cf2 * 0.45;
    g.strokeStyle = '#9fd0ff'; g.lineWidth = 6;
    g.stroke();
    g.restore();
  } else if (this.kind === 'splinter') {
    g.fillStyle = '#c9a273'; g.fillRect(sx, sy + 1, 5, 2);
    g.fillStyle = '#efe7d2'; g.fillRect(sx + 3, sy + 1, 2, 1);
  } else {
    var h = 6 + Math.round(Math.sin(this.t * 0.4) * 2);
    g.fillStyle = 'rgba(255,190,120,.85)';
    g.fillRect(sx, sy + this.h - h, this.w, h);
    g.fillStyle = '#fff0c0';
    g.fillRect(sx, sy + this.h - h, this.w, 1);
  }
};

/* ------------------------------------------------------------------ */
function Enemy(x, y, type, world) {
  this.x = x; this.y = y; this.type = type;
  this.vx = 0; this.vy = 0;
  this.facing = -1;
  this.dead = false;
  this.flash = 0;
  this.t = Math.random() * 100;
  this.grounded = false;
  this.hitWall = false;
  this.state = 'walk';
  this.timer = 0;
  var bonus = Math.max(0, (world || 1) - 1);
  if (type === 'grub') {
    this.w = 12; this.h = 10; this.hp = 2 + bonus; this.speed = 0.32;
    this.ox = -1; this.oy = -2;
  } else if (type === 'bat') {
    this.w = 12; this.h = 8; this.hp = 2 + bonus; this.speed = 1.15;
    this.ox = -2; this.oy = -2; this.homeY = y;
  } else if (type === 'hound') {
    this.w = 14; this.h = 13; this.hp = 3 + bonus; this.speed = 0.5;
    this.ox = -2; this.oy = -1;
  } else { /* thorn turret */
    this.w = 12; this.h = 14; this.hp = 3 + bonus; this.speed = 0;
    this.ox = -2; this.oy = -2; this.timer = 40 + Math.floor(Math.random() * 60);
  }
  this.maxHp = this.hp;
}
Enemy.prototype.hurt = function (dmg, dir, w) {
  this.hp -= dmg;
  this.flash = 8;
  if (this.type !== 'thorn') { this.vx = dir * 1.4; this.vy = -1.2; }
  if (this.hp <= 0) {
    this.dead = true;
    Sfx.kill();
    w.puff(this.x + this.w / 2, this.y + this.h / 2, '#ffd75e', 10);
    w.shake(3);
    var n = this.type === 'hound' ? 3 : (this.type === 'thorn' ? 3 : 2);
    for (var ci = 0; ci < n; ci++) w.dropCoin(this.x + this.w / 2, this.y + this.h / 2);
    /* occasionally an orb, so the special stays a treat */
    if (Math.random() < 0.12 && w.player.charges <= 0) {
      w.dropOrb(this.x + this.w / 2, this.y + this.h / 2);
    }
    var n = this.type === 'hound' ? 3 : (this.type === 'thorn' ? 3 : 2);
    for (var i = 0; i < n; i++) w.dropCoin(this.x + this.w / 2, this.y + this.h / 2);
  } else {
    Sfx.hit();
    w.puff(this.x + this.w / 2, this.y + this.h / 2, '#ffffff', 4);
  }
};
Enemy.prototype.update = function (w) {
  this.t++;
  if (this.flash > 0) this.flash--;
  var p = w.player;

  if (this.type === 'grub') {
    this.vx = Util.approach(this.vx, this.facing * this.speed, 0.08);
    this.vy = Math.min(this.vy + GRAV, MAXFALL);
    w.moveActor(this);
    if (this.hitWall) this.facing *= -1;
    /* turn at a ledge */
    if (this.grounded) {
      var ahead = this.x + (this.facing > 0 ? this.w + 2 : -2);
      if (!w.solidFor(this, w.codeAtPx(ahead, this.y + this.h + 2))) this.facing *= -1;
    }
  } else if (this.type === 'bat') {
    var near = Math.abs(p.x - this.x) < 104 && Math.abs(p.y - this.y) < 84;
    if (near) {
      var ax = p.x + p.w / 2 - (this.x + this.w / 2);
      var ay = p.y + p.h / 2 - (this.y + this.h / 2);
      var m = Math.max(1, Math.sqrt(ax * ax + ay * ay));
      this.vx = Util.approach(this.vx, (ax / m) * this.speed, 0.05);
      this.vy = Util.approach(this.vy, (ay / m) * this.speed, 0.05);
    } else {
      this.vx = Util.approach(this.vx, Math.sin(this.t * 0.02) * 0.55, 0.04);
      this.vy = Util.approach(this.vy, (this.homeY - this.y) * 0.03 + Math.sin(this.t * 0.07) * 0.35, 0.05);
    }
    w.moveActor(this, false, true);
    if (this.hitWall) this.vx *= -1;
    if (this.vx !== 0) this.facing = this.vx > 0 ? 1 : -1;
  } else if (this.type === 'hound') {
    this.vy = Math.min(this.vy + GRAV, MAXFALL);
    if (this.state === 'walk') {
      this.vx = Util.approach(this.vx, this.facing * this.speed, 0.08);
      var close = Math.abs(p.x - this.x) < 78 && Math.abs(p.y - this.y) < 40;
      if (close && this.grounded) {
        this.facing = p.x > this.x ? 1 : -1;
        this.state = 'crouch'; this.timer = 22;
      }
    } else if (this.state === 'crouch') {
      this.vx = Util.approach(this.vx, 0, 0.2);
      if (--this.timer <= 0) {
        this.state = 'leap';
        this.vx = this.facing * 2.1; this.vy = -4.7;
        Sfx.bark();
      }
    } else { /* leap */
      if (this.grounded && this.vy >= 0) {
        this.state = 'walk'; this.timer = 0; this.vx *= 0.3;
      }
    }
    w.moveActor(this);
    if (this.hitWall && this.state === 'walk') this.facing *= -1;
    if (this.grounded && this.state === 'walk') {
      var a2 = this.x + (this.facing > 0 ? this.w + 2 : -2);
      if (!w.solidFor(this, w.codeAtPx(a2, this.y + this.h + 2))) this.facing *= -1;
    }
  } else { /* thorn turret */
    this.facing = p.x + p.w / 2 > this.x + this.w / 2 ? 1 : -1;
    this.vy = Math.min(this.vy + GRAV, MAXFALL);
    w.moveActor(this);
    if (--this.timer <= 0) {
      this.timer = 104;
      if (Math.abs(p.x - this.x) < 150) {
        w.shots.push(new Shot(this.x + this.w / 2 - 2, this.y + 5, this.facing * 1.5, 0, 'thorn'));
        Sfx.swing();
      }
    }
  }
};
Enemy.prototype.draw = function (g, cam) {
  var sx = Math.round(this.x - cam.x + this.ox), sy = Math.round(this.y - cam.y + this.oy);
  var img, flashImg;
  if (this.type === 'grub') {
    img = this.facing > 0 ? Art.GRUB_L : Art.GRUB;
    flashImg = Art.GRUB_FLASH;
    sy += Math.round(Math.sin(this.t * 0.2) * 0.8);
  } else if (this.type === 'bat') {
    img = Art.BATLING[Math.floor(this.t / 7) % 2];
    flashImg = Art.BAT_FLASH;
  } else if (this.type === 'hound') {
    var fr = this.facing > 0 ? Art.hound.right : Art.hound.left;
    if (this.state === 'crouch') img = fr.sit;
    else if (this.state === 'leap') img = fr.jump;
    else if (Math.abs(this.vx) > 0.15) img = fr.run[Math.floor(this.t / 7) % 4];
    else img = fr.idle;
    flashImg = this.facing > 0 ? Art.hound.flash.right : Art.hound.flash.left;
    sx -= 2; sy -= 1;
  } else {
    img = this.facing > 0 ? Art.THORN : Art.THORN_L;
    flashImg = Art.THORN_FLASH;
  }
  g.drawImage(img, sx, sy);
  if (this.flash > 0 && flashImg) {
    g.globalAlpha = 0.8;
    g.drawImage(flashImg, sx, sy);
    g.globalAlpha = 1;
  }
};

/* ------------------------------------------------------------------ */
function Boss(x, y, kind) {
  this.kind = kind;
  this.x = x; this.y = y;
  this.vx = 0; this.vy = 0;
  this.facing = -1;
  this.dead = false;
  this.flash = 0;
  this.t = 0;
  this.state = 'wait';
  this.timer = 70;
  this.grounded = false;
  this.hitWall = false;
  this.phase = 1;
  this.spawned = false;
  this.hurtLock = 0;
  if (kind === 'boar') { this.w = 44; this.h = 30; this.hp = 16; this.name = 'GRUMBLEGUT'; }
  else if (kind === 'gloomwing') { this.w = 42; this.h = 26; this.hp = 20; this.name = 'GLOOMWING'; this.homeY = y; }
  else { this.w = 38; this.h = 38; this.hp = 28; this.name = 'THE KENNEL KING'; }
  this.maxHp = this.hp;
  this.y = y - this.h + TILE;
}
Boss.prototype.contactHarmless = function () {
  return this.state === 'stun' || this.state === 'dying';
};
Boss.prototype.hurt = function (dmg, dir, w) {
  if (this.hurtLock > 0 || this.state === 'dying') return;
  this.hurtLock = 10;
  this.hp -= dmg;
  this.flash = 9;
  Sfx.hit();
  w.puff(this.x + this.w / 2, this.y + this.h / 2, '#ffffff', 6);
  if (this.hp <= 0) {
    this.hp = 0;
    this.state = 'dying'; this.timer = 110;
    Sfx.crash();
    w.shake(9);
  } else if (this.hp <= this.maxHp / 2 && this.phase === 1) {
    this.phase = 2;
    w.shake(5);
  }
};
Boss.prototype.update = function (w) {
  this.t++;
  if (this.flash > 0) this.flash--;
  if (this.hurtLock > 0) this.hurtLock--;
  var p = w.player;

  if (this.state === 'dying') {
    this.vx = 0;
    this.vy = Math.min(this.vy + GRAV, MAXFALL);
    if (this.kind !== 'gloomwing') w.moveActor(this);
    else this.y += 0.6;
    if (this.t % 6 === 0) {
      w.puff(this.x + Util.rand(4, this.w - 4), this.y + Util.rand(4, this.h - 4), '#ffb45e', 5);
      Sfx.hit();
    }
    if (--this.timer <= 0) { this.dead = true; w.bossDefeated(); }
    return;
  }

  if (this.kind === 'boar') this.updateBoar(w, p);
  else if (this.kind === 'gloomwing') this.updateWing(w, p);
  else this.updateKing(w, p);
};

Boss.prototype.updateBoar = function (w, p) {
  this.vy = Math.min(this.vy + GRAV, MAXFALL);
  if (this.state === 'wait') {
    this.vx = Util.approach(this.vx, 0, 0.2);
    this.facing = p.x + p.w / 2 > this.x + this.w / 2 ? 1 : -1;
    if (--this.timer <= 0) {
      if (this.phase === 2 && Math.random() < 0.45) { this.state = 'slam'; this.vy = -7.2; this.timer = 0; }
      else { this.state = 'charge'; this.timer = 190; Sfx.bark(); }
    }
  } else if (this.state === 'charge') {
    this.vx = Util.approach(this.vx, this.facing * (this.phase === 2 ? 3.3 : 2.6), 0.22);
    if (this.t % 5 === 0 && this.grounded) {
      w.puff(this.x + (this.facing > 0 ? 0 : this.w), this.y + this.h - 2, '#b89a72', 3);
    }
    if (this.hitWall) {
      this.state = 'stun'; this.timer = 96;
      this.vx = 0;
      Sfx.crash(); w.shake(8);
      for (var i = 0; i < 12; i++) {
        w.parts.push(new Particle(this.x + (this.facing > 0 ? this.w : 0), this.y + Util.rand(4, this.h),
          Util.rand(-2, 2) - this.facing, Util.rand(-2.5, 0), 34, '#d8c49a', 2));
      }
    } else if (--this.timer <= 0) { this.state = 'wait'; this.timer = 50; }
  } else if (this.state === 'stun') {
    this.vx = Util.approach(this.vx, 0, 0.3);
    if (--this.timer <= 0) { this.state = 'wait'; this.timer = 40; }
  } else if (this.state === 'slam') {
    this.vx = Util.approach(this.vx, (p.x > this.x ? 1 : -1) * 1.3, 0.1);
    if (this.grounded && this.vy >= 0 && this.t % 2 === 0) {
      Sfx.crash(); w.shake(7);
      w.shots.push(new Shot(this.x - 6, this.y + this.h - 12, -2.0, 0, 'shock'));
      w.shots.push(new Shot(this.x + this.w + 2, this.y + this.h - 12, 2.0, 0, 'shock'));
      this.state = 'wait'; this.timer = 60;
      if (!this.spawned && this.phase === 2) {
        this.spawned = true;
        w.spawnEnemy(this.x - 30, this.y, 'grub');
        w.spawnEnemy(this.x + this.w + 20, this.y, 'grub');
      }
    }
  }
  w.moveActor(this);
};

Boss.prototype.updateWing = function (w, p) {
  var cx = this.x + this.w / 2;
  this.facing = p.x + p.w / 2 > cx ? 1 : -1;
  if (this.state === 'wait' || this.state === 'hover') {
    var targetX = p.x + p.w / 2 - this.w / 2;
    this.vx = Util.approach(this.vx, Util.clamp((targetX - this.x) * 0.03, -1.3, 1.3), 0.06);
    this.vy = Util.approach(this.vy, (this.homeY - this.y) * 0.04 + Math.sin(this.t * 0.06) * 0.5, 0.08);
    if (--this.timer <= 0) {
      var r = Math.random();
      if (this.phase === 2 && r < 0.3) { this.state = 'screech'; this.timer = 56; Sfx.bark(); }
      else { this.state = 'dive'; this.timer = 96; Sfx.bark(); }
    }
  } else if (this.state === 'dive') {
    var dx = p.x + p.w / 2 - cx, dy = p.y - this.y;
    var m = Math.max(1, Math.sqrt(dx * dx + dy * dy));
    var sp = this.phase === 2 ? 2.5 : 2.0;
    this.vx = Util.approach(this.vx, (dx / m) * sp, 0.13);
    this.vy = Util.approach(this.vy, (dy / m) * sp, 0.13);
    if (--this.timer <= 0 || w.solidAt(cx, this.y + this.h + 2)) {
      this.state = 'rise'; this.timer = 70;
    }
  } else if (this.state === 'rise') {
    this.vy = Util.approach(this.vy, -1.6, 0.1);
    this.vx = Util.approach(this.vx, 0, 0.08);
    if (this.y <= this.homeY + 6 || --this.timer <= 0) {
      this.state = 'hover'; this.timer = 70;
    }
  } else if (this.state === 'screech') {
    this.vx = Util.approach(this.vx, 0, 0.14);
    this.vy = Util.approach(this.vy, Math.sin(this.t * 0.3) * 0.6, 0.1);
    if (this.timer === 30) {
      for (var a = -1; a <= 1; a++) {
        w.shots.push(new Shot(cx - 2, this.y + this.h, a * 0.9, 1.5, 'sonic'));
      }
      if (!this.spawned) {
        this.spawned = true;
        w.spawnEnemy(this.x - 40, this.homeY + 10, 'bat');
        w.spawnEnemy(this.x + this.w + 30, this.homeY + 10, 'bat');
      }
    }
    if (--this.timer <= 0) { this.state = 'hover'; this.timer = 46; }
  }
  w.moveActor(this, false, true);
  if (this.hitWall) this.vx *= -0.6;
};

Boss.prototype.updateKing = function (w, p) {
  this.vy = Math.min(this.vy + GRAV, MAXFALL);
  var gap = (p.x + p.w / 2) - (this.x + this.w / 2);
  if (this.state !== 'dash') this.facing = gap > 0 ? 1 : -1;

  if (this.state === 'wait') {
    this.vx = Util.approach(this.vx, 0, 0.2);
    if (--this.timer <= 0) {
      var r = Math.random();
      if (Math.abs(gap) < 40) { this.state = 'sweep'; this.timer = 40; }
      else if (r < 0.34) { this.state = 'slam'; this.vy = -6.6; }
      else if (r < 0.62 && this.phase === 2) { this.state = 'dash'; this.timer = 56; Sfx.bark(); }
      else if (r < 0.74 && this.phase === 2 && !this.spawned) {
        this.spawned = true; this.state = 'summon'; this.timer = 50;
      } else { this.state = 'walk'; this.timer = 80; }
    }
  } else if (this.state === 'walk') {
    this.vx = Util.approach(this.vx, this.facing * 0.9, 0.12);
    if (--this.timer <= 0 || Math.abs(gap) < 34) { this.state = 'wait'; this.timer = 26; }
  } else if (this.state === 'sweep') {
    this.vx = Util.approach(this.vx, 0, 0.3);
    if (this.timer === 22) {
      Sfx.swing();
      w.bossMelee = {
        x: this.x + (this.facing > 0 ? this.w - 4 : -26), y: this.y + 10,
        w: 30, h: 20, life: 10
      };
    }
    if (--this.timer <= 0) { this.state = 'wait'; this.timer = 38; }
  } else if (this.state === 'slam') {
    this.vx = Util.approach(this.vx, this.facing * 1.1, 0.1);
    if (this.grounded && this.vy >= 0) {
      Sfx.crash(); w.shake(8);
      w.shots.push(new Shot(this.x - 8, this.y + this.h - 12, -2.2, 0, 'shock'));
      w.shots.push(new Shot(this.x + this.w + 2, this.y + this.h - 12, 2.2, 0, 'shock'));
      this.state = 'wait'; this.timer = 48;
    }
  } else if (this.state === 'dash') {
    this.vx = Util.approach(this.vx, this.facing * 3.4, 0.4);
    if (this.t % 4 === 0) w.puff(this.x + this.w / 2, this.y + this.h - 3, '#b9a2d8', 3);
    if (this.hitWall || --this.timer <= 0) {
      this.state = 'wait'; this.timer = 44;
      if (this.hitWall) { w.shake(5); Sfx.crash(); }
    }
  } else if (this.state === 'summon') {
    this.vx = Util.approach(this.vx, 0, 0.3);
    if (this.timer === 24) {
      w.spawnEnemy(this.x - 34, this.y + 10, 'hound');
      w.spawnEnemy(this.x + this.w + 24, this.y + 10, 'hound');
      Sfx.bark();
    }
    if (--this.timer <= 0) { this.state = 'wait'; this.timer = 40; }
  }
  w.moveActor(this);
};

Boss.prototype.draw = function (g, cam) {
  Art.drawBoss(g, { x: this.x - cam.x, y: this.y - cam.y, w: this.w, h: this.h,
    facing: this.facing, state: this.state, kind: this.kind, flash: this.flash }, this.t);
};

/* ------------------------------------------------------------------ */
function Player(x, y) {
  this.x = x; this.y = y;
  this.w = 12; this.h = 14;
  this.vx = 0; this.vy = 0;
  this.facing = 1;
  this.grounded = false;
  this.hitWall = false;
  this.t = 0;
  this.maxHp = Save.maxHearts();
  this.hp = this.maxHp;
  this.invuln = 0;
  this.attack = 0;        /* countdown while swinging */
  this.attackHit = [];    /* things already struck by this swing */
  this.coyote = 0;
  this.buffer = 0;
  this.jumps = BASE_JUMPS;
  this.dead = false;
  this.runFrame = 0;
  this.landSquash = 0;
  this.lift = 0;
  this.charges = 0;        /* special uses left, from orbs */
  this.maxCharges = 4;
  this.special = false;    /* is this swing a special? */
  this.crossHit = false;   /* iron: the arc lands a second time */
  this.crossAgain = false;
  this.lashHit = false;    /* whip: the arc reaches far further */
  this.fxCross = 0;        /* frames of the drawn X slash */
  this.fxLash = 0;         /* frames of the drawn whip crack */
}
Player.prototype.damage = function () { return Save.bladeDamage(); };
Player.prototype.reach = function () { return Save.bladeReach(); };

Player.prototype.hurt = function (w, fromX) {
  if (this.invuln > 0 || this.dead) return;
  this.hp--;
  this.invuln = Save.has('guard') ? 96 : 66;
  var dir = (this.x + this.w / 2) < fromX ? -1 : 1;
  this.vx = dir * 2.2; this.vy = -2.6;
  Sfx.hurt(); w.shake(5);
  w.puff(this.x + this.w / 2, this.y + this.h / 2, '#e0424f', 8);
  if (this.hp <= 0) { this.hp = 0; this.die(w); }
};
Player.prototype.die = function (w) {
  if (this.dead) return;
  this.dead = true;
  this.vy = -4.5;
  Sfx.dead();
  w.onPlayerDead();
};

Player.prototype.update = function (w) {
  this.t++;
  if (this.invuln > 0) this.invuln--;
  if (this.landSquash > 0) this.landSquash--;

  if (this.dead) {
    this.vy = Math.min(this.vy + GRAV, MAXFALL);
    this.y += this.vy;
    return;
  }

  var ax = Input.axis();          /* -1..1, analog on a touch stick */
  var speed = Save.has('swift') ? RUN_SPEED_SWIFT : RUN_SPEED;
  var accel = this.grounded ? 0.42 : 0.26;

  if (this.attack > 0 && this.grounded) {
    /* planted while swinging on the ground */
    this.vx = Util.approach(this.vx, 0, 0.35);
  } else if (ax !== 0) {
    this.vx = Util.approach(this.vx, ax * speed, accel);
    if (this.attack <= 0) this.facing = ax > 0 ? 1 : -1;
  } else {
    this.vx = Util.approach(this.vx, 0, this.grounded ? 0.4 : 0.12);
  }

  /* jump, with coyote time and an input buffer */
  if (this.grounded) { this.coyote = 7; this.jumps = Save.has('spring') ? BASE_JUMPS + 1 : BASE_JUMPS; }
  else if (this.coyote > 0) this.coyote--;
  if (Input.pressed('jump')) this.buffer = 7;
  else if (this.buffer > 0) this.buffer--;

  if (this.buffer > 0) {
    if (this.coyote > 0 || this.grounded) {
      this.vy = -JUMP_IMPULSE; this.buffer = 0; this.coyote = 0;
      this.jumps = (Save.has('spring') ? BASE_JUMPS + 1 : BASE_JUMPS) - 1;
      this.grounded = false;
      this.lift = JUMP_LIFT_FRAMES;
      Sfx.jump();
      w.puff(this.x + this.w / 2, this.y + this.h, '#e8e0cc', 4);
    } else if (this.jumps > 0) {
      /* Kill any downward speed first. Without this the air jump fights
         whatever fall was already underway and reads as heavy and vague. */
      this.vy = -DBL_JUMP_IMPULSE;
      this.buffer = 0; this.jumps--;
      this.lift = DBL_LIFT_FRAMES;
      Sfx.dbljump();
      for (var i = 0; i < 6; i++) {
        w.parts.push(new Particle(this.x + this.w / 2, this.y + this.h,
          Util.rand(-1.4, 1.4), Util.rand(-0.4, 1.2), 22, '#bfe9ff', 1));
      }
    }
  }
  /* Hold to rise further. Adding lift over several frames gives a smooth
     range between a tap and a full jump, instead of the abrupt velocity
     cut that made taps feel stunted and holds feel floaty. */
  if (Input.down('jump') && this.lift > 0 && this.vy < 0) {
    this.vy -= JUMP_LIFT;
    this.lift--;
  } else {
    this.lift = 0;
  }

  if (Input.pressed('attack') && this.attack <= 0) {
    this.attack = ATTACK_FRAMES;
    this.attackHit = [];
    this.crossHit = false; this.lashHit = false; this.crossAgain = false;
    this.fxCross = 0; this.fxLash = 0;
    /* with the gauge charged, every swing is the blade's special until
       it runs dry */
    this.special = this.charges > 0;
    if (this.special) {
      this.charges--;
      w.fireSpecial(this);
      Sfx.bark();
      w.shake(3);
    } else {
      Sfx.swing();
    }
  }
  if (this.attack > 0) this.attack--;
  if (this.fxCross > 0) this.fxCross--;
  if (this.fxLash > 0) this.fxLash--;

  this.vy = Math.min(this.vy + GRAV, MAXFALL);
  var wasAir = !this.grounded;
  w.moveActor(this);
  if (this.grounded && wasAir && this.vy >= 0) {
    this.landSquash = 5;
    if (Math.abs(this.vy) > 2) { Sfx.land(); w.puff(this.x + this.w / 2, this.y + this.h, '#e8e0cc', 3); }
  }

  if (Math.abs(this.vx) > 0.45 && this.grounded) this.runFrame += Math.abs(this.vx) * 0.11;
  else if (this.grounded) this.runFrame = 0;   /* stand still, legs still */
};

/* Active blade rectangle during the middle of a swing, else null. */
Player.prototype.hitbox = function () {
  if (this.attack > 14 || this.attack < 5) return null;
  var r = this.reach() * (this.special ? 1.6 : 1);
  /* the whip's special snaps right out across the room */
  if (this.lashHit) r = this.reach() * 2.8;
  return {
    x: this.facing > 0 ? this.x + this.w - 3 : this.x - r + 3,
    y: this.y - 2 - (this.special ? 6 : 0),
    w: r, h: this.special ? 29 : 17
  };
};

Player.prototype.bladeAngleAt = function (k) {
  return -1.25 + Util.clamp(k * 1.35, 0, 1) * 2.25;
};
Player.prototype.bladeAngle = function () {
  if (this.attack <= 0) return -0.45;
  return this.bladeAngleAt(1 - (this.attack / ATTACK_FRAMES));
};

Player.prototype.draw = function (g, cam) {
  if (this.invuln > 0 && Math.floor(this.invuln / 4) % 2 === 0 && !this.dead) return;
  var set = this.facing > 0 ? Art.dog.right : Art.dog.left;
  var img;
  if (this.dead) img = set.sit;
  else if (this.attack > 0) img = this.grounded ? set.swing : set.swingAir;
  else if (!this.grounded) img = this.vy < -0.6 ? set.jump : set.fall;
  else if (Math.abs(this.vx) > 0.45) img = set.run[Math.floor(this.runFrame) % 4];
  else img = set.idle;

  var sx = Math.round(this.x - cam.x - 3);
  var sy = Math.round(this.y - cam.y) + (this.landSquash > 2 ? 1 : 0);

  /* blade first when swinging behind, after when in front */
  var bx = sx + (this.facing > 0 ? 13 : 5);
  var by = sy + 8;
  g.drawImage(img, sx, sy);

  /* whatever he is wearing, pinned to the top of his head */
  var fit = Art.OUTFITS[Save.worn()];
  if (fit) {
    var hy = sy - 3 + (this.attack > 0 ? 1 : 0);
    g.drawImage(this.facing > 0 ? fit.right : fit.left,
                sx + (this.facing > 0 ? 8 : 0), hy);
  }

  Art.drawBlade(g, bx, by, this.bladeAngle(), Save.bladeId(), this.facing);

  /* Slash: a crescent that trails the blade through its arc and fades
     out with the swing, rather than a static ring hanging in the air. */
  if (this.attack > 2) {
    var prog = 1 - (this.attack / ATTACK_FRAMES);
    var a1 = this.bladeAngleAt(prog);
    var a0 = this.bladeAngleAt(Math.max(0, prog - 0.34));
    if (a1 - a0 > 0.03) {
      var reach = this.reach() * (this.lashHit ? 2.8 : (this.special ? 1.4 : 1));
      var bl = Save.blade();
      var col = this.special ? '200,170,255'
        : (bl.id === 'storm' ? '210,250,255'
        : (bl.id === 'ember' ? '255,208,138'
        : (bl.id === 'whip' ? '200,255,215' : '255,255,255')));
      var fade = Util.clamp((this.attack - 2) / 9, 0, 1);
      g.save();
      g.translate(bx, by);
      g.scale(this.facing, 1);
      /* two nested wedges: a bright leading edge over a softer body */
      var bands = [[reach * 0.52, reach * 1.0, 0.30], [reach * 0.78, reach * 0.99, 0.55]];
      for (var bI = 0; bI < bands.length; bI++) {
        g.beginPath();
        g.arc(0, 0, bands[bI][1], a0, a1);
        g.arc(0, 0, bands[bI][0], a1, a0, true);
        g.closePath();
        g.fillStyle = 'rgba(' + col + ',' + (bands[bI][2] * fade).toFixed(3) + ')';
        g.fill();
      }
      g.restore();
    }
  }

  /* Iron's cross and the whip's crack land no projectile, so without a
     mark of their own they looked like an ordinary swing. */
  if (this.fxCross > 0) {
    var cf = this.fxCross / 16;
    var ccx = bx + this.facing * 12, ccy = by;
    g.save();
    g.translate(ccx, ccy);
    g.globalAlpha = cf;
    g.strokeStyle = '#eef4fa';
    g.lineWidth = 3 - 2 * (1 - cf);
    var arm = 16 + 10 * (1 - cf);
    g.beginPath();
    g.moveTo(-arm, -arm); g.lineTo(arm, arm);
    g.moveTo(arm, -arm); g.lineTo(-arm, arm);
    g.stroke();
    g.globalAlpha = cf * 0.5;
    g.strokeStyle = '#9fd0ff';
    g.lineWidth = 1;
    g.stroke();
    g.restore();
  }
  if (this.fxLash > 0) {
    var lf = this.fxLash / 14;
    g.save();
    g.globalAlpha = lf;
    g.strokeStyle = '#d8ffe8';
    g.lineWidth = 2;
    g.beginPath();
    var lx0 = bx, ly0 = by;
    for (var li = 0; li <= 12; li++) {
      var tt = li / 12;
      var px2 = lx0 + this.facing * tt * this.reach() * 2.8;
      var py2 = ly0 + Math.sin(tt * 6.0 + (1 - lf) * 5) * 6 * tt;
      if (li === 0) g.moveTo(px2, py2); else g.lineTo(px2, py2);
    }
    g.stroke();
    g.restore();
  }
};
