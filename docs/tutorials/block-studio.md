# Tutorial: Block Studio

Block Studio is a Scratch-like composer for **real** Web Components. You
snap typed blocks together and it generates a self-contained custom
element (shadow DOM, state, listeners) — and a live preview server so you
see it run. Click a block to highlight the exact lines it produced.

<!-- screenshot: Block Studio with a block tree on the left, generated JS in the middle, live preview on the right -->

## Open it

`⌥⌘5`, or the **Block Studio** tab.

## Steps

1. **Name your element.** Every custom element needs a hyphenated tag.
   Start a component and give it a tag like `hello-badge`.

2. **Add blocks from the palette.** Drag an **Element** block (a DOM
   node), give it text; add a **State** field; add a **Listener** that
   flips a class on click. Only legal nestings are allowed — the canvas
   previews valid drop slots and refuses illegal ones, even on load.

3. **Read the code.** The middle pane shows the generated
   `text/javascript` — a complete custom element. Click any block and the
   lines it produced highlight; the mapping is exact.

4. **See it live.** Press **Preview**. Block Studio serves the component
   from an in-memory server and renders it; `⇄` and Quick Search show the
   live URL. Components in the same workspace can even use each other.

5. **Save it.** **Save Component** writes `src/components/<tag>.js` —
   atomic, never clobbering a hand-edited file. The whole workspace lives
   in `.nmoxblocks.json`; **Open Component…** re-imports a file you (or
   the studio) wrote, as long as it's still in the block dialect.

## What you just learned

- The output is a real, framework-free custom element you can ship.
- The block↔code mapping is two-way: in-dialect edits re-import cleanly.
- One workspace holds many components; switching is an undo boundary.

## Next

- Compose components from components — a block that names a sibling's tag
  renders it nested in the preview.
