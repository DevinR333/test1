/* Keyboard, gamepad, and a floating analog touch stick.
   The touch layer stays invisible until the screen is actually touched,
   so keyboard and controller players never see it. */
var Input = (function () {
  var MAP = {
    ArrowLeft: 'left', KeyA: 'left',
    ArrowRight: 'right', KeyD: 'right',
    ArrowUp: 'up', KeyW: 'up',
    ArrowDown: 'down', KeyS: 'down',
    Space: 'jump', KeyZ: 'jump', KeyK: 'jump',
    KeyX: 'attack', KeyJ: 'attack',
    Enter: 'confirm',
    Escape: 'pause', KeyP: 'pause',
    KeyM: 'mute', KeyR: 'restart'
  };
  var held = {}, fresh = {}, consumed = {};
  var stickX = 0;                 /* -1..1 from the touch stick or a pad */
  var host = null, stickEl = null, knobEl = null;
  var stickId = null, originX = 0, originY = 0;
  var RADIUS = 46, DEAD = 0.18;
  /* 'auto'  - the pad shows while you are touching and fades when you let go
     'always' - the pad stays put
     There is deliberately no "off": hiding the pad hid the pause button
     with it, which left no way back in. */
  var touchMode = 'auto';
  var sawTouch = false;
  var lastTouch = 0;
  var IDLE_MS = 1100;
  var mode = 'keyboard';          /* keyboard | touch | controller */
  var menuMode = false;           /* menus swap the pad for tappable UI */
  var tapConfirm = false;         /* result screens advance on any tap    */
  var taps = [];                  /* canvas taps waiting to be read       */
  var lastTapAt = 0;

  function set(name, on) {
    if (!name) return;
    if (on) { if (!held[name]) fresh[name] = true; held[name] = true; }
    else held[name] = false;
  }

  /* ---------------- keyboard ---------------- */
  window.addEventListener('keydown', function (e) {
    var n = MAP[e.code];
    if (n) {
      e.preventDefault();
      if (!e.repeat) set(n, true);
      if (mode !== 'controller') mode = 'keyboard';
      hideTouch();
    }
  });
  window.addEventListener('keyup', function (e) {
    var n = MAP[e.code];
    if (n) { e.preventDefault(); set(n, false); }
  });
  window.addEventListener('blur', function () { held = {}; stickX = 0; releaseStick(); });

  /* Any keyboard or pad input puts the touch controls away again. */
  function hideTouch() {
    if (sawTouch) { sawTouch = false; apply(); }
  }
  function apply() {
    if (!host) return;
    /* The pad exists for gameplay only. Outside it the stick zone would
       sit over the screen eating taps meant for menus. */
    if (!sawTouch || mode === 'controller' || menuMode) {
      host.classList.add('hidden');
      return;
    }
    host.classList.remove('hidden');
    var idle = !menuMode && (touchMode === 'auto') &&
               (Date.now() - lastTouch > IDLE_MS) && stickId === null;
    if (idle) host.classList.add('idle');
    else host.classList.remove('idle');
  }
  function touched() {
    lastTouch = Date.now();
    if (!sawTouch || mode !== 'touch') { sawTouch = true; mode = 'touch'; }
    apply();
  }

  /* A screen with nothing to choose should take a tap anywhere. Without
     this the pad is faded out on a results screen, and a tap only wakes
     it up again, which feels exactly like dead input. */
  function screenTap() {
    touched();
    if (tapConfirm) { set('confirm', true); }
  }

  /* ---------------- touch stick ---------------- */
  function moveKnob(dx, dy) {
    var d = Math.sqrt(dx * dx + dy * dy);
    if (d > RADIUS) { dx *= RADIUS / d; dy *= RADIUS / d; }
    if (knobEl) knobEl.style.transform = 'translate(' + dx + 'px,' + dy + 'px)';
    var v = dx / RADIUS;
    stickX = Math.abs(v) < DEAD ? 0 : Math.max(-1, Math.min(1, (v - Math.sign(v) * DEAD) / (1 - DEAD)));
    /* a firm push downward still works as "down" for menus */
    set('down', dy > RADIUS * 0.55);
  }
  function releaseStick() {
    stickId = null; stickX = 0;
    set('down', false);
    if (stickEl) stickEl.classList.remove('live');
    if (knobEl) knobEl.style.transform = 'translate(0,0)';
  }

  function bindStick(zone) {
    zone.addEventListener('pointerdown', function (e) {
      if (stickId !== null) return;
      if (e.pointerType === 'touch') touched();
      stickId = e.pointerId;
      originX = e.clientX; originY = e.clientY;
      stickEl.style.left = originX + 'px';
      stickEl.style.top = originY + 'px';
      stickEl.classList.add('live');
      moveKnob(0, 0);
      /* capture keeps the drag alive if the thumb slides off the zone;
         it can throw if the pointer is already gone, which must not
         take the stick down with it */
      try { zone.setPointerCapture(e.pointerId); } catch (err) { /* ignore */ }
      e.preventDefault();
    });
    zone.addEventListener('pointermove', function (e) {
      if (e.pointerId !== stickId) return;
      moveKnob(e.clientX - originX, e.clientY - originY);
      e.preventDefault();
    });
    function up(e) {
      if (e.pointerId !== stickId) return;
      releaseStick();
      e.preventDefault();
    }
    zone.addEventListener('pointerup', up);
    zone.addEventListener('pointercancel', up);
    zone.addEventListener('lostpointercapture', up);
  }

  function bindTouch() {
    host = document.getElementById('touch');
    if (!host) return;
    stickEl = document.getElementById('stick');
    knobEl = document.getElementById('knob');
    var zone = document.getElementById('stickzone');
    if (zone) bindStick(zone);
    bindTaps();

    var btns = host.querySelectorAll('.tbtn');
    for (var i = 0; i < btns.length; i++) {
      (function (b) {
        var key = b.getAttribute('data-key');
        var press = function (e) {
          e.preventDefault();
          set(key, true);
          if (key === 'jump') set('confirm', true);
        };
        var release = function (e) {
          e.preventDefault();
          set(key, false);
          if (key === 'jump') set('confirm', false);
        };
        b.addEventListener('pointerdown', function (e) {
          if (e.pointerType === 'touch') touched();
          press(e);
        });
        b.addEventListener('pointerup', release);
        b.addEventListener('pointercancel', release);
        b.addEventListener('pointerleave', release);
        /* belt and braces: some WebViews are unreliable with pointer events */
        b.addEventListener('touchstart', function (e) { touched(); press(e); }, { passive: false });
        b.addEventListener('touchend', release, { passive: false });
        b.addEventListener('touchcancel', release, { passive: false });
      })(btns[i]);
    }

    /* any touch reveals the pad; it fades again once you let go */
    window.addEventListener('touchstart', screenTap, { passive: true });
    window.addEventListener('touchmove', touched, { passive: true });
    window.addEventListener('touchend', function () {
      lastTouch = Date.now();
      set('confirm', false);
    }, { passive: true });
    /* mouse works the same way on a desktop results screen */
    window.addEventListener('pointerdown', function (e) {
      if (e.pointerType !== 'touch' && tapConfirm) set('confirm', true);
    });
    window.addEventListener('pointerup', function (e) {
      if (e.pointerType !== 'touch') set('confirm', false);
    });
    setInterval(apply, 200);
    apply();
  }

  /* ---------------- canvas taps ----------------
     Menus are driven by tapping what you can see. Pointer, touch and
     click are all wired up because WebViews disagree about which of
     them fire; a short guard stops one press counting twice. */
  function registerTap(clientX, clientY) {
    var now = Date.now();
    if (now - lastTapAt < 250) return;
    var cv = document.getElementById('screen');
    if (!cv) return;
    var r = cv.getBoundingClientRect();
    if (!r.width || !r.height) return;
    if (clientX < r.left || clientX > r.right || clientY < r.top || clientY > r.bottom) return;
    lastTapAt = now;
    taps.push({
      x: (clientX - r.left) / r.width * cv.width,
      y: (clientY - r.top) / r.height * cv.height
    });
    if (taps.length > 4) taps.shift();
  }

  function bindTaps() {
    var cv = document.getElementById('screen');
    if (!cv) return;
    cv.addEventListener('click', function (e) { registerTap(e.clientX, e.clientY); });
    cv.addEventListener('pointerdown', function (e) { registerTap(e.clientX, e.clientY); });
    cv.addEventListener('touchstart', function (e) {
      var t = e.changedTouches && e.changedTouches[0];
      if (t) registerTap(t.clientX, t.clientY);
    }, { passive: true });
  }

  /* ---------------- gamepad ---------------- */
  var padPrev = {};
  var padName = '';
  function pollPad() {
    if (!navigator.getGamepads) return;
    var pads = navigator.getGamepads();
    var gp = null;
    for (var i = 0; i < pads.length; i++) if (pads[i]) { gp = pads[i]; break; }
    if (!gp) { if (mode === 'controller') { mode = 'keyboard'; padName = ''; } return; }
    padName = gp.id || 'Controller';
    var active = false;
    var ax = gp.axes && gp.axes.length ? gp.axes[0] : 0;
    if (Math.abs(ax) > 0.25) { stickX = ax; active = true; }
    var B = gp.buttons || [];
    function btn(i) { return B[i] && B[i].pressed; }
    var map = { 14: 'left', 15: 'right', 12: 'up', 13: 'down',
                0: 'jump', 2: 'attack', 1: 'attack', 3: 'jump',
                9: 'pause', 8: 'restart' };
    for (var k in map) {
      var down = btn(Number(k));
      if (down !== !!padPrev[k]) {
        set(map[k], down);
        if (map[k] === 'jump' && down) set('confirm', true);
        if (map[k] === 'jump' && !down) set('confirm', false);
        padPrev[k] = down;
        if (down) active = true;
      }
    }
    if (active) {
      if (mode !== 'controller') { mode = 'controller'; }
      hideTouch();
    }
  }

  return {
    init: bindTouch,
    pollPad: pollPad,
    /* 'auto' or 'always'; never an off switch, so the pause button
       can never become unreachable */
    setTouchMode: function (m) { touchMode = m; lastTouch = Date.now(); apply(); },
    /* menus keep the pad up; tapConfirm is for screens whose only
       action is "carry on" */
    setMenuMode: function (on, tap) {
      menuMode = !!on;
      tapConfirm = !!tap;
      apply();
    },
    /* read and consume the next tap, in view coordinates */
    takeTap: function () { return taps.length ? taps.shift() : null; },
    peekTap: function () { return taps.length ? taps[0] : null; },
    clearTaps: function () { taps.length = 0; },
    touchMode: function () { return touchMode; },
    mode: function () { return mode; },
    padName: function () { return padName; },
    /* a short, friendly label for whatever is driving the game */
    schemeLabel: function () {
      if (mode === 'controller') return 'CONTROLLER';
      if (mode === 'touch') return 'TOUCH';
      return 'KEYBOARD';
    },
    hasTouch: function () { return ('ontouchstart' in window) || navigator.maxTouchPoints > 0; },
    down: function (n) { return !!held[n]; },
    pressed: function (n) {
      if (fresh[n] && !consumed[n]) { consumed[n] = true; return true; }
      return false;
    },
    endFrame: function () { fresh = {}; consumed = {}; },
    /* -1..1; analog from a stick, full tilt from keys */
    axis: function () {
      var k = (held.right ? 1 : 0) - (held.left ? 1 : 0);
      if (k !== 0) return k;
      return stickX;
    }
  };
})();
