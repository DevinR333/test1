/* Keyboard + touch input. Edge-triggered reads via Input.pressed(). */
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
  var anyKeyFlag = false;

  function set(name, on) {
    if (!name) return;
    if (on) { if (!held[name]) fresh[name] = true; held[name] = true; anyKeyFlag = true; }
    else { held[name] = false; }
  }

  window.addEventListener('keydown', function (e) {
    var n = MAP[e.code];
    if (n) { e.preventDefault(); if (!e.repeat) set(n, true); }
  });
  window.addEventListener('keyup', function (e) {
    var n = MAP[e.code];
    if (n) { e.preventDefault(); set(n, false); }
  });
  window.addEventListener('blur', function () { held = {}; });

  function bindTouch() {
    var host = document.getElementById('touch');
    if (!host) return;
    var btns = host.querySelectorAll('.tbtn');
    for (var i = 0; i < btns.length; i++) {
      (function (b) {
        var key = b.getAttribute('data-key');
        var press = function (e) { e.preventDefault(); set(key, true); if (key === 'jump') set('confirm', true); };
        var release = function (e) { e.preventDefault(); set(key, false); set('confirm', false); };
        b.addEventListener('touchstart', press, { passive: false });
        b.addEventListener('touchend', release, { passive: false });
        b.addEventListener('touchcancel', release, { passive: false });
        b.addEventListener('mousedown', press);
        b.addEventListener('mouseup', release);
        b.addEventListener('mouseleave', release);
      })(btns[i]);
    }
    if ('ontouchstart' in window) host.classList.remove('hidden');
  }

  return {
    init: bindTouch,
    down: function (n) { return !!held[n]; },
    pressed: function (n) {
      if (fresh[n] && !consumed[n]) { consumed[n] = true; return true; }
      return false;
    },
    anyPressed: function () {
      return !!(fresh.jump || fresh.attack || fresh.confirm);
    },
    /* Called at the end of each fixed update. */
    endFrame: function () { fresh = {}; consumed = {}; anyKeyFlag = false; },
    /* Axis helper: -1, 0 or 1. */
    axis: function () { return (held.right ? 1 : 0) - (held.left ? 1 : 0); }
  };
})();
