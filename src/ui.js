/* Menus, map screen, shop and result screens. */
var UI = (function () {
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
      Art.drawBlade(g, px + 30, py + 17, -0.5 + Math.sin(G.t * 0.05) * 0.12, Save.get().sword, 1);

      if (Math.floor(G.t / 26) % 2 === 0) {
        Text.centerShadow(g, 'PRESS ENTER', cx, VIEW_H - 32, '#ffffff', 1);
      }
      Text.centerShadow(g, 'ARROWS MOVE   Z JUMP   X SWING   M MUTE', cx, VIEW_H - 16, '#8d80ad', 1);
    }
  };

  /* ------------------------------------------------ WORLD MAP */
  function nodePos(i) {
    var w = Math.floor(i / 4), s = i % 4;
    var step = Math.floor((VIEW_W - 104) / 3);
    return { x: 52 + s * step, y: 62 + w * 44 };
  }
  var map = {
    update: function (G) {
      var d = Save.get();
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
      Text.draw(g, 'ENTER PLAY   X SHOP + WARDROBE', VIEW_W - 186, VIEW_H - 16, '#7f72a0', 1);
    }
  };

  /* ------------------------------------------------ SHOP */
  var shop = {
    update: function (G) {
      var list = Save.SHOP;
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
    draw: function (g, G) {
      backdrop(g, G.t, ['#201428', '#3a1f2f']);
      Text.centerShadow(g, 'THE TRADING POST', VIEW_W / 2, 10, '#ffffff', 2);
      g.drawImage(Art.COIN, 10, 10);
      Text.draw(g, String(Save.get().coins), 21, 11, '#ffe27a', 1);

      var list = Save.SHOP;
      var top = Util.clamp(G.shopSel - 3, 0, Math.max(0, list.length - 7));
      var listW = Math.round(VIEW_W * 0.60), detX = listW + 18;
      panel(g, 10, 30, listW, 150);
      for (var i = 0; i < 7 && top + i < list.length; i++) {
        var it = list[top + i], y = 37 + i * 19;
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
      panel(g, detX, 30, VIEW_W - detX - 10, 150);
      var sel = list[G.shopSel];
      Text.draw(g, sel.kind === 'sword' ? 'BLADE'
        : (sel.kind === 'collar' ? 'COLLAR'
        : (sel.kind === 'outfit' ? 'OUTFIT' : 'RELIC')), detX + 8, 38, '#7f72a0', 1);
      /* wrap the description */
      var words = sel.desc.split(' '), line = '', ly = 54;
      for (var wI = 0; wI < words.length; wI++) {
        var tryLine = line ? line + ' ' + words[wI] : words[wI];
        if (Text.width(tryLine, 1) > VIEW_W - detX - 26) { Text.draw(g, line, detX + 8, ly, '#d8cff0', 1); ly += 11; line = words[wI]; }
        else line = tryLine;
      }
      if (line) Text.draw(g, line, detX + 8, ly, '#d8cff0', 1);

      if (sel.kind === 'sword') {
        Art.drawBlade(g, detX + 26, 152, -0.5, sel.tier, 1);
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
      Text.draw(g, 'ENTER BUY    X BACK', 10, VIEW_H - 12, '#7f72a0', 1);
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

  var pause = {
    update: function (G) {
      var items = pauseItems();
      if (G.pauseSel >= items.length) G.pauseSel = 0;

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

      if (Input.pressed('confirm') || Input.pressed('jump')) {
        if (it.k === 'resume') { Sfx.select(); G.state = 'play'; }
        else if (it.k === 'restart') { Sfx.confirm(); G.startStage(G.world.index); }
        else if (it.k === 'quit') { Sfx.select(); G.go('map'); }
        else Sfx.select();
      }
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
      Text.center(g, 'INPUT: ' + Input.schemeLabel(), VIEW_W / 2, y + h - 12, '#6d6488', 1);
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
        Text.center(g, 'PRESS ENTER', VIEW_W / 2, VIEW_H - 22, '#ffffff', 1);
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
        Text.center(g, 'ENTER  TRY AGAIN', VIEW_W / 2, 164, '#ffffff', 1);
        Text.center(g, 'X      BACK TO MAP', VIEW_W / 2, 178, '#8d80ad', 1);
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
        Text.center(g, 'PRESS ENTER', VIEW_W / 2, VIEW_H - 16, '#ffffff', 1);
      }
    }
  };

  return { title: title, map: map, shop: shop, pause: pause,
           clear: clear, fail: fail, ending: ending, panel: panel, dim: dim, backdrop: backdrop };
})();
