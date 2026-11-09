# Nightcrawler Design Studio (web)

A standalone, static pixel-art editor for the 64&times;16 LED panels. Draw a static
graphic or a multi-frame animation, then download it as a GIF and upload it
into the Nightcrawler AutoComm Android app.

No build step, no backend, no account &mdash; everything (drawing, color picking,
GIF encoding, autosave) runs in the browser.

## Run it locally

Any static file server works, e.g.:

```bash
npx serve public
```

Then open the printed `localhost` URL. (Opening `index.html` directly via
`file://` will *not* work &mdash; GIF export needs a real origin for its web
worker.)

## Deploy

**GitHub Pages (recommended, already wired up):** the workflow at
`.github/workflows/pages.yml` deploys the contents of this `public/` folder
to GitHub Pages automatically on every push to `main`. Enable it once under
your repo's **Settings &rarr; Pages &rarr; Source &rarr; GitHub Actions**.

**Any other static host** (Netlify, Vercel, Cloudflare Pages, itch.io,
etc.): just upload the contents of this `public/` folder &mdash; there's nothing
else to configure.

## Using it

- **Graphics / Animation tabs** &mdash; a graphic is a single 64&times;16 image; an
  animation is a sequence of frames with its own per-frame delay.
- **Tools** &mdash; pencil, eraser, fill bucket, eyedropper (color picker from the
  grid), undo/redo, clear. Pick any of the panel's thousands of colors with
  the color swatch.
- **Upload Image** &mdash; opens a Position &amp; Fit dialog: drag, zoom (10&ndash;400%,
  defaults to filling the frame) and rotate a still image before baking it
  onto the grid. Uploading an animated GIF decodes every frame (with correct
  disposal/transparency handling) and applies the same position/zoom/rotation
  to all of them at once, inserting them into the current animation. GIFs
  longer than 40 frames (the Android app's animation limit) first go through
  a trim step with in/out sliders that auto-snap the selection to 40 frames
  and show the in/out times.
- **Save** &mdash; stores the current design in this browser (`localStorage`), so
  it's available again from **Open**. **Backup** downloads a `.json` copy you
  can move to another device and re-import from the **Open** dialog.
- **Download GIF** &mdash; renders the current graphic/animation as a native
  64&times;16 GIF and downloads it. This is the file to upload into the Android
  app.
- Work also autosaves continuously, so a reload won't lose it.

## Notes

- Unlit pixels are exported as pure black, which the Android app already
  treats as "off" &mdash; no transparency needed.
- GIF encoding runs client-side via [gif.js](https://github.com/jnordberg/gif.js),
  and GIF decoding (for the Upload flow) via [omggif](https://github.com/deanm/omggif)
  (both vendored in `vendor/`, no CDN dependency at runtime).
