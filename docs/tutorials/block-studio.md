# Tutorial: Block Studio

<!-- languages -->
**English** · [Español](block-studio.es.md) · [Français](block-studio.fr.md) · [Deutsch](block-studio.de.md) · [Русский](block-studio.ru.md) · [Українська](block-studio.uk.md) · [Polski](block-studio.pl.md) · [Português (Brasil)](block-studio.pt.md) · [Bahasa Indonesia](block-studio.id.md) · [Filipino](block-studio.tl.md) · [Tiếng Việt](block-studio.vi.md) · [简体中文](block-studio.zh.md) · [हिन्दी](block-studio.hi.md) · [עברית](block-studio.he.md) · [العربية](block-studio.ar.md)
<!-- /languages -->

Block Studio is a Scratch-like composer for **real** Web Components. You
snap typed blocks together and it generates a self-contained custom
element (shadow DOM, state, listeners) — and a live preview server so you
see it run. Click a block to highlight the exact lines it produced.

![Block Studio — the pieces palette, the canvas with a component root, and the generated custom element with click-a-piece code mapping](../images/tabs/block-studio.png)

## Open it

`⌥⌘5`, or the **Block Studio** tab.

## Steps

1. **Name your element.** Every custom element needs a hyphenated tag.
   Start a component and give it a tag like `hello-badge`.

2. **Add blocks from the palette.** Drag an **Element** block (a DOM
   node), give it text; add a **State** field; add an **On event** block
   with a **Toggle class** inside it. Only legal nestings are allowed — the canvas
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
