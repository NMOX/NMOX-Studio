# NMOX Studio Documentation

NMOX Studio is a free, open-source IDE for modern web development, built on
the NetBeans Rich Client Platform. Your whole toolchain lives in a
Reason-style **Task Rack** — install, build, test, serve, lint, and deploy
are hardware-styled devices you wire together with patch cables — wrapped
around a polyglot editor and a suite of studios for APIs, databases, smart
contracts, and cloud infrastructure. Licensed under Apache 2.0.

## Start here

**[📖 The User Guide](user-guide.md)** — the complete manual. Install,
first launch, projects, the rack, all four studios, the wizards, Docker,
Learning Spaces, and the safety nets. Illustrated, and written to be read
front to back or dipped into by section.

## The rest of the docs

- **[⛓️ Making a Smart Contract](making-a-smart-contract.md)** — a worked
  tutorial: build a real escrow contract the Contract Studio way, with
  Foundry tests, a gas gate, and the live local-chain loop. Every command
  and number in it is real.
- **[🎛️ The Device Reference](devices.md)** — every device in the rack,
  its knobs and its jacks. Generated from the source, so it never drifts.

## Installing

Grab a build from the
**[latest release](https://github.com/NMOX/NMOX-Studio/releases/latest)** —
macOS `.dmg`, Windows installer, Debian/Ubuntu `.deb`, or generic Linux
`.tar.gz`, each bundling its own Java runtime. macOS users on Homebrew can
`brew install --cask --no-quarantine nmox-studio` (see the
[User Guide](user-guide.md#1-install) for the full three-line tap). The
`-portable.zip` is the one bring-your-own-Java build.

## Building from source, or contributing

The repository [README](../README.md) covers building, the module layout,
and how to add a module; **[CLAUDE.md](../CLAUDE.md)** is the deep
architecture reference. Prerequisites are Java 21+ and Maven 3.6+:

```bash
git clone https://github.com/NMOX/NMOX-Studio.git
cd NMOX-Studio
mvn clean package -DskipTests
./run.sh
```

> **Note:** the `product/`, `hack/`, and most of `engineering/`
> subdirectories are early-era design documents, kept only for
> archaeology — each carries a "Historical document" banner, and none
> describes the shipping product. For current reality, use the docs above.
