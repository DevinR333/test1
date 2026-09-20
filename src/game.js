/* Boot, scaling, fixed-step loop and the screen state machine. */
var Game = (function () {
  var cv, g, acc = 0, last = 0;
  var STEP = 1000 / 60;

  var G = {
    state: 'title',
    prevState: null,
    world: null,
    sel: 0,
    shopSel: 0,
    shopTab: 0,
    btnSel: 1,
    pauseSel: 0,
    shopMsg: '', shopMsgT: 0,
    t: 0,
    lastStage: 0,
    resultCoins: 0,
    autoOut: 0,
    resultGems: 0,
    resultChests: 0,
    resultT: 0,
    fade: 0,          /* 0 = clear, 1 = black */
    fadeDir: 0,
    pending: null
  };

  /* The design reference is 400x224. Rather than pinning one axis and
     letting the other go short, the view is scaled so that reference
     ALWAYS fits, then whichever axis has room shows more world. A 4:3
     screen therefore gets the full width plus extra height instead of a
     cramped, narrow picture, and an ultrawide gets extra width. Pixels
     stay square in every case, and the picture fills the display. */
  var BASE_W = 400, BASE_H = 224;

  /* FIT picks the scale that makes the reference fit; a fixed zoom pins
     the pixel size instead, so you can trade world for legibility. */
  G.ZOOMS = [0, 1, 2, 3, 4];
  G.zoomLabel = function (z) { return z ? 'X' + z : 'FIT'; };

  function fitCanvas() {
    var vw = window.innerWidth, vh = window.innerHeight;
    if (!vw || !vh) return;

    var fit = Math.min(vw / BASE_W, vh / BASE_H);
    var z = 0;
    try { z = Save.get().zoom || 0; } catch (e) { z = 0; }
    var scale = z > 0 ? z : fit;
    var w = Math.round(vw / scale / 2) * 2;
    var h = Math.round(vh / scale / 2) * 2;
    if (z > 0) {
      /* a forced zoom may show less than the reference - that is the
         point - but never so little that the HUD has nowhere to sit */
      VIEW_W = Util.clamp(w, 320, 760);
      VIEW_H = Util.clamp(h, 180, 440);
    } else {
      VIEW_W = Math.max(BASE_W, Math.min(760, w));
      VIEW_H = Math.max(BASE_H, Math.min(440, h));
    }

    if (cv.width !== VIEW_W || cv.height !== VIEW_H) {
      cv.width = VIEW_W;
      cv.height = VIEW_H;
    }
    g = cv.getContext('2d');
    g.imageSmoothingEnabled = false;

    /* one uniform scale for both axes - never stretched */
    var s2 = Math.min(vw / VIEW_W, vh / VIEW_H);
    cv.style.width = Math.round(VIEW_W * s2) + 'px';
    cv.style.height = Math.round(VIEW_H * s2) + 'px';
  }

  G.go = function (state) {
    G.pending = state;
    G.fadeDir = 1;
  };
  function enter(state) {
    G.prevState = G.state;
    G.state = state;
    /* a tap that advanced the last screen must not also press something
       on the next one */
    Input.clearTaps();
    G.autoOut = 0;
    if (state === 'map') { Sfx.stopSong(); }
    if (state === 'title') { Sfx.stopSong(); }
  }

  G.applyZoom = function () { fitCanvas(); };

  G.startStage = function (index) {
    G.lastStage = index;
    /* what the purse looked like before this attempt */
    G.runSnapshot = Save.snapshot();
    G.pending = 'play';
    G.pendingStage = index;
    G.fadeDir = 1;
  };

  G.onLevelClear = function () {
    var w = G.world;
    G.resultCoins = w.coinsRun;
    G.resultGems = w.gemsGot;
    G.resultChests = w.chestsGot;
    G.resultT = 26;
    Save.clearStage(w.index, w.gemsGot, w.chestsGot);
    Save.flush();
    G.go('clear');
  };
  G.onLevelFailed = function () {
    G.resultT = 30;
    /* Go down and the run is forfeit - coin, gems, chests and anything a
       chest handed over all go back to where they stood at the gate. */
    var w = G.world;
    G.lostCoins = w ? w.coinsRun : 0;
    G.lostGems = w ? w.gemsGot : 0;
    G.lostChests = w ? w.chestsGot : 0;
    Save.restore(G.runSnapshot);
    Save.flush();
    G.go('fail');
  };

  function screen() {
    switch (G.state) {
      case 'title': return UI.title;
      case 'map': return UI.map;
      case 'shop': return UI.shop;
      case 'pause': return UI.pause;
      case 'clear': return UI.clear;
      case 'fail': return UI.fail;
      case 'ending': return UI.ending;
    }
    return null;
  }

  function update() {
    G.t++;
    Input.pollPad();
    Sfx.tick();

    if (Input.pressed('mute')) {
      var d = Save.get();
      d.sound = !d.sound;
      Sfx.setEnabled(d.sound);
      Save.flush();
      if (d.sound && G.state === 'play' && G.world) {
        Sfx.playSong(G.world.def.boss ? 'boss' : G.world.theme);
      }
    }

    /* screen transition */
    if (G.fadeDir === 1) {
      G.fade = Math.min(1, G.fade + 0.12);
      if (G.fade >= 1) {
        if (G.pending === 'clear' || G.pending === 'fail') G.btnSel = 1;
        if (G.pending === 'play') {
          G.world = new World(G.pendingStage != null ? G.pendingStage : G.lastStage);
          G.pendingStage = null;
          enter('play');
        } else if (G.pending) enter(G.pending);
        G.pending = null;
        G.fadeDir = -1;
      }
      Input.endFrame();
      return;
    } else if (G.fadeDir === -1) {
      G.fade = Math.max(0, G.fade - 0.1);
      if (G.fade <= 0) G.fadeDir = 0;
    }

    /* Outside gameplay the touch pad stays put, and on screens that only
       wait for "carry on" a tap anywhere counts. */
    var justCarryOn = (G.state === 'clear' || G.state === 'fail' ||
                       G.state === 'ending' || G.state === 'title');
    Input.setMenuMode(G.state !== 'play', justCarryOn);
    syncMenuButtons();
    syncPopupButton();

    /* A controller cannot touch an HTML button, so drive them here. */
    if (G.state === 'clear' || G.state === 'fail') {
      if (Input.pressed('left')) { G.btnSel = 0; Sfx.select(); }
      if (Input.pressed('right')) { G.btnSel = 1; Sfx.select(); }
      if (Input.pressed('confirm') || Input.pressed('jump') || Input.pressed('attack')) {
        Save.flush();
        if (G.btnSel === 0) { Sfx.select(); G.go('map'); }
        else { Sfx.confirm(); G.startStage(G.lastStage); }
        Input.endFrame();
        return;
      }
    }

    if (G.state === 'play' && G.world && G.world.popupBlocking()) {
      if (Input.pressed('confirm') || Input.pressed('jump') || Input.pressed('attack')) {
        G.world.dismissPopup();
        Sfx.select();
      }
      Input.endFrame();
      return;
    }

    if (G.state === 'play') {
      if (Input.pressed('pause')) { Sfx.select(); G.state = 'pause'; }
      else if (Input.pressed('restart')) { Sfx.confirm(); G.startStage(G.world.index); }
      else G.world.update();
    } else {
      var s = screen();
      if (s) s.update(G);
    }
    Input.endFrame();
  }

  function draw() {
    g.imageSmoothingEnabled = false;
    if (G.state === 'play' && G.world) G.world.draw(g);
    else {
      var s = screen();
      if (s) s.draw(g, G);
    }
    if (G.fade > 0) {
      g.fillStyle = 'rgba(6,4,10,' + G.fade + ')';
      g.fillRect(0, 0, VIEW_W, VIEW_H);
    }
  }

  /* One thrown error used to kill the animation loop outright, which
     froze the game and made every control look dead. Keep the loop
     running no matter what, and report the first failure. */
  var reported = false;
  function frame(now) {
    if (!last) last = now;
    var dt = Math.min(now - last, 250);
    last = now;
    acc += dt;
    var guard = 0;
    try {
      while (acc >= STEP && guard++ < 5) { update(); acc -= STEP; }
    } catch (e) {
      acc = 0;
      Input.endFrame();
      if (!reported) { reported = true; G.lastError = String(e && e.message || e); }
      if (window.console) console.error('update failed:', e);
    }
    try {
      draw();
    } catch (e2) {
      if (window.console) console.error('draw failed:', e2);
    }
    requestAnimationFrame(frame);
  }

  /* Show the two result-screen buttons, label them for the screen we are
     on, and wire them straight to the action. */
  var menuEl, mbLeft, mbRight;
  function initMenuButtons() {
    menuEl = document.getElementById('menubtns');
    mbLeft = document.getElementById('mb-left');
    mbRight = document.getElementById('mb-right');
    if (!menuEl) return;
    function press(el, fn) {
      var go = function (e) { e.preventDefault(); e.stopPropagation(); fn(); };
      el.addEventListener('click', go);
      el.addEventListener('touchend', go, { passive: false });
    }
    press(mbLeft, function () {
      Sfx.select();
      Save.flush();
      G.go('map');
    });
    press(mbRight, function () {
      Sfx.confirm();
      Save.flush();              /* keep everything earned this run */
      G.startStage(G.lastStage);
    });
  }
  var popEl, pbOk;
  function initPopupButton() {
    popEl = document.getElementById('popupbtn');
    pbOk = document.getElementById('pb-ok');
    if (!popEl) return;
    var go = function (e) {
      e.preventDefault(); e.stopPropagation();
      if (G.world) G.world.dismissPopup();
      Sfx.select();
    };
    pbOk.addEventListener('click', go);
    pbOk.addEventListener('touchend', go, { passive: false });
  }
  function syncPopupButton() {
    if (!popEl) return;
    var on = (G.state === 'play' && G.world && G.world.popupBlocking());
    if (on) popEl.classList.remove('hidden');
    else popEl.classList.add('hidden');
  }

  function syncMenuButtons() {
    if (!menuEl) return;
    var show = (G.state === 'clear' || G.state === 'fail');
    if (!show) { menuEl.classList.add('hidden'); return; }
    menuEl.classList.remove('hidden');
    mbLeft.textContent = 'LEVEL SELECT';
    mbRight.textContent = (G.state === 'fail') ? 'TRY AGAIN' : 'PLAY AGAIN';
    /* show which one a pad or keyboard would press */
    if (G.btnSel === 0) { mbLeft.classList.add('sel'); mbRight.classList.remove('sel'); }
    else { mbRight.classList.add('sel'); mbLeft.classList.remove('sel'); }
  }

  function boot() {
    cv = document.getElementById('screen');
    g = cv.getContext('2d');
    g.imageSmoothingEnabled = false;
    Save.load();
    Sfx.setEnabled(Save.get().sound !== false);
    Input.init();
    initMenuButtons();
    initPopupButton();
    Input.setTouchMode(Save.get().touchMode || 'auto');
    fitCanvas();
    window.addEventListener('resize', fitCanvas);
    /* audio contexts need a gesture before they will make noise */
    var wake = function () { Sfx.resume(); };
    window.addEventListener('keydown', wake, { once: true });
    window.addEventListener('pointerdown', wake, { once: true });
    /* start the cursor on the newest unlocked stage */
    G.sel = Util.clamp(Save.get().unlocked - 1, 0, LEVELS.length - 1);
    requestAnimationFrame(frame);
  }

  G.boot = boot;
  return G;
})();
window.addEventListener('DOMContentLoaded', Game.boot);
