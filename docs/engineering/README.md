# Engineering Documentation

This directory holds **four live documents and a pile of archaeology.**
Read the live ones; the rest are v0.x-era plans kept for history and
describe a product that was never built. Each carries its own
"Historical document" banner, and none of them is a safe guide to how
NMOX Studio actually works.

## The live documents

- **[plan.md](./plan.md)** — where the project stands, the honest gaps,
  the ranked opportunities, and the house laws and failure patterns.
  **Read this first when deciding what to do next.**
- **[codebase-guide.md](./codebase-guide.md)** — a beginner's walk
  through the codebase: the five NetBeans RCP ideas everything rides on,
  four traced flows, and where each house law came from. **Read this
  first when deciding how something works.**
- **[tech-debt.md](./tech-debt.md)** — the current debt ledger: open
  items with their deferral reasons, closed items by version.
- **[nbm-signing.md](./nbm-signing.md)** — how the secret-gated NBM
  signing pipeline is wired, and how to turn it on.
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

## The archaeology

Everything else here — architecture, api-design, performance,
scalability, security, testing-strategy, technical-roadmap,
implementation-guide, team-structure, and the rest — predates the
shipping product. They are kept because deleting history is worse than
labelling it, not because they are accurate.

---

## Historical: the original v0.x engineering README

> **Everything below this line is archaeology** and is preserved only
> as history. The targets were aspirations, the metrics were never
> measured, and parts of the stack described here (J2V8, for one) were
> never shipped. For real numbers see `plan.md`; for the real stack see
> `CLAUDE.md`.

## 🎯 Engineering Principles

1. **Performance First** - Every feature must meet performance budgets
2. **Modular Architecture** - Clean separation of concerns via NBM modules
3. **Test-Driven Development** - Minimum 80% code coverage
4. **API Stability** - Backward compatibility for public APIs
5. **Security by Design** - Security considered in every decision

## 📊 Key Technical Metrics

| Metric | Target | Current | Status |
|--------|--------|---------|--------|
| Startup Time | <3s | 2.8s | ✅ |
| Memory (Idle) | <500MB | 450MB | ✅ |
| Test Coverage | >80% | 85% | ✅ |
| Build Time | <5min | 4min | ✅ |
| P95 Response | <100ms | 95ms | ✅ |

## 🔧 Technology Stack

### Core Platform
- **Platform:** NetBeans RCP 22.0
- **Language:** Java 17 (Temurin)
- **Build:** Maven 3.9.x
- **Modules:** NetBeans Module System (NBM)

### Web Technologies
- **JavaScript:** Chrome V8 via J2V8
- **TypeScript:** Native compiler integration
- **Language Servers:** LSP protocol support
- **Debugging:** Chrome DevTools Protocol

### Infrastructure
- **CI/CD:** GitHub Actions
- **Monitoring:** OpenTelemetry
- **Analytics:** Privacy-first telemetry
- **Distribution:** Platform-specific installers

## 🚀 Quick Links

- [GitHub Repository](https://github.com/NMOX/NMOX-Studio)
- [Build Status](https://github.com/NMOX/NMOX-Studio/actions)
- [Performance Dashboard](https://metrics.nmox.studio/performance)
- [Security Reports](./security/reports/)

## 📧 Engineering Contacts

- **Engineering Lead:** eng-lead@nmox.studio
- **Architecture:** architecture@nmox.studio
- **Security:** security@nmox.studio
- **DevOps:** devops@nmox.studio

---

*Last Updated: January 2025*
*Review Cycle: Bi-weekly*