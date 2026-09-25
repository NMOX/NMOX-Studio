# Engineering Documentation

Every document here is current; the v0.x-era papers were removed in
3.0.1 (see the end of this page).

## The live documents

- **[plan.md](./plan.md)** — where the project stands, the honest gaps,
  the ranked opportunities, and the house laws and failure patterns.
  **Read this first when deciding what to do next.**
- **[codebase-guide.md](./codebase-guide.md)** — a beginner's walk
  through the codebase: the five NetBeans RCP ideas everything rides on,
  four traced flows, and where each house law came from. **Read this
  first when deciding how something works.**
- **[l10n-completion.md](./l10n-completion.md)** — what fifteen-language
  support covers, and every ceiling with the measurement behind it. The
  record that closed the i18n/l10n arc at v2.147.0.
- **[dx-plan-3.1.md](./dx-plan-3.1.md)** — the developer-experience
  plan behind 3.1: what the first-hour, VS Code switcher and contributor
  walks found, and what was built about each.
- **[dx-plan-3.2.md](./dx-plan-3.2.md)** — the plan behind 3.2: the
  second week, where a developer who stayed commits, reviews diffs and
  hands work to a coding agent, and what the walks of that week found.
- **[tech-debt.md](./tech-debt.md)** — the current debt ledger: open
  items with their deferral reasons, closed items by version.
- **[gates.md](./gates.md)** — every build-failing law test, grouped by
  theme, with the law it holds and where it came from. **Read this when
  your build fails on a test whose name ends in `GateTest` or
  `LedgerTest`.** A test derives the list from the source tree, so it
  cannot fall behind.
- **[signing.md](./signing.md)** — **start here for signing.** The four
  independent systems, what question each one answers, and why none
  substitutes for another: GPG checksums (every asset, every platform),
  NBM signing (self-signed by decision), Apple Developer ID (official
  since v3.0.0) and Windows Authenticode (unpurchased).
- **[nbm-signing.md](./nbm-signing.md)** — how the secret-gated NBM
  signing pipeline is wired, and how to turn it on.
- **[release-signing.md](./release-signing.md)** — the installer lanes:
  what Apple Developer ID and Windows Authenticode cost, the accounts and
  secrets to create in one sitting, and — now that macOS has run for real
  — what the first signing run actually took, five releases of it.
- **[futures-2031.md](./futures-2031.md)** and
  **[competitive-lens.md](./competitive-lens.md)** — the two living
  backlogs: the bets argued from trajectories, and the rival's relief
  list.
- **[jdk25-fx26-dossier.md](./jdk25-fx26-dossier.md)** — the bundled-JDK
  decision, measured: JDK 25 LTS + OpenJFX 26 probed live (jlink, boot,
  the Browser's WebKit) before the baseline moved in v1.253.0.
- **[release310-dossier.md](./release310-dossier.md)** — the platform
  upgrade's measured facts (RELEASE300→310): every decompiled assumption
  re-checked, the slf4j placement root cause, the GO call's remainder.
- **[angular-parity.md](./angular-parity.md)** — the Angular parity
  scorecard against the reference tooling, kept current as the framework
  bet's honest ledger.
- **[agent-port-execution-dossier.md](./agent-port-execution-dossier.md)**
  — the design (not the decision) for the Agent Port's execution
  verbs: a per-session grant armed at the keyboard, lanes not
  commands, tools that appear only while armed.

Outside this directory, [CLAUDE.md](../../CLAUDE.md) is the deep
architecture reference and [the docs index](../README.md) is the way in
for users.

Every document in this directory is current. The v0.x-era papers that
used to sit here — architecture, api-design, performance, scalability,
security, testing-strategy, technical-roadmap, implementation-guide,
team-structure and the rest — were removed in 3.0.1: **git is the
archaeology.** A banner saying "do not believe this" is still something
a reader has to open and dismiss, and a working tree of documents that
are all true is worth more than a shelf of labelled wrong ones.
`git log -- docs/engineering/` has them if you want them.
