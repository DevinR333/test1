/* Menus, map screen, shop and result screens. */
var UI = (function () {
  function inRect(p, r) {
    return !!p && p.x >= r.x && p.x <= r.x + r.w && p.y >= r.y && p.y <= r.y + r.h;
  }
  /* Label the controls for whatever is actually in the player's hands. */
  function hint(kb, pad, touch) {
    var m = Input.mode();
    return m === 'touch' ? touch : (m === 'controller' ? pad : kb);
  }
  /* A tappable on-screen button, drawn and hit-tested from one rect. */
  function tapBtn(g, r, label, hot) {
    g.fillStyle = hot ? 'rgba(255,215,94,.18)' : 'rgba(20,16,30,.72)';
    g.fillRect(r.x, r.y, r.w, r.h);
    g.fillStyle = hot ? '#ffd75e' : '#6b5a8a';
    g.fillRect(r.x, r.y, r.w, 1); g.fillRect(r.x, r.y + r.h - 1, r.w, 1);
    g.fillRect(r.x, r.y, 1, r.h); g.fillRect(r.x + r.w - 1, r.y, 1, r.h);
    Text.center(g, label, r.x + r.w / 2, r.y + Math.round((r.h - 7) / 2), '#ffffff', 1);
  }

  /* A plank sign staked in the grass - dark enough to carry pale text. */
  function panel(g, x, y, w, h, fill) {
    g.fillStyle = fill || 'rgba(28,22,15,.96)';
    g.fillRect(x, y, w, h);
    g.fillStyle = '#c08a42'; g.fillRect(x, y, w, 1); g.fillRect(x, y + h - 1, w, 1);
    g.fillRect(x, y, 1, h); g.fillRect(x + w - 1, y, 1, h);
    g.fillStyle = '#6b4a22';
    g.fillRect(x + 1, y + 1, w - 2, 1); g.fillRect(x + 1, y + h - 2, w - 2, 1);
  }
  function dim(g, a) {
    g.fillStyle = 'rgba(8,5,14,' + a + ')';
    g.fillRect(0, 0, VIEW_W, VIEW_H);
  }
  function cursor(g, x, y, t) {
    var off = Math.round(Math.sin(t * 0.14) * 1.4);
    g.fillStyle = '#ffd75e';
    g.fillRect(x + off, y + 1, 2, 5);
    g.fillRect(x + off + 2, y + 2, 2, 3);
    g.fillRect(x + off + 4, y + 3, 1, 1);
  }

  /* ---------------------------------------------------------------
     THE FIELD
     Every menu sits in the same meadow the dog walks through: open sky,
     clouds on the drift, hills falling away, grass underfoot. Laid out
     in fractions of the view so it fills 4:3 and 21:9 alike.
  --------------------------------------------------------------- */
  var SKIES = {
    day:  ['#2f7fc8', '#6db4e4', '#bfe4f4'],
    dusk: ['#2b3f6e', '#8a6aa0', '#e7a377']
  };
  var GRASS = {
    day:  { far: '#6b9e52', mid: '#4f8a3e', near: '#3d7331',
            lip: '#8fd455', dark: '#2c5624', hill: '#7fb06a', haze: '#a8cf9a' },
    dusk: { far: '#4d6b48', mid: '#3a5738', near: '#2d442c',
            lip: '#6d9a58', dark: '#1e2f1f', hill: '#5c7a5c', haze: '#8b9a8c' }
  };
  /* fields are fractions of the view, so nothing is pinned to 400x224 */
  var clouds = [];
  for (var ci = 0; ci < 8; ci++) {
    clouds.push({ x: Math.random(), y: 0.07 + Math.random() * 0.30,
                  s: 0.55 + Math.random() * 1.0, v: 0.00022 + Math.random() * 0.00035 });
  }
  var blooms = [];
  for (var bi = 0; bi < 54; bi++) {
    blooms.push({ x: Math.random(), y: Math.random(), k: Math.floor(Math.random() * 4),
                  p: Math.random() * 6.28 });
  }
  var BLOOM_COL = ['#ffe27a', '#ffb7d8', '#fdfbef', '#d4f07f'];

  function cloud(g, x, y, s) {
    /* four overlapping slabs read as a soft cumulus at this size */
    var w = Math.round(22 * s), h = Math.round(5 * s);
    g.fillStyle = 'rgba(255,255,255,.92)';
    g.fillRect(x, y + h, w, h);
    g.fillRect(x + Math.round(w * 0.18), y, Math.round(w * 0.5), h + 1);
    g.fillRect(x + Math.round(w * 0.58), y + Math.round(h * 0.5), Math.round(w * 0.34), h);
    g.fillStyle = 'rgba(196,224,244,.85)';
    g.fillRect(x, y + h * 2, w, Math.max(1, Math.round(h * 0.5)));
  }
  /* A soft ridge line: same shape every frame, just sampled per column. */
  function ridge(g, baseY, amp, freq, phase, col) {
    for (var x = 0; x < VIEW_W; x++) {
      var h = Math.round(amp * Math.sin(x * freq + phase) + amp * 0.5 * Math.sin(x * freq * 2.3 + phase * 1.7));
      g.fillStyle = col;
      g.fillRect(x, baseY - h, 1, VIEW_H - (baseY - h));
    }
  }
  function backdrop(g, t, mood) {
    var key = (mood === 'dusk') ? 'dusk' : 'day';
    var sky = SKIES[key], gr = GRASS[key];
    var horizon = Math.round(VIEW_H * 0.58);

    var grad = g.createLinearGradient(0, 0, 0, horizon);
    grad.addColorStop(0, sky[0]);
    grad.addColorStop(0.6, sky[1]);
    grad.addColorStop(1, sky[2]);
    g.fillStyle = grad;
    /* paint past the horizon: the ridges below are drawn crest-first and
       must never leave a bare strip of canvas behind them */
    g.fillRect(0, 0, VIEW_W, VIEW_H);

    /* sun: three soft discs, not a stack of squares */
    var sx = Math.round(VIEW_W * 0.80), sy = Math.round(VIEW_H * 0.16);
    function disc(r, col) {
      g.fillStyle = col;
      g.beginPath(); g.arc(sx, sy, r, 0, 6.2832); g.fill();
    }
    disc(15, key === 'dusk' ? 'rgba(255,180,120,.16)' : 'rgba(255,246,200,.16)');
    disc(10, key === 'dusk' ? 'rgba(255,196,146,.24)' : 'rgba(255,250,222,.26)');
    disc(6, key === 'dusk' ? '#ffd9a8' : '#fffbe6');

    for (var i = 0; i < clouds.length; i++) {
      var c = clouds[i];
      var cx = Math.round(((c.x + t * c.v) % 1.25 - 0.14) * VIEW_W);
      cloud(g, cx, Math.round(c.y * VIEW_H), c.s);
    }

    /* two ridges behind the field, then the field itself */
    /* each ridge overlaps the one behind it, so the crests read as depth
       rather than as stacked flat bands */
    var amp = Math.max(5, VIEW_H * 0.035);
    ridge(g, horizon + Math.round(amp * 0.9), amp, 0.017, 0.8, gr.haze);
    ridge(g, horizon + Math.round(amp * 1.7), amp * 1.2, 0.011, 2.4, gr.hill);
    ridge(g, horizon + Math.round(amp * 2.6), amp * 0.8, 0.023, 5.1, gr.far);

    var top = horizon + Math.round(amp * 3.2);
    g.fillStyle = gr.mid;
    g.fillRect(0, top, VIEW_W, VIEW_H - top);
    /* the near field rolls in on a gentle, uneven line rather than a rule */
    var nearY = top + Math.round((VIEW_H - top) * 0.42);
    g.fillStyle = gr.near;
    for (var x3 = 0; x3 < VIEW_W; x3++) {
      var ny = nearY + Math.round(2.5 * Math.sin(x3 * 0.035) + 1.5 * Math.sin(x3 * 0.09 + 1.3));
      g.fillRect(x3, ny, 1, VIEW_H - ny);
    }
    /* light catching the crest of the far field */
    g.fillStyle = gr.lip;
    for (var x2 = 0; x2 < VIEW_W; x2 += 3) g.fillRect(x2, top, 2, 1);

    /* grass blades and flowers, still but for a slow sway */
    var fieldH = VIEW_H - top;
    for (var b = 0; b < blooms.length; b++) {
      var f = blooms[b];
      var bx = Math.round(f.x * VIEW_W);
      var by = top + Math.round(f.y * fieldH);
      var sway = Math.round(Math.sin(t * 0.03 + f.p) * 1.2);
      g.fillStyle = gr.dark;
      g.fillRect(bx, by, 1, 3);
      g.fillRect(bx + sway, by - 2, 1, 2);
      if (f.k < 3 && by > top + 3) {
        g.fillStyle = BLOOM_COL[f.k];
        g.fillRect(bx + sway, by - 3, 1, 1);
      }
    }
  }

  /* ------------------------------------------------ TITLE */
  var title = {
    update: function (G) {
      if (Input.pressed('confirm') || Input.pressed('jump') || Input.pressed('attack')) {
        Sfx.confirm();
        G.go('map');
      }
    },
    draw: function (g, G) {
      backdrop(g, G.t);
      var cx = VIEW_W / 2;
      /* the title block hangs off the top, the dog stands in the grass,
         the hints sit on the bottom edge - all keyed off the view */
      var ty = Math.round(VIEW_H * 0.11);
      Text.centerShadow(g, 'BUDDY', cx, ty, '#ffffff', 3);
      Text.centerShadow(g, 'BLADE', cx, ty + 28, '#ffd75e', 3);
      Text.centerShadow(g, 'A GOOD BOY WITH A VERY SHARP STICK',
        cx, ty + 56, '#f3f8ff', 1);

      /* hero idles on the title, blade out */
      var bob = Math.round(Math.sin(G.t * 0.06) * 2);
      var px = Math.round(cx - 18), py = Math.round(VIEW_H * 0.70) + bob;
      g.save();
      g.imageSmoothingEnabled = false;
      g.drawImage(Art.dog.right.idle, px, py, 36, 28);
      g.restore();
      Art.drawBlade(g, px + 30, py + 17, -0.5 + Math.sin(G.t * 0.05) * 0.12, Save.bladeId(), 1);

      if (Math.floor(G.t / 26) % 2 === 0) {
        Text.centerShadow(g, hint('PRESS ENTER', 'PRESS A', 'TAP TO START'),
          cx, VIEW_H - 32, '#ffffff', 1);
      }
      Text.shadow(g, 'BUILD ' + (window.BUILD_ID || 'DEV'), 6, VIEW_H - 10, '#dfe8d4', 1);
      Text.centerShadow(g, hint('ARROWS MOVE   Z JUMP   X SWING',
                                'STICK MOVE   A JUMP   X SWING',
                                'STICK MOVE   JUMP   SWING'),
        cx, VIEW_H - 16, '#eaf4ff', 1);
    }
  };

  /* ------------------------------------------------ WORLD MAP */
  /* Three world bands, spread down whatever height we were given, so a
     4:3 screen gets taller lanes instead of a hole in the middle. */
  function bandTop() { return Math.round(VIEW_H * 0.16) + 10; }
  function bandGap() {
    var room = (VIEW_H - 48) - bandTop();       /* keep clear of the sign */
    return Util.clamp(Math.round(room / 3), 34, 64);
  }
  function nodePos(i) {
    var w = Math.floor(i / 4), s = i % 4;
    var step = Math.floor((VIEW_W - 104) / 3);
    return { x: 52 + s * step, y: bandTop() + w * bandGap() };
  }
  function nodeRect(i) {
    var p = nodePos(i);
    return { x: p.x - 3, y: p.y - 4, w: 30, h: 22 };
  }
  function mapShopRect() {
    return { x: VIEW_W - 86, y: VIEW_H - 22, w: 78, h: 16 };
  }
  function mapOptRect() {
    return { x: VIEW_W - 170, y: VIEW_H - 22, w: 78, h: 16 };
  }
  function openOptions(G) {
    G.optionsFrom = 'map';
    G.pauseSel = 0;
    G.go('pause');
  }

  var map = {
    update: function (G) {
      var d = Save.get();

      /* touch: tap a stage to pick it, tap it again to go */
      var tap = Input.takeTap();
      if (tap) {
        if (inRect(tap, mapShopRect())) { Sfx.confirm(); G.go('shop'); return; }
        if (inRect(tap, mapOptRect())) { Sfx.confirm(); openOptions(G); return; }
        for (var ti = 0; ti < LEVELS.length && ti < d.unlocked; ti++) {
          if (inRect(tap, nodeRect(ti))) {
            /* one tap plays it - a second tap to confirm just read as
               nothing happening */
            G.sel = ti;
            Sfx.confirm();
            G.startStage(ti);
            return;
          }
        }
      }
      var max = Math.min(d.unlocked, LEVELS.length) - 1;
      var moved = 0;
      if (Input.pressed('right')) moved = 1;
      else if (Input.pressed('left')) moved = -1;
      else if (Input.pressed('down')) moved = 4;
      else if (Input.pressed('up')) moved = -4;
      if (moved) {
        var n = Util.clamp(G.sel + moved, 0, max);
        if (n !== G.sel) { G.sel = n; Sfx.select(); }
        else Sfx.deny();
      }
      if (Input.pressed('confirm') || Input.pressed('jump')) {
        Sfx.confirm(); G.startStage(G.sel);
      }
      if (Input.pressed('attack')) { Sfx.confirm(); G.go('shop'); }
      if (Input.pressed('pause')) { Sfx.confirm(); openOptions(G); }
    },
    draw: function (g, G) {
      backdrop(g, G.t);
      var d = Save.get();
      Text.centerShadow(g, 'THE LONG WALK', VIEW_W / 2, Math.round(VIEW_H * 0.06),
        '#ffffff', 2);

      /* world bands */
      for (var w = 0; w < 3; w++) {
        var y = bandTop() + w * bandGap();
        /* a worn dirt trail cut across the field, one per world */
        var by = y - 15, bh = 36;
        g.fillStyle = 'rgba(58,42,26,.90)';
        g.fillRect(16, by, VIEW_W - 32, bh);
        g.fillStyle = 'rgba(96,72,44,.95)';
        g.fillRect(16, by, VIEW_W - 32, 2);
        g.fillStyle = 'rgba(28,20,12,.95)';
        g.fillRect(16, by + bh - 2, VIEW_W - 32, 2);
        /* grit in the path */
        for (var q = 0; q < 26; q++) {
          var qx = 20 + ((q * 61 + w * 17) % (VIEW_W - 44));
          g.fillStyle = (q % 3) ? 'rgba(122,96,62,.55)' : 'rgba(30,22,14,.55)';
          g.fillRect(qx, by + 5 + ((q * 7 + w * 3) % (bh - 10)), 2, 1);
        }
        Text.shadow(g, WORLDS[w].name, 20, by + 3, '#f0d9a8', 1);
      }
      /* connecting track */
      for (var i = 0; i < LEVELS.length - 1; i++) {
        if (Math.floor(i / 4) !== Math.floor((i + 1) / 4)) continue;
        var a = nodePos(i), b = nodePos(i + 1);
        for (var x = a.x + 12; x < b.x; x += 5) {
          g.fillStyle = (i + 1 < d.unlocked) ? '#f0e2b0' : 'rgba(30,40,24,.45)';
          g.fillRect(x, a.y + 5, 2, 2);
        }
      }
      /* nodes */
      for (i = 0; i < LEVELS.length; i++) {
        var L = LEVELS[i], p = nodePos(i);
        var open = i < d.unlocked;
        var done = !!d.cleared[L.id];
        var isBoss = !!L.boss;
        /* locked stays a dim placeholder; playable is bright and solid;
           finished is green */
        var fill = !open ? 'rgba(24,32,20,.72)'
                 : (done ? '#2f7a4a' : (isBoss ? '#93304a' : '#4a3f7d'));
        var edge = !open ? 'rgba(180,200,170,.35)'
                 : (done ? '#9dffc4' : (isBoss ? '#ff6b7f' : '#cbbcff'));
        g.fillStyle = fill;
        g.fillRect(p.x, p.y, 24, 14);
        if (open) {
          g.fillStyle = 'rgba(255,255,255,.14)';
          g.fillRect(p.x + 1, p.y + 1, 22, 4);
        }
        g.fillStyle = edge;
        g.fillRect(p.x, p.y, 24, 1); g.fillRect(p.x, p.y + 13, 24, 1);
        g.fillRect(p.x, p.y, 1, 14); g.fillRect(p.x + 23, p.y, 1, 14);
        Text.draw(g, open ? L.id : '??', p.x + 5, p.y + 4,
          open ? '#ffffff' : 'rgba(225,235,215,.45)', 1);
        /* a playable stage you have not finished gets a nudge */
        if (open && !done) {
          g.fillStyle = 'rgba(185,168,255,' + (0.30 + 0.25 * Math.sin(G.t * 0.1 + i)) + ')';
          g.fillRect(p.x - 2, p.y - 2, 28, 1);
          g.fillRect(p.x - 2, p.y + 15, 28, 1);
        }
        if (!isBoss && open) {
          var got = d.gems[L.id] || 0;
          var gimg = Art.THEME_GEMS[L.theme] || Art.THEME_GEMS.meadow;
          for (var gI = 0; gI < 3; gI++) {
            if (gI < got) g.drawImage(gimg, p.x + 26 + gI * 8, p.y + 4);
            else { g.fillStyle = '#3a3350'; g.fillRect(p.x + 27 + gI * 8, p.y + 5, 5, 5); }
          }
        }
        if (done) { g.fillStyle = '#7fe0a0'; g.fillRect(p.x + 2, p.y + 2, 2, 2); }
      }
      var sp = nodePos(G.sel);
      cursor(g, sp.x - 10, sp.y + 4, G.t);

      /* selected stage info */
      var L2 = LEVELS[G.sel];
      panel(g, 8, VIEW_H - 44, VIEW_W - 16, 36);
      Text.draw(g, L2.id + ' ' + L2.name, 16, VIEW_H - 38, '#ffd75e', 1);
      Text.draw(g, 'COINS ' + d.coins + '   GEMS ' + Save.gemsFound() + '/' + Save.gemsTotal() +
        '   CHESTS ' + Save.chestsFound(), 16, VIEW_H - 27, '#e2d6c0', 1);
      /* the selected stage glows so a tap-to-confirm is obvious */
      var selR = nodeRect(G.sel);
      g.fillStyle = 'rgba(255,215,94,' + (0.25 + 0.15 * Math.sin(G.t * 0.12)) + ')';
      g.fillRect(selR.x, selR.y, selR.w, 1);
      g.fillRect(selR.x, selR.y + selR.h - 1, selR.w, 1);
      g.fillRect(selR.x, selR.y, 1, selR.h);
      g.fillRect(selR.x + selR.w - 1, selR.y, 1, selR.h);

      tapBtn(g, mapShopRect(), 'SHOP', false);
      tapBtn(g, mapOptRect(), 'OPTIONS', false);
      Text.draw(g, hint('ENTER PLAY', 'A PLAY', 'TAP A STAGE TO PLAY'),
        16, VIEW_H - 16, '#b9ab92', 1);
    }
  };

  /* ------------------------------------------------ SHOP */
  /* one shared tab header, clear of the title */
  function tabRects() {
    var names = ['GEAR', 'BLADES', 'WARDROBE'];
    var txt = names.join('  ');
    var x = Math.round(VIEW_W / 2 - Text.width(txt, 1) / 2);
    var out = [];
    for (var j = 0; j < names.length; j++) {
      var w = Text.width(names[j], 1);
      out.push({ x: x - 3, y: 19, w: w + 6, h: 16, name: names[j] });
      x += w + 12;
    }
    return out;
  }
  function shopBackRect() { return { x: 8, y: VIEW_H - 22, w: 60, h: 16 }; }
  function shopRowRect(i) { return { x: 12, y: 41 + i * 18, w: Math.round(VIEW_W * 0.52), h: 18 }; }

  /* Shared tap handling for all three shop tabs. Returns true when the
     tap was consumed. */
  function shopTap(G, rows, onPick, top) {
    top = top || 0;
    var tap = Input.takeTap();
    if (!tap) return false;
    if (inRect(tap, shopBackRect())) { Sfx.select(); G.go('map'); return true; }
    var tabs = tabRects();
    for (var t = 0; t < tabs.length; t++) {
      if (inRect(tap, tabs[t])) {
        if (G.shopTab !== t) { G.shopTab = t; G.shopSel = 0; Sfx.select(); }
        return true;
      }
    }
    for (var i = 0; i < rows; i++) {
      if (inRect(tap, shopRowRect(i))) {
        var idx = top + i;
        if (G.shopSel === idx) onPick();
        else { G.shopSel = idx; Sfx.select(); }
        return true;
      }
    }
    return false;
  }

  function tabStrip(g, G) {
    var names = ['GEAR', 'BLADES', 'WARDROBE'];
    var txt = '';
    for (var i = 0; i < names.length; i++) txt += (i ? '  ' : '') + names[i];
    var x = Math.round(VIEW_W / 2 - Text.width(txt, 1) / 2);
    Text.draw(g, '<', x - 14, 24, '#5d5478', 1);
    Text.draw(g, '>', x + Text.width(txt, 1) + 8, 24, '#5d5478', 1);
    var cx2 = x;
    for (var j = 0; j < names.length; j++) {
      var on = (G.shopTab || 0) === j;
      if (on) {
        g.fillStyle = 'rgba(255,215,94,.14)';
        g.fillRect(cx2 - 3, 21, Text.width(names[j], 1) + 6, 12);
      }
      Text.draw(g, names[j], cx2, 24, on ? '#ffd75e' : '#6d6488', 1);
      cx2 += Text.width(names[j], 1) + 12;
    }
  }

  function wardrobeList() {
    var out = [{ id: 'none', name: 'No Outfit' }];
    for (var k in Art.OUTFITS) out.push({ id: k, name: Art.OUTFITS[k].name });
    return out;
  }

  var shop = {
    update: function (G) {
      if (G.shopTab === undefined) G.shopTab = 0;
      if (Input.pressed('right')) { G.shopTab = (G.shopTab + 1) % 3; G.shopSel = 0; Sfx.select(); return; }
      if (Input.pressed('left')) { G.shopTab = (G.shopTab + 2) % 3; G.shopSel = 0; Sfx.select(); return; }
      if (G.shopTab === 1) return shop.blades(G);
      if (G.shopTab === 2) return shop.wardrobe(G);
      var list = Save.SHOP;
      var gtop = Util.clamp(G.shopSel - 3, 0, Math.max(0, list.length - 7));
      if (shopTap(G, Math.min(7, list.length), function () {
        var it2 = list[G.shopSel];
        if (Save.buy(it2)) { Sfx.buy(); G.shopMsg = 'BOUGHT ' + it2.name + '!'; }
        else { Sfx.deny(); G.shopMsg = Save.owned(it2) ? 'ALREADY YOURS' : 'NOT ENOUGH COIN'; }
        G.shopMsgT = 100;
      }, gtop)) return;
      if (Input.pressed('down')) { G.shopSel = (G.shopSel + 1) % list.length; Sfx.select(); }
      if (Input.pressed('up')) { G.shopSel = (G.shopSel + list.length - 1) % list.length; Sfx.select(); }
      if (Input.pressed('confirm') || Input.pressed('jump')) {
        var it = list[G.shopSel];
        /* something you already own and can wear: put it on */
        if (it.kind === 'outfit' && Save.owned(it)) {
          if (Save.worn() === it.id) { Save.wear('none'); G.shopMsg = 'TAKEN OFF'; }
          else { Save.wear(it.id); G.shopMsg = 'WEARING ' + it.name.toUpperCase(); }
          G.shopMsgT = 100; Sfx.confirm();
        } else if (Save.buy(it)) {
          Sfx.buy();
          G.shopMsg = 'BOUGHT ' + it.name + '!'; G.shopMsgT = 100;
        } else {
          Sfx.deny();
          G.shopMsg = Save.owned(it) ? 'ALREADY YOURS' :
            (!Save.available(it) ? 'BUY THE EARLIER TIER FIRST' : 'NOT ENOUGH COIN');
          G.shopMsgT = 100;
        }
      }
      if (Input.pressed('pause') || Input.pressed('attack')) { Sfx.select(); G.go('map'); }
    },

    /* Blades work the same way as outfits: once one is yours you can
       switch back to it whenever you like. They are not a one-way ladder -
       a short heavy cleaver stays useful next to a long thin whip. */
    blades: function (G) {
      var list = Art.BLADES;
      if (shopTap(G, list.length, function () {
        var b2 = list[G.shopSel];
        if (Save.ownsBlade(b2.id)) { Save.equipBlade(b2.id); G.shopMsg = 'DRAWING ' + b2.name.toUpperCase(); Sfx.confirm(); }
        else { G.shopMsg = 'BUY IT IN GEAR FIRST'; Sfx.deny(); }
        G.shopMsgT = 100;
      })) return;
      if (Input.pressed('down')) { G.shopSel = (G.shopSel + 1) % list.length; Sfx.select(); }
      if (Input.pressed('up')) { G.shopSel = (G.shopSel + list.length - 1) % list.length; Sfx.select(); }
      if (Input.pressed('confirm') || Input.pressed('jump')) {
        var bl = list[G.shopSel];
        if (Save.ownsBlade(bl.id)) {
          Save.equipBlade(bl.id);
          G.shopMsg = 'DRAWING ' + bl.name.toUpperCase();
          Sfx.confirm();
        } else {
          G.shopMsg = 'BUY IT IN GEAR FIRST';
          Sfx.deny();
        }
        G.shopMsgT = 100;
      }
      if (Input.pressed('pause') || Input.pressed('attack')) { Sfx.select(); G.go('map'); }
    },

    drawBlades: function (g, G) {
      backdrop(g, G.t);
      Text.centerShadow(g, 'BLADES', VIEW_W / 2, 10, '#ffffff', 2);
      tabStrip(g, G);

      var list = Art.BLADES;
      var listW = Math.round(VIEW_W * 0.52), detX = listW + 18;
      panel(g, 10, 38, listW, 142);
      for (var i = 0; i < list.length; i++) {
        var bl = list[i], y = 45 + i * 18;
        var own = Save.ownsBlade(bl.id);
        var held = Save.bladeId() === bl.id;
        if (i === G.shopSel) {
          g.fillStyle = 'rgba(200,220,255,.12)';
          g.fillRect(12, y - 4, listW - 4, 17);
          cursor(g, 14, y, G.t);
        }
        Text.draw(g, own ? bl.name : '? ? ?', 24, y,
          own ? (held ? '#bcd6ff' : '#ffffff') : '#5d5478', 1);
        Text.draw(g, held ? 'DRAWN' : (own ? 'DRAW' : 'LOCKED'), listW - 48, y,
          held ? '#bcd6ff' : (own ? '#7fe0a0' : '#5d5478'), 1);
      }

      panel(g, detX, 38, VIEW_W - detX - 10, 142);
      var sel = list[G.shopSel];
      var ownSel = Save.ownsBlade(sel.id);
      Text.draw(g, ownSel ? sel.name.toUpperCase() : 'NOT YOURS YET', detX + 8, 46,
        ownSel ? '#bcd6ff' : '#7f72a0', 1);
      /* the blade itself, plus what it trades */
      Art.drawBlade(g, detX + 16, 68, -0.35, sel.id, 1);
      Text.draw(g, 'DAMAGE', detX + 8, 78, '#7f72a0', 1);
      for (var d2 = 0; d2 < sel.dmg; d2++) {
        g.fillStyle = '#e0424f'; g.fillRect(detX + 56 + d2 * 6, 78, 4, 6);
        g.fillStyle = '#ff8a92'; g.fillRect(detX + 56 + d2 * 6, 78, 4, 1);
      }
      Text.draw(g, 'REACH', detX + 8, 92, '#7f72a0', 1);
      g.fillStyle = '#8fa0bd';
      g.fillRect(detX + 56, 93, Math.round(sel.reach * 1.6), 4);
      g.fillStyle = '#c6d3de'; g.fillRect(detX + 56, 93, Math.round(sel.reach * 1.6), 1);
      Text.draw(g, 'SPECIAL', detX + 8, 108, '#7f72a0', 1);
      Text.draw(g, Art.SPECIALS[sel.special].name, detX + 8, 120, '#b9a0ff', 1);
      var words = Art.SPECIALS[sel.special].desc.split(' '), line = '', ly = 132;
      for (var wI = 0; wI < words.length; wI++) {
        var tryLine = line ? line + ' ' + words[wI] : words[wI];
        if (Text.width(tryLine, 1) > VIEW_W - detX - 26) {
          Text.draw(g, line, detX + 8, ly, '#8d80ad', 1); ly += 10; line = words[wI];
        } else line = tryLine;
      }
      if (line) Text.draw(g, line, detX + 8, ly, '#8d80ad', 1);

      if (G.shopMsgT > 0) {
        Text.centerShadow(g, G.shopMsg, VIEW_W / 2, VIEW_H - 26, '#bcd6ff', 1);
        G.shopMsgT--;
      }
      tapBtn(g, shopBackRect(), 'BACK', false);
      Text.draw(g, hint('ENTER DRAW   < > TABS', 'A DRAW   LB/RB TABS', 'TAP TO DRAW'),
        78, VIEW_H - 18, '#7f72a0', 1);
    },

    /* Outfits are never locked away once owned - wear, swap or remove
       any of them here, as often as you like. */
    wardrobe: function (G) {
      var list = wardrobeList();
      if (shopTap(G, Math.min(7, list.length), function () {
        var o2 = list[G.shopSel];
        if (Save.ownsOutfit(o2.id)) {
          if (Save.worn() === o2.id && o2.id !== 'none') { Save.wear('none'); G.shopMsg = 'TAKEN OFF'; }
          else { Save.wear(o2.id); G.shopMsg = o2.id === 'none' ? 'TAKEN OFF' : 'WEARING ' + o2.name.toUpperCase(); }
          Sfx.confirm();
        } else { G.shopMsg = 'LOCKED - FIND IT IN A CHEST'; Sfx.deny(); }
        G.shopMsgT = 100;
      }, Util.clamp(G.shopSel - 3, 0, Math.max(0, list.length - 7)))) return;
      if (Input.pressed('down')) { G.shopSel = (G.shopSel + 1) % list.length; Sfx.select(); }
      if (Input.pressed('up')) { G.shopSel = (G.shopSel + list.length - 1) % list.length; Sfx.select(); }
      if (Input.pressed('confirm') || Input.pressed('jump')) {
        var it = list[G.shopSel];
        if (Save.ownsOutfit(it.id)) {
          if (Save.worn() === it.id && it.id !== 'none') {
            Save.wear('none'); G.shopMsg = 'TAKEN OFF';
          } else {
            Save.wear(it.id);
            G.shopMsg = it.id === 'none' ? 'TAKEN OFF' : 'WEARING ' + it.name.toUpperCase();
          }
          Sfx.confirm();
        } else {
          G.shopMsg = 'LOCKED - FIND IT IN A CHEST';
          Sfx.deny();
        }
        G.shopMsgT = 100;
      }
      if (Input.pressed('pause') || Input.pressed('attack')) { Sfx.select(); G.go('map'); }
    },

    draw: function (g, G) {
      if (G.shopTab === 1) return shop.drawBlades(g, G);
      if (G.shopTab === 2) return shop.drawWardrobe(g, G);
      backdrop(g, G.t);
      Text.centerShadow(g, 'THE TRADING POST', VIEW_W / 2, 10, '#ffffff', 2);
      tabStrip(g, G);
      g.drawImage(Art.COIN, 10, 10);
      Text.draw(g, String(Save.get().coins), 21, 11, '#ffe27a', 1);

      var list = Save.SHOP;
      var top = Util.clamp(G.shopSel - 3, 0, Math.max(0, list.length - 7));
      var listW = Math.round(VIEW_W * 0.60), detX = listW + 18;
      panel(g, 10, 38, listW, 142);
      for (var i = 0; i < 7 && top + i < list.length; i++) {
        var it = list[top + i], y = 45 + i * 18;
        var own = Save.owned(it), avail = Save.available(it);
        var col = own ? '#5f7a5f' : (avail && Save.get().coins >= it.cost ? '#ffffff' : '#8d80ad');
        if (top + i === G.shopSel) {
          g.fillStyle = 'rgba(255,215,94,.14)';
          g.fillRect(12, y - 4, listW - 4, 17);
          cursor(g, 14, y, G.t);
        }
        Text.draw(g, it.name, 24, y, col, 1);
        var tag = own ? 'OWNED' : String(it.cost);
        if (it.kind === 'outfit' && own) tag = (Save.worn() === it.id) ? 'WORN' : 'WEAR';
        Text.draw(g, tag, listW - 46, y,
          (it.kind === 'outfit' && Save.worn() === it.id) ? '#a8ffd0'
            : (own ? '#7fe0a0' : '#ffd75e'), 1);
      }

      /* detail card */
      panel(g, detX, 38, VIEW_W - detX - 10, 142);
      var sel = list[G.shopSel];
      Text.draw(g, sel.kind === 'sword' ? 'BLADE'
        : (sel.kind === 'collar' ? 'COLLAR'
        : (sel.kind === 'outfit' ? 'OUTFIT' : 'RELIC')), detX + 8, 46, '#7f72a0', 1);
      /* wrap the description */
      var words = sel.desc.split(' '), line = '', ly = 54;
      for (var wI = 0; wI < words.length; wI++) {
        var tryLine = line ? line + ' ' + words[wI] : words[wI];
        if (Text.width(tryLine, 1) > VIEW_W - detX - 26) { Text.draw(g, line, detX + 8, ly, '#d8cff0', 1); ly += 11; line = words[wI]; }
        else line = tryLine;
      }
      if (line) Text.draw(g, line, detX + 8, ly, '#d8cff0', 1);

      if (sel.kind === 'sword') {
        Art.drawBlade(g, detX + 26, 152, -0.5, sel.id, 1);
      } else if (sel.kind === 'collar') {
        for (var h = 0; h < 3 + sel.tier; h++) g.drawImage(Art.HEART_FULL, detX + 8 + h * 11, 146);
      } else if (sel.kind === 'outfit') {
        /* preview it on him */
        var pv2 = detX + 18;
        g.drawImage(Art.dog.right.idle, pv2, 128, 36, 28);
        var fitp = Art.OUTFITS[sel.id];
        if (fitp) g.drawImage(fitp.right, pv2 + 16, 122, 20, 14);
      } else {
        g.drawImage(Art.BONE_GOLD, detX + 20, 148);
      }

      if (G.shopMsgT > 0) {
        Text.centerShadow(g, G.shopMsg, VIEW_W / 2, VIEW_H - 22, '#ffd75e', 1);
        G.shopMsgT--;
      }
      tapBtn(g, shopBackRect(), 'BACK', false);
      Text.draw(g, hint('ENTER BUY   < > TABS', 'A BUY   LB/RB TABS', 'TAP TO BUY'),
        78, VIEW_H - 18, '#7f72a0', 1);
    },

    drawWardrobe: function (g, G) {
      backdrop(g, G.t);
      Text.centerShadow(g, 'WARDROBE', VIEW_W / 2, 10, '#ffffff', 2);
      tabStrip(g, G);

      var list = wardrobeList();
      var listW = Math.round(VIEW_W * 0.52), detX = listW + 18;
      var top = Util.clamp(G.shopSel - 3, 0, Math.max(0, list.length - 7));
      panel(g, 10, 38, listW, 142);
      for (var i = 0; i < 7 && top + i < list.length; i++) {
        var it = list[top + i], y = 45 + i * 18;
        var own = Save.ownsOutfit(it.id);
        var isWorn = Save.worn() === it.id;
        if (top + i === G.shopSel) {
          g.fillStyle = 'rgba(168,255,208,.12)';
          g.fillRect(12, y - 4, listW - 4, 17);
          cursor(g, 14, y, G.t);
        }
        Text.draw(g, own ? it.name : '? ? ?', 24, y,
          own ? (isWorn ? '#a8ffd0' : '#ffffff') : '#5d5478', 1);
        Text.draw(g, isWorn ? 'WORN' : (own ? 'WEAR' : 'LOCKED'), listW - 48, y,
          isWorn ? '#a8ffd0' : (own ? '#7fe0a0' : '#5d5478'), 1);
      }

      panel(g, detX, 38, VIEW_W - detX - 10, 142);
      var sel = list[G.shopSel];
      var ownSel = Save.ownsOutfit(sel.id);
      Text.draw(g, ownSel ? sel.name.toUpperCase() : 'NOT FOUND YET', detX + 8, 46,
        ownSel ? '#a8ffd0' : '#7f72a0', 1);
      /* him, wearing it */
      var pvx = detX + Math.round((VIEW_W - detX - 10) / 2) - 22;
      g.drawImage(Art.dog.right.idle, pvx, 74, 45, 35);
      if (ownSel && sel.id !== 'none' && Art.OUTFITS[sel.id]) {
        g.drawImage(Art.OUTFITS[sel.id].right, pvx + 20, 66, 25, 18);
      }
      Text.draw(g, ownSel ? 'ENTER TO WEAR' : 'HIDDEN IN A CHEST', detX + 8, 126,
        '#8d80ad', 1);
      var found = 0, total = 0;
      var all = wardrobeList();
      for (var q = 1; q < all.length; q++) { total++; if (Save.ownsOutfit(all[q].id)) found++; }
      Text.draw(g, 'FOUND ' + found + ' / ' + total, detX + 8, 140, '#7f72a0', 1);

      if (G.shopMsgT > 0) {
        Text.centerShadow(g, G.shopMsg, VIEW_W / 2, VIEW_H - 26, '#a8ffd0', 1);
        G.shopMsgT--;
      }
      tapBtn(g, shopBackRect(), 'BACK', false);
      Text.draw(g, hint('ENTER WEAR   < > TABS', 'A WEAR   LB/RB TABS', 'TAP TO WEAR'),
        78, VIEW_H - 18, '#7f72a0', 1);
    }
  };

  /* ------------------------------------------------ PAUSE / OPTIONS */
  /* The same screen serves as the in-game pause and as the options page
     off the map; only the top and bottom entries differ. */
  function fromPlay(G) { return G.optionsFrom !== 'map'; }

  function pauseItems(G) {
    var items = [];
    if (fromPlay(G)) {
      items.push({ k: 'resume', label: 'RESUME' });
      items.push({ k: 'restart', label: 'RESTART STAGE' });
    }
    items.push({ k: 'sound', label: 'SOUND',
      value: function () { return Sfx.isEnabled() ? 'ON' : 'OFF'; } });
    items.push({ k: 'zoom', label: 'ZOOM',
      value: function () { return Game.zoomLabel(Save.get().zoom || 0); } });
    /* only worth offering once the screen has actually been touched */
    if (Input.mode() === 'touch') {
      items.push({ k: 'touch', label: 'TOUCH PAD',
        value: function () { return Input.touchMode() === 'always' ? 'ALWAYS ON' : 'AUTO HIDE'; } });
    }
    items.push(fromPlay(G) ? { k: 'quit', label: 'QUIT TO MAP' }
                           : { k: 'back', label: 'BACK TO MAP' });
    return items;
  }

  /* dir is -1, 0 or +1; 0 means "cycle forward" (a tap or a press) */
  function set_value(G, it, dir) {
    if (it.k === 'sound') {
      var d = Save.get();
      d.sound = !Sfx.isEnabled();
      Sfx.setEnabled(d.sound);
      Save.flush();
      if (d.sound && G.world) Sfx.playSong(G.world.def.boss ? 'boss' : G.world.theme);
    } else if (it.k === 'touch') {
      var d2 = Save.get();
      d2.touchMode = (Input.touchMode() === 'always') ? 'auto' : 'always';
      Input.setTouchMode(d2.touchMode);
      Save.flush();
    } else if (it.k === 'zoom') {
      var d3 = Save.get();
      var list = Game.ZOOMS;
      var at = list.indexOf(d3.zoom || 0);
      if (at < 0) at = 0;
      at = (at + (dir < 0 ? list.length - 1 : 1)) % list.length;
      d3.zoom = list[at];
      Save.flush();
      Game.applyZoom();
    }
    Sfx.select();
  }
  function activate(G, it) {
    if (it.k === 'resume') { Sfx.select(); G.state = 'play'; }
    else if (it.k === 'restart') { Sfx.confirm(); G.startStage(G.world.index); }
    else if (it.k === 'quit' || it.k === 'back') { Sfx.select(); G.go('map'); }
    else if (it.value) set_value(G, it, 0);
    else Sfx.select();
  }

  var pause = {
    update: function (G) {
      var items = pauseItems(G);
      if (G.pauseSel >= items.length) G.pauseSel = 0;

      var w0 = 214, h0 = 48 + items.length * 18;
      var bx0 = Math.round(VIEW_W / 2 - w0 / 2), by0 = Math.round(VIEW_H / 2 - h0 / 2);
      var tap = Input.takeTap();
      if (tap) {
        for (var pi = 0; pi < items.length; pi++) {
          var rr = { x: bx0 + 6, y: by0 + 27 + pi * 18, w: w0 - 12, h: 16 };
          if (inRect(tap, rr)) {
            if (G.pauseSel !== pi) { G.pauseSel = pi; Sfx.select(); }
            else if (items[pi].value) {
              /* tapping the right half nudges the value along */
              set_value(G, items[pi]);
            } else {
              activate(G, items[pi]);
            }
            return;
          }
        }
      }

      if (Input.pressed('down')) { G.pauseSel = (G.pauseSel + 1) % items.length; Sfx.select(); }
      if (Input.pressed('up')) { G.pauseSel = (G.pauseSel + items.length - 1) % items.length; Sfx.select(); }

      var it = items[G.pauseSel];
      var nudge = Input.pressed('left') ? -1 : (Input.pressed('right') ? 1 : 0);
      if (nudge && it.value) set_value(G, it, nudge);

      if (Input.pressed('confirm') || Input.pressed('jump')) activate(G, it);
      if (Input.pressed('pause')) {
        Sfx.select();
        if (fromPlay(G) && G.world) G.state = 'play'; else G.go('map');
      }
      if (Input.pressed('restart') && fromPlay(G) && G.world) {
        Sfx.confirm(); G.startStage(G.world.index);
      }
    },
    draw: function (g, G) {
      if (fromPlay(G) && G.world) { G.world.draw(g); dim(g, 0.72); }
      else { backdrop(g, G.t); }
      var items = pauseItems(G);
      var w = 214, h = 48 + items.length * 18;
      var x = Math.round(VIEW_W / 2 - w / 2), y = Math.round(VIEW_H / 2 - h / 2);
      panel(g, x, y, w, h);
      Text.centerShadow(g, fromPlay(G) ? 'PAUSED' : 'OPTIONS',
        VIEW_W / 2, y + 10, '#ffffff', 2);

      for (var i = 0; i < items.length; i++) {
        var iy = y + 32 + i * 18;
        var on = i === G.pauseSel;
        if (on) {
          g.fillStyle = 'rgba(255,215,94,.12)';
          g.fillRect(x + 6, iy - 5, w - 12, 16);
          cursor(g, x + 10, iy - 1, G.t);
        }
        Text.draw(g, items[i].label, x + 24, iy, on ? '#ffffff' : '#9d92b8', 1);
        if (items[i].value) {
          var v = items[i].value();
          Text.draw(g, '<', x + w - 86, iy, on ? '#ffd75e' : '#5d5478', 1);
          Text.draw(g, v, x + w - 76, iy, on ? '#ffd75e' : '#7f72a0', 1);
          Text.draw(g, '>', x + w - 14, iy, on ? '#ffd75e' : '#5d5478', 1);
        }
      }
      Text.center(g, hint('INPUT: KEYBOARD', 'INPUT: CONTROLLER', 'TAP AN ITEM'),
        VIEW_W / 2, y + h - 12, '#6d6488', 1);
    }
  };

  /* ------------------------------------------------ RESULTS */
  function continueRect() {
    var w = 118, h = 22;
    return { x: Math.round(VIEW_W / 2 - w / 2), y: VIEW_H - 30, w: w, h: h };
  }
  /* The two buttons are the only way off a results screen. A stray tap
     or a leftover button press must not skip it. */
  function anyInput() { return false; }

  var clear = {
    update: function (G) {
      if (G.resultT > 0) { G.resultT--; }
      if (G.autoOut === undefined) G.autoOut = 0;
      G.autoOut++;
      if (anyInput()) {
        G.autoOut = 0;
        Sfx.confirm();
        if (Save.allCleared() && G.lastStage === LEVELS.length - 1) G.go('ending');
        else G.go('map');
      }
    },
    draw: function (g, G) {
      backdrop(g, G.t);
      /* the sheet floats in the middle of whatever height we were given,
         and the two buttons live below it on the page itself */
      var ph = 84, py = Math.round((VIEW_H - 52) / 2 - ph / 2) + 4;
      Text.centerShadow(g, 'STAGE CLEAR', VIEW_W / 2, py - 26, '#ffd75e', 2);

      /* the good boy, pleased with himself, clear of the sign */
      var bob = Math.round(Math.sin(G.t * 0.07) * 2);
      g.drawImage(Art.dog.right.idle,
        Math.round(VIEW_W / 2 - 158), py + ph - 30 + bob, 36, 28);

      panel(g, VIEW_W / 2 - 110, py, 220, ph);
      var px0 = VIEW_W / 2 - 96, pv = VIEW_W / 2 - 34;
      Text.draw(g, 'STAGE', px0, py + 12, '#c7b79a', 1);
      Text.draw(g, LEVELS[G.lastStage].name, pv, py + 12, '#ffffff', 1);
      Text.draw(g, 'COIN', px0, py + 30, '#c7b79a', 1);
      g.drawImage(Art.COIN, pv - 2, py + 29);
      Text.draw(g, '+' + G.resultCoins, pv + 10, py + 30, '#ffe27a', 1);
      Text.draw(g, 'GEMS', px0, py + 48, '#c7b79a', 1);
      var rg = Art.THEME_GEMS[LEVELS[G.lastStage].theme] || Art.THEME_GEMS.meadow;
      for (var gI = 0; gI < 3; gI++) {
        if (gI < G.resultGems) g.drawImage(rg, pv - 2 + gI * 9, py + 47);
        else { g.fillStyle = '#453a2c'; g.fillRect(pv - 1 + gI * 9, py + 48, 5, 5); }
      }
      Text.draw(g, G.resultChests + ' CHEST' + (G.resultChests === 1 ? '' : 'S'),
        pv + 30, py + 48, G.resultChests ? '#e8c45c' : '#8a7c66', 1);
      Text.draw(g, 'PURSE', px0, py + 66, '#c7b79a', 1);
      Text.draw(g, String(Save.get().coins), pv, py + 66, '#ffe27a', 1);
      /* say plainly what PLAY AGAIN does, because it is not obvious */
      Text.centerShadow(g, 'THIS HAUL IS SAVED', VIEW_W / 2, py + ph + 8, '#ffe9a0', 1);
      Text.centerShadow(g, 'PLAY AGAIN TO GO BACK FOR THE REST',
        VIEW_W / 2, py + ph + 22, '#eaf4ff', 1);
      Text.shadow(g, 'BUILD ' + (window.BUILD_ID || 'DEV'), 6, VIEW_H - 10, '#dfe8d4', 1);
    }
  };

  function retryRect() { return { x: Math.round(VIEW_W / 2 - 122), y: VIEW_H - 32, w: 116, h: 22 }; }
  function quitRect() { return { x: Math.round(VIEW_W / 2 + 6), y: VIEW_H - 32, w: 116, h: 22 }; }

  var fail = {
    update: function (G) {
      if (G.resultT > 0) { G.resultT--; }
      Input.clearTaps();
    },
    draw: function (g, G) {
      backdrop(g, G.t, 'dusk');
      var cy = Math.round((VIEW_H - 40) / 2);
      Text.centerShadow(g, 'DOWN BOY', VIEW_W / 2, cy - 54, '#e0424f', 3);
      /* ears down, eye shut - he is not happy about this */
      var droop = Math.round(Math.sin(G.t * 0.035) * 1);
      g.drawImage(Art.dog.right.sad, Math.round(VIEW_W / 2 - 18), cy - 16 + droop, 36, 28);

      var lost = (G.lostCoins || 0) + (G.lostGems || 0) + (G.lostChests || 0);
      if (lost > 0) {
        Text.centerShadow(g, 'THE RUN IS LOST WITH HIM', VIEW_W / 2, cy + 20, '#ffd0d6', 1);
        var bits = [];
        if (G.lostCoins) bits.push(G.lostCoins + ' COIN');
        if (G.lostGems) bits.push(G.lostGems + ' GEM' + (G.lostGems === 1 ? '' : 'S'));
        if (G.lostChests) bits.push(G.lostChests + ' CHEST' + (G.lostChests === 1 ? '' : 'S'));
        Text.centerShadow(g, 'DROPPED: ' + bits.join('   '), VIEW_W / 2, cy + 34, '#f3c9a0', 1);
      } else {
        Text.centerShadow(g, 'NOTHING GAINED, NOTHING LOST', VIEW_W / 2, cy + 20, '#ffd0d6', 1);
      }
      Text.centerShadow(g, 'WHAT YOU KEPT BEFORE THIS RUN IS SAFE',
        VIEW_W / 2, cy + 48, '#e6e0d0', 1);
      Text.shadow(g, 'BUILD ' + (window.BUILD_ID || 'DEV'), 6, VIEW_H - 10, '#dfe8d4', 1);
    }
  };

  var ending = {
    update: function (G) {
      if (anyInput()) { Sfx.confirm(); G.go('title'); }
    },
    draw: function (g, G) {
      backdrop(g, G.t);
      var ey = Math.round(VIEW_H * 0.13);
      Text.centerShadow(g, 'GOOD DOG', VIEW_W / 2, ey, '#ffd75e', 3);
      Text.centerShadow(g, 'THE KEEP IS QUIET. THE GARDEN IS SAFE.', VIEW_W / 2, ey + 40, '#f3f8ff', 1);
      Text.centerShadow(g, 'YOU HAVE EARNED THE NAP.', VIEW_W / 2, ey + 54, '#f3f8ff', 1);
      var bob = Math.round(Math.sin(G.t * 0.05) * 2);
      g.drawImage(Art.dog.right.sit, VIEW_W / 2 - 18, ey + 76 + bob, 36, 28);
      Text.centerShadow(g, 'GEMS  ' + Save.gemsFound() + ' / ' + Save.gemsTotal() +
        '     CHESTS  ' + Save.chestsFound(), VIEW_W / 2, ey + 122, '#bff0ff', 1);
      Text.centerShadow(g, 'COIN IN THE PURSE  ' + Save.get().coins, VIEW_W / 2, ey + 136, '#ffe27a', 1);
      if (Math.floor(G.t / 26) % 2 === 0) {
        Text.center(g, hint('PRESS ENTER', 'PRESS A', 'TAP TO CONTINUE'),
          VIEW_W / 2, VIEW_H - 16, '#ffffff', 1);
      }
    }
  };

  return { title: title, map: map, shop: shop, pause: pause,
           clear: clear, fail: fail, ending: ending, panel: panel, dim: dim, backdrop: backdrop };
})();
