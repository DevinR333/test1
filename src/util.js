/* Black Lab Blade - small helpers + a pixel-grid sprite baker. */
var Util = (function () {
  function clamp(v, a, b) { return v < a ? a : (v > b ? b : v); }
  function lerp(a, b, t) { return a + (b - a) * t; }
  function approach(v, target, step) {
    if (v < target) return Math.min(v + step, target);
    if (v > target) return Math.max(v - step, target);
    return v;
  }
  function rand(a, b) { return a + Math.random() * (b - a); }
  function randInt(a, b) { return Math.floor(a + Math.random() * (b - a + 1)); }
  function pick(arr) { return arr[Math.floor(Math.random() * arr.length)]; }
  function aabb(a, b) {
    return a.x < b.x + b.w && a.x + a.w > b.x && a.y < b.y + b.h && a.y + a.h > b.y;
  }
  function dist(ax, ay, bx, by) { var dx = ax - bx, dy = ay - by; return Math.sqrt(dx * dx + dy * dy); }

  function canvas(w, h) {
    var c = document.createElement('canvas');
    c.width = w; c.height = h;
    c.getContext('2d').imageSmoothingEnabled = false;
    return c;
  }

  /* rows: array of equal-length strings, pal: char -> css color ('.' = transparent) */
  function sprite(rows, pal) {
    var w = rows[0].length, h = rows.length;
    for (var i = 0; i < h; i++) {
      if (rows[i].length !== w) {
        throw new Error('sprite row ' + i + ' is ' + rows[i].length + ' wide, expected ' + w + ': "' + rows[i] + '"');
      }
    }
    var c = canvas(w, h), g = c.getContext('2d');
    for (var y = 0; y < h; y++) {
      for (var x = 0; x < w; x++) {
        var col = pal[rows[y][x]];
        if (col) { g.fillStyle = col; g.fillRect(x, y, 1, 1); }
      }
    }
    return c;
  }

  function flip(src) {
    var c = canvas(src.width, src.height), g = c.getContext('2d');
    g.translate(src.width, 0); g.scale(-1, 1); g.drawImage(src, 0, 0);
    return c;
  }

  /* White silhouette of a sprite, used for hit flashes. */
  function silhouette(src, color) {
    var c = canvas(src.width, src.height), g = c.getContext('2d');
    g.drawImage(src, 0, 0);
    g.globalCompositeOperation = 'source-in';
    g.fillStyle = color || '#fff';
    g.fillRect(0, 0, c.width, c.height);
    return c;
  }

  /* Deterministic value noise so generated tiles look the same every run. */
  function seeded(seed) {
    var s = seed >>> 0;
    return function () {
      s ^= s << 13; s >>>= 0;
      s ^= s >> 17;
      s ^= s << 5; s >>>= 0;
      return s / 4294967296;
    };
  }

  return {
    clamp: clamp, lerp: lerp, approach: approach, rand: rand, randInt: randInt,
    pick: pick, aabb: aabb, dist: dist, canvas: canvas, sprite: sprite,
    flip: flip, silhouette: silhouette, seeded: seeded
  };
})();
