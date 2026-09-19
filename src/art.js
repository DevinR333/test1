/* All artwork is generated at runtime: hand-written pixel grids run
   through an automatic shader, plus procedurally detailed tiles.
   No image files, no external assets. */
var Art = (function () {
  var S = Util.sprite, F = Util.flip;

  /* ---------------------------------------------------------------
     PIXEL SHADER
     Flat fill colours are the "clay"; these passes carve tone into
     them so sprites read as lit volumes instead of silhouettes.
  --------------------------------------------------------------- */
  function hex(c) {
    return [parseInt(c.substr(1, 2), 16), parseInt(c.substr(3, 2), 16), parseInt(c.substr(5, 2), 16)];
  }
  function px(d, w, x, y) { return (y * w + x) * 4; }

  /* Replace `target` pixels with a 4-stop ramp keyed on how deep the
     pixel sits below the sprite's lit top edge, then darken underhangs. */
  function shade(cv, target, ramp) {
    var g = cv.getContext('2d'), w = cv.width, h = cv.height;
    var img = g.getImageData(0, 0, w, h), d = img.data;
    var t = hex(target);
    var R = ramp.map(hex);
    function isT(x, y) {
      if (x < 0 || y < 0 || x >= w || y >= h) return false;
      var i = px(d, w, x, y);
      return d[i + 3] > 0 && d[i] === t[0] && d[i + 1] === t[1] && d[i + 2] === t[2];
    }
    function solid(x, y) {
      if (x < 0 || y < 0 || x >= w || y >= h) return false;
      return d[px(d, w, x, y) + 3] > 0;
    }
    var out = new Uint8ClampedArray(d);
    for (var y = 0; y < h; y++) {
      for (var x = 0; x < w; x++) {
        if (!isT(x, y)) continue;
        /* how far below the top of this column of body we are */
        var depth = 0;
        while (depth < 5 && solid(x, y - depth - 1)) depth++;
        var tone;
        if (!solid(x, y - 1)) tone = R[0];            /* catching the light */
        else if (depth <= 1) tone = R[0];
        else if (depth === 2) tone = R[1];
        else if (depth === 3) tone = R[2];
        else tone = R[3];
        if (!solid(x, y + 1)) tone = R[3];            /* underside falls off */
        var i = px(d, w, x, y);
        out[i] = tone[0]; out[i + 1] = tone[1]; out[i + 2] = tone[2];
      }
    }
    img.data.set(out);
    g.putImageData(img, 0, 0);
    return cv;
  }

  /* A 1px light along upper-left facing edges. */
  function rim(cv, color, skip) {
    var g = cv.getContext('2d'), w = cv.width, h = cv.height;
    var img = g.getImageData(0, 0, w, h), d = img.data;
    var c = hex(color), sk = (skip || []).map(hex);
    function solid(x, y) {
      if (x < 0 || y < 0 || x >= w || y >= h) return false;
      return d[px(d, w, x, y) + 3] > 0;
    }
    var out = new Uint8ClampedArray(d);
    for (var y = 0; y < h; y++) {
      for (var x = 0; x < w; x++) {
        var i = px(d, w, x, y);
        if (d[i + 3] === 0) continue;
        var keep = false;
        for (var k = 0; k < sk.length; k++) {
          if (d[i] === sk[k][0] && d[i + 1] === sk[k][1] && d[i + 2] === sk[k][2]) keep = true;
        }
        if (keep) continue;
        /* only the lit edge, and never on the outline itself */
        if (!solid(x, y - 1) && solid(x, y + 1) && solid(x + 1, y) && solid(x - 1, y)) {
          out[i] = c[0]; out[i + 1] = c[1]; out[i + 2] = c[2];
        }
      }
    }
    img.data.set(out);
    g.putImageData(img, 0, 0);
    return cv;
  }

  /* ---------------------------------------------------------------
     THE HERO - a black lab who carries his blade in his jaws.
  --------------------------------------------------------------- */
  var TORSO = [
    '..................',
    '.kk........kkkkk..',
    '.kbk.....kbbbbbbk.',
    '.kbk....kbdddbbbbk',
    '.kbkkkkkkbdddbebbk',
    '.kbbbbbbbbdddbbbnk',
    '.kbbbbbbbbbddbbwnk',
    '.kbbbbbbbbbbbrttkk',
    '.kbbbbbbbbbbbrRrk.',
    '.kbbbbbbbbbbbbk...',
    '..kbbbbbbbbbk.....'
  ];
  var TORSO_SWING = [
    '..................',
    '.kk...............',
    '.kbk.......kkkkk..',
    '.kbk.....kbdddbbk.',
    '.kbkkkkkkbbdddbebk',
    '.kbbbbbbbbbdddbbnk',
    '.kbbbbbbbbbbddbwnk',
    '.kbbbbbbbbbbbrttkk',
    '.kbbbbbbbbbbbrRrk.',
    '.kbbbbbbbbbbbbk...',
    '..kbbbbbbbbbk.....'
  ];
  var LEGS = {
    stand: ['..kbk...kkbbk.....', '..kbk....kbbk.....', '..kkk....kkkk.....'],
    run0:  ['.kbk.....kbbk.....', '.kbk......kbbk....', '.kkk......kkk.....'],
    run1:  ['..kbk...kbbk......', '..kbk...kbbk......', '..kkk...kkkk......'],
    run2:  ['...kbk..kbbk......', '...kbk...kbbk.....', '...kkk...kkk......'],
    run3:  ['..kbk....kbbk.....', '.kbk.....kbbk.....', '.kkk.....kkkk.....'],
    jump:  ['..kbk...kbbk......', '..kkk...kbbk......', '.........kkk......'],
    fall:  ['..kbk....kbbk.....', '.kbk......kbbk....', '.kkk......kkk.....'],
    sit:   ['..kbbk..kkbbk.....', '..kbbk...kbbk.....', '..kkkk...kkkk.....']
  };

  /* A black lab is not flat black - it is blue-black in the light and
     warm brown where the light dies. */
  var LAB = {
    k: '#06060a',            /* outline        */
    b: '#262430',            /* coat base      */
    d: '#0f0e15',            /* ear, in shadow */
    e: '#f0ad3c',            /* amber eye      */
    n: '#4a4656',            /* muzzle         */
    w: '#f4efe2',            /* tooth          */
    r: '#b02f3c',            /* collar         */
    R: '#e8c45c',            /* brass tag      */
    t: '#dc6b84'             /* tongue         */
  };
  var LAB_RAMP = ['#43404f', '#322f3d', '#26232f', '#171522'];
  var BONE = {
    k: '#2e2b26', b: '#d9d1b8', d: '#a89e83', e: '#d94a3f',
    n: '#8e8676', w: '#fffaf0', r: '#6b5a34', R: '#c9a24a', t: '#c08a6e'
  };
  var BONE_RAMP = ['#efe8d2', '#d9d1b8', '#bdb49a', '#968c76'];

  function buildDog(pal, ramp, rimCol) {
    function frame(torso, legsKey) {
      var c = S(torso.concat(LEGS[legsKey]), pal);
      shade(c, pal.b, ramp);
      rim(c, rimCol, [pal.k, pal.e, pal.r, pal.R, pal.t, pal.w, pal.d]);
      return c;
    }
    var f = {
      idle: frame(TORSO, 'stand'),
      sit: frame(TORSO, 'sit'),
      run: [frame(TORSO, 'run0'), frame(TORSO, 'run1'), frame(TORSO, 'run2'), frame(TORSO, 'run3')],
      jump: frame(TORSO, 'jump'),
      fall: frame(TORSO, 'fall'),
      swing: frame(TORSO_SWING, 'stand'),
      swingAir: frame(TORSO_SWING, 'fall')
    };
    var out = { right: f, left: {}, flash: {} };
    out.left.idle = F(f.idle); out.left.sit = F(f.sit);
    out.left.jump = F(f.jump); out.left.fall = F(f.fall);
    out.left.swing = F(f.swing); out.left.swingAir = F(f.swingAir);
    out.left.run = f.run.map(F);
    out.flash.right = Util.silhouette(f.idle, '#ffffff');
    out.flash.left = F(out.flash.right);
    return out;
  }

  /* ---------------------------------------------------------------
     ENEMIES
  --------------------------------------------------------------- */
  function grubSprite() {
    var c = S([
      '..............',
      '...oo....oo...',
      '..oossoosoo...',
      '.ogGGGGGGGGgo.',
      '.oGGGGGGGGGGo.',
      'ooGGwGGGGwGGoo',
      'ooGGkGGGGkGGoo',
      '.oGGGGGGGGGGo.',
      '.ogGGGGGGGGgo.',
      '..oooooooooo..',
      '..o.oo..oo.o..',
      '..............'
    ], { o: '#132408', g: '#3f7a26', G: '#64b03c', w: '#f4f0d8', k: '#0e1a08', s: '#8fd455' });
    shade(c, '#64b03c', ['#8fd455', '#6fbf46', '#4f9130', '#356a20']);
    rim(c, '#b6ef7a', ['#132408', '#f4f0d8', '#0e1a08']);
    return c;
  }
  var GRUB = grubSprite();

  function batSprite(rows) {
    var c = S(rows, { o: '#150a1e', p: '#5e3d82', P: '#8f61bd', w: '#ffe27a' });
    shade(c, '#8f61bd', ['#b487e0', '#9a6cc6', '#7a4fa4', '#583876']);
    shade(c, '#5e3d82', ['#7d55a8', '#66458c', '#4e3370', '#3a2554']);
    rim(c, '#d0a8f0', ['#150a1e', '#ffe27a']);
    return c;
  }
  var BATLING = batSprite([
    '................',
    '.o............o.',
    'opo...oooo...opo',
    'oppo.oPPPPo.oppo',
    '.oppooPwPwPoppo.',
    '..opppPPPPPPppo.',
    '...oppPPPPPPpo..',
    '....ooPPPPPPoo..',
    '......oooooo....',
    '................'
  ]);
  var BATLING2 = batSprite([
    '................',
    '................',
    '......oooo......',
    '.oo..oPPPPo..oo.',
    'oppooPwPwPPoppo.',
    'opppPPPPPPPppo..',
    '.oppPPPPPPpo....',
    '..ooPPPPPPoo....',
    '....oooooo......',
    '................'
  ]);

  function thornSprite() {
    var c = S([
      '................',
      '.....oooo.......',
      '....opppo.......',
      '...oppPPpo......',
      '...opPPPPpo.....',
      '..oppPPwwPPpo...',
      '..oppPPwwPPpo...',
      '...opPPPPpo.....',
      '...oppPPpo......',
      '....osso........',
      '....osso........',
      '...ossso........',
      '..osssso........',
      '..ossssoo.......',
      '.oossssooo......',
      '................'
    ], { o: '#180f14', p: '#8a3f62', P: '#c25f88', w: '#2a0f1c', s: '#3f6b28' });
    shade(c, '#c25f88', ['#e78bae', '#cf7095', '#a9527a', '#7d3a59']);
    shade(c, '#3f6b28', ['#5f9138', '#47762b', '#365c22', '#25431a']);
    rim(c, '#f4b0cc', ['#180f14', '#2a0f1c']);
    return c;
  }
  var THORN = thornSprite();

  var THORNBALL = S([
    '.oo.',
    'oPPo',
    'oPPo',
    '.oo.'
  ], { o: '#2a0f1c', P: '#e78bae' });

  /* ---------------------------------------------------------------
     PICKUPS + HUD ICONS
  --------------------------------------------------------------- */
  var COIN = S([
    '..oooo..',
    '.oggggo.',
    'oglggggo',
    'oglggggo',
    'oggggggo',
    '.oggggo.',
    '..oooo..'
  ], { o: '#6b3d08', g: '#f0c040', l: '#fff3bc' });
  shade(COIN, '#f0c040', ['#ffe27a', '#f5cb52', '#d9a52e', '#a8781c']);

  function boneSprite(pal, base, ramp) {
    var c = S([
      'oo......oo',
      'owwo..owwo',
      'owwwwwwwwo',
      'owwwwwwwwo',
      'owwo..owwo',
      'oo......oo'
    ], pal);
    if (base) shade(c, base, ramp);
    return c;
  }
  var BONE_GOLD = boneSprite({ o: '#6b4a10', w: '#ffd75e' }, '#ffd75e',
    ['#fff0a8', '#ffd75e', '#e0b038', '#a87d1e']);
  var BONE_GREY = boneSprite({ o: '#2e2b26', w: '#6b6458' });

  var HEART_FULL = S([
    '.oo..oo.',
    'olrrrrro',
    'orrrrrro',
    '.orrrro.',
    '..orro..',
    '...oo...'
  ], { o: '#40060f', r: '#e0424f', l: '#ffb0b6' });
  shade(HEART_FULL, '#e0424f', ['#ff8a92', '#ea5a65', '#c8323e', '#93202b']);

  var HEART_EMPTY = S([
    '.oo..oo.',
    'oddddddo',
    'oddddddo',
    '.oddddo.',
    '..oddo..',
    '...oo...'
  ], { o: '#1e0c12', d: '#402530' });

  var MEAT = S([
    '..oooo..',
    '.orrrro.',
    'orrllrro',
    'orrrrrro',
    'owwwwwwo',
    '.oowwoo.',
    '..o..o..'
  ], { o: '#3a1210', r: '#c8513f', l: '#ec9a76', w: '#f0e2cc' });
  shade(MEAT, '#c8513f', ['#e87d5e', '#d0604a', '#a83f32', '#7c2c23']);

  /* ---------------------------------------------------------------
     THE BLADE
  --------------------------------------------------------------- */
  var BLADES = [
    { name: 'Chewed Stick', len: 9,  w: 2, blade: '#9a7045', edge: '#c9a273', dark: '#5f4526', hilt: '#4a3520', glow: null },
    { name: 'Iron Fang',    len: 11, w: 2, blade: '#aeb8c4', edge: '#eef4fa', dark: '#6d7783', hilt: '#5d4728', glow: null },
    { name: 'Emberblade',   len: 13, w: 3, blade: '#e0732c', edge: '#ffd08a', dark: '#96401a', hilt: '#4c2314', glow: 'rgba(255,140,50,.35)' },
    { name: 'Stormfang',    len: 15, w: 3, blade: '#7fd7f0', edge: '#eafdff', dark: '#3b8fad', hilt: '#26405a', glow: 'rgba(120,220,255,.38)' }
  ];

  function drawBlade(g, px2, py2, angle, tier, facing) {
    var b = BLADES[Util.clamp(tier, 0, BLADES.length - 1)];
    g.save();
    g.translate(px2, py2);
    g.rotate(angle * facing);
    g.scale(facing, 1);
    if (b.glow) {
      g.fillStyle = b.glow;
      g.fillRect(1, -b.w, b.len + 2, b.w + 2);
    }
    g.fillStyle = b.hilt;
    g.fillRect(-3, -1, 4, 3);          /* grip   */
    g.fillStyle = '#2b2118';
    g.fillRect(0, -2, 1, 5);           /* guard  */
    g.fillStyle = b.dark;
    g.fillRect(2, -b.w + 1, b.len, b.w);
    g.fillStyle = b.blade;
    g.fillRect(2, -b.w + 1, b.len, b.w - 1);
    g.fillStyle = b.edge;
    g.fillRect(2, -b.w + 1, b.len, 1);
    g.fillRect(2 + b.len, -b.w + 2, 1, 1);
    g.restore();
  }

  return {
    LAB: LAB, BONE: BONE, BLADES: BLADES, shade: shade, rim: rim,
    dog: null, hound: null,
    GRUB: GRUB, BATLING: [BATLING, BATLING2], THORN: THORN, THORN_L: F(THORN),
    THORNBALL: THORNBALL,
    COIN: COIN, BONE_GOLD: BONE_GOLD, BONE_GREY: BONE_GREY,
    HEART_FULL: HEART_FULL, HEART_EMPTY: HEART_EMPTY, MEAT: MEAT,
    buildDog: buildDog, drawBlade: drawBlade
  };
})();
Art.dog = Art.buildDog(Art.LAB, ['#43404f', '#322f3d', '#26232f', '#171522'], '#5d5a70');
Art.hound = Art.buildDog(Art.BONE, ['#efe8d2', '#d9d1b8', '#bdb49a', '#968c76'], '#fffbf0');
Art.GRUB_L = Util.flip(Art.GRUB);
Art.GRUB_FLASH = Util.silhouette(Art.GRUB, '#ffffff');
Art.BAT_FLASH = Util.silhouette(Art.BATLING[0], '#ffffff');
Art.THORN_FLASH = Util.silhouette(Art.THORN, '#ffffff');

/* ===================================================================
   TILESETS
   A block is drawn as a body plus autotiled trim: moulding on exposed
   tops, quoined edges on exposed sides, corbels underneath. Structures
   therefore read as built masonry rather than cut-out squares.
=================================================================== */
Art.THEMES = {
  meadow: {
    sky: ['#68c6e6', '#a6dfd6', '#f2e4b0'],
    base: '#7d5636', light: '#a2764a', hi: '#c29a68', dark: '#4f3118',
    deep: '#35200e', mortar: '#3a2311',
    cap: '#59a63f', capLight: '#86d75e', capDark: '#2f6b26',
    trim: '#8a6240', trimHi: '#bb8d5e', trimDark: '#43290f',
    wall: null,
    hazard: ['#2b78bd', '#5cbfe8', '#a6e4ff'],
    ambient: 'rgba(255,232,168,.08)', glow: 'rgba(255,214,140,.55)',
    vignette: 0.30
  },
  cavern: {
    sky: ['#0b1220', '#121a2e', '#1d2742'],
    base: '#6d7a88', light: '#9aa8b5', hi: '#c6d3de', dark: '#3d4654',
    deep: '#242b38', mortar: '#1f2530',
    cap: '#7d8a99', capLight: '#adbac7', capDark: '#434d5c',
    trim: '#78859a', trimHi: '#b2bfd0', trimDark: '#2d3442',
    wall: { base: '#1b2234', light: '#262f46', dark: '#121724', mortar: '#0e121c' },
    hazard: ['#57289b', '#9a5fd6', '#ceaaf5'],
    ambient: 'rgba(120,170,255,.07)', glow: 'rgba(150,210,255,.50)',
    vignette: 0.48
  },
  keep: {
    sky: ['#120d1e', '#1d1430', '#2c1a38'],
    base: '#8a7566', light: '#b9a18c', hi: '#e0cdb6', dark: '#53412f',
    deep: '#32261c', mortar: '#2b2119',
    cap: '#9c8570', capLight: '#cbb59a', capDark: '#5c4835',
    trim: '#a08a72', trimHi: '#dcc6a8', trimDark: '#3d2f22',
    wall: { base: '#2a2440', light: '#372f52', dark: '#1a1628', mortar: '#141020' },
    hazard: ['#8f1f34', '#d94f3a', '#ffa863'],
    ambient: 'rgba(255,150,90,.07)', glow: 'rgba(255,175,95,.60)',
    vignette: 0.46
  }
};
Art.THEMES.cavern.capLight = '#8b9abc';

Art._tileCache = {};

Art.tiles = function (themeName) {
  if (Art._tileCache[themeName]) return Art._tileCache[themeName];
  var T = Art.THEMES[themeName], N = 16;
  function nc() { return Util.canvas(N, N); }
  function tone(hexc, amt) {
    var r = Util.clamp(parseInt(hexc.substr(1, 2), 16) + amt, 0, 255);
    var g2 = Util.clamp(parseInt(hexc.substr(3, 2), 16) + amt, 0, 255);
    var b2 = Util.clamp(parseInt(hexc.substr(5, 2), 16) + amt, 0, 255);
    return 'rgb(' + r + ',' + g2 + ',' + b2 + ')';
  }

  /* ---- interior masonry ---- */
  function masonry(g, rnd, C, offset) {
    g.fillStyle = C.mortar; g.fillRect(0, 0, N, N);
    for (var course = 0; course < 2; course++) {
      var y0 = course * 8;
      var shift = ((course + offset) % 2) ? 5 : 0;
      var x = -shift;
      while (x < N) {
        var bw = (x === -shift) ? 5 + shift : (rnd() > 0.5 ? 9 : 6);
        bw = Math.min(bw, N - x);
        if (bw > 1) {
          g.fillStyle = tone(C.base, Math.floor(rnd() * 5 - 2) * 5);
          g.fillRect(x, y0, bw - 1, 7);
          g.fillStyle = C.light; g.fillRect(x, y0, bw - 1, 1); g.fillRect(x, y0, 1, 6);
          g.fillStyle = C.dark;
          g.fillRect(x, y0 + 6, bw - 1, 1); g.fillRect(x + bw - 2, y0 + 1, 1, 6);
          for (var s = 0; s < 3; s++) {
            if (rnd() > 0.5) {
              g.fillStyle = rnd() > 0.5 ? C.dark : C.light;
              g.fillRect(x + 1 + Math.floor(rnd() * Math.max(1, bw - 3)),
                         y0 + 2 + Math.floor(rnd() * 3), 1, 1);
            }
          }
        }
        x += bw;
      }
    }
    if (rnd() > 0.4) {
      g.fillStyle = C.deep;
      var cx = 2 + Math.floor(rnd() * 12), cy = 1 + Math.floor(rnd() * 10);
      for (var k = 0; k < 4 + Math.floor(rnd() * 4); k++) {
        g.fillRect(cx, cy + k, 1, 1);
        if (rnd() > 0.5) cx += rnd() > 0.5 ? 1 : -1;
      }
    }
  }

  /* ---- packed earth: clumped, never banded ---- */
  function earth(g, rnd, C) {
    g.fillStyle = C.base; g.fillRect(0, 0, N, N);
    for (var i = 0; i < 9; i++) {         /* soil clumps */
      var cx = Math.floor(rnd() * N), cy = Math.floor(rnd() * N);
      var rad = 2 + Math.floor(rnd() * 3);
      g.fillStyle = tone(C.base, rnd() > 0.5 ? -14 : 12);
      for (var dy = -rad; dy <= rad; dy++) {
        var wd = rad - Math.abs(dy);
        if (wd > 0) g.fillRect(cx - wd, cy + dy, wd * 2, 1);
      }
    }
    for (i = 0; i < 4; i++) {             /* embedded stones */
      var sx = 1 + Math.floor(rnd() * 11), sy = 2 + Math.floor(rnd() * 11);
      var sw = 3 + Math.floor(rnd() * 3), sh = 2 + Math.floor(rnd() * 2);
      g.fillStyle = C.dark; g.fillRect(sx, sy, sw, sh);
      g.fillStyle = C.light; g.fillRect(sx, sy, sw, 1);
      g.fillStyle = C.hi; g.fillRect(sx, sy, 1, 1);
      g.fillStyle = C.deep; g.fillRect(sx, sy + sh - 1, sw, 1);
    }
    for (i = 0; i < 26; i++) {            /* grit */
      g.fillStyle = rnd() > 0.5 ? C.deep : C.light;
      g.fillRect(Math.floor(rnd() * N), Math.floor(rnd() * N), 1, 1);
    }
    if (rnd() > 0.6) {                    /* a root */
      var rx = Math.floor(rnd() * 12) + 2, ry = Math.floor(rnd() * 8) + 4;
      g.fillStyle = C.deep;
      for (var r = 0; r < 6; r++) { g.fillRect(rx + r, ry, 1, 1); if (rnd() > 0.6) ry += 1; }
    }
  }

  function body(rnd, offset) {
    var c = nc(), g = c.getContext('2d');
    if (themeName === 'meadow') earth(g, rnd, T); else masonry(g, rnd, T, offset);
    return c;
  }

  var solid = [], cap = [];
  for (var v = 0; v < 4; v++) {
    solid.push(body(Util.seeded(themeName.charCodeAt(0) * 131 + v * 7717 + 3), v));
  }

  /* ---- exposed top: turf, or a dressed stone moulding ---- */
  for (v = 0; v < 3; v++) {
    var rnd2 = Util.seeded(themeName.charCodeAt(1) * 977 + v * 4231 + 11);
    var c2 = body(rnd2, v), g2 = c2.getContext('2d');
    if (themeName === 'meadow') {
      g2.fillStyle = T.capDark; g2.fillRect(0, 0, N, 7);
      g2.fillStyle = T.cap; g2.fillRect(0, 0, N, 5);
      g2.fillStyle = T.capLight; g2.fillRect(0, 0, N, 2);
      for (var x2 = 0; x2 < N; x2++) {
        var d2 = Math.floor(rnd2() * 3);
        g2.fillStyle = T.capDark; g2.fillRect(x2, 5 + d2, 1, 3);
        if (rnd2() > 0.66) { g2.fillStyle = T.capLight; g2.fillRect(x2, d2, 1, 2); }
        if (rnd2() > 0.88) { g2.fillStyle = '#c8e87a'; g2.fillRect(x2, 1, 1, 1); }
      }
    } else {
      /* moulded cornice: highlight, dentils, shadow */
      g2.fillStyle = T.cap; g2.fillRect(0, 0, N, 5);
      g2.fillStyle = T.capLight; g2.fillRect(0, 0, N, 2);
      g2.fillStyle = T.hi; g2.fillRect(0, 0, N, 1);
      g2.fillStyle = T.capDark;
      for (var dnt = 1; dnt < N; dnt += 4) g2.fillRect(dnt, 3, 2, 2);
      g2.fillStyle = T.mortar; g2.fillRect(0, 5, N, 1);
      g2.fillStyle = T.dark; g2.fillRect(0, 6, N, 1);
      for (var x3 = 0; x3 < N; x3++) {      /* moss creeping over the lip */
        if (rnd2() > 0.7) {
          g2.fillStyle = themeName === 'cavern' ? '#3f7a6a' : '#4a7a3a';
          g2.fillRect(x3, 4 + Math.floor(rnd2() * 2), 1, 1 + Math.floor(rnd2() * 3));
        }
      }
    }
    cap.push(c2);
  }

  /* ---- autotile trim overlays (transparent) ---- */
  function overlay(fn) { var c = nc(); fn(c.getContext('2d')); return c; }

  /* quoined side edges - alternating long and short blocks */
  var trimL = [0, 1].map(function (k) {
    return overlay(function (g) {
      var wd = k ? 5 : 3;
      g.fillStyle = T.trim; g.fillRect(0, 0, wd, N);
      g.fillStyle = T.trimHi; g.fillRect(0, 0, 1, N);
      g.fillStyle = T.light; g.fillRect(0, 0, wd, 1);
      g.fillStyle = T.trimDark; g.fillRect(wd - 1, 0, 1, N);
      g.fillStyle = T.mortar; g.fillRect(0, N - 1, wd, 1);
      for (var s = 2; s < N - 2; s += 5) { g.fillStyle = T.dark; g.fillRect(1, s, wd - 2, 1); }
    });
  });
  var trimR = [0, 1].map(function (k) {
    return overlay(function (g) {
      var wd = k ? 5 : 3;
      g.fillStyle = T.trim; g.fillRect(N - wd, 0, wd, N);
      g.fillStyle = T.trimDark; g.fillRect(N - 1, 0, 1, N);
      g.fillStyle = T.light; g.fillRect(N - wd, 0, wd, 1);
      g.fillStyle = T.trimHi; g.fillRect(N - wd, 0, 1, N);
      g.fillStyle = T.mortar; g.fillRect(N - wd, N - 1, wd, 1);
      for (var s = 2; s < N - 2; s += 5) { g.fillStyle = T.dark; g.fillRect(N - wd + 1, s, wd - 2, 1); }
    });
  });
  /* corbelled underside */
  var trimB = overlay(function (g) {
    g.fillStyle = T.dark; g.fillRect(0, N - 4, N, 4);
    g.fillStyle = T.deep; g.fillRect(0, N - 2, N, 2);
    g.fillStyle = T.trim;
    for (var b = 1; b < N; b += 6) { g.fillRect(b, N - 4, 4, 2); }
    g.fillStyle = 'rgba(0,0,0,.35)'; g.fillRect(0, N - 5, N, 1);
  });

  /* ---- one-way platform, with finished ends ---- */
  function platPiece(kind) {
    return overlay(function (g) {
      var woodA = themeName === 'meadow' ? '#7a5228' : '#544a68';
      var woodB = themeName === 'meadow' ? '#a2764a' : '#7a6f94';
      var woodC = themeName === 'meadow' ? '#c29a68' : '#a397bd';
      var iron = '#3f4854', ironHi = '#8b95a6';
      g.fillStyle = woodA; g.fillRect(0, 0, N, 8);
      g.fillStyle = woodB; g.fillRect(0, 0, N, 4);
      g.fillStyle = woodC; g.fillRect(0, 0, N, 1);
      g.fillStyle = 'rgba(0,0,0,.30)';
      for (var pl = 2; pl < N; pl += 5) g.fillRect(pl, 1, 1, 6);
      g.fillStyle = 'rgba(0,0,0,.5)'; g.fillRect(0, 7, N, 2);
      if (kind !== 'm') {
        var ex = kind === 'l' ? 0 : N - 3;
        g.fillStyle = iron; g.fillRect(ex, 0, 3, 9);
        g.fillStyle = ironHi; g.fillRect(ex, 0, 3, 1);
        g.fillStyle = '#20252e'; g.fillRect(ex + (kind === 'l' ? 2 : 0), 1, 1, 8);
        /* hanging bracket */
        g.fillStyle = iron; g.fillRect(ex, 9, 2, 4);
      }
      g.fillStyle = '#6b7280';
      g.fillRect(5, 2, 1, 1); g.fillRect(N - 6, 2, 1, 1);
    });
  }
  var platL = platPiece('l'), platM = platPiece('m'), platR = platPiece('r');

  /* ---- spikes ---- */
  var spike = overlay(function (g) {
    /* three tall blades rather than four stubs */
    var bases = [1, 6, 11];
    for (var sp = 0; sp < bases.length; sp++) {
      var bx = bases[sp], tip = sp === 1 ? 14 : 12;
      for (var y4 = 0; y4 < tip; y4++) {
        var half = Math.max(0, Math.round((y4 + 1) / 3.4));
        g.fillStyle = '#9aa7b8'; g.fillRect(bx + 2 - half, N - 3 - y4, half * 2 + 1, 1);
        g.fillStyle = '#eef5fd'; g.fillRect(bx + 2 - half, N - 3 - y4, 1, 1);
        g.fillStyle = '#4e5968'; g.fillRect(bx + 2 + half, N - 3 - y4, 1, 1);
      }
    }
    g.fillStyle = '#2b323d'; g.fillRect(0, N - 4, N, 4);
    g.fillStyle = '#5f6b7a'; g.fillRect(0, N - 4, N, 1);
    g.fillStyle = '#161a21'; g.fillRect(0, N - 1, N, 1);
    for (var rv = 2; rv < N; rv += 5) { g.fillStyle = '#8b95a6'; g.fillRect(rv, N - 3, 1, 1); }
  });

  /* ---- hazard, 3 frames ---- */
  var haz = [];
  for (var f = 0; f < 3; f++) {
    var hc = nc(), hg = hc.getContext('2d');
    hg.fillStyle = T.hazard[0]; hg.fillRect(0, 0, N, N);
    for (var x5 = 0; x5 < N; x5++) {
      var wav = 2 + Math.round(1.7 * Math.sin((x5 / N) * Math.PI * 2 + f * 2.1));
      hg.fillStyle = T.hazard[1]; hg.fillRect(x5, wav, 1, 3);
      hg.fillStyle = T.hazard[2]; hg.fillRect(x5, wav, 1, 1);
    }
    hg.fillStyle = 'rgba(255,255,255,.12)';
    for (var s2 = 0; s2 < 3; s2++) hg.fillRect((s2 * 5 + f * 3) % N, 7 + ((s2 * 3 + f) % 6), 2, 1);
    haz.push(hc);
  }

  /* ---- crate ---- */
  var crate = overlay(function (g) {
    g.fillStyle = '#6b4622'; g.fillRect(0, 0, N, N);
    g.fillStyle = '#a2764a'; g.fillRect(1, 1, N - 2, N - 2);
    for (var wg = 2; wg < N - 2; wg += 3) { g.fillStyle = 'rgba(70,42,15,.4)'; g.fillRect(1, wg, N - 2, 1); }
    g.fillStyle = '#c29a68'; g.fillRect(1, 1, N - 2, 1); g.fillRect(1, 1, 1, N - 2);
    g.fillStyle = '#42280f'; g.fillRect(0, N - 2, N, 2); g.fillRect(N - 2, 0, 2, N);
    g.fillStyle = '#6b7280'; g.fillRect(0, 6, N, 3); g.fillRect(6, 0, 3, N);
    g.fillStyle = '#98a2b0'; g.fillRect(0, 6, N, 1); g.fillRect(6, 0, 1, N);
    g.fillStyle = '#39414c'; g.fillRect(0, 8, N, 1); g.fillRect(8, 0, 1, N);
    g.fillStyle = '#e8c45c'; g.fillRect(7, 7, 2, 2);
    g.fillStyle = '#8a6a2a'; g.fillRect(7, 8, 2, 1);
  });

  /* ---- bounce pad ---- */
  var spring = overlay(function (g) {
    g.fillStyle = '#241d2e'; g.fillRect(1, 11, N - 2, 5);
    g.fillStyle = '#4a4358'; g.fillRect(2, 12, N - 4, 3);
    g.fillStyle = '#b02f3c'; g.fillRect(0, 6, N, 5);
    g.fillStyle = '#e0656f'; g.fillRect(0, 6, N, 2);
    g.fillStyle = '#ff9aa2'; g.fillRect(0, 6, N, 1);
    g.fillStyle = '#6e1a22'; g.fillRect(0, 10, N, 1);
    g.fillStyle = '#8d84a8'; g.fillRect(2, 12, 2, 3); g.fillRect(N - 4, 12, 2, 3);
    g.fillStyle = '#e8c45c'; g.fillRect(N / 2 - 1, 7, 2, 2);
  });

  /* ---- exit: an arched doorway with a lit interior ---- */
  var door = Util.canvas(N + 8, N * 2 + 6), dg = door.getContext('2d');
  (function (g) {
    var W = N + 8, Hh = N * 2 + 6;
    g.fillStyle = T.dark; g.fillRect(0, 4, W, Hh - 4);
    g.beginPath(); g.arc(W / 2, 10, W / 2, Math.PI, 0); g.fill();
    g.fillStyle = T.trim;
    g.fillRect(0, 4, 4, Hh - 4); g.fillRect(W - 4, 4, 4, Hh - 4);
    g.beginPath(); g.arc(W / 2, 10, W / 2, Math.PI, 0);
    g.arc(W / 2, 10, W / 2 - 4, 0, Math.PI, true); g.fill();
    g.fillStyle = T.trimHi;
    g.fillRect(0, 4, 1, Hh - 4);
    g.fillStyle = '#1a1220';
    g.fillRect(4, 10, W - 8, Hh - 10);
    g.beginPath(); g.arc(W / 2, 11, W / 2 - 4, Math.PI, 0); g.fill();
    /* warm light spilling out */
    g.fillStyle = '#c9a24a'; g.fillRect(6, 14, W - 12, Hh - 16);
    g.fillStyle = '#e8c45c'; g.fillRect(6, 14, W - 12, 2);
    g.fillStyle = '#8a6a2a';
    for (var yy = 18; yy < Hh - 2; yy += 5) g.fillRect(6, yy, W - 12, 1);
    g.fillStyle = '#fff3bc'; g.fillRect(W - 11, Hh - 16, 2, 4);
    /* keystone */
    g.fillStyle = T.trimHi; g.fillRect(W / 2 - 2, 0, 4, 7);
    g.fillStyle = T.trimDark; g.fillRect(W / 2 + 1, 0, 1, 7);
  })(dg);

  var set = {
    solid: solid, cap: cap, trimL: trimL, trimR: trimR, trimB: trimB,
    platL: platL, platM: platM, platR: platR,
    spike: spike, hazard: haz, crate: crate, spring: spring, door: door
  };
  Art._tileCache[themeName] = set;
  return set;
};

Art.variant = function (arr, tx, ty) {
  var h = (((tx * 73856093) ^ (ty * 19349663)) >>> 0);
  return arr[h % arr.length];
};

/* ===================================================================
   PROPS
=================================================================== */
Art.PROPS = (function () {
  var S = Util.sprite;
  var P = {};

  P.BRACKET = S([
    '..oo....',
    '.okko...',
    '.okwo...',
    'ookkwo..',
    'okkwwo..',
    'okwwoo..',
    '.oooo...',
    '..oko...',
    '..oko...',
    '..ooo...'
  ], { o: '#171420', k: '#5a5468', w: '#9d94ab' });

  P.POT = S([
    '..oooo..',
    '.o####o.',
    'o#llll#o',
    'o#llll#o',
    'o######o',
    'o######o',
    '.o####o.',
    '..oooo..'
  ], { o: '#2a1622', '#': '#8e4a62', l: '#c27a92' });
  Art.shade(P.POT, '#8e4a62', ['#b06880', '#95506a', '#733a52', '#52273a']);

  P.VASE = S([
    '..oooo..',
    '.o#ll#o.',
    '.o####o.',
    '..o##o..',
    '..o##o..',
    '.o####o.',
    'o######o',
    'o##ll##o',
    'o######o',
    '.o####o.',
    '..oooo..'
  ], { o: '#1e2436', '#': '#3f7a8c', l: '#7cc0cc' });
  Art.shade(P.VASE, '#3f7a8c', ['#63a7b8', '#4a8a9c', '#356676', '#234854']);

  P.BARREL = S([
    '.oooooo.',
    'o##ll##o',
    'okkkkkko',
    'o##ll##o',
    'o##ll##o',
    'okkkkkko',
    'o##ll##o',
    '.oooooo.'
  ], { o: '#2a1a0e', '#': '#7a5228', l: '#a2764a', k: '#6b7280' });

  P.SKULL = S([
    '.ooooo.',
    'o#####o',
    'o#o#o#o',
    'o#####o',
    '.o###o.',
    '.o#o#o.',
    '..ooo..'
  ], { o: '#2a2620', '#': '#d9d1b8' });

  P.BONEPILE = S([
    '..............',
    '....oo...oo...',
    '...o##o.o##o..',
    '..o####o####o.',
    '.o#ooo#o#ooo#o',
    'o##o.o###o.o#o',
    'o###o#####o##o',
    '.oooooooooooo.'
  ], { o: '#2a2620', '#': '#c6bda4' });

  P.SHROOM = S([
    '..ooo..',
    '.o###o.',
    'o##l##o',
    'o#####o',
    '.ooooo.',
    '..o#o..',
    '..o#o..',
    '..ooo..'
  ], { o: '#2a1420', '#': '#c2445a', l: '#f0a8b4' });

  P.TUFT = S([
    '..o..o....',
    '.olo.olo..',
    'ololoolo.o',
    'olllollolo',
    'oooooooooo'
  ], { o: '#2f6b26', l: '#7ec94f' });

  P.CRYSTAL = S([
    '...oo...',
    '..olko..',
    '.olllko.',
    'ollllkko',
    'olllkkko',
    '.olkkko.',
    '..okko..',
    '...oo...'
  ], { o: '#123044', l: '#a8e8ff', k: '#4aa8d0' });

  P.RUBBLE = S([
    '...........',
    '..oo...oo..',
    '.o##o.o##o.',
    'o####o####o',
    'o#l##o#l##o',
    'ooooooooooo'
  ], { o: '#241f33', '#': '#6e6578', l: '#9b90ae' });

  P.BANNER = S([
    'oooooooo',
    'o##kk##o',
    'o##kk##o',
    'o#kkkk#o',
    'o#k##k#o',
    'o##kk##o',
    'o##kk##o',
    'o######o',
    'o######o',
    '.o####o.',
    '..o##o..',
    '...oo...'
  ], { o: '#2a1020', '#': '#8e2438', k: '#e8c45c' });
  Art.shade(P.BANNER, '#8e2438', ['#b03246', '#95283a', '#74202e', '#521622']);

  P.CANDELABRA = S([
    'w.w.w',
    'k.k.k',
    'o.o.o',
    'ooooo',
    '..o..',
    '..o..',
    '..o..',
    '.ooo.',
    'ooooo'
  ], { o: '#c9a24a', k: '#fff3bc', w: '#ffb43c' });

  P.RING = S([
    '.ooo.',
    'o###o',
    'o#.#o',
    'o###o',
    '.ooo.'
  ], { o: '#20252e', '#': '#6b7280' });

  return P;
})();

Art.drawCobweb = function (g, x, y, size, flip) {
  g.save();
  g.translate(x, y);
  if (flip) { g.scale(-1, 1); }
  g.strokeStyle = 'rgba(220,225,240,.30)';
  g.lineWidth = 1;
  for (var i = 1; i <= 3; i++) {
    g.beginPath();
    g.arc(0, 0, (size / 3) * i, 0, Math.PI / 2);
    g.stroke();
  }
  for (var a = 0; a <= 4; a++) {
    g.beginPath(); g.moveTo(0, 0);
    var ang = (Math.PI / 2) * (a / 4);
    g.lineTo(Math.cos(ang) * size, Math.sin(ang) * size);
    g.stroke();
  }
  g.restore();
};

Art.drawTorch = function (g, x, y, t, seed) {
  g.drawImage(Art.PROPS.BRACKET, x, y + 6);
  var fx = x + 2, fy = y + 4;
  var n = Math.sin(t * 0.31 + seed) * 0.5 + Math.sin(t * 0.17 + seed * 2.3) * 0.5;
  var hgt = 7 + Math.round(n * 1.8);
  for (var i = 0; i < hgt; i++) {
    var frac = i / hgt;
    var wdt = Math.max(1, Math.round((1 - frac) * 4 + Math.sin(t * 0.4 + i + seed) * 0.6));
    var sway = Math.round(Math.sin(t * 0.22 + i * 0.5 + seed) * frac * 1.8);
    g.fillStyle = frac < 0.25 ? '#fff3bc' : (frac < 0.55 ? '#ffb43c' : '#e0621e');
    g.fillRect(fx - Math.floor(wdt / 2) + sway, fy - i, wdt, 1);
  }
  if ((Math.floor(t * 0.5) + seed) % 7 === 0) {
    g.fillStyle = 'rgba(255,180,90,.7)';
    g.fillRect(fx + Math.round(Math.sin(t * 0.3 + seed) * 2), fy - hgt - 2 - (t % 6), 1, 1);
  }
};

Art.drawVine = function (g, x, y, len, t, seed) {
  for (var i = 0; i < len; i++) {
    var sway = Math.round(Math.sin(t * 0.03 + i * 0.35 + seed) * (i / len) * 1.8);
    g.fillStyle = i % 5 === 4 ? '#7ec94f' : '#3f7a2a';
    g.fillRect(x + sway, y + i, 1, 1);
    if (i % 4 === 2) { g.fillStyle = '#59a63f'; g.fillRect(x + sway + (i % 8 === 2 ? 1 : -1), y + i, 1, 1); }
  }
};

Art.drawChain = function (g, x, y, len) {
  for (var i = 0; i < len; i += 2) {
    g.fillStyle = '#6b7280'; g.fillRect(x, y + i, 2, 1);
    g.fillStyle = '#98a2b0'; g.fillRect(x, y + i, 1, 1);
    g.fillStyle = '#39414c'; g.fillRect(x, y + i + 1, 2, 1);
  }
};

/* ===================================================================
   LIGHTING
=================================================================== */
Art.light = function (g, x, y, radius, color) {
  var grad = g.createRadialGradient(x, y, 0, x, y, radius);
  grad.addColorStop(0, color);
  grad.addColorStop(0.45, color.replace(/[\d.]+\)$/, '0.20)'));
  grad.addColorStop(1, 'rgba(0,0,0,0)');
  g.fillStyle = grad;
  g.fillRect(x - radius, y - radius, radius * 2, radius * 2);
};

Art.vignette = function (g, w, h, strength) {
  var grad = g.createRadialGradient(w / 2, h / 2, h * 0.38, w / 2, h / 2, h * 1.0);
  grad.addColorStop(0, 'rgba(0,0,0,0)');
  grad.addColorStop(1, 'rgba(4,2,10,' + (strength || 0.5) + ')');
  g.fillStyle = grad;
  g.fillRect(0, 0, w, h);
};

/* ===================================================================
   BACKDROPS
   Interiors get real architecture: fluted columns with capitals and
   bases, arched niches between them, a belt course and hung banners.
=================================================================== */
Art.drawBackground = function (g, themeName, camX, camY, t, W, H) {
  var T = Art.THEMES[themeName];
  var grad = g.createLinearGradient(0, 0, 0, H);
  grad.addColorStop(0, T.sky[0]);
  grad.addColorStop(0.55, T.sky[1]);
  grad.addColorStop(1, T.sky[2]);
  g.fillStyle = grad; g.fillRect(0, 0, W, H);

  if (themeName === 'meadow') {
    g.fillStyle = 'rgba(255,246,200,.5)';
    g.beginPath(); g.arc(W - 70, 40, 18, 0, 7); g.fill();
    for (var c = 0; c < 6; c++) {
      var cx = ((c * 103 + t * 0.14 - camX * 0.07) % (W + 90)) - 45;
      var cy = 18 + (c % 3) * 17;
      g.fillStyle = 'rgba(255,255,255,.5)';
      g.fillRect(cx, cy, 26, 5); g.fillRect(cx + 6, cy - 4, 15, 5);
      g.fillStyle = 'rgba(255,255,255,.25)'; g.fillRect(cx - 4, cy + 5, 32, 3);
    }
    /* far ridge */
    g.fillStyle = '#5d8a5f';
    for (var x = 0; x < W; x++) {
      var wx = x + camX * 0.15;
      var hh = Math.floor(30 + 12 * Math.sin(wx * 0.013) + 6 * Math.sin(wx * 0.041 + 1.1));
      g.fillRect(x, H - 60 - hh, 1, hh + 60);
    }
    /* treeline */
    g.fillStyle = '#3f7248';
    for (x = 0; x < W; x++) {
      var wx2 = x + camX * 0.3;
      var h2 = Math.floor(18 + 9 * Math.sin(wx2 * 0.026) + 5 * Math.sin(wx2 * 0.07 + 2.2));
      g.fillRect(x, H - 40 - h2, 1, h2 + 40);
    }
    g.fillStyle = '#2f5738';
    for (x = 0; x < W; x++) {
      var wx3 = x + camX * 0.5;
      var h3 = Math.floor(11 + 6 * Math.sin(wx3 * 0.035 + 2) + 3 * Math.sin(wx3 * 0.09));
      g.fillRect(x, H - 22 - h3, 1, h3 + 22);
    }
  } else {
    var px = camX * 0.4, py = camY * 0.4;
    var ts = Art.tiles(themeName), Cw = T.wall;

    /* masonry panelling */
    var ox = Math.floor(px) % 16, oy = Math.floor(py) % 16;
    for (var ty = -1; ty <= Math.ceil(H / 16); ty++) {
      for (var tx = -1; tx <= Math.ceil(W / 16); tx++) {
        var wx4 = tx + Math.floor(px / 16), wy4 = ty + Math.floor(py / 16);
        var shade2 = ((wx4 * 7 + wy4 * 13) >>> 0) % 3;
        g.fillStyle = shade2 === 0 ? Cw.base : (shade2 === 1 ? Cw.light : Cw.dark);
        g.fillRect(tx * 16 - ox, ty * 16 - oy, 16, 16);
        g.fillStyle = Cw.mortar;
        g.fillRect(tx * 16 - ox, ty * 16 - oy, 16, 1);
        g.fillRect(tx * 16 - ox + ((wy4 % 2) ? 8 : 0), ty * 16 - oy, 1, 16);
        g.fillStyle = 'rgba(255,255,255,.04)';
        g.fillRect(tx * 16 - ox, ty * 16 - oy + 1, 16, 1);
      }
    }

    /* belt course */
    var beltY = Math.round(58 - py);
    g.fillStyle = Cw.light; g.fillRect(0, beltY, W, 5);
    g.fillStyle = T.trimHi; g.fillRect(0, beltY, W, 1);
    g.fillStyle = Cw.dark; g.fillRect(0, beltY + 4, W, 2);

    /* bays: a column, then a niche, repeating */
    var bay = 96, colW = 20;
    var first = Math.floor((px - bay) / bay);
    for (var bI = first; bI < first + Math.ceil(W / bay) + 2; bI++) {
      var bx = Math.round(bI * bay - px);

      /* arched niche between the columns */
      var nx = bx + colW + 14, nw = bay - colW - 30;
      if (nw > 16) {
        var ny = Math.round(64 - py), nh = 62;
        g.fillStyle = 'rgba(0,0,0,.50)';
        g.fillRect(nx, ny, nw, nh);
        g.beginPath(); g.arc(nx + nw / 2, ny, nw / 2, Math.PI, 0); g.fill();
        g.fillStyle = Cw.light;
        g.fillRect(nx - 2, ny - 1, 2, nh + 1); g.fillRect(nx + nw, ny - 1, 2, nh + 1);
        g.strokeStyle = Cw.light; g.lineWidth = 2;
        g.beginPath(); g.arc(nx + nw / 2, ny, nw / 2 + 1, Math.PI, 0); g.stroke();
        g.fillStyle = themeName === 'keep' ? 'rgba(120,70,150,.18)' : 'rgba(80,150,200,.14)';
        g.fillRect(nx + 3, ny - 6, nw - 6, nh - 8);
        /* an urn standing in the recess */
        if ((bI % 2) === 0) {
          g.drawImage(themeName === 'keep' ? Art.PROPS.VASE : Art.PROPS.CRYSTAL,
                      Math.round(nx + nw / 2 - 4), ny + nh - 12);
        }
      }

      /* fluted column */
      g.fillStyle = Cw.base; g.fillRect(bx, 0, colW, H);
      g.fillStyle = Cw.light; g.fillRect(bx, 0, 2, H);
      g.fillStyle = Cw.dark; g.fillRect(bx + colW - 3, 0, 3, H);
      for (var fl = 4; fl < colW - 4; fl += 5) {
        g.fillStyle = 'rgba(0,0,0,.22)'; g.fillRect(bx + fl, 0, 2, H);
        g.fillStyle = 'rgba(255,255,255,.06)'; g.fillRect(bx + fl + 2, 0, 1, H);
      }
      /* capital + base */
      var capY = Math.round(40 - py);
      g.fillStyle = T.trim; g.fillRect(bx - 3, capY, colW + 6, 8);
      g.fillStyle = T.trimHi; g.fillRect(bx - 3, capY, colW + 6, 2);
      g.fillStyle = T.trimDark; g.fillRect(bx - 3, capY + 7, colW + 6, 1);
      var baseY = Math.round(150 - py);
      g.fillStyle = T.trim; g.fillRect(bx - 3, baseY, colW + 6, 9);
      g.fillStyle = T.trimHi; g.fillRect(bx - 3, baseY, colW + 6, 2);
      /* hung banner on every third bay */
      if (themeName === 'keep' && (((bI % 3) + 3) % 3) === 1) {
        Art.drawChain(g, bx + colW / 2 - 1, capY + 8, 10);
        g.drawImage(Art.PROPS.BANNER, Math.round(bx + colW / 2 - 4), capY + 18);
      }
      if (themeName === 'cavern' && (((bI % 2) + 2) % 2) === 1) {
        g.drawImage(Art.PROPS.CRYSTAL, bx + 6, Math.round(96 - py));
      }
      /* cobwebs where the column meets the ceiling */
      Art.drawCobweb(g, bx + colW + 1, Math.round(2 - py), 13, false);
      Art.drawCobweb(g, bx - 1, Math.round(2 - py), 13, true);
    }

    if (themeName === 'cavern') {
      for (var m = 0; m < 24; m++) {
        var mx = ((m * 53 - camX * 0.45) % (W + 20) + W + 20) % (W + 20) - 10;
        var my = (H - 20 - ((t * 0.28 + m * 29) % (H + 20)));
        g.fillStyle = 'rgba(160,220,255,' + (0.12 + 0.2 * Math.abs(Math.sin(t * 0.04 + m))) + ')';
        g.fillRect(mx, my, 1, 1);
      }
    }
  }
  g.fillStyle = T.ambient; g.fillRect(0, 0, W, H);
};

/* ===================================================================
   BOSSES - built from shaded rectangles so limbs can animate.
=================================================================== */
Art.drawBoss = function (g, e, t) {
  var x = Math.round(e.x), y = Math.round(e.y), fc = e.facing;
  g.save();
  g.translate(x + e.w / 2, y + e.h);
  g.scale(fc, 1);
  g.translate(-e.w / 2, -e.h);
  if (e.kind === 'boar') Art._boar(g, e, t);
  else if (e.kind === 'gloomwing') Art._wing(g, e, t);
  else Art._king(g, e, t);
  g.restore();
  if (e.flash > 0) {
    g.save();
    g.globalAlpha = 0.6; g.globalCompositeOperation = 'lighter';
    g.fillStyle = '#ffffff';
    g.fillRect(x, y, e.w, e.h);
    g.restore();
  }
};

/* helper: a rectangle with a lit top and a shaded underside */
function plate(g, x, y, w, h, base, light, dark) {
  g.fillStyle = base; g.fillRect(x, y, w, h);
  g.fillStyle = light; g.fillRect(x, y, w, 1);
  if (h > 2) { g.fillStyle = dark; g.fillRect(x, y + h - 1, w, 1); }
}

Art._boar = function (g, e, t) {
  var W = e.w, H = e.h;
  var bob = e.state === 'charge' ? Math.sin(t * 0.7) : Math.sin(t * 0.12);
  var swing = e.state === 'charge' ? Math.sin(t * 0.9) * 4 : 0;
  /* legs */
  plate(g, 8, H - 10, 7, 10, '#33241b', '#4a3527', '#1f1610');
  plate(g, W - 17 + swing, H - 10, 7, 10, '#33241b', '#4a3527', '#1f1610');
  plate(g, 17 - swing, H - 9, 6, 9, '#2b1e16', '#3b2a20', '#171009');
  plate(g, W - 25, H - 9, 6, 9, '#2b1e16', '#3b2a20', '#171009');
  /* hooves */
  g.fillStyle = '#16100c';
  g.fillRect(8, H - 2, 7, 2); g.fillRect(W - 17 + swing, H - 2, 7, 2);
  /* barrel body */
  plate(g, 5, 10 + bob, W - 10, H - 18, '#5c4230', '#7a5a41', '#2f2118');
  g.fillStyle = '#6e5039'; g.fillRect(8, 13 + bob, W - 20, 7);
  g.fillStyle = '#3f2d20'; g.fillRect(5, H - 11 + bob, W - 10, 3);
  /* bristled spine */
  for (var i = 8; i < W - 14; i += 4) {
    g.fillStyle = '#231913';
    var sp = 5 + Math.round(Math.sin(t * 0.3 + i) * 0.6);
    g.fillRect(i, sp + bob, 2, 6);
    g.fillStyle = '#3f2d20'; g.fillRect(i, sp + bob, 1, 3);
  }
  /* head */
  plate(g, W - 21, 13 + bob, 19, H - 22, '#6e5039', '#8a6647', '#3f2d20');
  g.fillStyle = '#4a3527'; g.fillRect(W - 9, 19 + bob, 9, 11);
  /* snout */
  plate(g, W - 7, 23 + bob, 7, 8, '#8a6647', '#a37f5c', '#4a3527');
  g.fillStyle = '#2b1e16';
  g.fillRect(W - 4, 25 + bob, 2, 2); g.fillRect(W - 4, 28 + bob, 2, 2);
  /* tusks */
  g.fillStyle = '#efe6cd';
  g.fillRect(W - 8, 21 + bob, 4, 2);
  g.fillRect(W - 10, 18 + bob, 2, 4);
  g.fillStyle = '#fffaf0';
  g.fillRect(W - 8, 21 + bob, 4, 1);
  /* eye */
  g.fillStyle = '#1a1210'; g.fillRect(W - 14, 18 + bob, 5, 4);
  g.fillStyle = e.state === 'stun' ? '#8c8c8c' : '#ff4b3a';
  g.fillRect(W - 13, 19 + bob, 3, 2);
  if (e.state !== 'stun') { g.fillStyle = '#ffd0a0'; g.fillRect(W - 13, 19 + bob, 1, 1); }
  /* ear */
  plate(g, W - 24, 9 + bob, 7, 8, '#4a3527', '#634833', '#2b1e16');
  /* stun stars */
  if (e.state === 'stun') {
    for (var s = 0; s < 3; s++) {
      var a = t * 0.12 + s * 2.1;
      g.fillStyle = '#ffe27a';
      g.fillRect(W - 14 + Math.round(Math.cos(a) * 9), 4 + Math.round(Math.sin(a) * 3) + bob, 2, 2);
    }
  }
};

Art._wing = function (g, e, t) {
  var W = e.w, H = e.h;
  var flap = Math.sin(t * (e.state === 'dive' ? 0.5 : 0.18));
  var wy = Math.round(flap * 7);
  /* membranes */
  function wing(dir) {
    var sx = W / 2 + dir * 6;
    g.fillStyle = '#42295e';
    g.beginPath();
    g.moveTo(sx, 12);
    g.lineTo(W / 2 + dir * (W / 2 - 2), 8 + wy);
    g.lineTo(W / 2 + dir * (W / 2 - 6), 21 + wy);
    g.lineTo(sx, 23);
    g.closePath(); g.fill();
    g.fillStyle = '#5e3d82';
    g.beginPath();
    g.moveTo(sx, 12);
    g.lineTo(W / 2 + dir * (W / 2 - 4), 10 + wy);
    g.lineTo(sx + dir * 5, 17);
    g.closePath(); g.fill();
    /* finger bones */
    g.fillStyle = '#7d55a8';
    g.fillRect(W / 2 + dir * 8, 11 + Math.round(wy * 0.4), dir > 0 ? W / 2 - 10 : -(W / 2 - 10), 1);
  }
  wing(-1); wing(1);
  /* body */
  plate(g, W / 2 - 8, 7, 16, H - 13, '#7d55a8', '#a077cc', '#4a2f6b');
  g.fillStyle = '#5e3d82'; g.fillRect(W / 2 - 8, H - 11, 16, 4);
  /* ruff */
  g.fillStyle = '#c9a8e8';
  for (var r = -6; r < 7; r += 3) g.fillRect(W / 2 + r, 18, 2, 3);
  /* ears */
  g.fillStyle = '#42295e';
  g.fillRect(W / 2 - 8, 0, 3, 8); g.fillRect(W / 2 + 5, 0, 3, 8);
  g.fillStyle = '#7d55a8';
  g.fillRect(W / 2 - 7, 2, 1, 5); g.fillRect(W / 2 + 6, 2, 1, 5);
  /* face */
  plate(g, W / 2 - 7, 10, 14, 9, '#9d74c9', '#bb98e0', '#6b4594');
  g.fillStyle = '#2a1636';
  g.fillRect(W / 2 - 5, 12, 4, 4); g.fillRect(W / 2 + 1, 12, 4, 4);
  g.fillStyle = e.state === 'stun' ? '#6b6b6b' : '#ffe27a';
  g.fillRect(W / 2 - 4, 13, 2, 2); g.fillRect(W / 2 + 2, 13, 2, 2);
  g.fillStyle = '#fffaf0';
  g.fillRect(W / 2 - 3, 18, 2, 4); g.fillRect(W / 2 + 1, 18, 2, 4);
  /* claws */
  g.fillStyle = '#42295e';
  g.fillRect(W / 2 - 7, H - 7, 5, 6); g.fillRect(W / 2 + 2, H - 7, 5, 6);
  g.fillStyle = '#d9d1b8';
  g.fillRect(W / 2 - 7, H - 2, 5, 1); g.fillRect(W / 2 + 2, H - 2, 5, 1);
};

Art._king = function (g, e, t) {
  var W = e.w, H = e.h;
  var bob = Math.round(Math.sin(t * 0.09));
  var raise = e.state === 'slam' ? -8 : 0;
  /* legs + sabatons */
  plate(g, 9, H - 12, 9, 12, '#3a3448', '#544c68', '#231f30');
  plate(g, W - 19, H - 12, 9, 12, '#3a3448', '#544c68', '#231f30');
  g.fillStyle = '#1b1826';
  g.fillRect(7, H - 3, 12, 3); g.fillRect(W - 21, H - 3, 12, 3);
  /* cloak */
  g.fillStyle = '#5e1730';
  g.fillRect(3, 15 + bob, W - 12, H - 24);
  g.fillStyle = '#8e2438';
  g.fillRect(3, 15 + bob, W - 12, 4);
  g.fillStyle = '#3d0e20';
  for (var f = 6; f < W - 12; f += 7) g.fillRect(f, 19 + bob, 1, H - 28);
  /* cuirass */
  plate(g, 8, 14 + bob, W - 18, 17, '#8b8299', '#bdb4cc', '#4a4358');
  g.fillStyle = '#a79eb8'; g.fillRect(10, 17 + bob, W - 24, 4);
  g.fillStyle = '#e8c45c';
  g.fillRect(9, 23 + bob, W - 20, 1);
  g.fillRect(W / 2 - 3, 18 + bob, 5, 8);
  g.fillStyle = '#fff3bc'; g.fillRect(W / 2 - 3, 18 + bob, 5, 1);
  /* pauldron */
  plate(g, 6, 12 + bob, 11, 8, '#9d94ab', '#cfc6dd', '#544c68');
  /* hound helm */
  plate(g, W - 25, 1 + bob, 21, 16, '#4a4358', '#6b6284', '#2a2536');
  g.fillStyle = '#3b352f'; g.fillRect(W - 13, 8 + bob, 12, 8);
  g.fillStyle = '#231f18'; g.fillRect(W - 13, 12 + bob, 12, 2);
  g.fillStyle = '#efe6cd';
  g.fillRect(W - 6, 14 + bob, 5, 2);
  g.fillStyle = '#1a1220'; g.fillRect(W - 17, 5 + bob, 7, 5);
  g.fillStyle = '#ff5a3a'; g.fillRect(W - 16, 6 + bob, 5, 3);
  g.fillStyle = '#ffd0a0'; g.fillRect(W - 16, 6 + bob, 2, 1);
  /* plume */
  for (var p = 0; p < 7; p++) {
    g.fillStyle = p % 2 ? '#c9a24a' : '#e8c45c';
    g.fillRect(W - 24 + Math.round(Math.sin(t * 0.08 + p * 0.5) * 1.2), -5 + p + bob, 3, 1);
  }
  /* greatsword */
  g.save();
  g.translate(W - 8, 22 + bob + raise);
  g.rotate(e.state === 'slam' ? -0.9 : (e.state === 'sweep' ? 0.5 : -0.25));
  g.fillStyle = '#2a2536'; g.fillRect(-5, -2, 7, 5);
  g.fillStyle = '#e8c45c'; g.fillRect(1, -4, 2, 9);
  g.fillStyle = '#8b8299'; g.fillRect(3, -3, 27, 5);
  g.fillStyle = '#dfe6f0'; g.fillRect(3, -3, 27, 1);
  g.fillStyle = '#ffffff'; g.fillRect(3, -3, 27, 1);
  g.fillStyle = '#5a5468'; g.fillRect(3, 1, 27, 1);
  g.restore();
};
