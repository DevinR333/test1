# Bone Machine

Press the bone, invent a dog. Every dog is generated from scratch by an image model —
none of them exist, have ever existed, or can be adopted in real life.

Open `index.html` in a browser. No build step, no dependencies, no API key.

## What it does

- **A bone the size of the thing it does.** Outlined, shadowed, and sitting in a glowing
  dock at the bottom of the app. Tap it, or press `space`.
- **It squeaks.** A short WebAudio chirp on every press — toggle it off in the top bar.
- **Every dog is invented twice.** A seeded generator writes the photo prompt *and* the
  dog's name, breed, and stat line, so "Tilly, a Little Bramble Pointer" is as made-up as
  her face. Same seed, same dog, every time.
- **Steer the machine.** Chips above the bone bias the prompt: Puppy, Extra fluff, Tiny,
  Giant, Zoomies, Just bathed.
- **Adopt them.** Tap the heart (or press `f`) and the dog moves to the Kennel tab, saved
  in `localStorage` so it survives a reload.
- **Save photo / Copy link.** The link carries the seed, so whoever opens it gets the
  identical dog.
- **Installable.** Ships a web manifest and icon, so it can be added to a phone home
  screen and run without browser chrome (needs to be served over http/https, not `file://`).
- Light and dark themes, `prefers-reduced-motion` respected, and a real error state when
  the generator is unreachable.

While a dog is being drawn, the next one is already generating in the background — so
presses after the first feel instant.

## Swapping the image model

Generation goes through [Pollinations](https://pollinations.ai), which serves images
straight from a URL with no key. One function decides that:

```js
function imageUrl(prompt, seed) {
  return "https://image.pollinations.ai/prompt/" + encodeURIComponent(prompt) +
         "?width=896&height=1120&seed=" + seed + "&model=flux&nologo=true";
}
```

Point it at any endpoint that resolves to an image and everything downstream — preloading,
the kennel, the nameplate, the seed permalinks — keeps working unchanged. For a keyed API
(OpenAI, Replicate, Fal), put a small proxy in front of it rather than shipping the key in
the page.

## Files

| File | Purpose |
| --- | --- |
| `index.html` | The whole app — markup, styles, and script |
| `manifest.webmanifest` | Makes it installable as a home-screen app |
| `icon.svg` | App icon |
