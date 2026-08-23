# The RELEASE310 dossier — measured facts for the platform-upgrade call

*2026-08-20, the ledger-84 recon (the v1.250.0 JDK-dossier model:
the numbers are in, the decision is David's).*

RELEASE310 is Apache NetBeans 31, the platform release after the
RELEASE300 line this product ships on. Dependabot proposed the bump
inside a grouped deps PR on 2026-08-20; it was refused structurally
(v2.19.4) because a platform major validates nothing when it rides a
routine PR. This dossier is the validation's measured half.

## Measured — GREEN

1. **Every decompiled-behavior assumption is byte-identical between
   release300 and release310** (fetched from apache/netbeans and
   diffed, zero changed lines in each):
   - `LSPBindings.java` — ledger 83's whole mechanism: the
     instance-keyed `project2MimeType2Server` reuse map, the
     MultiMime sibling-mime registration, the GC + 10-minute
     keep-alive teardown, `resolveLanguageId`'s raw-mime fallback
     (v1.218.0).
   - `bindings/refactoring/Refactoring.java` — ledger 81: the rename
     plugin still collects edit sets from EVERY binding on the mime
     (the always-true capability predicate).
   - `declmime/MIMEResolverImpl.java` — v1.217.0: declarative
     resolver composition (`FileElement$Type.accept`, ext+name
     AND-composition, position ordering).
   - `updateprovider/AutoupdateCatalogParser.java` — v1.51.0: catalog
     URLs still resolve against the PRE-redirect catalog URI, so the
     absolute-URL pinning in build-update-site.sh stays load-bearing
     and correct.
2. **The artifacts are fully on Maven Central** (org.netbeans.api,
   org.netbeans.modules, org.netbeans.cluster all HTTP 200 at
   RELEASE310; the search index lags and still reports RELEASE300 as
   latest — trust repo1, not the index).
3. **The whole reactor compiles against RELEASE310**: main + test
   sources of all ten modules, `-Dnetbeans.version=RELEASE310
   clean test-compile`, zero errors, no source edits.
4. **Full `mvn verify` against RELEASE310**: GREEN — exit 0, every
   module's tests pass, and every SpotBugs / find-sec-bugs / JaCoCo
   floor holds unchanged on the new platform. No source edits, no
   floor moved, tree untouched (property override only).

## Measured — RED (2026-08-20, the runtime probe)

**The assembled RELEASE310 app does not boot.** Same recipe that
boots the RELEASE300 assembly clean in ~30s (fresh userdir, bundled
JDK 25 jre, headless close flag): the 310 assembly logs

    org.eclipse.jgit - InvalidException: Netigso: … Cannot start
    org.eclipse.jgit state remains INSTALLED after start()

and then BLOCKS on a modal Warning dialog (Exit / Disable Modules
and Continue) that a headless close flag can never answer — the app
offers degraded boot interactively, but the failure is real. Discriminating facts, measured: the jgit bundle is
BYTE-IDENTICAL between the two assemblies (7.6.0.202603022253-r) and
the ide-cluster bundle census matches — the regression is in
RELEASE310's OSGi host (netbinox/core.netigso) resolution, not in
our modules and not in the bundle. ROOT-CAUSED same day (v2.21.6), and the host was
INNOCENT: a standalone Felix 7.0.5 probe with the exact bundle set
named the missing requirement verbatim — org.eclipse.jgit imports
osgi.wiring.package org.slf4j [1.7.0,3.0.0) and NOTHING exports it
under RELEASE310. RELEASE300 satisfied it only by accident of cluster
placement: the transitive org.slf4j:slf4j-api:1.7.36 was auto-wrapped
into extra/modules as an OSGi bundle; under RELEASE310 the same maven
artifact resolves into a module's ext/ classpath (testng's) instead —
no bundle, no export, no resolution. The FIX is one explicit
dependency in application/pom.xml (org.slf4j:slf4j-api:1.7.36, the
version matching the platform's slf4j-jdk14 binding), which pins the
bundle placement on both platforms; the RELEASE310 assembly now boots
CLEAN (exit 0, zero could-not-install, zero SEVERE, modules on).
The netigso Export-Package parsing change that was the initial
suspect is cleared. RELEASE310 is GO-READY: the remaining gauntlets
(browser, update-center-across-the-boundary, keymap chords,
update-site dry-run) run on the bump PR itself.

## Not yet measured — the GO remainder

- **Assembled-app boot laws** on a RELEASE310 cluster: window time,
  zero boot spawns (JFR), zero SEVERE, the `--add-opens` set still
  sufficient on the new platform, the platform's own deprecation
  warnings (the RELEASE300 baseline is two, both platform-internal).
- **The browser gauntlet**: FX 26 WebView against the RELEASE310
  window system (the h2c flag, DevTools bridge, viewport presets).
- **The update-center gauntlet across the boundary**: an install on a
  RELEASE300-built version must self-update to a RELEASE310-built one
  in-app — the v1.261.0 runtime-boundary precedent; module spec
  ranges (`core > X`) make old modules refuse to load rather than
  LinkageError, but the PLATFORM cluster underneath changes only via
  installers, so the timing law from v1.256.0 applies: platform-bound
  behavior ships in installers, never through the update center.
- **The keymap/layer surfaces**: KeymapProfileParityTest passes at
  verify, but the five platform profiles' CONTENTS can drift between
  releases — press the advertised chords in the assembled app
  (the v1.38.1 law: an affordance documented but never exercised is
  untested).
- **nbm tooling**: the nbm-maven-plugin version's compatibility with
  the new harness at `nbm:autoupdate` (the update-site build) — runs
  only in the release workflow, so a full dry-run of
  scripts/build-update-site.sh is part of GO.

## The shape of the upgrade, when called

One release, on the JDK-25 model (v1.253.0): move `netbeans.version`
in the root pom (the ONE home), run the full gauntlet set above on
the assembled app, ship through the normal gate with the update-center
timing law respected, and re-pin the CI/windows lanes' expectations if
any measured floor moves. Rollback is the property flipped back. (The runtime probe's RED was
root-caused and fixed in v2.21.6 — the missing org.slf4j provider was
a cluster-placement accident, not the OSGi host — and v2.35.0
executed this shape: property flip, full gauntlet set PASS on the
assembled app, shipped through the normal gate. The update boundary
behaved as designed: RELEASE310 dependency floors hold older installs
back honestly; installers carry them across.)
