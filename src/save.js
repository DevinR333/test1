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
      sword: 0,           /* index into Art.BLADES */
      collar: 0,          /* extra hearts */
      relics: {},         /* relic id -> true */
      outfits: { none: true },
      worn: 'none',
      sound: true,
      touchMode: 'auto',  /* 'auto' fades when idle, 'always' stays put */
      seenIntro: false
    };
  }

  function load() {
    try {
      var raw = localStorage.getItem(KEY);
      data = raw ? JSON.parse(raw) : fresh();
    } catch (e) { data = fresh(); }
    var f = fresh();
    for (var k in f) if (!(k in data)) data[k] = f[k];
    return data;
  }
  function flush() {
    try { localStorage.setItem(KEY, JSON.stringify(data)); } catch (e) { /* private mode */ }
  }
  function get() { return data || load(); }
  function wipe() { data = fresh(); flush(); return data; }

  var SHOP = [
    { id: 'sword1', kind: 'sword', tier: 1, name: 'Iron Fang', cost: 60,
      desc: '2 damage. A real blade.' },
    { id: 'collar1', kind: 'collar', tier: 1, name: 'Studded Collar', cost: 80,
      desc: '+1 heart.' },
    { id: 'spring', kind: 'relic', name: 'Gale Collar', cost: 130,
      desc: 'A third jump in mid-air.' },
    { id: 'swift', kind: 'relic', name: 'Swift Paws', cost: 90,
      desc: 'Run noticeably faster.' },
    { id: 'sword2', kind: 'sword', tier: 2, name: 'Emberblade', cost: 180,
      desc: '3 damage, longer reach.' },
    { id: 'magnet', kind: 'relic', name: 'Wet Nose', cost: 120,
      desc: 'Coins drift toward you.' },
    { id: 'collar2', kind: 'collar', tier: 2, name: 'Iron Collar', cost: 200,
      desc: '+2 hearts total.' },
    { id: 'lucky', kind: 'relic', name: 'Lucky Tag', cost: 150,
      desc: 'Coins are worth double.' },
    { id: 'sword3', kind: 'sword', tier: 3, name: 'Stormfang', cost: 320,
      desc: '4 damage, longest reach.' },
    { id: 'guard', kind: 'relic', name: 'Thick Coat', cost: 260,
      desc: 'Longer mercy time after a hit.' },
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

  function owned(item) {
    var d = get();
    if (item.kind === 'sword') return d.sword >= item.tier;
    if (item.kind === 'collar') return d.collar >= item.tier;
    if (item.kind === 'outfit') return ownsOutfit(item.id);
    return !!d.relics[item.id];
  }
  /* Tiered gear must be bought in order. */
  function available(item) {
    var d = get();
    if (owned(item)) return false;
    if (item.kind === 'sword') return d.sword === item.tier - 1;
    if (item.kind === 'collar') return d.collar === item.tier - 1;
    return true;   /* relics and outfits have no order */
  }
  function buy(item) {
    var d = get();
    if (owned(item) || !available(item) || d.coins < item.cost) return false;
    d.coins -= item.cost;
    if (item.kind === 'sword') d.sword = item.tier;
    else if (item.kind === 'collar') d.collar = item.tier;
    else if (item.kind === 'outfit') { d.outfits[item.id] = true; d.worn = item.id; }
    else d.relics[item.id] = true;
    flush();
    return true;
  }

  function has(relicId) { return !!get().relics[relicId]; }
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
  function addHeartPiece(levelId) {
    var d = get();
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
    SHOP: SHOP, owned: owned, available: available, buy: buy,
    has: has, addCoins: addCoins, clearStage: clearStage,
    addGems: addGems, gemScore: gemScore, addHeartPiece: addHeartPiece,
    ownsOutfit: ownsOutfit, unlockOutfit: unlockOutfit, wear: wear, worn: worn,
    lockedOutfit: lockedOutfit,
    heartPieceCount: heartPieceCount, maxHearts: maxHearts,
    gemsFound: gemsFound, chestsFound: chestsFound, gemsTotal: gemsTotal,
    allCleared: allCleared
  };
})();
