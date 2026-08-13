# Browser to Source: pick, jump, restyle

*One sitting. You'll click an element in the in-app Browser, land in
the file that produced it, change its style from DevTools, and watch
the change arrive in your stylesheet — without retyping anything.*

The oldest split in web development is that the browser and the editor
know different things: the browser knows *which element you mean*, the
editor knows *where the code lives*, and you carry information between
them by hand. NMOX Studio's Browser closes that split. This tutorial
walks the whole loop on a page you'll make in two minutes.

## 1. Make a page

Create a folder with two files (Project Studio's New File works, or
any way you like):

`index.html`

```html
<!doctype html>
<html>
<head>
    <title>Loop Demo</title>
    <link rel="stylesheet" href="style.css">
</head>
<body>
    <header class="hero">
        <h1 id="headline">Hello, loop</h1>
        <p class="tagline">watch this paragraph change color</p>
    </header>
</body>
</html>
```

`style.css`

```css
.hero {
    background: #222;
    color: white;
    padding: 2rem;
}

.tagline {
    color: gray;
    font-style: italic;
}
```

## 2. Open it in the Browser

Open the **Browser** tab (⌥⌘4), type the file's path into the URL bar
as a `file://` URL — for example
`file:///Users/you/NMOX/loopdemo/index.html` — and press Return.

> A page served by one of the rack's serve devices (IGNITION, VELOCITY,
> HALO, and friends) works exactly the same way — the Browser knows
> which project a live serving belongs to. What does **not** work is a
> remote site: the loop only trusts pages it can trace to files on your
> disk, and it will say so rather than guess.

Click **DevTools** in the Browser toolbar and select the **DOM** tab.

## 3. Pick an element in the page

Click **Pick element**. The page cursor becomes a crosshair. Now click
the headline in the page itself.

Three things happen at once: the click is swallowed (no navigation),
the DOM tree selects `h1#headline`, and a blue outline rings the
element in the page. The detail pane fills with its attributes and
computed styles — including a WCAG contrast verdict when both colors
are known.

## 4. Jump to the source

With the element selected, click **Open Source** (double-clicking the
tree node does the same). The editor opens `index.html` with the caret
on the exact line that produced the element.

How it finds the line, and when it refuses:

- An element **with an id** is found by that id — ids are unique, so
  this is exact.
- An element **without an id** is found as the Nth occurrence of its
  tag in document order, with comments and `<script>`/`<style>` bodies
  ignored (a `<div>` inside a comment or a JS string is not an
  element).
- An element that **only exists because a script created it** is not
  in your source at all — the status bar says "likely
  script-generated" instead of jumping somewhere wrong.
- A page that is not backed by a local file — a remote site, an
  unknown dev server — refuses with "not served from a project here."

The refusals are the point: a jump that might be wrong is worse than
no jump.

## 5. Restyle it — and watch the source change

Select the tagline (`p.tagline`) — pick it in the page or click it in
the tree — and press **Edit Style…**. In the dialog choose property
`color`, type value `tomato`, and press OK.

Two things happen, in order:

1. **The page repaints instantly.** The tweak is applied inline first,
   so you always see what you asked for.
2. **The source stylesheet changes.** The status bar reports
   `Saved to style.css (.tagline)` — open `style.css` and
   `color: gray;` has become `color: tomato;`, in place, with every
   other byte untouched.

The rule to edit is chosen by asking the *page* which stylesheet rules
matched the element — the cascade's own answer, last match wins — so
the write lands in the rule that actually styles what you see, even
when the same selector appears twice in a file.

## 6. The honest limits

Edit Style… refuses, with the reason on the status bar, whenever
writing would be a guess or would destroy work. The inline preview
still applies in every case — you see the tweak; the message tells you
why it wasn't saved.

| Situation | What it says |
|-----------|--------------|
| The rule lives in an inline `<style>` block | "rule lives in an inline `<style>`, not a stylesheet file" |
| The stylesheet is remote or from an unknown server | "not served from a project here" |
| The `.css` has a `.scss`/`.less`/`.sass` sibling | "compiled output — edit the preprocessor source instead" (a write here would be lost on the next compile) |
| The file has unsaved changes in an editor | "has unsaved editor changes — save it first" |
| No stylesheet rule matches the element at all | "Applied in page only — no stylesheet rule matches this element" |

## 7. Close the loop

If the page is being served by a rack device, you don't even need to
reload: the Browser's save-to-reload watches web-file saves and
refreshes local pages automatically. Pick → tweak → source updated →
page reloaded from that source. The browser and the editor, one
surface.
