# Overnight brief — 2026-07-12

A morning summary of the autonomous run that shipped v1.44.0 → v1.50.0.
Factual and skimmable; the full record is in CHANGELOG.md and tech-debt.md.

## What shipped (seven releases)

Each closed real ledger items; no new product surfaces except the diagnostics
bridge. Every sub-fix is mutation-proven (a named test that fails on the
pre-fix code).

- **v1.44.0 — the debt sweep.** Ledger 15/16/17/22/26/28 closed, 34 mostly.
  Stop All / project-switch no longer freeze the EDT (async on a bounded
  worker; orphan guarantee intact — measured 1.5s/device freeze on the old
  code); all four studios write workspace JSON on single-throughput SaveLanes;
  Window-menu items show their accelerators; SBOM covers the vendored js-debug.
- **v1.45.0 — the context release.** Ledger 29, the big architectural arc: a
  real aim now publishes to `OpenProjects`/`setMainProject` and the aim node to
  `actionsGlobalContext`. Payoff live-verified — the Team menu is the full
  enabled git suite with just a project aimed, and the git chip's Show Changes
  / Diff / Annotate verbs open real platform windows.
- **v1.46.0 — soft-dependencies.** Ledger 30/31: optionality is now a Lookup of
  a `core.spi` facade, not `catch(LinkageError)`; apiclient/web3/infra dropped
  their rack Maven dependency entirely; rack's exports are friend-declared.
- **v1.47.0 — spec versions.** Ledger 20: module manifests carry the product
  version with real inter-module ranges, so an old-install jar is refused by
  the module loader instead of throwing LinkageError at call time.
- **v1.48.0 — the remainder sprint.** Ledger 34 fully closed (web3 artifact
  walk under a real ProgressHandle); ledger 29 reduced to Kit actions only
  (file tree publishes the selected file's node; NPM Explorer publishes the
  found project's node).
- **v1.49.0 — the diagnostics bridge.** Ledger 32: rack tool findings (eslint,
  tsc/phpstan) reach the platform Action Items window; the squiggle half turned
  out to already be platform plumbing and stays byte-identical.
- **v1.50.0 — housekeeping.** Ledger 19 (rack undo can't cross a preset/patch
  load; per-device trigger bookkeeping cleared on removal; TAIL/TEMPO display
  sync on undo re-attach) and ledger 23 (org.json version centralized to one
  root property — the eight copies stay by classloader necessity).

## Two flakes caught and killed

Both diagnosed to root cause, fixed, mutation-proven — not re-run away.

- **#133 — four ubuntu-only failures in the v1.45.0 context tests.** Root
  cause was surefire test ORDER, not headless visibility:
  `OpenProjectsBridgeTest` ran the real bridge, `ProjectManager.<clinit>` found
  no `ProjectManagerImplementation` on rack's test classpath and threw,
  poisoning the class for the whole JVM — after which every
  `DataObject.getNodeDelegate` died with NoClassDefFoundError (raw in one test,
  swallowed by AimNodePublisher's deliberate `catch(LinkageError)` in three,
  giving 10s null timeouts). Reproduced exactly on macOS with
  `-Dsurefire.runOrder=reversealphabetical`. Fix:
  `org-netbeans-modules-projectapi-nb` (the impl provider — in the shipped
  cluster, missing only from the test classpath) joins rack's test scope.
- **#136 — RealChromeIntegrationTest ubuntu attach flake.** Root cause was
  ENVIRONMENT, not a product race: js-debug's own internal `timeout:10000`
  attach ceiling. On a loaded ubuntu runner headless Chrome cold-started slower
  than 10s, so js-debug abandoned the launch before DapProxy was ever involved
  (no `startDebugging` in either transcript; DapProxy audited clean in #130).
  Mutation-proven by shrinking the launch timeout to 100ms to reproduce it
  byte-for-byte. Fix: the TEST's launch request passes `timeout:120000`
  (matching the cold-start window the `stopped` await already grants); the
  PRODUCT keeps js-debug's stock 10s default — the same trade VS Code ships,
  with no field evidence against it.

## One process slip — owned plainly

**v1.45.0 (#132) was merged with the ubuntu CI lane failed.** The ship
pipeline's `for … done; gh pr merge` chain treated "the poll loop ended" as
"the checks passed," so it merged on red. The four ubuntu failures were the
#133 flake above, and they were fixed immediately after — but the merge should
never have landed.

The release itself was NOT compromised: the **tag gate held**. The pipeline
tags only after main is green, so v1.45.0 was not tagged until #133 turned main
green. A second, independent gate caught what the merge gate missed.

The lesson is now recorded in two places: the `gated-ship-pipeline` memory (gate
the merge on literal "pass" lines between poll and merge, `|| exit` otherwise)
and the failure-patterns section of `docs/engineering/plan.md`.

## Current state

- **main is green**; HEAD = the v1.50.0 commit.
- **v1.50.0 is released** with **6 assets** (macos dmg, windows setup exe, linux
  deb + tar.gz, portable zip, SBOM json).
- Test suite ~2,370+, all module floors passing; the windows-latest full-verify
  lane is green.

## Bottom line

**The high-value tech-debt queue is drained.** The seven releases worked
through the entire actionable backlog — the context migration, spec versions,
soft-dependencies, the diagnostics bridge, and the rack-undo cluster all
shipped, on top of the browser debugging / Windows lane / accessibility work
that preceded them.

What remains splits into (1) a product decision — the update-center policy
(ledger 21; its technical blocker vanished when spec versions closed), (2)
net-new features that need a direction from you — a public device SPI (now
unblocked), a community learning catalog, the ORACLE/AI-through-the-rack idea,
and (3) settled won't-fixes and bounded residue (ledger 1–7, 33, 36, 24, the
Windows Job-Objects pair 38/40) best left until a premise changes.

**Autonomous shipping is paused pending your direction.** Grinding further
would mean churning settled won't-fixes or spending a sprint on
i18n/wsmode-migration work with nobody pulling for it — lower value than a
short conversation about what to build next.
