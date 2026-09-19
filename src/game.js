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
    pauseSel: 0,
    shopMsg: '', shopMsgT: 0,
    t: 0,
    lastStage: 0,
    resultCoins: 0,
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

  function fitCanvas() {
    var vw = window.innerWidth, vh = window.innerHeight;
    if (!vw || !vh) return;

    var scale = Math.min(vw / BASE_W, vh / BASE_H);
    var w = Math.round(vw / scale / 2) * 2;
    var h = Math.round(vh / scale / 2) * 2;
    VIEW_W = Math.max(BASE_W, Math.min(760, w));
    VIEW_H = Math.max(BASE_H, Math.min(440, h));

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
    if (state === 'map') { Sfx.stopSong(); }
    if (state === 'title') { Sfx.stopSong(); }
  }

  G.startStage = function (index) {
    G.lastStage = index;
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

  function frame(now) {
    if (!last) last = now;
    var dt = Math.min(now - last, 250);
    last = now;
    acc += dt;
    var guard = 0;
    while (acc >= STEP && guard++ < 5) { update(); acc -= STEP; }
    draw();
    requestAnimationFrame(frame);
  }

  function boot() {
    cv = document.getElementById('screen');
    g = cv.getContext('2d');
    g.imageSmoothingEnabled = false;
    Save.load();
    Sfx.setEnabled(Save.get().sound !== false);
    Input.init();
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
