# Tutorial: Image Kit (Web) — press your images

Images are usually the heaviest thing a site ships. The Image Kit
finds your project's JPEGs and PNGs and presses them for the web:
smaller `.min.jpg` siblings by pure-Java re-encoding (nothing to
install), optional downscaling, and `.webp` siblings through your own
`cwebp` when it's installed. In the live proof for this release, a
17.8 MB wallpaper became a 347 KB `.min.jpg` and a 342 KB `.webp` —
98% smaller.

## The laws it keeps

- **Originals are never touched.** Outputs are siblings
  (`photo.min.jpg`, `photo.webp`), and an output that already exists
  is skipped and said — never clobbered.
- **An "optimization" that saves nothing is discarded**: a press that
  reclaims under 10% is deleted and reported as *already tight*
  rather than shipping a bigger "optimized" file. (A resized output
  is kept regardless — smaller pixels were the point.)
- **PNG re-encoding is deliberately absent.** ImageIO can't beat a
  real PNG optimizer, so for PNGs the honest win is the WebP sibling.

## Steps

1. **Aim at a project** and choose **File ▸ Image Kit (Web)…**. The
   dialog tells you how many images it found and their total weight
   (node_modules and build outputs are skipped, and so are its own
   `.min.` outputs — pressing a press would compound loss).

2. **Pick your press.** JPEG quality (85 visually lossless / 80 web
   default / 70 aggressive), an optional maximum width (2560 retina
   hero / 1600 content / 800 thumbnails), and — if `cwebp` is on your
   PATH — WebP siblings. If it isn't, the checkbox says so and where
   to get it (`brew install webp`); the Environment Doctor probes it
   too.

3. **Read the report.** Per file: what was written, before → after
   sizes, or the honest reason nothing was ("already exists",
   "already tight"). The total bytes saved is at the top, along with
   a copy-ready `<picture>` snippet that serves the WebP where
   supported and falls back to the original.

## What you just learned

- Web image optimization with zero required tooling — and your own
  `cwebp` when you have it.
- The kit family's never-clobber and honest-report laws apply to
  pixels too.
