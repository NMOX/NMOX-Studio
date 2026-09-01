# The 2031 dossier — web development five years out

*Opened 2026-08-29 at v2.51.0, on David's directive: bring NMOX Studio
up to speed with where web development is going, early and often. The
discipline: every bet is argued from a trajectory that is REAL today —
a shipped API, a Stage-3 proposal, a standards group with vendor
commitments — never invented. Every unit shipped under this dossier
must pay for itself in 2026 even if its bet drifts; a wrong bet may
cost us a vendored grammar, never a rotten feature. Bets are reviewed
when their ground truth moves. This is the futures sibling of
competitive-lens.md: that document looks sideways, this one looks
forward.*

## The bets

### F1 — The WebAssembly Component Model is the polyglot substrate. — SHIPPED (editor citizenship) v2.52.0

**Trajectory (2026):** WASI 0.2 shipped on the Component Model; wasmtime,
jco, and componentize-* toolchains ship today; the Bytecode Alliance's
`.wit` IDL is how components declare interfaces across languages.
**The 2031 claim:** `.wit` files are what `.proto` became for RPC — the
contract format a polyglot web team reads daily, with components the
unit of reuse between JS, Rust, Go, and Python on both edge and
browser. A polyglot IDE that cannot read the polyglot contract format
is blind at the seam it exists for.
**Shipped now:** full editor citizenship for `.wit` — the vendored
bytecodealliance grammar (Apache-2.0, sha256-pinned), CSL language with
`//` comments, keyword completion, spellcheck, typing, brace outline,
`wasm-tools` on the Environment Doctor.
**Recorded next:** a `wit-bindgen`/`jco` lane when a manifest signal
exists to hang it on; a Component Model learning space.

### F2 — Temporal, WebGPU, view transitions, and built-in AI are the everyday platform. — SHIPPED (vocabulary) v2.52.0

**Trajectory (2026):** Temporal is Stage 3 with shipping engines; WebGPU
is stable in every major browser; `document.startViewTransition` ships;
Chrome's built-in AI task APIs (LanguageModel/Summarizer/Translator)
are shipping origin-trial-to-stable.
**The 2031 claim:** these are the APIs a web developer types cold, the
way `fetch` and `querySelector` are typed today — `new Date()` reads as
legacy, GPU compute is ordinary front-end work, and on-device model
calls are as unremarkable as `localStorage`.
**Shipped now:** the completion vocabulary — `Temporal.*` and
`navigator.*` namespaces, `document.startViewTransition`, the built-in
AI and GPU-usage globals, and snippets (`viewtransition`, `temporal`,
`importjson`, `gpu`, `llm`).
**Recorded next:** CSS anchor positioning / `@starting-style` /
view-transition property vocabulary — pending a probe of what the
RELEASE310 platform CSS property DB already carries (the v1.231.0
recon said the property DB was current; re-measure before adding).

### F3 — Import maps make the CDN-less ESM app normal. — NEXT

**Trajectory (2026):** every browser ships import maps; frameworks and
registries (JSPM, esm.sh) lean on them; buildless dev is respectable
again for real apps.
**The 2031 claim:** a large share of web projects resolve modules
through a map in the page, not a bundler config — and an IDE that can't
follow a bare specifier through the map can't answer "where does this
import go" for those projects.
**The next unit:** import-map intelligence — ⌘-click a bare specifier
in JS/TS to land on its mapping in the page's `<script
type="importmap">` (specific-over-prefix per the spec's resolution
order), completion of mapped specifiers in import statements, honest
misses for unmapped names.

### F4 — Agents are a development surface; the IDE must be toolable. — RECORDED

**Trajectory (2026):** MCP is the de-facto agent-tool protocol; every
major assistant speaks it; ORACLE already gives this IDE an outbound
AI surface.
**The 2031 claim:** developers routinely point agents AT their IDE —
"what's failing", "what's serving", "run this lane" — and an IDE whose
state is reachable only through pixels is a second-class citizen of
that loop.
**Why recorded, not built:** an MCP server exposing the rack, servings,
and test lanes is a security surface the size of the product — every
exposed verb needs the trust/consent law rebuilt for a caller that
isn't the user at the keyboard. It deserves its own arc with its own
dossier, not a batch slot. The inward-execution law (v1.103.0) and the
consent-kind law (v1.171.0) are the design constraints already named.

### F5 — TypeScript's native port replaces tsserver. — RECORDED (watch item)

**Trajectory (2026):** TS 7 (the Go port) exists and dropped
`tsserverlibrary.js` — this product already pins TS 5 for ngserver and
the tsserver lane (v1.241.0 ceilings).
**The 2031 claim:** the native server is the only server; the TS-5 pin
becomes a liability on a schedule someone else controls.
**Why recorded:** the swap is an LSP-authority change in the most
law-dense corner of the product (ledgers 81/83, the workspace-kind
partition). The watch trigger is written at the pin: when ngserver
gains native-TS support, the ceiling moves as its own gauntleted arc.

### F6 — The classic web never dies. — STANDING (no change)

The 2031 web still serves jQuery pages, and this product's classic-web
citizenship (v1.34.0, v2.13.0) is already a decade bet in the other
direction. Named here so the futures work never trades it away: the
next decade is additive.
