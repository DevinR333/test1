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
  var touchPref = null;           /* null = auto, true/false = forced */
  var sawTouch = false;

  function set(name, on) {
    if (!name) return;
    if (on) { if (!held[name]) fresh[name] = true; held[name] = true; }
    else held[name] = false;
  }

  /* ---------------- keyboard ---------------- */
  window.addEventListener('keydown', function (e) {
    var n = MAP[e.code];
    if (n) { e.preventDefault(); if (!e.repeat) set(n, true); hideTouch(); }
  });
  window.addEventListener('keyup', function (e) {
    var n = MAP[e.code];
    if (n) { e.preventDefault(); set(n, false); }
  });
  window.addEventListener('blur', function () { held = {}; stickX = 0; releaseStick(); });

  /* Any keyboard or pad input puts the touch controls away again. */
  function hideTouch() {
    if (touchPref === null && sawTouch) { sawTouch = false; apply(); }
  }
  function apply() {
    if (!host) return;
    var on = (touchPref === null) ? sawTouch : touchPref;
    if (on) host.classList.remove('hidden');
    else host.classList.add('hidden');
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
        b.addEventListener('pointerdown', press);
        b.addEventListener('pointerup', release);
        b.addEventListener('pointercancel', release);
        b.addEventListener('pointerleave', release);
      })(btns[i]);
    }

    /* first real touch anywhere reveals the pad */
    window.addEventListener('touchstart', function () {
      if (!sawTouch) { sawTouch = true; apply(); }
    }, { passive: true });
    apply();
  }

  /* ---------------- gamepad ---------------- */
  var padPrev = {};
  function pollPad() {
    if (!navigator.getGamepads) return;
    var pads = navigator.getGamepads();
    var gp = null;
    for (var i = 0; i < pads.length; i++) if (pads[i]) { gp = pads[i]; break; }
    if (!gp) return;
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
    if (active) hideTouch();
  }

  return {
    init: bindTouch,
    pollPad: pollPad,
    setTouchVisible: function (on) { touchPref = on; apply(); },
    touchIsOn: function () { return (touchPref === null) ? sawTouch : touchPref; },
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
