/* WebAudio: every sound is synthesised at runtime, no sample files. */
var Sfx = (function () {
  var ctx = null, master = null, musicGain = null;
  var enabled = true;
  var song = null, nextNoteTime = 0, step = 0;

  function ensure() {
    if (ctx) return ctx;
    var AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return null;
    ctx = new AC();
    master = ctx.createGain(); master.gain.value = 0.5; master.connect(ctx.destination);
    musicGain = ctx.createGain(); musicGain.gain.value = 0.16; musicGain.connect(master);
    return ctx;
  }
  function resume() { var c = ensure(); if (c && c.state === 'suspended') c.resume(); }

  function blip(opts) {
    if (!enabled) return;
    var c = ensure(); if (!c) return;
    var t = c.currentTime;
    var o = c.createOscillator(), g = c.createGain();
    o.type = opts.type || 'square';
    o.frequency.setValueAtTime(opts.freq, t);
    if (opts.to) o.frequency.exponentialRampToValueAtTime(Math.max(20, opts.to), t + opts.dur);
    var vol = opts.vol == null ? 0.22 : opts.vol;
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(vol, t + 0.008);
    g.gain.exponentialRampToValueAtTime(0.0001, t + opts.dur);
    o.connect(g); g.connect(master);
    o.start(t); o.stop(t + opts.dur + 0.02);
  }

  function noise(dur, vol, filterFreq, sweepTo) {
    if (!enabled) return;
    var c = ensure(); if (!c) return;
    var t = c.currentTime;
    var len = Math.max(1, Math.floor(c.sampleRate * dur));
    var buf = c.createBuffer(1, len, c.sampleRate);
    var data = buf.getChannelData(0);
    for (var i = 0; i < len; i++) data[i] = (Math.random() * 2 - 1) * (1 - i / len);
    var src = c.createBufferSource(); src.buffer = buf;
    var f = c.createBiquadFilter(); f.type = 'lowpass';
    f.frequency.setValueAtTime(filterFreq || 1800, t);
    if (sweepTo) f.frequency.exponentialRampToValueAtTime(sweepTo, t + dur);
    var g = c.createGain(); g.gain.setValueAtTime(vol == null ? 0.18 : vol, t);
    g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    src.connect(f); f.connect(g); g.connect(master);
    src.start(t); src.stop(t + dur + 0.02);
  }

  /* ---- Music: tiny step sequencer driven from the game loop ---- */
  var SONGS = {
    meadow: {
      bpm: 132,
      lead: [64, 0, 67, 0, 71, 0, 67, 0, 64, 0, 62, 0, 64, 0, 0, 0,
             62, 0, 64, 0, 67, 0, 64, 0, 59, 0, 62, 0, 60, 0, 0, 0],
      bass: [40, 0, 40, 0, 47, 0, 47, 0, 45, 0, 45, 0, 43, 0, 43, 0,
             38, 0, 38, 0, 45, 0, 45, 0, 40, 0, 40, 0, 43, 0, 43, 0]
    },
    cavern: {
      bpm: 118,
      lead: [62, 0, 0, 65, 0, 69, 0, 0, 67, 0, 65, 0, 62, 0, 0, 0,
             60, 0, 0, 63, 0, 67, 0, 0, 65, 0, 63, 0, 60, 0, 0, 0],
      bass: [38, 0, 38, 0, 38, 0, 45, 0, 43, 0, 43, 0, 36, 0, 36, 0,
             36, 0, 36, 0, 41, 0, 41, 0, 38, 0, 38, 0, 33, 0, 33, 0]
    },
    keep: {
      bpm: 146,
      lead: [69, 0, 69, 71, 0, 72, 0, 71, 69, 0, 67, 0, 69, 0, 0, 0,
             64, 0, 67, 0, 69, 0, 72, 0, 71, 0, 69, 0, 67, 0, 64, 0],
      bass: [45, 45, 0, 45, 40, 0, 40, 0, 43, 43, 0, 43, 38, 0, 38, 0,
             45, 45, 0, 45, 41, 0, 41, 0, 40, 40, 0, 40, 35, 0, 35, 0]
    },
    boss: {
      bpm: 158,
      lead: [52, 52, 55, 52, 58, 0, 57, 0, 52, 52, 55, 52, 59, 0, 58, 0,
             52, 52, 55, 52, 60, 0, 59, 0, 58, 57, 56, 55, 54, 0, 0, 0],
      bass: [28, 28, 28, 28, 35, 35, 33, 33, 28, 28, 28, 28, 31, 31, 30, 30,
             28, 28, 28, 28, 35, 35, 33, 33, 31, 31, 30, 30, 29, 29, 28, 28]
    }
  };

  function hz(midi) { return 440 * Math.pow(2, (midi - 69) / 12); }

  function voice(midi, when, dur, type, vol, dest) {
    var o = ctx.createOscillator(), g = ctx.createGain();
    o.type = type; o.frequency.setValueAtTime(hz(midi), when);
    g.gain.setValueAtTime(0.0001, when);
    g.gain.linearRampToValueAtTime(vol, when + 0.012);
    g.gain.exponentialRampToValueAtTime(0.0001, when + dur);
    o.connect(g); g.connect(dest);
    o.start(when); o.stop(when + dur + 0.02);
  }

  function playSong(name) {
    var s = SONGS[name];
    if (!s || song === s) return;
    song = s; step = 0; nextNoteTime = 0;
  }
  function stopSong() { song = null; }

  /* Call once per frame; schedules a little ahead of the audio clock. */
  function tick() {
    if (!enabled || !song || !ctx) return;
    var spb = 60 / song.bpm / 4; /* sixteenth notes */
    if (nextNoteTime === 0) nextNoteTime = ctx.currentTime + 0.06;
    while (nextNoteTime < ctx.currentTime + 0.25) {
      var i = step % song.lead.length;
      if (song.lead[i]) voice(song.lead[i], nextNoteTime, spb * 1.6, 'square', 0.18, musicGain);
      if (song.bass[i]) voice(song.bass[i], nextNoteTime, spb * 1.9, 'triangle', 0.3, musicGain);
      if (i % 4 === 0) {
        var g = ctx.createGain(), o = ctx.createOscillator();
        o.type = 'sine'; o.frequency.setValueAtTime(110, nextNoteTime);
        o.frequency.exponentialRampToValueAtTime(45, nextNoteTime + 0.1);
        g.gain.setValueAtTime(0.22, nextNoteTime);
        g.gain.exponentialRampToValueAtTime(0.0001, nextNoteTime + 0.11);
        o.connect(g); g.connect(musicGain);
        o.start(nextNoteTime); o.stop(nextNoteTime + 0.13);
      }
      nextNoteTime += spb; step++;
    }
  }

  return {
    resume: resume,
    tick: tick,
    playSong: playSong,
    stopSong: stopSong,
    setEnabled: function (v) {
      enabled = v;
      if (!v && ctx) { stopSong(); master.gain.setValueAtTime(0, ctx.currentTime); }
      else if (ctx) master.gain.setValueAtTime(0.5, ctx.currentTime);
    },
    isEnabled: function () { return enabled; },
    jump:    function () { blip({ freq: 340, to: 720, dur: 0.14, type: 'square', vol: 0.16 }); },
    dbljump: function () { blip({ freq: 480, to: 900, dur: 0.13, type: 'triangle', vol: 0.18 }); },
    swing:   function () { noise(0.13, 0.13, 4200, 900); },
    hit:     function () { blip({ freq: 240, to: 90, dur: 0.12, type: 'square', vol: 0.2 }); noise(0.1, 0.12, 2600); },
    kill:    function () { blip({ freq: 180, to: 60, dur: 0.22, type: 'sawtooth', vol: 0.18 }); noise(0.18, 0.14, 1400); },
    coin:    function () { blip({ freq: 988, dur: 0.06, type: 'square', vol: 0.14 }); setTimeout(function () { blip({ freq: 1319, dur: 0.1, type: 'square', vol: 0.13 }); }, 55); },
    bone:    function () { [784, 988, 1175, 1568].forEach(function (f, i) { setTimeout(function () { blip({ freq: f, dur: 0.12, type: 'square', vol: 0.14 }); }, i * 80); }); },
    hurt:    function () { blip({ freq: 400, to: 120, dur: 0.3, type: 'sawtooth', vol: 0.2 }); },
    bark:    function () { blip({ freq: 520, to: 180, dur: 0.1, type: 'sawtooth', vol: 0.22 }); noise(0.09, 0.1, 1200); },
    land:    function () { noise(0.07, 0.1, 900); },
    spring:  function () { blip({ freq: 300, to: 1100, dur: 0.18, type: 'sine', vol: 0.2 }); },
    select:  function () { blip({ freq: 660, dur: 0.06, type: 'square', vol: 0.12 }); },
    confirm: function () { blip({ freq: 660, dur: 0.07, type: 'square', vol: 0.14 }); setTimeout(function () { blip({ freq: 990, dur: 0.12, type: 'square', vol: 0.14 }); }, 60); },
    deny:    function () { blip({ freq: 200, to: 140, dur: 0.16, type: 'square', vol: 0.15 }); },
    buy:     function () { [659, 784, 1047].forEach(function (f, i) { setTimeout(function () { blip({ freq: f, dur: 0.12, type: 'triangle', vol: 0.18 }); }, i * 70); }); },
    fanfare: function () { [659, 784, 988, 1319].forEach(function (f, i) { setTimeout(function () { blip({ freq: f, dur: 0.24, type: 'square', vol: 0.16 }); }, i * 130); }); },
    dead:    function () { [440, 392, 330, 262].forEach(function (f, i) { setTimeout(function () { blip({ freq: f, dur: 0.3, type: 'triangle', vol: 0.18 }); }, i * 160); }); },
    crash:   function () { noise(0.4, 0.2, 3000, 400); }
  };
})();
