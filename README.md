# Press the Bone

A one-button web app: press the bone, get a random photorealistic dog.

Open `index.html` in any browser — no build step, no dependencies, no API key.

## How it works

Each press calls the free [Dog API](https://dog.ceo/dog-api/) (`https://dog.ceo/api/breeds/image/random`),
which returns the URL of a real dog photograph from a collection of thousands. The app:

- preloads the image before swapping it in, so you never see a half-drawn photo
- keeps the *next* dog warm in the background, so the second press onward is instant
- reads the breed out of the image URL (`.../breeds/hound-afghan/...` → "Afghan Hound") for the nameplate
- keeps the last 12 dogs in a "Today's walk" rail you can click back through
- responds to the spacebar as well as the bone

Photos are genuine photographs rather than model renderings — that is what makes them
photorealistic. To swap in a generative image model instead, replace `randomUrl()` in
`index.html` with a call to your image endpoint that resolves to an image URL; everything
downstream (preloading, history, nameplate) works unchanged.

## Files

| File | Purpose |
| --- | --- |
| `index.html` | The whole app — markup, styles, and script |
