# The Competitive Lens

*Written 2026-08-29 at v2.47.1, from David's directive: read NMOX Studio
as a rival IDE's developer would — find the places where they feel
RELIEF, capture them, and build them until the relief is gone. This is
the persona-lens method (v1.210–v1.215) aimed at the product's
competitive posture instead of a user journey. It is a living document:
each relief item carries its evidence, its response, and its state.*

## Where the rival is already worried (the moat — for context)

Not the subject of this document, but the honest baseline: the task
rack and its 53 devices as a programmable, patchable surface; the six
studios over per-project files with the whole law family (atomic,
healed, keychain-only, bounded); 86 grammars / 92 learning spaces / the
teach-and-check loop; the walk-and-review culture that finds bugs
before users do; the definitive-web3 vertical proven on real mainnet;
a self-updating, self-signed, self-documenting distribution; free and
Apache-2.0. None of that is what a rival copies quickly.

## The relief list

Each item is what the rival's developer says out loud, with the
evidence that makes it true, and what we did about it.

### R1 — "Their AI explains code. It doesn't *write* any." — CLOSED v2.48.0

**Evidence (2026-08-29):** the whole ORACLE family — EXPLAIN on the
device, Explain… in API/DB Studio, Ask ORACLE About Selection, the
checkpoint tutor, Explain error… — was read-only: not one code path
wrote to a document. In 2026 the competitive headline is AI that edits.

**Response:** Edit with ORACLE… (v2.48.0) — selection + instruction →
one gated send → the reply accepted only as exactly one fenced code
block → a BEFORE/AFTER preview → Apply as one undo unit behind a
stale-buffer guard. The laws that make it ours rather than a clone:
over-cap selections REFUSE (a truncated edit deletes the un-sent
tail), ambiguous replies REFUSE (never guess which block), the buffer
is re-verified at apply time, and nothing is sent without the explicit
gesture plus the CODE consent.

### R2 — "No project-wide symbol navigation." — OPEN, next

**Evidence:** the platform's jumpto module ships in the cluster and
nothing feeds it; no `workspace/symbol` consumer exists; ⌘I reaches
projects, devices, servers, requests, and infra nodes — never a
function or class by name. Go to Declaration works from a *usage*; there
is no way to jump to a symbol you merely *remember*.

**Response:** planned as its own unit — a symbol provider over the
outline-extractor family (the same extractors that feed the Navigator
for 58 mimes), bounded walk, heavy dirs skipped, mtime-cached.

### R3 — "Tests are invisible until they run." — OPEN, next

**Evidence:** VERITAS runs suites and Run Focused Test runs one file's
worth, but no surface LISTS the project's tests. A rival's test
explorer shows the tree before anything runs.

**Response:** planned — a discovery surface riding the Run Focused
Test machinery, honest about the frameworks it can and cannot name.

### R4 — "No ghost-text inline completion." — OPEN BY POSITION

**The honest state:** true, and deliberate for now. Always-on
completion streams keystrokes to a network service; the house law is
*no network without an explicit gesture, each disclosure kind earns its
own consent, every send bounded and named*. Ask/Edit/Explain are that
law's shape. An inline lane that honors it (local-model or
opt-in-per-project with a visible chip) is a real future unit, not a
denial — recorded so the position is chosen, not drifted into.

### R5 — "No remote development." — OPEN

**Evidence:** no SSH/container/WSL workspaces. HELM (ssh runner) and
the Docker panel are adjacent, not remote workspaces. A platform-scale
investment; recorded honestly, no small unit closes it.

### R6 — "No PR or code-review surface." — OPEN

**Evidence:** the Team menu is the platform git suite; PRs live in the
browser. A bounded unit exists here (PR list/checkout via the forge
API) but it needs an auth story that honors the keychain-only law.

### R7 — "The editor lacks minimap / sticky scroll." — OPEN

**Evidence:** platform editor surface; both are deep-editor
investments. Recorded; the walks have never surfaced a user journey
blocked on either, so they rank below R2/R3.

## The rule this exercise proved

What relieves a rival is never the features you polished — it is the
daily-driver gestures you never built because your own walks kept
working around them. The relief list is therefore a better backlog
than introspection: it ranks by what an outsider would exploit, not by
what an insider finds interesting.
