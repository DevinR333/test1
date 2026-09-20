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

  function panel(g, x, y, w, h, fill) {
    g.fillStyle = fill || 'rgba(14,10,22,.88)';
    g.fillRect(x, y, w, h);
    g.fillStyle = '#6b5a8a'; g.fillRect(x, y, w, 1); g.fillRect(x, y + h - 1, w, 1);
    g.fillRect(x, y, 1, h); g.fillRect(x + w - 1, y, 1, h);
    g.fillStyle = '#39304f';
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

  /* Decorative starfield shared by the menu screens. */
  var stars = [];
  for (var i = 0; i < 46; i++) {
    stars.push({ x: Math.random() * VIEW_W, y: Math.random() * VIEW_H,
                 s: Math.random() < 0.25 ? 2 : 1, p: Math.random() * 6.28 });
  }
  function backdrop(g, t, tint) {
    var grad = g.createLinearGradient(0, 0, 0, VIEW_H);
    grad.addColorStop(0, tint ? tint[0] : '#140f22');
    grad.addColorStop(1, tint ? tint[1] : '#2a1836');
    g.fillStyle = grad; g.fillRect(0, 0, VIEW_W, VIEW_H);
    for (var i = 0; i < stars.length; i++) {
      var s = stars[i];
      g.globalAlpha = 0.25 + 0.45 * Math.abs(Math.sin(t * 0.02 + s.p));
      g.fillStyle = '#ffe9c0';
      g.fillRect(s.x, s.y, s.s, s.s);
    }
    g.globalAlpha = 1;
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
      /* rolling hills */
      g.fillStyle = '#1b1328';
      for (var x = 0; x < VIEW_W; x++) {
        var h = Math.floor(16 + 7 * Math.sin(x * 0.02) + 4 * Math.sin(x * 0.06 + 2));
        g.fillRect(x, VIEW_H - h, 1, h);
      }
      var cx = VIEW_W / 2;
      Text.centerShadow(g, 'BUDDY', cx, 34, '#ffffff', 3);
      Text.centerShadow(g, 'BLADE', cx, 62, '#ffd75e', 3);
      Text.centerShadow(g, 'A GOOD BOY WITH A VERY SHARP STICK', cx, 90, '#b8a8d8', 1);

      /* hero idles on the title, blade out */
      var bob = Math.round(Math.sin(G.t * 0.06) * 2);
      var px = Math.round(cx - 18), py = 124 + bob;
      g.save();
      g.imageSmoothingEnabled = false;
      g.drawImage(Art.dog.right.idle, px, py, 36, 28);
      g.restore();
      Art.drawBlade(g, px + 30, py + 17, -0.5 + Math.sin(G.t * 0.05) * 0.12, Save.bladeId(), 1);

      if (Math.floor(G.t / 26) % 2 === 0) {
        Text.centerShadow(g, hint('PRESS ENTER', 'PRESS A', 'TAP TO START'),
          cx, VIEW_H - 32, '#ffffff', 1);
      }
      Text.centerShadow(g, hint('ARROWS MOVE   Z JUMP   X SWING',
                                'STICK MOVE   A JUMP   X SWING',
                                'STICK MOVE   JUMP   SWING'),
        cx, VIEW_H - 16, '#8d80ad', 1);
    }
  };

  /* ------------------------------------------------ WORLD MAP */
  function nodePos(i) {
    var w = Math.floor(i / 4), s = i % 4;
    var step = Math.floor((VIEW_W - 104) / 3);
    return { x: 52 + s * step, y: 62 + w * 44 };
  }
  function nodeRect(i) {
    var p = nodePos(i);
    return { x: p.x - 3, y: p.y - 4, w: 30, h: 22 };
  }
  function mapShopRect() {
    return { x: VIEW_W - 86, y: VIEW_H - 22, w: 78, h: 16 };
  }

  var map = {
    update: function (G) {
      var d = Save.get();

      /* touch: tap a stage to pick it, tap it again to go */
      var tap = Input.takeTap();
      if (tap) {
        if (inRect(tap, mapShopRect())) { Sfx.confirm(); G.go('shop'); return; }
        for (var ti = 0; ti < LEVELS.length && ti < d.unlocked; ti++) {
          if (inRect(tap, nodeRect(ti))) {
            if (G.sel === ti) { Sfx.confirm(); G.startStage(ti); }
            else { G.sel = ti; Sfx.select(); }
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
      if (Input.pressed('pause')) { Sfx.select(); G.go('title'); }
    },
    draw: function (g, G) {
      backdrop(g, G.t, ['#121b2a', '#26183a']);
      var d = Save.get();
      Text.centerShadow(g, 'THE LONG WALK', VIEW_W / 2, 14, '#ffffff', 2);

      /* world bands */
      for (var w = 0; w < 3; w++) {
        var y = 62 + w * 44;
        g.fillStyle = 'rgba(255,255,255,.05)';
        g.fillRect(16, y - 14, VIEW_W - 32, 34);
        Text.draw(g, WORLDS[w].name, 20, y - 12, '#7f72a0', 1);
      }
      /* connecting track */
      for (var i = 0; i < LEVELS.length - 1; i++) {
        if (Math.floor(i / 4) !== Math.floor((i + 1) / 4)) continue;
        var a = nodePos(i), b = nodePos(i + 1);
        for (var x = a.x + 12; x < b.x; x += 5) {
          g.fillStyle = (i + 1 < d.unlocked) ? '#6b5a8a' : '#342b47';
          g.fillRect(x, a.y + 5, 2, 2);
        }
      }
      /* nodes */
      for (i = 0; i < LEVELS.length; i++) {
        var L = LEVELS[i], p = nodePos(i);
        var open = i < d.unlocked;
        var done = !!d.cleared[L.id];
        var isBoss = !!L.boss;
        g.fillStyle = open ? (done ? '#3f7a52' : (isBoss ? '#7a2d3f' : '#3c3558')) : '#241f33';
        g.fillRect(p.x, p.y, 24, 14);
        g.fillStyle = open ? (isBoss ? '#e0424f' : '#8d80ad') : '#3a3350';
        g.fillRect(p.x, p.y, 24, 1); g.fillRect(p.x, p.y + 13, 24, 1);
        g.fillRect(p.x, p.y, 1, 14); g.fillRect(p.x + 23, p.y, 1, 14);
        Text.draw(g, open ? L.id : '??', p.x + 5, p.y + 4, open ? '#ffffff' : '#5a5170', 1);
        if (!isBoss && open) {
          var got = d.gems[L.id] || 0;
          var gimg = Art.GEMS[L.theme] || Art.GEMS.meadow;
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
        '   CHESTS ' + Save.chestsFound(), 16, VIEW_H - 27, '#b8a8d8', 1);
      /* the selected stage glows so a tap-to-confirm is obvious */
      var selR = nodeRect(G.sel);
      g.fillStyle = 'rgba(255,215,94,' + (0.25 + 0.15 * Math.sin(G.t * 0.12)) + ')';
      g.fillRect(selR.x, selR.y, selR.w, 1);
      g.fillRect(selR.x, selR.y + selR.h - 1, selR.w, 1);
      g.fillRect(selR.x, selR.y, 1, selR.h);
      g.fillRect(selR.x + selR.w - 1, selR.y, 1, selR.h);

      tapBtn(g, mapShopRect(), 'SHOP', false);
      Text.draw(g, hint('ENTER PLAY', 'A PLAY', 'TAP A STAGE TO PLAY'),
        16, VIEW_H - 16, '#7f72a0', 1);
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
      backdrop(g, G.t, ['#1c1a2c', '#2e2a3f']);
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
      backdrop(g, G.t, ['#201428', '#3a1f2f']);
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
      backdrop(g, G.t, ['#16202e', '#24303f']);
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

  /* ------------------------------------------------ PAUSE */
  function pauseItems() {
    var items = [
      { k: 'resume', label: 'RESUME' },
      { k: 'restart', label: 'RESTART STAGE' },
      { k: 'sound', label: 'SOUND',
        value: function () { return Sfx.isEnabled() ? 'ON' : 'OFF'; } }
    ];
    /* only worth offering once the screen has actually been touched */
    if (Input.mode() === 'touch') {
      items.push({ k: 'touch', label: 'TOUCH PAD',
        value: function () { return Input.touchMode() === 'always' ? 'ALWAYS ON' : 'AUTO HIDE'; } });
    }
    items.push({ k: 'quit', label: 'QUIT TO MAP' });
    return items;
  }

  function set_value(G, it) {
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
    }
    Sfx.select();
  }
  function activate(G, it) {
    if (it.k === 'resume') { Sfx.select(); G.state = 'play'; }
    else if (it.k === 'restart') { Sfx.confirm(); G.startStage(G.world.index); }
    else if (it.k === 'quit') { Sfx.select(); G.go('map'); }
    else if (it.value) set_value(G, it);
    else Sfx.select();
  }

  var pause = {
    update: function (G) {
      var items = pauseItems();
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
      if (nudge && it.value) {
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
        }
        Sfx.select();
      }

      if (Input.pressed('confirm') || Input.pressed('jump')) activate(G, it);
      if (Input.pressed('pause')) { Sfx.select(); G.state = 'play'; }
      if (Input.pressed('restart')) { Sfx.confirm(); G.startStage(G.world.index); }
    },
    draw: function (g, G) {
      G.world.draw(g);
      dim(g, 0.72);
      var items = pauseItems();
      var w = 214, h = 48 + items.length * 18;
      var x = Math.round(VIEW_W / 2 - w / 2), y = Math.round(VIEW_H / 2 - h / 2);
      panel(g, x, y, w, h);
      Text.centerShadow(g, 'PAUSED', VIEW_W / 2, y + 10, '#ffffff', 2);

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
  var clear = {
    update: function (G) {
      if (G.resultT > 0) { G.resultT--; return; }
      if (Input.pressed('confirm') || Input.pressed('jump') || Input.pressed('attack')) {
        Sfx.confirm();
        if (Save.allCleared() && G.lastStage === LEVELS.length - 1) G.go('ending');
        else G.go('map');
      }
    },
    draw: function (g, G) {
      backdrop(g, G.t, ['#16241c', '#243a26']);
      Text.centerShadow(g, 'STAGE CLEAR', VIEW_W / 2, 32, '#ffd75e', 2);
      panel(g, VIEW_W / 2 - 110, 64, 220, 88);
      var px0 = VIEW_W / 2 - 96, pv = VIEW_W / 2 - 34;
      Text.draw(g, 'STAGE', px0, 76, '#8d80ad', 1);
      Text.draw(g, LEVELS[G.lastStage].name, pv, 76, '#ffffff', 1);
      Text.draw(g, 'COIN', px0, 94, '#8d80ad', 1);
      g.drawImage(Art.COIN, pv - 2, 93);
      Text.draw(g, '+' + G.resultCoins, pv + 10, 94, '#ffe27a', 1);
      Text.draw(g, 'GEMS', px0, 112, '#8d80ad', 1);
      var rg = Art.GEMS[LEVELS[G.lastStage].theme] || Art.GEMS.meadow;
      for (var gI = 0; gI < 3; gI++) {
        if (gI < G.resultGems) g.drawImage(rg, pv - 2 + gI * 9, 111);
        else { g.fillStyle = '#332b44'; g.fillRect(pv - 1 + gI * 9, 112, 5, 5); }
      }
      Text.draw(g, G.resultChests + ' CHEST' + (G.resultChests === 1 ? '' : 'S'),
        pv + 30, 112, G.resultChests ? '#e8c45c' : '#6b6458', 1);
      Text.draw(g, 'PURSE', px0, 130, '#8d80ad', 1);
      Text.draw(g, String(Save.get().coins), pv, 130, '#ffe27a', 1);
      if (G.resultT <= 0 && Math.floor(G.t / 24) % 2 === 0) {
        Text.center(g, hint('PRESS ENTER', 'PRESS A', 'TAP TO CONTINUE'),
          VIEW_W / 2, VIEW_H - 22, '#ffffff', 1);
      }
    }
  };

  var fail = {
    update: function (G) {
      if (G.resultT > 0) { G.resultT--; return; }
      if (Input.pressed('confirm') || Input.pressed('jump')) { Sfx.confirm(); G.startStage(G.lastStage); }
      if (Input.pressed('attack') || Input.pressed('pause')) { Sfx.select(); G.go('map'); }
    },
    draw: function (g, G) {
      backdrop(g, G.t, ['#1c1016', '#331823']);
      Text.centerShadow(g, 'DOWN BOY', VIEW_W / 2, 52, '#e0424f', 3);
      g.drawImage(Art.dog.right.sit, VIEW_W / 2 - 18, 96, 36, 28);
      Text.center(g, 'THE COIN YOU PICKED UP IS KEPT.', VIEW_W / 2, 140, '#b8a8d8', 1);
      if (G.resultT <= 0) {
        Text.center(g, hint('ENTER  TRY AGAIN', 'A  TRY AGAIN', 'TAP TO TRY AGAIN'),
          VIEW_W / 2, 164, '#ffffff', 1);
        Text.center(g, hint('X  BACK TO MAP', 'X  BACK TO MAP', ''),
          VIEW_W / 2, 178, '#8d80ad', 1);
      }
    }
  };

  var ending = {
    update: function (G) {
      if (Input.pressed('confirm') || Input.pressed('pause')) { Sfx.confirm(); G.go('title'); }
    },
    draw: function (g, G) {
      backdrop(g, G.t, ['#101a2a', '#2a1f3a']);
      Text.centerShadow(g, 'GOOD DOG', VIEW_W / 2, 30, '#ffd75e', 3);
      Text.center(g, 'THE KEEP IS QUIET. THE GARDEN IS SAFE.', VIEW_W / 2, 70, '#d8cff0', 1);
      Text.center(g, 'YOU HAVE EARNED THE NAP.', VIEW_W / 2, 84, '#d8cff0', 1);
      var bob = Math.round(Math.sin(G.t * 0.05) * 2);
      g.drawImage(Art.dog.right.sit, VIEW_W / 2 - 18, 106 + bob, 36, 28);
      Text.center(g, 'GEMS  ' + Save.gemsFound() + ' / ' + Save.gemsTotal() +
        '     CHESTS  ' + Save.chestsFound(), VIEW_W / 2, 152, '#a8e0ff', 1);
      Text.center(g, 'COIN IN THE PURSE  ' + Save.get().coins, VIEW_W / 2, 166, '#ffe27a', 1);
      if (Math.floor(G.t / 26) % 2 === 0) {
        Text.center(g, hint('PRESS ENTER', 'PRESS A', 'TAP TO CONTINUE'),
          VIEW_W / 2, VIEW_H - 16, '#ffffff', 1);
      }
    }
  };

  return { title: title, map: map, shop: shop, pause: pause,
           clear: clear, fail: fail, ending: ending, panel: panel, dim: dim, backdrop: backdrop };
})();
