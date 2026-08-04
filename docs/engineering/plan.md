# The Plan

*Rewritten 2026-07-12, at v1.50.0, as a fresh senior-eyes capstone by the
assistant that built v1.8→v1.50 with David; currency pass the same day at
v1.56.0 after the roadmap sprint shipped six more releases (update center,
ORACLE, community Learning Spaces, the SPI pre-work, the Device SPI, the
third senior review); currency pass 2026-07-13 at v1.62.0 after the
overnight web-toolchain run (v1.59–v1.62: the Gleam vertical, package-manager
truth, Biome lanes, the journey polish, ledger 45); extended the same night
at v1.63.2 (the workspaces vertical, the dynamic-knob restore fix, the
review pass over the overnight surface); currency pass 2026-07-19 at
v1.95.1 after the two day-run marathons (v1.86–v1.95: SPECTER and the
closed E2E loop, the console-jack and serving-gate truth work, ORACLE's
cable path built AND live-proven against the real API, the modern
lightweights, click-to-click patching). The prior capstone was written
at v1.36.0; seven releases (v1.44.0→v1.50.0) ran overnight and drained the
high-value debt queue, so this is a from-scratch pass, not a patch. Currency pass 2026-07-23 at
v1.126.0 after the security-and-robustness arc (v1.96–v1.124: a dedicated
review for EVERY module, the editor/tools RCE gates, keychain-only secrets
in the last holdout, bounded reads on every HTTP/process/file path, the
whole deferred ledger worked to empty) plus forge v2 (13/13 tutorials
illustrated) and the same-day review of that surface. Currency pass 2026-07-24 at
v1.144.0 after the Web3-kit day: the arc that began at v1.130.0 closed
and then kept going — the Contract Kit wizard (v1.139.0, nine chains by
v1.143.0 including Bitcoin-as-spending-conditions and Clarity on
Stacks, every template live-proven against its real toolchain BEFORE
ship), Move speaking both dialects (v1.142.0, the AptosFramework sniff
steering all five lanes), a debt-only sprint (v1.141.0: the kit
write-law extracted to one KitFiles home + the NEW KitCatalogParityTest
that fails the build when kit and learning-catalog crate pins drift —
the pin-rot lesson made structural), and the arc's own review
(v1.144.0: two same-day finds, both fixed). Extended the same
night at v1.151.0 after the AI arc and the Cardano close: **the AI
surface reached the editor** (v1.146–v1.150: Ask ORACLE on any
selection, multi-turn conversations for both the code and failure
flows, the consent-scoping and prompt-parity laws, the arc's own
review, Fast/Deep model choice — the editor face click-verified LIVE
in the shipped app), and the Contract Kit closed its tenth AND eleventh chains
(v1.151.0 Cardano/Aiken, refusal paths as declared tests; v1.153.0
TON via Tact — FunC honestly out on the GPL+archived grammar, the
refusal a bounced transaction asserted as such). Currency pass 2026-07-25 at v1.162.0
after the docs-and-detection day: the demo tranche (v1.156–v1.159:
README hero, docs/tour.html, the smart-contracts beginner's guide,
docs/tour.md rendering natively on GitHub), the About-screen logo
(v1.160.0, branded about.png the platform prefers via
Splash.loadContent(true)), and the kind-lattice close (v1.161.0:
aiken.toml/Clarinet.toml/tact.config.json as ProjectKinds — 58
recognized manifests, every lane wired, the CLARITY-outranks-NODE
precedence inversion mutation-proven; its same-day review, v1.162.0,
caught the one starved consumer — PreflightPlan fell to the empty
default for CLARITY-primary repos, losing the npm ship checks the
same repo had as NODE — the "precedence flip starves NODE-keyed
consumers" failure pattern). Standing counts then at
v1.162.0: 53 devices, 77 grammars, 88 learning spaces, five studios,
eleven kit chains, 58 recognized manifests — the three contract
languages the kit scaffolds (.clar/.ak/.tact) are editor citizens AND
their repos open as projects with real lanes. Currency pass 2026-07-26 (the overnight API-surface arc, v1.164–v1.168):
the same-day review law paid four more times. v1.164.0 held the new
preflight plans against their own oldest law (plansOnlyWhatExists) and
conditioned five file-assuming checks; v1.165.0 gave API Studio curl
both ways (Import curl… with the Authorization-header lift into the
keychain-backed Auth field — the secrets law applied at import time —
and Copy curl emitting the exact command Send would run); v1.166.0 made
.http/.rest files citizens (the REST Client dialect imports
near-losslessly because its {{variables}} ARE API Studio's syntax; the
vscode-restclient grammar vendored MIT as the 78th); v1.167.0 was the
live in-app gauntlet of that whole surface — every law check passed on
screen (masked keychain Bearer, no plaintext header row, byte-exact
clipboard) and its two finds (method-duped names, toolbar overflow
hiding Delete) shipped fixed the same night; v1.168.0's night-arc
review caught the XML-body refusal (startsWith("<") is not a file
reference — "< path" is) and moved the .http read off the EDT per the
v1.108.0 law. Standing counts as of v1.168.0: 53 devices, 78 grammars,
88 learning spaces, five studios, eleven kit chains, 58 recognized
manifests. Process lessons of the night, recorded in the pipeline
memory: the tree-frozen law includes the CHECKED-OUT BRANCH (a
mid-pipeline `git checkout -b` killed one ship at gh pr create), and
the pipeline's own step-1 verify caught two more real bugs before
anything left the machine (CRATE null starving CI export; a
months-latent test reading the developer's real ~/NMOX).

Currency pass 2026-07-26 (the AI-surface arc, v1.169–v1.175): the
import trio closed with OpenAPI 3 JSON (v1.170.0, one request per
operation, `{petId}`→`{{petId}}`, honest YAML/Swagger-2 refusals) and
the update-center gauntlet re-passed across nine releases of drift
(v1.160→v1.169 in-app). Then ORACLE grew its fourth and fifth faces on
a NEW frozen seam: `core.spi.OracleAsk` carries a text-only
`Disclosure` — no request/response/result types can cross it, so the
rack can never widen what a studio chose to send — and each disclosure
KIND earns its own one-time consent. v1.171.0 wired API Studio
responses through it (query values masked, credential headers
dropped-and-counted); v1.174.0 wired DB Studio failed statements
(literals deliberately NOT masked — the error is usually about a
literal — and the consent line says so). Both live-proven against the
real API in the shipped app. The arc's reviews caught the same bug
class TWICE in fresh code — *a stale artifact that was cosmetic for
years becomes a disclosure path the moment an Explain button is
attached to it* (v1.172.0: API Studio kept the previous project's
response armed across a re-aim; v1.175.0: DB Studio kept its error
tabs) — now a standing law: A RESULT BELONGS TO THE WORKSPACE THAT
PRODUCED IT, and the review lens that finds it is "does consumer N
survive the hazard consumer N-1 failed?". Two more process laws paid
for in blood: *tests that touch real-userRoot prefs own a private
`test.*` namespace, clean in @AfterEach, and assert UNCHANGED on keys
they don't own* (bit three times before v1.173.0 pinned it), and
*`mvn verify -pl module` cannot see cross-module @ServiceProvider
registrations — a soft-dependency claim is only proven by the full
reactor plus a grep of the pom* (v1.174.0 shipped a test whose premise
was wrong under exactly that blindness). Standing counts as of
v1.175.0: 53 devices, 78 grammars, 88 learning spaces, five studios,
eleven kit chains, 58 recognized manifests, and ORACLE reachable from
the rack, the editor, API Studio, and DB Studio.

Currency pass 2026-07-26 evening (the day shift, v1.176–v1.184, nine
releases): the migration story completed — API Studio now reads
Postman Collections v2.x (v1.177.0) and browser HAR captures
(v1.178.0) and writes .http files back out (v1.179.0), making the
import family curl/.http/OpenAPI/Postman/HAR in, .http out. The
secrets law was applied at every new border and then TIGHTENED by the
same-day review (v1.181.0): a HAR is a RECORDING, so a captured
non-Bearer/Basic Authorization now follows the Cookie rule
(drop-and-count) while the curl import deliberately keeps what the
user typed — the distinction is written at both code sites. The
review's second find was proven failing-first on shipped code: a body
line starting with ### split the .http round trip into two requests;
the fix is the auth idiom, omit-and-say, because the dialect has no
escape. The whole surface was then gauntleted live in the INSTALLED
app with a byte-level secret scan (v1.182.0, zero finds, three
planted secrets absent from both the exported file and
.nmoxapi.json). David's image-optimization feature shipped as the
Image Kit (v1.183.0): pure-Java JPEG press + downscale with the
user's own cwebp for WebP siblings, the kit laws applied to pixels
(never-clobber, under-10%-savings discarded as already-tight),
live-proven 17.8 MB → 347 KB before ship. Debt paid alongside: all
ten JaCoCo floors ratcheted to measured-minus-margin (v1.180.0), the
CHANGELOG link block regenerated for all 217 versions after
dead-ending at v1.129.0 (v1.176.0 — found because a docs pass LOOKED),
and two new tutorials (Explain-anything, Migrating-from-Postman,
Image Kit makes three) joined the index. Process laws added to the
pipeline memory: the ship-script deriver must replace the WHOLE
multi-line pr-create command and `bash -n` the result before launch
(bit once, then the check caught the second attempt), and a
Maven-Central 403 on a release lane is a rerun---failed flake, not a
code problem.

Currency pass 2026-07-27 (the night shift, v1.185–v1.189, five
releases): the same-day review law paid twice more, both times on
code written hours earlier by the same hands. v1.185.0 found the
Image Kit's disk scan running ON THE EDT in actionPerformed — the
v1.33.1/v1.115.0 class regrown in day-old code, which is exactly why
the review lens list must always include "who walks the disk, on
whose thread?" for any new action; the fix is source-gated by a test
that fails on the shipped shape by construction. v1.189.0 found the
fresh InsomniaCodec's environment merge claiming base-favoring in a
comment while the code favored resources[] order — proven
failing-first with a sub-env-first fixture; the method gained *a
comment that claims an ordering property is a test that hasn't been
written yet*. Between the reviews: Postman environment import
(v1.186.0) turned the v1.177.0 refusal into the feature with the
refusal message now pointing at the menu item — a refusal is a
promise to either keep saying why or ship the feature — and
secret-typed values never enter the committable .nmoxapi.json;
Insomnia v4 import (v1.188.0) made the migration family
curl/.http/OpenAPI/Postman-collections/Postman-environments/Insomnia/
HAR in, .http out; and the night gauntlet (v1.187.0) byte-proved both
new surfaces in the INSTALLED app, recording the brew-races-
homebrew-job trap (verify the cask VERSION, not the upgrade's exit
status, before gauntleting a just-shipped feature). The one honest
refusal left in the import family is OpenAPI YAML. It is
the current-reality companion to [tech-debt.md](tech-debt.md) (the itemized
ledger): where the project stands, what's genuinely not done, what's worth
doing next, and the working method that got it here. Unlike most of
docs/engineering/, this file is NOT historical — keep it true or delete it.*

Currency pass 2026-07-31 at **v1.208.0**, after the day that took the
IDE from "web studio" to "web studio you can live in": the muscle-memory
and API-Studio tranche (v1.194–v1.198), the **in-app browser** (v1.199.0,
FX-lit platform WebKit, ⌥⌘4), the coverage-and-comments arc David asked
for (v1.202–v1.203: behaviour tests across every module, floors ratcheted,
and docs/engineering/codebase-guide.md — the beginner's walk), a **full
IRC client** (v1.204–v1.205: freenode by default on ⌥⌘3, then SASL,
IRCv3 caps, tab completion, highlights, logging, /list, WHOIS, ignore, a
network editor; Escape is dead in a docked TopComponent so clearing is
Ctrl+U), **Browser DevTools** (v1.206.0 — JavaFX WebView has no inspector
and no remote-debug protocol, so we own the engine: Console/DOM/Network/
Storage/Vue panes over executeScript plus an injected bridge; a Vue
production build exposes no component tree and the pane says so),
**Svelte as a full vertical** (v1.207.0, plus the DevTools Svelte pane
that maps live DOM back to .svelte source via __svelte_meta — Svelte
compiles components away, so source mapping is the honest ceiling), the
**Apache-2.0 relicense** (v1.207.0, David's decision: the app now shares
the NetBeans Platform's license; permissive-to-permissive, deliberate,
NOT legally required — don't cargo-cult it as forced), the release-CDN
hardening (v1.207.1), and **the arc review** (v1.208.0) that found two
security bugs and one file-corruption bug in four-day-old code. All four
v1.208.0 fixes were then click-verified in the shipped app, two of them
behaviourally (an empty beacon log proving no fetch; a ticker that stops
when the tab closes). Standing counts at v1.208.0: 53 devices, 80
grammars, 88 learning spaces, six studios (Block, API, DB, Contract,
Infra + the Browser), 58 recognized manifests, Apache-2.0.

Currency pass 2026-08-01 at **v1.216.0**: the persona-lens arc
(v1.210–v1.215) and its review. Three persona lenses in a row found
bugs ~200 releases of senior review had not — a junior hit a shipping
TextMate grammar that crashed on their FIRST project (v1.210.0); a
mid-level dev found the IDE's own Run button passing an EMPTY LAMBDA
for output, silently disabling the whole serving chain (v1.212.0); an
information architect measured 24 of 49 ordinary search terms
returning NOTHING (v1.215.0: one term-based matcher across all eight
search surfaces + a controlled device vocabulary, 0 of 49 after).
Between them: Browser+IRC default-open for discovery (v1.211.0,
build-on-first-show so boot stays free), eslint as a second LSP on the
TS/JS mimes (v1.213.0 — the platform's lookupAll lets servers share a
mime, decompiled not assumed), and the Angular Language Service
(v1.214.0, David's positioning call: stop investing in React, be
excellent at Angular; ngserver needs mandatory probe locations and
TypeScript 5 — TS 7 dropped tsserverlibrary.js). **The arc review
(v1.216.0) found the persona sprints' own fixes had the arc's two
worst bugs**: the two new LSP providers honored the trust law for the
server binary while executing the workspace's own code as the payload
(ngserver require()s the repo's node_modules; eslint flat configs are
plain JS the server evaluates) — both now trust-gated; and the ⌘P
rebind was dead EVERYWHERE, killed three times over (wrong tree,
editor-consumed key, action disabled without an open project) — the
live gauntlet that the review demanded is what surfaced all three,
and the fix is now click-verified in the built app. Also: the Angular
install path was unwalkable (project-local install, global-only
resolve, Install button running npm in $HOME), the serving id
collided across two Runs of one project, the STATIC lane relearned
the v1.37.0 python -u lesson, and short CJK searches regressed by the
v1.215.0 word-boundary rule — all fixed, three mutation-proven,
ledger 63–67 records the deferrals. Failure patterns reinforced: *a
gate that covers the binary but not its payload is not a gate*, and
*an affordance documented but never exercised is untested* (the ⌘P
chord, the User Guide link, the Angular install — all v1.38.1's
lesson recurring).

Currency addendum 2026-08-01 at **v1.217.0**: Angular template
awareness — `.component.html` resolves to its own text/x-ng-template
mime (a declarative MIME resolver at position 250; the live rounds
proved a @ServiceProvider resolver NEVER beats a declarative claim,
and the decompiled matcher AND-composes ext + name-substring), the
Angular team's five grammars vendored sha256-pinned with FOUR
registered as TextMate injections into text.html.basic (the first
injection use in the codebase; expression.ng stays include-only
because injecting it tokenized `<h1>` as a TS operator), @-block and
*-directive completion gated on angular.json, and the whole vertical
click-verified live. The release's real discovery is a NEW failure
pattern: **a TextMate grammar alone does not make a mime** — with no
DataObject loader and no EditorKit the editor opens the file with the
plain kit and every MimeLookup feature (coloring, completion,
actions) is dead at once, silently. Making the mime a CSL language is
what brings the loader + kit; applying the same fix to
text/x-http-request revealed **.http files had never highlighted
since v1.166.0** ("grammar-only by design" shipped without a live
coloring proof — the affordance-never-exercised pattern again, fifty
releases deep). Second lesson: a CSL kit's own toggle-comment SHADOWS
same-named Actions-folder registrations — configure CSL's
CommentHandler instead of fighting the shadow.

Currency addendum 2026-08-01 at **v1.218.0**: the consumer-2 proof —
"does ngserver actually attach and answer on the new mime?" — found
the Angular Language Service silently dead twice over. (1) The
platform LSP client sends the RAW MIME as didOpen's languageId unless
a LanguageIdResolver rides the server description's Lookup; servers
key on the VS Code identifier vocabulary, so ngserver never treated
our documents as templates. New `LspLanguageIds` at the `launch()`
choke point maps every mime (generic subtype rule + a four-entry
exception table) for all ~55 registered servers. (2) `launch()`'s
missing-tool guard tested `new File(pathDir, name)` with an ABSOLUTE
path as name — never resolves — so every absolute command was refused
quietly: **the v1.216.0 project-local `.bin/ngserver` fix could never
launch the server it correctly found**. That is the payload-vs-gate
failure pattern's third strike in three releases, now with its
sharpest formulation: *a fix verified by unit-testing the resolution
function is unverified until the resolved value survives every
downstream gate — live.* The proof that closed the release: ngserver
spawned from the project's own .bin in the shipped app, and a typo'd
`user.logedIn` drew the Angular compiler's own "Did you mean
'loggedIn'?" squiggle against the component class's type — cleared on
revert.

Currency addendum 2026-08-01 at **v1.219.0**: the senior-Angular
persona pass ("grant their wishes"), granting the two navigation
gestures the arc had earned but not yet delivered. **⌘B Go to
Declaration in templates** required two cooperating pieces, both
proven necessary live: a mime-registered `HyperlinkProviderExt`
enabler (identifier-span answers, click delegated to the platform LSP
client's own provider, which owns the server connection) and an
`ng-goto-declaration` editor-kit action bound through mime-scoped
keybindings — because on CSL panes the platform's global
goto-declaration never consults the hyperlink-provider chain for the
mime, and a SAME-NAME mime action loses to the root registration. The
recipe worth keeping: *non-colliding action name + mime keybinding for
the chord; enabler feeds the hyperlink/hover chain; the action owns
the gesture.* **Component ↔ template switching** shipped as two popup
actions over a pure `NgSwitch` core (templateUrl-wins resolution,
sibling convention backstop, spec-excluded owner scan, honest
status-line misses). The session's method lesson, earned the expensive
way: **a stale platform cachedir masks NEW layer registrations from
hot-swapped module jars** — code edits to already-registered classes
take, so a correct new registration reads exactly like a wrong one;
wipe the cachedir before concluding anything about registration
shapes. Several hours of this session's recon were spent disproving
conclusions that were cache artifacts.

Currency addendum 2026-08-01 at **v1.220.0**: the same-day arc review
(v1.217–v1.219), one find: the v1.219.0 switcher actions did their
file reads — component source plus a sibling-directory scan — in
`actionPerformed`, i.e. on the EDT, the v1.108.0 disk-on-EDT class in
day-old code; resolution moved to a named RP, source-gated. The
review's clean sweep is itself evidence the arc's laws held:
`LspLanguageIds` needed no fifth exception because the product's mime
vocabulary was already VS-Code-aligned, and `refusesCommand` survived
extraction intact. The review streak stands: every same-day arc review
since v1.135.0 has found at least one real defect the arc's own tests
missed, and it is almost always a HOUSE LAW violated in fresh code —
the laws are learned per-surface, not per-author.

Currency addendum 2026-08-01 at **v1.222.0**: the DevTools Angular
pane — the framework bet gets what Vue and Svelte already had. Design
mirrors the Vue pane exactly (page-side walker with caps + Java-side
parser re-imposing them + honest empty states), with one Angular-shaped
insight: `ng-version` is stamped in dev AND prod while `window.ng`
exists only in dev, so "production build, no component tree" is
detectable as its own answer, never confused with "no Angular". The
gauntlet ran all three states against a real Angular 18.2 app in the
assembled cluster (dev tree + state + highlight; prod message; HN
no-Angular) and caught two things worth keeping: `__ngContext__` must
be filtered from the state bag (framework plumbing reads as a leak),
and **ledger 70** — `ng serve`'s esbuild dev server hangs the JFX
WebView (load never commits; the same bundle served statically is
fine). Method notes: the dev cluster can borrow the installed app's
FX-bundled jre via `--jdkhome` for Browser work, and the jar-swap +
wiped-cachedir loop is now a fully reliable fast iteration path.

Currency addendum 2026-08-01 at **v1.223.0**: Run Focused Test speaks
Angular (the CLI's own runner, file-focused — Karma's honest ceiling),
and working the surface found a SECURITY hole both prior sweeps
missed: the action spawned project runners (`npx jest`, `cargo test`,
`phpunit`, `mix test`) with no Workspace Trust gate — one context-menu
click on a cloned repo's spec executed its committed code. Gated now,
the debug-action idiom, source-pinned. The live proof then caught its
own second bug: Angular's `src/index.html` is a STATIC-kind manifest,
so the generic manifest walk stopped at `src/` and both mislocated the
trust root AND hid `angular.json` — the branch silently fell back to
jest. Two failure patterns reinforced in one release: *an action that
spawns is untested until its TRUST PROMPT has been seen live* (the
prompt named the wrong directory — that's how the bug announced
itself), and *a fixture that omits a manifest the real layout carries
tests a project that doesn't exist* (src/index.html now in the
fixture).

Currency addendum 2026-08-01 at **v1.224.0**: the spawn-site trust
sweep — the v1.223.0 lesson made structural the same day. Every
`CommandExecutor.run` caller is now enumerated in
`SpawnSiteTrustLedgerTest`, a build-failing ledger where each site is
classified GATED / GATED-BY-CALLER / BLESSED-with-reason; an
unclassified new caller fails the build. The sweep found THREE more
ungated sites: Contract Studio's forge build/test (the sharpest —
Foundry's ffi cheatcode makes a repo's tests arbitrary host commands;
fixed via the new `core.spi.TrustGate` facade + rack adapter, closing
the v1.46.0 "TrustGate deliberately not facaded" deferral the moment
it grew a soft-dependency consumer), Project Config's npm add/remove,
and the project-local ALS install. The method lesson: *per-incident
security fixes leak — two dedicated sweeps each fixed every site they
knew about and still missed four; only an enumerated, build-enforced
ledger closes the class.* Live proof: the forge trust prompt fired
naming the correct root through the facade chain, and a grant ran the
real forge build to completion.

Currency addendum 2026-08-01 at **v1.225.0**: the day closes with its
own review — v1.222–v1.224 lenses CLEAN (facade wiring now pinned by
RackTrustGateTest; one LOW accepted in writing) — and ledger 69 closed
the day it earned closing: clearForTest now flips WorkspaceTrust into
a scratch-node test mode, so a local `mvn verify` stops deleting the
developer's real trust grants (it bit five times today; the sentinel
regression pins it). Seven releases on 2026-08-01: v1.219 goto +
switcher, v1.220 review, v1.221 docs, v1.222 DevTools pane, v1.223
focused tests + the missed gate, v1.224 the sweep + ledger, v1.225
this close. The Angular roadmap's remaining item is the ng-serve ↔
JFX WebView hang investigation (ledger 70).

Currency addendum 2026-08-01 at **v1.226.0**: ledger 70 root-caused
and FIXED the same day it was filed. The JFX WebView sends the RFC
7540 §3.2 cleartext-upgrade probe on every plain-HTTP request, and
Angular's esbuild dev server accepts such a connection and never
answers. The method is the transferable part: **capture the exact
bytes, replay them headlessly** — a socket logger recorded the
WebView's real request, curl replayed it, and a UI-only "it just
hangs" became a one-header bisect (hangs with `Upgrade: h2c`, 200 in
5 ms without, static server fine either way). Fixed by setting
`com.sun.webkit.useHTTP2Loader=false` before any WebKit class loads.
`ng serve` now renders in the Browser with the DevTools Angular pane
reading its live tree — the framework bet's dev loop, in-app, end to
end. EIGHT releases on 2026-08-01.

Currency addendum 2026-08-02 at **v1.227.0**: the Senior Web Designer
persona pass opens ("Look at the software through the eyes of a Senior
Web Designer, and grant their wishes"). The recon drive's verdict in
three minutes: CSS opens with highlighting and outline, but `#336699`
is hex soup — no swatch anywhere (the cluster's css-editor modules
ship but don't decorate our CSL panes). Part 1 grants: inline color
swatches (literals painted AS their color via a HighlightsLayer, the
JsOccurrencesHighlighter idiom; pure CssColors core with
comments-are-prose and identifier-boundary rules) and a WCAG contrast
verdict in the DevTools DOM pane (pure WcagContrast pinned on
reference values; transparent backgrounds honestly refuse). Both
live-proven. Part 2 candidates for the persona: responsive viewport
presets in the Browser, and save-to-reload for served static projects.

Currency addendum 2026-08-02 at **v1.228.0**: designer pass part 2 —
viewport presets (StackPane-centered WebView capped to CSS-pixel
device sizes; the media query fired live on the iPhone preset) and
save-to-reload (global FileChangeAdapter filtered to web extensions,
local-URL-only, 400 ms coalesced, attach/detach symmetric with the
tab). The persona's loop is closed: swatches where colors live,
contrast where elements are, breakpoints at real widths, save → see.

Currency addendum 2026-08-02 at **v1.229.0**: designer pass part 3 —
the click-a-color picker (⌘-click a literal → chooser seeded with it →
in-authored-form replacement as one undo unit, stale-document refusal
with an honest status line; rides the same HyperlinkProviderExt idiom
as the v1.219.0 template goto). The release's gauntlet found a real
v1.227.0 bug: the swatch layer sat in the SYNTAX racks and the
CSL/TextMate coloring painted over it for every grammar-recognized
literal — `tomato` and `#hex` never showed a swatch, only
grammar-unknown names did; moved to SHOW_OFF_RACK, all forms paint.
The failure pattern to keep: *a highlight layer that loses an
attribute merge fails silently and partially — the feature "works" on
exactly the tokens the grammar doesn't know, which a quick look
mistakes for working.*

Currency addendum 2026-08-02 at **v1.231.0**: the Junior CSS3 persona
— CSS Color 4 functions (oklch/oklab/lab/lch/hwb/color-mix) swatch
with real color-space math and pick back in authored form; the swatch
layer moved to TOP rack after the gauntlet showed the legacy parser's
warning background hiding modern colors. Ledger 71 records the
three-way external block on silencing those false warnings, with the
decompiled evidence. Recon lesson: the platform property DB is
current (completion knew container-type with spec docs) — probe
before assuming a whole surface is stale; the rot was only in the
value grammar.

Currency addendum 2026-08-02 at **v1.230.0**: designer pass part 4 —
Compile to CSS with recompile-on-save armed on the gesture (sass CLI,
Prettier-idiom trust-gated binary, partial refusal, error verbatim);
the scss → css → Browser-repaint loop live-proven with one ⌘S. The
gauntlet's find: **css-prep claims .scss/.less as text/scss and
text/less before our x- resolver**, so every x-scss/x-less surface
(v1.227 swatches, v1.229 picker, Prettier popup) had never reached a
real .scss file — only .sass. The failure pattern to keep: *a mime
registration is only as real as the resolver race it wins — verify
which mime a REAL file gets (the popup's action list names the owner)
before believing any per-mime feature shipped.*

Currency addendum 2026-08-03 at **v1.234.0**: the CSS arc closed with
v1.232.0 (stylelint-lsp as the css family's second linter — trust- and
config-gated, ledger 71's recommended mitigation shipped) and the IDE
lanes opened for every stack in v1.233.0 (DOTNET/TACT/CMAKE lanes, the
glob-kinds recognition fix — "the lanes existed; the door didn't" —
the async OpenProjects barrier, and a completeness gate so a
default-null switch arm can never ship silently again). The v1.234.0
night review (two lenses over v1.226–v1.233, seven MEDs fixed) then
caught the recognition fix's own tail the same week it shipped:
~/.nimble (a DIRECTORY) made $HOME a project, CMakeLists.txt-per-subdir
fragmented ownership, and getChildren() paid a full listing per
ancestor walk. Method notes worth keeping: **a z-order comment is a
claim about a total order — decompile the anchors (warnings SHOW_OFF
420, selection SHOW_OFF 500) instead of hoping**; **a second door into
the spawn room (ProcessSupport.builder beside CommandExecutor.run)
needs its own ledger row or a gated site is invisible to the gate
audit**; and **a stale module cache after a jar swap fakes product
bugs — the corrupted dev install manufactured three of them before a
cachedir wipe told the truth** (the v1.233.0 session's detour).

Currency addendum 2026-08-03 at **v1.238.0** (the night shift's tail):
four more releases after the v1.234.0 review. **v1.235.0** closed the
ledger-29 remainder the affordable way — AimFollower gives six suite
windows (Welcome/Browser/IRC/Docker/Block/DB Studio) the aimed
project's node as ambient selection, so Test Project ^F6 and the Team
menu work wherever focus is (live-proven from the WELCOME tab: Test
Project (cmake-demo) ran ctest 1/1); apiclient/web3/infra stay
selection-less by the v1.46.0 architecture → ledger 72. **v1.236.0**
and **v1.237.0** applied one lens twice — *a version literal inside a
generator is invisible to every bot that keeps the rest of the repo
current*: Export CI's action pins were three majors stale (nine bumps
+ CiExportPinCurrencyTest riding Dependabot's freshness for shared
actions), Dockerize bases moved to node:24/rust:1.89/golang:1.25/
php:8.4/trixie, and the project templates moved to React 19/Express
5/Vue 3.5/eslint 10/vitest 3/Vite ^6 — with ALL SEVEN touched
templates live-proven by real npm install + build, which is exactly
how the two deliberate ceilings were found (TS stays 5.x: TS 7
dropped tsserverlibrary.js and the IDE's own intelligence needs 5;
Vite stays ^6: 7+ demands node >=22.12 and a starter must run on a
learner's node — vite 8's rolldown binding refused node 22.9 in the
proof itself). The night also re-proved the UPDATE CENTER across
seven releases of drift: a stock v1.230.0 portable updated itself
in-app to 1.237.0, all 11 modules digest-verified with originals in
update/backup, clean restart. Process laws collected: mvn output
piped through tail EATS the exit code (pipefail always); -rf resumes
assemble a STALE cluster from ~/.m2 (fresh clusters need the full
reactor); a nohup'd pipeline dies with the tool shell
(run_in_background only).

Currency addendum 2026-08-03 at **v1.245.0** (the shift's last
word): two more releases after the close. **v1.243.0** — deps
housekeeping with the bump that would have lied refused: snakeyaml
2.6 + MariaDB 3.5.10 through the gate, and Dependabot's OpenJFX
21→26 pom-only bump REJECTED — ui's javafx deps are provided-scope
compile-time halves of the FX runtime the release workflow jlinks
from sha256-pinned 21.0.5 jmods, so a pom-only bump compiles the
Browser against an API it doesn't ship, skew no CI lane can see
(tests run with the maven dep, never the jlinked runtime);
dependabot.yml now ignores org.openjfx and ledger 74 owns the real
upgrade (both pins together + browser gauntlet). **v1.244.0** — the
THIRD generator home joined the proven line: the platform New
Project wizard still scaffolded Create React App (react-scripts 5,
dead upstream, unable to npm-install beside the React 19 pin
Dependabot kept fresh AROUND it) and its Vue template had marched to
vite ^8 because that home had no gate; react is now the proven Vite
set (the proof loop caught plugin-react 5 being ESM-only →
type:module), vue clamped, both npm-proven from the exact shipped
files on node 22.9, WizardTemplateCeilingTest holds the line. The
post-ship live check then found the platform template dialog itself
UNREACHABLE — both File ▸ New Project… and the Welcome tab open
Project Studio's wizard (the platform action was evicted in the
v1.11 chrome pass) — so the CRA breakage was invisible dead weight;
the fix is gated for whenever that door returns, and the
delete-vs-re-expose decision is queued for David. Method rule from
the tail: after any Dependabot rebase, verify mergedAt — `gh pr
merge` can fail "head out of date" while a checks poll reads STALE
pre-rebase runs, and script flow alone will lie about both.

Currency addendum 2026-08-03 at **v1.242.0** (the night shift's
close): three more releases. **v1.239.0** — File ▸ New Angular
Schematic…: `ng generate` as an IDE gesture (kit-action idiom,
HALO's exact schematic vocabulary, trust-gated + spawn-ledger
classified, traversal-guarded pure core), live-proven on ngdemo with
all four generated files byte-verified on disk. **v1.240.0** — the
night-tail review: the CoalescerTest sleep-based timing assumption
that flaked v1.239.0's macOS CI lane made deterministic
(await-the-dispatch — the merge it tripped over was CORRECT
coalescing), the schematic-vocabulary parity claim made structural
(source-reading gate, mutation-proven; and the mutation session
re-learned that `surefire:test` never recompiles a mutated source
and `-pl` without `-am` compiles against stale ~/.m2 — either one
makes a mutant lie). **v1.241.0** — the Angular truth release, the
night's sharpest find: the framework bet's OWN STARTERS were broken
and only installing them could show it. The ANGULAR template's ^22
pins could NEVER npm-install (Angular 22 requires TypeScript 6 —
all of it, 22.0 included — ERESOLVE against the template's own
~5.9), and the angular learning space shipped Angular-18 sketch
files with NO angular.json, so its own START (`ng serve`) had
nothing to read. Both now pin the live-proven ~21.2 + TS ~5.9 line
(the TS-5 ceiling binds), the space is a real minimal zoneless
workspace whose exact shipped files were npm-proven before ship,
both pin `@schematics/angular:component {type: component}` so ng
generate keeps emitting the `.component.*` naming the IDE's
template intelligence keys on (Angular 20+ scaffolds suffixless by
default — external suffixless repos are ledger 73), and
AngularSpacePinParityTest locks the two pin homes together. Facts
pinned so they never get re-derived: Angular's node engines are
19: >=22.0 / 20-21: ^20.19||^22.12||>=24 / 22: ^22.22.3||^24.15,
npm engines only WARN but the Angular CLI HARD-refuses (exit 3),
and the box's node 22.9 runs only Angular 19 natively (keg-only
brew node@22 = 22.23 exists for proofs). The v1.241.0 space was
then gauntleted in the shipped app: picker shows the new blurb,
scaffold lands the real workspace, and app.component.html opens
with Angular-template highlighting (@if keyword-colored,
interpolation and event bindings as expressions) — the v1.217.0
resolver claiming the space's files live. The night's law, twice
proven: *a starter that was never installed is a claim, not a
product — npm-prove every generator whose output is meant to run.*

Currency addendum 2026-08-04 at **v1.257.0** (the baseline advance):
David said "advance to the future," and the bundled runtime is now
**JDK 25 LTS + OpenJFX 26.0.2** — proven in the shipped artifact, not
just a local jlink. It took four releases, and each failure was a
distinct fact worth keeping: v1.253.0 built ZERO assets because a
JDK's jmods/ directory is OPTIONAL since JDK 24 (JEP 493 linkable
run-time images); v1.254.0 ALSO built zero because **Temurin 25 ships
NEITHER jmods NOR a linkable image** — jlink refuses outright ("This
JDK does not contain packaged modules"), so that distribution simply
cannot build a bundled runtime; v1.255.0 moved the three jlink lanes
+ the windows-installer check to **Zulu 25** (all 70 jmods, verified
on a real download) while build-and-test deliberately stays on
Temurin to prove the product builds on the distro most users have.
Neither failed tag ever published a release, so `latest` never
pointed at nothing. **The law that must not move:
maven.compiler.target STAYS 21** — the update center ships module
NBMs, never a runtime, so class-69 bytecode would brick an in-app
update on an install still running bundled Java 21 (recorded at the
property in the root pom; shipped jars verified class 65). Corollary
from the same failure: when a jmods-less JDK is used, the platform
module list must come from `java --list-modules` — a hand-picked
subset measured 39 modules vs 76, silently dropping
java.compiler/jdk.jdi/jdk.attach. v1.256.0 was the live gauntlet's
own finding: the shipped app calls RESTRICTED METHODS on every boot
(JNA's System::loadLibrary from the classpath, the FX modules'
natives when the Browser opens) and JDK 25's JEP 472 warnings become
ERRORS in a future release — the base conf now grants
--enable-native-access=ALL-UNNAMED and the runtime-installing
scripts append the named javafx.graphics,javafx.web entries where
they exist. The timing law: **the conf ships only inside installers,
never through the update center, so conf-level hardening must ship
before the JDK forces it** — an install that never gets a fresh conf
can never be fixed retroactively. Two traps now structural: the
Windows .exe launcher GREPS the conf for keys instead of sourcing
it, so two default_options lines have no defined winner (extend the
one line, gate-pinned); and an unanchored sed rewrites the COMMENT
naming a flag as readily as the flag (anchor conf rewrites to the
default_options line — measured, both shells). v1.252.0, before the
baseline work, was the QA persona's find: VERITAS could not read
node:test's TAP output (P:0 F:0 on a real failing suite) — one
tallyFrom seam now parses "# pass/# fail" + "not ok N - name", with
"node" appended to the RUNNER knob per the index law. v1.257.0's
arc review then caught, in that day-old lane and across the family,
that Re-run failed joined RAW names into the runners' REGEX filters:
measured live, the raw name `applies discount (10%)` as a
--test-name-pattern SKIPPED the failing test of that exact name and
reported PASS — a false green from the truth button. Names are now
backslash-escaped per metacharacter (Pattern.quote's \Q\E is
Java-only; JS/RE2/Rust regex all speak backslash), deno's --filter
gained its /.../ regex form (bare --filter is a SUBSTRING match, so
a joined "a|b" could never match two names), pytest/cargo stay
verbatim by design. The review verified killTreeAndWait's
Process-vs-handle reap rewrite and the whole packaging chain CLEAN.

Currency addendum 2026-08-03 at **v1.251.0** (the day shift):
v1.248.0 took ledger 74 the honest way — OpenJFX 21.0.5 → 21.0.12 in
FULL lockstep (ui pom ×2, the windows workflow pin, bundle-jre.sh's
three platform sha256s), gauntleted live on the new WebKit (https
render, plain-http through the h2c-flagged loader, DevTools bridge
reading the DOM) and byte-verified in the brewed app
(javafx.web@21.0.12); the FX/JDK floors are now MEASURED from the
jmods' class versions — FX 24 = JDK 22, FX 25 = JDK 23, FX 26 =
JDK 24 — so every FX major past 21 is chained to a bundled-JDK
baseline decision. v1.249.0's day-arc review (v1.241–v1.248) found
the bundled FX runtime was the ONLY unattributed vendored component;
NOTICE now carries it and FxPinLockstepTest binds the FX version's
four homes into build-failing lockstep (mutation-proven); the
v1.246.0 wizard deletion verified complete, the org.openjfx ignore
survived, the v1.247.0 ceiling port is real. v1.250.0 delivered the
JDK 25 + FX 26 decision dossier, measured live on this box: jlink
green, the app boots with ZERO SEVERE on JDK 25 (the --add-opens set
holds), the Browser renders on FX 26's WebKit, and the v1.226.0 h2c
flag survives — docs/engineering/jdk25-fx26-dossier.md lists the
honest GO-remainder; the decision is David's. v1.251.0 ran the FIRST
CONTRIBUTOR persona: a fresh clone built the assembled app with the
documented command verbatim in 21 seconds, zero friction — the only
finds were CLAUDE.md's fossil JDK-17 Troubleshooting claims (v0.x
era), now corrected to the Java 21 baseline. Process laws from the
day: a branch touching .github/workflows needs the SSH insteadOf
bypass (the repo's local url rewrite defeats naive git@ pushes), and
after any Dependabot rebase verify mergedAt before trusting script
flow.

Currency addendum 2026-08-03 at **v1.246.0**: the queued
delete-vs-re-expose decision resolved as DELETE — the platform
`@TemplateRegistration` wizard (`WebProjectWizardIterator` + panels +
the React/Vue/Vanilla resource templates + WizardTemplateCeilingTest
+ the dependabot npm block + the root-pom JaCoCo excludes) is gone.
Rationale: the surface had been unreachable since the v1.11 chrome
pass, Project Studio's wizard is strictly richer (14 templates,
presets, npm install), and the v1.36.0 precedent (tools.build,
CodeIndexService) says superseded surfaces get deleted, not
preserved. v1.244.0's modernization made the deletion safe — the
last shipped state was proven, not broken. One wizard, one door.

Process law added 2026-08-03 (learned the expensive way): **verify a
docs edit landed by reading the file back — never trust the script's
own "ok".** A scripted CHANGELOG insert during v1.248.0 failed its
anchor assertion inside a batched shell block. Bash does not stop at a
failing python heredoc, the traceback scrolled past under a `tail`,
and every later release anchored its insert on the entry that was
never written — so SIX shipped releases (v1.248–v1.253) carried no
CHANGELOG entry, while their code, gates and installers were all
correct. The releases never lied; the file did. Two rules follow:
a docs edit is not done until a `grep` proves it, and an assertion
inside a batched block must be the LAST thing in that block or run on
its own where its exit code is read.

## Where the project stands

NMOX Studio is a shipping NetBeans RCP IDE (v1.257.0, Apache-2.0, bundled JDK 25 LTS +
OpenJFX 26 runtime since v1.253.0, 19 release assets per
tag — six installers/SBOM plus the update-center catalog and the 11 module
NBMs — Homebrew cask, a windows-latest CI lane that runs the full verify)
whose identity is the **Reason-style task rack**: 53 hardware-styled devices
(STELLAR and ANCHOR joined in the v1.130+ Web3 arc) wired with patch
cables, backed by real process execution, session
resurrection, CI export, and since v1.55.0 a **frozen public Device SPI**
third parties extend it through. Around it: an **80-grammar polyglot editor**
(70+ language mimes — the 2026-07-16 run added V, Fortran, Smalltalk,
Prolog, Tcl, Scheme, Ada, Pascal, Odin, COBOL, Haxe, Janet; every
cleanly-licensed grammar is now vendored, the Raku/Forth-class skips
documented in NOTICE) with LSP, five studios (Block, API, DB,
Contract/Web3, Infra), the classic-web-first-class layer, 86 Learning
Spaces (the twelve v1.72–v1.77 additions live-verified against real
installed toolchains in v1.77.2, the six v1.92.0 lightweights the same
way), and the v1.35 "connections" spine (ServingRegistry +
ManifestPulse) that keeps every surface live-synced. Since v1.86.0 the
E2E story closes end-to-end: SPECTER (the 51st device) runs
Playwright/Cypress suites with ENGINE=auto, serves the HTML report,
and the E2E Loop preset chains VELOCITY READY → SPECTER RUN — proven
live with real Chromium, zero defects. Cables patch by click as well
as drag (v1.95.0), because the real rack is wider than a default
window.

**Block Studio is the fifth studio (v1.78.0–v1.85.0, ⌥⌘5).** A
Scratch-like composer whose interlocking pieces generate a real custom
element beside the canvas with per-piece code ranges (click a piece,
see its lines): a strict reverse parser makes the round trip byte-exact
(generate(parse(code)) is law), a workspace holds any number of
components with switch-as-patch-boundary undo, the in-memory preview
serves the whole component library so components render composed, the
canvas is fully keyboard-operable and screen-reader-visible (ledger 48
closed), and the arc carried its own two-lens review release (v1.82.0,
14 mutation-proven fixes).

**The security-and-robustness arc is complete as of v1.124.0 — and the
deferred ledger is EMPTY.** Starting from the first dedicated apiclient
review (v1.97.0), every module got its own senior review, and the whole
class of findings shipped: the editor and tools RCE gates (LSP servers,
Prettier-on-save, Run/Build/Test, NPM Explorer all Workspace-Trust-gated
before running a cloned repo's code, v1.102–v1.103), API Studio tokens
moved to the OS keychain (the last Keyring-law holdout, v1.97.0),
safe-default dialogs everywhere a reflexive Enter could destroy something,
and bounded reads on EVERY path — HTTP (capped per-site v1.99–v1.104, then
unified into core.http.HttpBodies in v1.124.0 with a cross-module
re-inline gate), process capture (v1.106.0), process lines (v1.112.0),
LOB cells (v1.116.0), and DB Studio connections that refuse local-infile
and zero their password on close (v1.117.0). The 2026-07-22 night shift
(v1.110–v1.118) and 2026-07-23 morning (v1.119–v1.124) then worked the
deferred ledger to empty: items 51 (SPI additive overload) and 45
(Tailwind LSP) remain deferred with standing reasons; everything else is
CLOSED. Docs screenshots are a product capability (DocsShots, v1.109.0;
forge v2's dialog shots in v1.125.0 put an image in all 13 tutorials),
and the same-day review of the day's own surface (v1.126.0) caught the
infra op-lock needing a depth counter.

**The language-compatibility mission is complete as of v1.77.2.** Full
verticals where a real manifest/toolchain exists (V/fpm/Alire join
cargo/go/mix/…), honest editor-citizenship scope where none does, two
review releases (v1.71.0, v1.76.0) that caught real bugs in the expansion —
including ten languages' Navigator outlines being built-but-unreachable,
now drift-gated by OutlineNavigatorGateTest — and ledger 47 closed
(INSPECTOR greys honestly). Open expansion residue: ledger 46 (CI-export
setup steps for post-v1.59 toolchains, deferred until a user hits it).

Since the v1.36.0 senior-review capstone, five things graduated from
"opportunity" or "deferred" to "shipped and tested":

- **It debugs.** JS/TS breakpoints out of the box (v1.37.0) via the vendored
  js-debug adapter and the `DapProxy` session multiplexer; **browser/Chrome
  debugging** (v1.43.0) on the same one-child splice, gated on Workspace
  Trust, real-adapter integration-tested. The honest ceiling is recorded,
  not hidden: one child session per run (ledger 25), and a page's Web Workers
  sit *paused* rather than undebugged (ledger 39) — both wait on a platform
  N-session DAP client.
- **It knows its branch, and its project is a platform citizen.** The git
  chip (v1.40.0) reads HEAD from disk and opens the platform History browser.
  Then the big one: **ledger 29, the context migration, landed** (v1.45.0 +
  v1.48.0). A real aim now publishes to `OpenProjects` and `setMainProject`
  on a background lane; the aim-owning windows (Task Rack, Project Studio,
  Workbench) publish the aimed node via `setActivatedNodes`; Project Studio's
  file tree publishes the selected FILE's DataObject node and NPM Explorer
  publishes the found Node project's node. The payoff is live: the **Team
  menu is the full enabled git suite** with just a project aimed (it was one
  disabled stub in v1.40.0), and the chip's Show Changes / Diff / Annotate
  verbs open the platform's real windows. The rack IS the context system now,
  bridged to the platform's — not read past it.
- **It's tested where it ships.** windows-latest joined the CI matrix as a
  blocking full-verify gate (v1.42.0); its first green found two real product
  bugs (language servers never detected on Windows; a cross-OS DapProxy
  disconnect race).
- **It's usable without a mouse or a screen.** The widget library speaks
  Swing accessibility (v1.41.0): SLIDER knobs with keyboard arrows and focus
  rings, Space/Enter buttons, state-announcing LEDs/LCDs/VU meters; every
  control on every device (46 then, 51 today) exposes an accessible name,
  CI-gated by DeviceContractTest's name law (59 controls fixed to get
  there); the Block Studio canvas joined the law in v1.83.0.
- **It respects the project's own toolchain.** The corepack pin or
  lockfile decides npm/yarn/pnpm in every Node AUTO lane (v1.60.0); a
  biome.json flips PURITY/GLOSS to biome with honest fix spelling,
  diagnostics, and LCD counts (v1.61.0); the wizard installs with the
  detected manager and first-run defaults join the ~/NMOX workspace
  (v1.62.0). Mutation-proven at every consumer.
- **It conducts monorepos.** WAYPOINT (46th device, v1.63.0) is ROSETTA
  one level down: package.json workspaces / pnpm-workspace.yaml resolve to
  a per-package dial, and Node lanes re-root through the ONE
  CommandDevice.commandDir choke point — NPM-9000's scripts, PURITY,
  GLOSS, VERITAS, and the CI export's working-directory all follow
  (composition test-pinned). Saved dynamic-knob selections survive reload
  (v1.63.1 Knob.pendingSelection — healing an NPM-9000 latent since v1.0);
  exported workflows use forward slashes on every OS (v1.63.2).
- **Its file tree is a platform citizen and its framework consoles are
  complete.** Ledger 36 closed (v1.64.0): Project Studio's tree is a
  BeanTreeView over the real DataFolder node — file-type icons, git
  annotation, the full node menu, ~230 lines lighter, its close/reopen
  and dispose laws review-hardened in v1.65.1. Every dominant modern web
  stack has a version-aware console (v1.65–v1.67): HALO/NEXUS/VELOCITY/
  COSMOS/KINETIC/NIMBUS/PHOENIX/ARTISAN, each with the serving-registry
  deregister-on-stop contract.
- **The functional web is first-class (v1.70.0).** Elm (elm.json,
  reactor/make/elm-test, elm-repl space), ReScript (rescript.json +
  bsconfig.json, build/clean), and PureScript (spago lanes) — with the
  honest detection rule that NODE outranks all three beside a
  package.json (test-pinned). The live drive found and fixed two bugs
  (REPLs never stripped ANSI; a learning space must never pin the user's
  compiler) and a windows-gate catch widened the js-debug readiness
  deadline for cold machines. 55 grammars, 59 spaces.
- **The indie stacks are first-class (v1.69.0).** Julia's half-shipped
  support was finished (Project.toml kind, Pkg lanes, honest no-run), and
  Nim, D, and Racket each got the full vertical: detection (the nimble
  glob generalized from dotnet's), pinned grammars, LSP entries, every
  AUTO lane, outlines, Doctor probes, and REPL learning spaces where a
  real REPL exists (nim secret, racket -i; D's absence recorded, not
  faked). 58 learning spaces, 52 grammars.
- **Its module system tells the truth.** Spec versions track the product
  version with real inter-module dependency ranges (v1.47.0, ledger 20), so a
  module jar dropped into an older install is refused by the loader instead of
  throwing LinkageError at call time. Soft-dependency is now a Lookup, not a
  caught classloader failure (v1.46.0, ledger 30/31): core exports
  `org.nmox.studio.core.spi` facades, rack publishes @ServiceProvider adapters,
  and **apiclient/web3/infra dropped their rack Maven dependency entirely**.
  Rack tool findings reach the platform Action Items window (v1.49.0, ledger 32).

The v1.36.0 audit remains the trust anchor: its finding was mostly *negative
space* — the house laws held under adversarial reading. The seven overnight
releases were disciplined debt work in that spirit, each sub-fix
mutation-proven against the pre-fix code. The codebase's verified state is
still the most valuable asset this project has; every section below protects it.

## Not done (the honest gaps)

The old v1.36.0 gap list is nearly all closed: Windows is now test-executed
(v1.42.0), the EDT-freeze on Stop All is async (v1.44.0), studio workspace
writes ride named SaveLanes (v1.44.0), and spec versions are real (v1.47.0).
What remains, ranked by how much it would matter to a daily driver — and the
honest headline is that **the high-value queue is drained**. Most of what's
left is either a settled won't-fix or a call that needs a product decision.

1. **The update-center policy decision — DECIDED AND SHIPPED (v1.51.0,
   ledger 21 closed).** The user chose "build the real update center": Tools ▸
   Plugins now offers every newer release's product modules from a catalog the
   release workflow publishes (`releases/latest/download/updates.xml`, every
   NBM pinned by absolute per-tag URL). The v1.56.0 review then unified the
   channels: the daily release heads-up opens the same in-app Plugin Manager
   the platform's weekly check uses.

2. **~~The ledger-29 remainder: Kit-action context registration~~ —
   DECIDED (v1.192.0): always-enabled IS the correct behavior, not debt.**
   The UX answer the deferral waited on turned out to be a category
   correction: every kit acts on the AIMED project (the rack's
   `getProjectDir()`), not the window selection, so selection/focus-keyed
   enablement would be semantically wrong — it would grey a valid action
   whenever focus sat in an editor. The runtime guard ("Aim the studio at
   a project first") is the honest gate, applied at the only moment the
   answer is knowable. The decision is written at the code site
   (PwaKitAction, the family exemplar). Ledger 29 is now fully closed.

3. **The architectural won't-fixes (ledger 1–7).** Re-audited with fresh
   evidence and each is a decision, not laziness: faceplate "boilerplate" is a
   repeated *idiom* not repeated *values* (#1); the Build/Test/Run switches map
   one enum to three unrelated verbs with nothing shared (#2); the
   RackIO/GraphIO/WorkspaceIO shape can't share a core helper across module
   classloaders without ClassCastException (#3); templates hardcoded in
   ProjectTemplates.java wait for the user-templates feature that would rewrite
   them anyway (#4); JS/TS on a custom lexer vs. TextMate is a real editor
   sprint with a regex-awareness cost (#5); `.sass` approximated by the SCSS
   grammar needs upstream grammar-sourcing (#6); the rack shelf's ~0.3s of
   boot is <5% of a boot the profiler says is dominated by module scanning
   (#7). These are "won't fix unless the premise changes," and the premises
   were re-read, not recalled.

4. **The Windows Job-Objects pair (ledger 38/40) — DECIDED (v1.192.0):
   conditional won't-fix, with named triggers.** MSYS/Git-Bash breaks the
   parent-PID chain, so `ProcessHandle.descendants()` can't see its
   grandchildren — killTree can't reap a Git-Bash grandchild (#38) and only
   the product's explicit Stop reaps Chrome on a bare DAP disconnect (#40).
   The guarantees that matter HOLD today: Stop leaves zero orphans on every
   OS, runBounded returns bounded, and no shipping path spawns through an
   MSYS shell. A JNA/FFM Job-Objects sprint buys nothing a user can feel,
   costs a native dependency, and can't be fully validated on this
   project's macOS-first bench — so it is built only when one of two
   triggers fires: (a) a Windows user reports an orphaned grandchild in the
   wild, or (b) the product gains a feature that launches via Git-Bash.
   Until then this is a solved-enough boundary, not open work.

5. **~~FileTreePanel~~ — CLOSED (v1.64.0), stale here through two currency
   passes.** The tree became a platform citizen (BeanTreeView over the real
   DataFolder node: file-type icons, git annotation, the full node menu,
   lazy off-EDT children) and this entry contradicted the "as of v1.85.0"
   paragraph below for ten days. Kept struck-through as a reminder that a
   gap list is a claim like any other — verify against the ledger's
   headers, don't trust the last pass.

6. **The seven studios live in the `editor` wsmode (ledger 33) — DECIDED
   (v1.192.0): won't-move.** Documents interleave with the suite tabs, and
   that is the discovery design working as designed (v1.29.0: every major
   surface one click away from minute one). A custom `studios` wsmode
   would churn every existing user's persisted window layout to buy an
   aesthetic separation that has produced zero user-visible defects in
   160+ releases. Re-open only with BOTH a reported user pain and a
   layout-migration story; absent those, this is a decision, not debt.

7. **i18n: ~450 hardcoded UI strings (ledger 24).** A reality note, not a
   plan. The house style is deliberate English-only UI; making it localizable
   is a dedicated @Messages-migration sprint. Do it only if a non-English
   audience actually materializes.

8. **The Tailwind LSP waits on the platform client (ledger 45).** Built
   and live-tested in the v1.62.0 sprint: multi-server-per-mime works
   (bytecode-verified) and the server detects Tailwind v4 projects, but
   the platform's LanguageClientImpl throws on client/registerCapability
   and the server's init dies. Restore path recorded in the ledger;
   re-test each platform bump.

9. **The small, deliberately-bounded residue.** Contract Studio never signs
   (by design — no private keys, ever; tuple/struct ABI encode, eth_subscribe
   WS, Vyper/non-EVM, a slither lane, and a `forge init` wizard are the noted
   deferrals, ledger 12); the classic-web "second shelf" (YUI/Dojo/ExtJS
   completion, AngularJS-1.x tooling, a jQuery-migration assistant, ledger 13);
   DB Studio's Mongo cancel is a no-op and cursors read `firstBatch` only, with
   no live Mongo/Couch integration tests (ledger 10/11); CommandExecutor's
   exit-detection and stale-run guards are queued behind a reproduction (ledger
   18); no @OnStop seam yet (ledger 35 — noted so the first need adds one
   rather than misusing a hook); no MySQL learning space (the REPL model needs
   a live server, ledger 8); the platform Breakpoints window never lists DAP
   breakpoints (ledger 27 — reproduced with stock Python, an upstream defect,
   the gutter is the documented manager).

## Could be done (opportunities)

The v1.36.0 list is mostly *done*: browser debugging shipped (v1.43.0), the
git surface shipped (v1.40.0/1.45.0), accessibility shipped (v1.41.0),
startup was measured and closed (v1.38.0). What's genuinely left as
**net-new** — not debt, and not padding — is short, because the product is
mature. Each earns its place only as a full vertical slice (device + tests +
docs + live verify), never as a checkbox:

- **A public device SPI — DECIDED, pre-work shipped (v1.54.0).** The user
  chose shape B from the design dossier: a small *declarative* contract in
  core.spi (descriptor + faceplate builder + logic callbacks, pure-JDK types),
  hosted by the rack through an internal adapter — NOT freezing the
  organically-grown `RackDevice` class. Rationale: the freeze surface designed
  for freezing is ~8 small types instead of ~19 grown ones; every house law
  (trust gate, color law via a Role enum, mandatory accessible names,
  transport columns) is enforced by the HOST rather than by plugin good
  behavior; rack's friends line and internals stay free to change; B-now does
  not preclude A-later, while A-now precludes ever narrowing. v1.54.0 shipped
  the pre-work (DeviceCatalog seam, MissingDevice lossless placeholders,
  bus-name identity, catalog-driven contract tests, CI-step capability);
  **v1.55.0 shipped the SPI itself** (core.spi.device, ExtensionDevice host,
  Lookup merge, live NBM install E2E) and **v1.56.0 review-hardened it** the
  day after (onAttached revival hook, guarded mount paths, dispose ordering,
  port-count cap) while it had zero external consumers.
- **Learning Spaces as a community catalog — SHIPPED (v1.53.0).** Every
  `*.json` in `~/.nmox/learn-catalog.d/` joins the picker (same schema as the
  built-ins, documented in docs/learning-spaces.md); slug-match overrides a
  built-in, malformed files skip-with-note, read lazily on picker-open behind
  a path+mtime+size cache. Live-verified with a planted community space.
- **AI assistance through the rack's metaphor — SHIPPED (v1.52.0).** The
  ORACLE device (45th) explains the error currently on the MONITOR bus:
  EXPLAIN (QUERY-blue) reads the last failed run off the FlightRecorder and
  asks the Anthropic Messages API what went wrong and how to fix it —
  visible, wired, unpluggable, exactly as the identity demanded. It is not a
  chat sidebar. Zero boot cost; no network without the button press; its own
  one-time consent for the outward data flow (WorkspaceTrust is an inward
  execution guard and does not cover it); the API key Keyring-only (or
  `ANTHROPIC_API_KEY`/`CLAUDE_API_KEY`); honest degradation for every failure
  state. **Auto-explain shipped too (v1.91.0)**: EXPLAIN trigger in-jack +
  OUT data out-jack — a cable never prompts (consent stays a human button
  press) and consults rate-limit at 30s. **Live-proven 2026-07-19**: both
  paths ran against the real Anthropic API in the shipped app — the button
  consult through the consent dialog, and a hands-free VERITAS FAIL →
  EXPLAIN cable consult delivering a correct diagnosis with zero faceplate
  interaction. The remaining AI surface (streaming, conversation) stays
  deferred — each is a direction to be *chosen*, not a gap.

That's the whole honest list. The old "six tabs is the discovery ceiling"
guidance has since been tested by Block Studio (a seventh studio that
earns its tab); 72 grammars is well past diminishing returns for new
languages, and any feature that can't be drawn as a device with an honest
control surface (or a studio with an honest canvas) remains a non-goal.

## Planned (the method — keep doing this)

The cadence that produced 40-plus releases without a broken *shipped* main
(one merge-on-red near-miss overnight, caught by the tag gate — see below):

1. **Sprint → gated PR → tag → live-verify.** One background fail-closed
   pipeline script per release (see the scratchpad templates and the
   `gated-ship-pipeline` memory): local `mvn verify` before push, CI green
   before merge, main green before tag, 6 assets before done. Never touch the
   tree while it runs.
2. **Review-then-fix with evidence.** The v1.36.0 shape held through the
   overnight run: read-only audit lenses first, findings with file:line proof,
   then triage into FIX-NOW / FIX-LATER-with-reason / FINE-AS-IS-blessed.
   Fixes carry regression tests proven against the old code — every v1.44–1.50
   sub-fix was mutation-proven (deleting the fix fails a named test).
3. **The ledger is decisions, not wishes.** Every deferred item in
   tech-debt.md has a reason; every blessed oddity has a written rationale. The
   overnight run re-read premises rather than recalling them — that is why
   ledger 1–3 hardened into won't-fix and ledger 23 closed as "centralize the
   version string only, keep the eight copies" (the copies are an
   architectural constraint, not laziness).
4. **Docs truth pass every ship.** CHANGELOG + CLAUDE.md status/history +
   README claims + ledger. devices.md is generated and CI-gated — the model
   for any future generated doc.
5. **Live-verify before claiming fixed.** Hard rule since v1.33.2. Boot the
   real assembled app; user-visible fixes get a click-through.
6. **Walk a persona's whole journey, not the feature you just built.**
   v1.38.1's lesson stands: the bugs cluster one keypress *off* the feature
   path (a shortcut that opens the wrong window, a console that never surfaces,
   a chooser rooted in a folder we never create). Press the keys the docs
   promise; open the windows the feature implies.

**The house laws** (each earned by a real incident — enforcement lives in CI
gates and regression tests, this list is the index): no EDT I/O and no EDT
process spawns, including the *mutation* half; interactive Stop/switch runs on
a bounded worker while the shutdown reaper stays synchronous, so the orphan
guarantee holds (v1.44.0); listeners bounded + equality-guarded + attach/detach
symmetric per open; process timeouts are waitFor-first with both streams
drained on threads and the **whole tree** killed — dash spawns grandchildren
where bash execs; UTF-8 explicit at every byte↔char boundary; secrets in the
OS Keyring only, never prefs or files, RPC URLs never serialized;
DialogDisplayer/NotificationDisplayer, never JOptionPane; prefs values under
8KB, lists as one-entry-per-item; coverage floors on the testable surface with
pure-Swing excluded by name with reasons; rack tests drain the EDT *and* the
router (`awaitRouterIdle`) before asserting; workspace files written atomically
(temp sibling + `ATOMIC_MOVE` via core `AtomicFiles`) and on a single-throughput
SaveLane whose write+stamp is one task (v1.44.0); file create/rename/delete
through DataObject/FileObject so open editors follow; **optionality is a Lookup
of a core.spi facade, not `catch(LinkageError)`** (v1.46.0); **a real aim
publishes to OpenProjects/setMainProject and the aim node to
actionsGlobalContext, passive aims never resolving a platform project** (v1.45.0).

The overnight run also re-proved the gate's worth twice in one night: the
WAYPOINT ghost-steering race (an in-flight apply() after removeDevice —
fix idiom: disposed-flag first, cleanup last, guard the continuation) and
the CI export's backslash working-directory were both caught by lanes this
machine cannot reproduce, each then pinned deterministically.

Two pipeline laws earned overnight (2026-07-13): **anchor every CI wait
to the SHA or tag, never "latest run"** — the v1.60.0 tag was cut against
the previous merge's green because the new run didn't exist yet (the real
run failed on a test flake minutes later), and the release-workflow wait
hit the same trap the same night; and **the EDT-drain law applies at
writing time** — any test that calls an async-reload then dials/asserts
drains the EDT in between, even when green everywhere you ran it.

**Failure patterns to grep for in new code** — every bug class that actually
shipped, once: constructor-attached listeners on TopComponents (remove-half
without the re-add half); read-to-EOF before a timed waitFor; a corrupt file
loading as empty then autosaving emptiness over the original;
`invokeLater(this::self)` while a component is 0×0; full-refresh listeners
fanning out per event across default-open tabs; filesystem walks of `$HOME` on
any thread the user waits on (macOS TCC); unverified `pkill`; a keyboard
shortcut registered but never pressed (`Shortcuts/`-folder chords lose silently
to the Keymaps profile); a UI affordance documented but never exercised (the
empty Breakpoints window); a test that spawns a process into its `@TempDir`
must confirm the process dead — or point its cwd elsewhere — before cleanup
(Windows file locking); **undo capture left ON across a bulk load** — a
preset/patch replace kept the pre-load removals on the stack, so ⌘Z peeled the
just-loaded patch apart (v1.50.0, ledger 19; clear the history at the single
`fromJson` choke point every load routes through).

**Failure patterns learned overnight (v1.44–v1.50) — new, and load-bearing:**

- **Gate the merge on literal "pass" lines, not on the poll loop ending.** A
  `for … done; gh pr merge` chain merges on RED when the loop merely *stops
  polling*. This bit for real: **v1.45.0 was merged with the ubuntu lane
  failed**, because the pipeline treated "the poll loop ended" as "the checks
  passed." Between poll and merge, assert each check line contains "pass",
  `|| exit` otherwise. The release itself was saved by the *second* gate — the
  tag stays behind its own main-green check — but the merge should never have
  landed. Now in the `gated-ship-pipeline` memory.
- **Per-OS surefire filesystem order can poison class-init.** A test-scope API
  without its impl (`projectapi` without `projectapi-nb`) fails `<clinit>` once,
  and then every later `DataObject.getNodeDelegate` in that JVM dies of
  NoClassDefFoundError — whichever OS's directory-listing order hits the poison
  test first. Reproduce with `-Dsurefire.runOrder=reversealphabetical` before
  believing a "passes on my machine" green.
- **Mutation testing can `git checkout` and wipe uncommitted work.** Hit
  **twice** overnight: a mutation harness that resets the tree between runs
  silently discards anything not committed. Commit (or `git stash`) before any
  mutation run, and never launch one over an open unrelated edit.

## What I'd do next, in order

The honest headline first: **the high-value debt queue is drained.** The
seven overnight releases worked through the entire actionable backlog — the
context migration, spec versions, soft-dependencies, the Windows lane,
accessibility, browser debugging, the diagnostics bridge, and the rack-undo
cluster all shipped. What remains is not "more debt to grind"; it splits
cleanly into three buckets, and only one of them is a coding task I can pick
up without direction:

1. **The update-center policy: RESOLVED (v1.51.0).** Built, shipped,
   live-verified against real GitHub-hosted assets, and (v1.56.0) unified
   with the daily notifier onto one in-app updater.

2. **The public device SPI: SHIPPED (v1.55.0).** Shape B built and live-
   verified — an out-of-tree NBM installed through Tools ▸ Plugins put a
   working third-party device on the shelf with the trust gate enforced by
   the host. Deliberate v1 scope (grow additively on real demand): no CI
   export for extensions, no custom painting, no session resurrection.

3. **Settled won't-fixes and bounded residue** (ledger 1–7, 33, 36, 24, and
   the Windows Job-Objects pair 38/40): revisit only when a premise changes or
   a real triggering path appears. Do NOT reopen them on a remembered reason —
   each has its written verdict in tech-debt.md.

So, as of v1.56.0: **every direction call named above got its answer and
shipped** — the update center (v1.51.0), ORACLE (v1.52.0), the community
learning catalog (v1.53.0), and the Device SPI (v1.54.0 pre-work, v1.55.0
build, v1.56.0 review-hardening). The roadmap the user set is complete and
the third senior review found the house laws holding. What genuinely remains,
in order of value:

1. **Ledger 41 — DONE (v1.57.0).** Every device's RUN now forks off the
   EDT on a RequestProcessor lane with the observable contract identical.
2. **The Windows `JsDebugServerTest` @TempDir flake — DONE (v1.57.0)**
   (self-managed retry-delete temp dir).
3. **Watching the SPI in the wild** — the deliberate v1 scope items (CI
   export for extensions, custom painting, resurrection, `DeviceLogic`
   additions) grow additively when a real plugin author asks.
4. **Settled won't-fixes and bounded residue** (ledger 1–7, 33, 24,
   38/40, 42, 49): revisit only when a premise changes.

**As of v1.85.0** the story since that list: the language-compatibility
mission ran to completion (v1.59–v1.77: package-manager truth, Biome,
workspaces, the eight framework consoles, and ~24 new language verticals
through V/Fortran/Ada/COBOL/Haxe/Janet), ledger 36 closed with the
platform-native file tree (v1.64.0), and Block Studio became the fifth
studio across v1.78–v1.85 (composer → live preview → byte-exact round
trip → review hardening → keyboard canvas → multi-component workspaces →
composition). Two more review releases (v1.71.0, v1.76.0, v1.82.0) each
caught real bugs. The debt queue is still drained; the standing guidance
holds: new studios, new languages, new devices — anything that can't be
drawn with an honest control surface stays out.

**As of v1.95.1** (the 2026-07-18/19 marathons, sixteen releases): SPECTER
made E2E suites rack citizens and the E2E Loop preset closed the marquee
journey live with real Playwright/Chromium (one DEV press → suite runs →
report served → clean stop, zero defects); ledger 50 closed (every
declared console jack routes, gated catalog-wide); the serving gate was
made honest through the trust prompt (v1.93.0 — the one live-drive
observation that turned out to be a real bug); rear jacks compress
instead of colliding (v1.93.1); ORACLE became composable (v1.91.0) and
then live-proven on both consult paths against the real API; the modern
lightweights joined (v1.92.0, 78 spaces); Block Studio finished its idea
backlog (v1.94.0 jump-to-component); and cables patch by click as well
as drag (v1.95.0). Two more reviews ran (v1.89.0 fifth, v1.92.1 sixth).
**Every backlog is empty** — the debt ledger's actionable items, this
file's opportunity list, Block Studio's ideas, and the live-drive
observation queue. The next unit comes from a premise change, a real
user, or David — not from manufacturing work.

**As of v1.126.0** (the 2026-07-22 night shift + 2026-07-23 day shift,
seventeen releases v1.110–v1.126): the security-and-robustness arc
completed — every module now has a dedicated review behind it, every
read path in the product is bounded (the capped HTTP mechanics unified
into core.http.HttpBodies with a cross-module re-inline gate), the
editor/tools RCE class is gated, the last plaintext secret moved to the
keychain, and the deferred ledger was worked to EMPTY (51 and 45 stay
deferred with standing reasons — additive-when-a-plugin-author-asks and
waits-on-platform). The docs forge became a product capability and every
one of the 13 tutorials carries a real screenshot (v1.109.0 tabs,
v1.125.0 dialog shots). Two same-day reviews of fresh surface each found
exactly one real bug (v1.115.0's ledger 62, v1.126.0's op-lock depth),
which is the pattern to keep: review the day's work the same day, while
the design intent is loud. Two NEW failure patterns joined the method:
*a scripted docs edit that doesn't assert its anchor no-ops silently*
(CLAUDE.md drifted five releases before v1.123.0 caught it), and *a
"done" claim must be checked against the artifact it summarizes* (the
ledger's own section headers, not the working-set memory of them). The
standing guidance is unchanged — and the backlogs are empty again.
Two more joined at v1.208.0, and the first is the one to internalize:
*both security bugs in that review were TRUSTING A PLATFORM DEFAULT.* A
raw `SSLSocket` validates the certificate chain but NOT the hostname
(unlike `HttpsURLConnection`, endpoint identification is off until you
set it), so the IRC client's TLS accepted any CA-valid certificate for
any domain and handed the SASL/NickServ credentials to whoever held it.
A Swing `JLabel` whose text starts with `<html>` RENDERS it, so the
DevTools panes — which display component names, source paths, URLs and
storage values straight from the inspected page — would make the IDE's
own JVM fetch `<html><img src="http://evil/...">`. Neither was a logic
error; both were the platform's default being the opposite of what the
surrounding code assumed. **Standing lens question for any new surface:
what does this API do by DEFAULT that we assumed it doesn't?** The
second pattern is narrower but bit the same release: *a test gated on
the PACKAGED app needs both halves of the surefire wiring* — excluded
from the `default-test` execution AND included in the integration-test
one. Wire only the include and it runs in the plain test phase too,
before the app is assembled, and fails there.

A third pattern joined at v1.162.0: *changing detection precedence
starves every consumer keyed on the OLD primary kind* — when CLARITY
began outranking NODE, PreflightPlan's `case NODE` silently stopped
matching Clarinet repos and fell to the empty default; after any
precedence change, grep every `switch (kind)` and every `== NODE`
comparison for behavior the flip removes.

**As of v1.136.0** (the 2026-07-23/24 Web3 arc, David-directed: "increase
our support and coverage of Web3 tech"): STELLAR (52nd device, Soroban)
and ANCHOR (53rd, Solana — a truthful SERVING gate on a real validator
long-runner), learning spaces for Stellar/Solana/CosmWasm/ink! all
live-proven against real toolchains, the Cairo/Starknet FULL language
vertical (live-proven with scarb before the code was written — now the
house pattern for verticals), the Multi-Chain Bench preset, and the
arc's same-day review (third run, third single-bug catch: STELLAR's
exit attribution had to capture the launched verb — never consult a
knob in onFinished). Recorded premise: Move (Aptos/Sui) waits on
grammar provenance; Sui's CLI side is clean (Apache-2.0, brewed).
