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
**Shipped v2.57.0 — the CSS half:** the probe was run before adding
(control terms container-type and text-wrap PRESENT in RELEASE310's
css-lib DB; anchor positioning, view transitions, `@starting-style`,
scroll-driven animations, field-sizing, interpolate-size ABSENT — the
v1.231.0 "current" reading held for the old properties and not for the
2031 ones). CssFutures completes the 23 absent properties with their
meanings, their keyword values, and the three at-rules, across the
stylesheet family and the markup family's style regions. `anchor()`/`anchor-size()` complete as
values on the spec's inset and sizing properties — the one deliberate
reach onto platform-known properties. Honest limit: the platform
checker may still warn on the new names (ledger 71).

### F3 — Import maps make the CDN-less ESM app normal. — SHIPPED v2.53.0

**Trajectory (2026):** every browser ships import maps; frameworks and
registries (JSPM, esm.sh) lean on them; buildless dev is respectable
again for real apps.
**The 2031 claim:** a large share of web projects resolve modules
through a map in the page, not a bundler config — and an IDE that can't
follow a bare specifier through the map can't answer "where does this
import go" for those projects.
**Shipped v2.53.0:** import-map intelligence — ⌘-click a bare specifier
in JS/TS to land on its mapping in the page's `<script
type="importmap">` (specific-over-prefix per the spec's resolution
order), completion of mapped specifiers in import statements, honest
misses for unmapped names.

### F4 — Agents are a development surface; the IDE must be toolable. — SHIPPED (read-only) v2.54.0

**Trajectory (2026):** MCP is the de-facto agent-tool protocol; every
major assistant speaks it; ORACLE already gives this IDE an outbound
AI surface.
**The 2031 claim:** developers routinely point agents AT their IDE —
"what's failing", "what's serving", "run this lane" — and an IDE whose
state is reachable only through pixels is a second-class citizen of
that loop.
**Shipped v2.54.0, READ-ONLY BY CONSTRUCTION:** the Agent Port —
Tools ▸ Agent Port (MCP)…, off until an explicit gesture starts it, a
loopback-witnessed MCP Streamable-HTTP endpoint over the SiteServer
recipe. Five read-only tools (project_state, live_servers,
last_failure, diagnostics, rack_devices) over already-bounded state —
last_failure is ORACLE's own FailureContext, the shape a consent
dialog already describes. The transport rewrites the trust laws for a
caller that is not at the keyboard: per-start SecureRandom bearer token
(constant-time compare, never logged or persisted), any Origin header
refused outright (the spec's own DNS-rebinding defense, made total),
POST-only, body-capped. And the arc's load-bearing law is STRUCTURAL:
McpReadOnlyLedgerTest fails the build if any class in the mcp package
names a spawn, a file write, or the trust gate — so the inbound port
can never quietly grow teeth.
**The recorded v2 (execution):** run-a-lane / stop-a-server verbs are
the deliberate next arc, and the entry fee is the consent design — the
inward-execution law (v1.103.0) and the consent-kind law (v1.171.0)
say a caller-that-is-not-the-user cannot simply be handed
WorkspaceTrust; it needs a consent model of its own (a per-session
grant the user arms at the keyboard, scoped to named verbs). The
read-only ledger is what makes that a DELIBERATE future step instead
of an accident waiting in a pull request.

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
