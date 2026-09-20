/* Persistence + the shop catalogue. */
var Save = (function () {
  /* Storage key kept from the old title on purpose - renaming it would
     wipe every existing save. */
  var KEY = 'blacklabblade.save.v2';
  var data = null;

  function fresh() {
    return {
      coins: 0,
      unlocked: 1,        /* how many stages are playable */
      cleared: {},        /* stage id -> true          */
      gems: {},           /* stage id -> gems found 0-3 */
      gemScore: 0,        /* running total of gem value            */
      heartPieces: {},    /* stage id -> true, four make a heart    */
      chests: {},         /* stage id -> chests found   */
      blades: { stick: true },
      blade: 'stick',
      collar: 0,          /* extra hearts */
      relics: {},         /* relic id -> true */
      outfits: { none: true },
      worn: 'none',
      sound: true,
      touchMode: 'auto',  /* 'auto' fades when idle, 'always' stays put */
      zoom: 1,            /* 1 = fit the screen, higher crops in closer */
      seenIntro: false,
      seenOrbTip: false
    };
  }

  /* Android WebView does not reliably keep localStorage for file:// pages
     between app launches, which silently wiped progress in the APK. When
     the app exposes a native bridge we use that instead, and fall back to
     localStorage everywhere else. */
  function readRaw() {
    try {
      if (window.AndroidSave && window.AndroidSave.load) {
        var n = window.AndroidSave.load();
        if (n) return n;
      }
    } catch (e) { /* bridge missing or unhappy */ }
    try { return localStorage.getItem(KEY); } catch (e2) { return null; }
  }
  function writeRaw(str) {
    var ok = false;
    try {
      if (window.AndroidSave && window.AndroidSave.save) {
        window.AndroidSave.save(str);
        ok = true;
      }
    } catch (e) { /* fall through to web storage */ }
    try { localStorage.setItem(KEY, str); ok = true; } catch (e2) { /* private mode */ }
    return ok;
  }

  function load() {
    try {
      var raw = readRaw();
      data = raw ? JSON.parse(raw) : fresh();
    } catch (e) { data = fresh(); }
    var f = fresh();
    for (var k in f) if (!(k in data)) data[k] = f[k];
    /* migrate the old numeric sword tier to the named blades */
    if (typeof data.sword === 'number') {
      var order = ['stick', 'iron', 'ember', 'storm'];
      for (var i = 0; i <= data.sword && i < order.length; i++) data.blades[order[i]] = true;
      data.blade = order[Math.min(data.sword, order.length - 1)];
      delete data.sword;
    }
    return data;
  }
  function flush() {
    try { writeRaw(JSON.stringify(data)); } catch (e) { /* nothing we can do */ }
  }
  function get() { return data || load(); }

  /* Everything a single run can add to. A stage takes a copy on the way
     in; going down puts it all back, so dying costs you the trip. */
  var RUN_FIELDS = ['coins', 'gemScore', 'heartPieces', 'outfits', 'gems',
                    'chests', 'blades', 'relics', 'collar', 'worn', 'blade'];
  function snapshot() {
    var d = get(), o = {};
    for (var i = 0; i < RUN_FIELDS.length; i++) {
      var k = RUN_FIELDS[i];
      o[k] = (d[k] && typeof d[k] === 'object') ? JSON.parse(JSON.stringify(d[k])) : d[k];
    }
    return o;
  }
  function restore(snap) {
    if (!snap) return;
    var d = get();
    for (var k in snap) d[k] = snap[k];
    flush();
  }
  function wipe() { data = fresh(); flush(); return data; }

  var SHOP = [
    { id: 'iron', kind: 'sword', name: 'Iron Fang', cost: 60,
      desc: '2 damage, fair reach. Special: cross slash.' },
    { id: 'collar1', kind: 'collar', tier: 1, name: 'Studded Collar', cost: 80,
      desc: '+1 heart.' },
    { id: 'swift', kind: 'relic', name: 'Swift Paws', cost: 90,
      desc: 'Run noticeably faster.' },
    { id: 'ember', kind: 'sword', name: 'Emberblade', cost: 190,
      desc: '3 damage, long. Breaks reinforced stone. Special: flame wave.' },
    { id: 'magnet', kind: 'relic', name: 'Wet Nose', cost: 120,
      desc: 'Coins drift toward you.' },
    { id: 'collar2', kind: 'collar', tier: 2, name: 'Iron Collar', cost: 200,
      desc: '+2 hearts total.' },
    { id: 'storm', kind: 'sword', name: 'Stormfang', cost: 380,
      desc: '5 damage, long. Special: piercing thunder arc.' },
    /* Wardrobe. Most turn up in chests; these are the ones you can
       simply buy. Selecting one you already own wears it. */
    { id: 'bandana', kind: 'outfit', name: 'Bandana', cost: 40,
      desc: 'Pure swagger. No stats.' },
    { id: 'party', kind: 'outfit', name: 'Party Hat', cost: 70,
      desc: 'Every stage is a birthday.' },
    { id: 'santa', kind: 'outfit', name: 'Santa Hat', cost: 120,
      desc: 'Seasonal, aggressively.' },
    { id: 'crown', kind: 'outfit', name: 'Gold Crown', cost: 400,
      desc: 'Who is a good king? You are.' }
  ];

  /* Gear no amount of coin will buy. It is only ever in a chest, and the
     shop lists it greyed out so you know it is out there. */
  var CHEST_ONLY = [
    { id: 'cleaver', kind: 'sword', name: 'Boar Cleaver',
      desc: '4 damage but stubby. Special: quake.' },
    { id: 'whip', kind: 'sword', name: 'Whip Fang',
      desc: '2 damage, enormous reach. Special: long lash.' },
    { id: 'spring', kind: 'relic', name: 'Gale Collar',
      desc: 'A third jump in mid-air.' },
    { id: 'guard', kind: 'relic', name: 'Thick Coat',
      desc: 'Longer mercy time after a hit.' },
    { id: 'lucky', kind: 'relic', name: 'Lucky Tag',
      desc: 'Coins are worth double.' }
  ];
  var CHEST_ONLY_BY_ID = {};
  for (var ci = 0; ci < CHEST_ONLY.length; ci++) {
    CHEST_ONLY[ci].cost = -1;                /* never purchasable */
    CHEST_ONLY_BY_ID[CHEST_ONLY[ci].id] = CHEST_ONLY[ci];
  }

  /* Hand over whatever a chest was carrying. Returns a description of
     what actually landed, or null when the prize was already yours. */
  function grant(spec) {
    var d = get();
    var bits = String(spec).split(':');
    var kind = bits[0], id = bits[1];
    if (kind === 'blade') {
      if (d.blades[id]) return null;
      d.blades[id] = true;
      d.blade = id;                          /* swing it straight away */
      flush();
      var bl = Art.BLADE_BY_ID[id];
      return { title: 'A BLADE', color: '#eef4fa', lines: [
        (bl ? bl.name.toUpperCase() : id.toUpperCase()),
        (bl ? (bl.dmg + ' DAMAGE   SPECIAL: ' +
               Art.SPECIALS[bl.special].name.toUpperCase()) : ''),
        'EQUIPPED. SWAP BLADES AT THE POST.'
      ] };
    }
    if (kind === 'relic') {
      if (d.relics[id]) return null;
      d.relics[id] = true;
      flush();
      var rl = CHEST_ONLY_BY_ID[id];
      return { title: 'AN UPGRADE', color: '#b9a0ff', lines: [
        (rl ? rl.name.toUpperCase() : id.toUpperCase()),
        (rl ? rl.desc.toUpperCase() : ''),
        'IT IS YOURS FOR GOOD'
      ] };
    }
    return null;
  }

  function owned(item) {
    var d = get();
    if (item.kind === 'sword') return !!d.blades[item.id];
    if (item.kind === 'collar') return d.collar >= item.tier;
    if (item.kind === 'outfit') return ownsOutfit(item.id);
    return !!d.relics[item.id];
  }
  /* Tiered gear must be bought in order. */
  function available(item) {
    var d = get();
    if (owned(item)) return false;
    if (item.cost < 0) return false;          /* chest-only: no price */
    if (item.kind === 'sword') return true;
    if (item.kind === 'collar') return d.collar === item.tier - 1;
    return true;   /* relics and outfits have no order */
  }
  function buy(item) {
    var d = get();
    if (item.cost < 0) return false;
    if (owned(item) || !available(item) || d.coins < item.cost) return false;
    d.coins -= item.cost;
    if (item.kind === 'sword') { d.blades[item.id] = true; d.blade = item.id; }
    else if (item.kind === 'collar') d.collar = item.tier;
    else if (item.kind === 'outfit') { d.outfits[item.id] = true; d.worn = item.id; }
    else d.relics[item.id] = true;
    flush();
    return true;
  }

  function has(relicId) { return !!get().relics[relicId]; }
  function isChestOnly(id) { return !!CHEST_ONLY_BY_ID[id]; }
  /* the shop shows everything, chest-only gear included, so you know it
     is out there rather than wondering whether the list is the game */
  function catalogue() { return SHOP.concat(CHEST_ONLY); }

  /* ---- blades ---- */
  function ownsBlade(id) { return !!get().blades[id]; }
  function equipBlade(id) { if (ownsBlade(id)) { get().blade = id; flush(); } }
  function blade() {
    var b = Art.BLADE_BY_ID[get().blade];
    return b || Art.BLADES[0];
  }
  function bladeId() { return blade().id; }
  function bladeDamage() { return blade().dmg; }
  function bladeReach() { return blade().reach; }
  /* the best blade owned, for gating reinforced stone */
  function bestDamage() {
    var d = get(), best = 1;
    for (var i = 0; i < Art.BLADES.length; i++) {
      if (d.blades[Art.BLADES[i].id]) best = Math.max(best, Art.BLADES[i].dmg);
    }
    return best;
  }
  function ownsOutfit(id) { return id === 'none' || !!get().outfits[id]; }
  function unlockOutfit(id) {
    var d = get();
    if (d.outfits[id]) return false;
    d.outfits[id] = true; flush();
    return true;
  }
  function wear(id) { if (ownsOutfit(id)) { get().worn = id; flush(); } }
  function worn() { var w = get().worn; return ownsOutfit(w) ? w : 'none'; }
  /* an outfit the player has not got yet, for a chest to hand over */
  function lockedOutfit() {
    var ids = [];
    for (var k in Art.OUTFITS) if (!ownsOutfit(k)) ids.push(k);
    return ids.length ? ids[Math.floor(Math.random() * ids.length)] : null;
  }
  function addGems(n) { get().gemScore = (get().gemScore || 0) + n; }
  function gemScore() { return get().gemScore || 0; }
  function heartPieceCount() {
    var d = get(), n = 0;
    for (var k in d.heartPieces) if (d.heartPieces[k]) n++;
    return n;
  }
  function hasHeartPiece(levelId) { return !!get().heartPieces[levelId]; }
  function addHeartPiece(levelId) {
    var d = get();
    if (d.heartPieces[levelId]) return 0;    /* one per stage, ever */
    d.heartPieces[levelId] = true;
    flush();
    return heartPieceCount();
  }
  /* three to start, one per collar, and one for every four vessel pieces */
  function maxHearts() {
    return 3 + get().collar + Math.floor(heartPieceCount() / 4);
  }
  function addCoins(n) { get().coins += n; }
  function clearStage(index, gems, chests) {
    var d = get(), L = LEVELS[index];
    d.cleared[L.id] = true;
    /* keep the best haul across attempts */
    d.gems[L.id] = Math.max(d.gems[L.id] || 0, gems || 0);
    d.chests[L.id] = Math.max(d.chests[L.id] || 0, chests || 0);
    if (d.unlocked < index + 2) d.unlocked = Math.min(index + 2, LEVELS.length);
    flush();
  }
  function gemsFound() {
    var d = get(), n = 0;
    for (var k in d.gems) n += d.gems[k] || 0;
    return n;
  }
  function chestsFound() {
    var d = get(), n = 0;
    for (var k in d.chests) n += d.chests[k] || 0;
    return n;
  }
  function gemsTotal() {
    var n = 0;
    for (var i = 0; i < LEVELS.length; i++) if (!LEVELS[i].boss) n += 3;
    return n;
  }
  function allCleared() {
    for (var i = 0; i < LEVELS.length; i++) if (!get().cleared[LEVELS[i].id]) return false;
    return true;
  }

  return {
    load: load, get: get, flush: flush, wipe: wipe,
    snapshot: snapshot, restore: restore,
    SHOP: SHOP, CHEST_ONLY: CHEST_ONLY, catalogue: catalogue,
    isChestOnly: isChestOnly, grant: grant,
    owned: owned, available: available, buy: buy,
    has: has, addCoins: addCoins, clearStage: clearStage,
    ownsBlade: ownsBlade, equipBlade: equipBlade, blade: blade, bladeId: bladeId,
    bladeDamage: bladeDamage, bladeReach: bladeReach, bestDamage: bestDamage,
    addGems: addGems, gemScore: gemScore, addHeartPiece: addHeartPiece,
    hasHeartPiece: hasHeartPiece,
    ownsOutfit: ownsOutfit, unlockOutfit: unlockOutfit, wear: wear, worn: worn,
    lockedOutfit: lockedOutfit,
    heartPieceCount: heartPieceCount, maxHearts: maxHearts,
    gemsFound: gemsFound, chestsFound: chestsFound, gemsTotal: gemsTotal,
    allCleared: allCleared
  };
})();
