# The bundled-JDK decision: JDK 25 LTS + OpenJFX 26, measured

**Status**: recon dossier, 2026-08-03. No product change — this
document gives David the measured facts for the ledger-74 decision.
The product today: Java 21 LTS baseline (CI, workflow, bundled
runtime) + OpenJFX 21.0.12 (v1.248.0).

## Why this pairing

OpenJFX majors past 21 have hard JDK floors, measured from the jmods'
class-file versions (v1.248.0/v1.249.0): FX 24 → JDK 22, FX 25 →
JDK 23, **FX 26 → JDK 24** — jlink refuses with "Unsupported
major.minor version" on anything older. The next Java LTS after 21 is
**25** (GA 2025-09, current LTS), which satisfies FX 26's floor. So
the only future baseline worth deciding on is **JDK 25 LTS + the FX
26 line**; intermediate pairings inherit non-LTS support windows on
one side or the other.

## What was measured (macOS aarch64, 2026-08-03)

All four probes GREEN, run against the v1.249.0 cluster:

1. **jlink**: OpenJDK 25.0.4 (Homebrew) + FX 26.0.2 core jmods
   (base/graphics/controls/swing/web/media/fxml) → runtime builds
   clean, `javafx.web@26.0.2` present.
2. **Platform boot**: the full app boots on that runtime with **zero
   SEVERE and zero IllegalAccess/InaccessibleObject entries** in
   messages.log — the nmoxstudio.conf `--add-opens` set carries to
   JDK 25 unchanged.
3. **Browser**: Hacker News renders over https on FX 26's WebKit in
   the live app.
4. **The v1.226.0 h2c fix survives**: `useHTTP2Loader` is still
   present in FX 26's `javafx.web` classes — the flag the Browser
   sets before WebKit loads still has something to switch off.

## What was NOT measured (the remaining work if GO)

- **CI matrix on JDK 25**: the full `mvn verify` (tests + SpotBugs +
  find-sec-bugs + JaCoCo) has only ever run on 21; surefire forks,
  bytecode tools, and -proc:full behavior on 25 are unproven.
- **Windows + Linux runtimes**: only the mac aarch64 jlink was
  probed; the windows job and bundle-jre.sh linux arm need the same
  treatment (plus four fresh sha256 pins for FX 26 jmods — the
  osx-aarch64 zip hash from the probe:
  ed6ac7d8d056b29fa221edb029ed232eb54f3a7068c4d4e1304faf99f8d93285).
- **FX 26 incubator jmods** (`jfx.incubator.*`) were EXCLUDED from
  the probe runtime; a full-dir ALL-MODULE-PATH jlink would include
  them — decide module list vs full dir at upgrade time.
- **Full browser gauntlet** on FX 26 (DevTools bridge panes, viewport
  presets, save-to-reload, ng-serve h2c behavior) — the v1.248.0
  checklist, re-run.
- **NetBeans platform support statement** for JDK 25 — boots clean
  here, but the platform release notes should be checked before
  betting the baseline.

## The decision this enables

GO means one release that moves, together: workflow `java-version`
(3 sites) + bundle-jre.sh FX pins + release.yml FX pins + ui pom FX
deps + NOTICE (FxPinLockstepTest enforces the FX half), the CI
matrix, and the full gauntlet. NO-GO costs nothing: the FX 21 LTS
line keeps taking patches on Java 21 (v1.248.0's lane), and this
dossier stays current until the numbers change.
