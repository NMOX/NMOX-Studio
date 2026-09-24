# NMOX Studio Documentation

NMOX Studio is a free, open-source IDE for modern web development, built on
the NetBeans Rich Client Platform. Your whole toolchain lives in a
Reason-style **Task Rack** — install, build, test, serve, lint, and deploy
are hardware-styled devices you wire together with patch cables — wrapped
around a polyglot editor and a suite of studios for APIs, databases, smart
contracts, and cloud infrastructure. Licensed under Apache 2.0.

## Start here

**[⏱️ The quickstart](quickstart.md)** — five minutes from download to your
own project running: install, open, run, find, test.

**[↔️ Coming from VS Code](coming-from-vscode.md)** — the chords your hands
already know, the concepts mapped, and the honest gaps.

**[📖 The User Guide](user-guide.md)** — the complete manual. Install,
first launch, projects, the rack, the studios, the wizards, Docker,
Learning Spaces, and the safety nets. Illustrated, and written to be read
front to back or dipped into by section.

**[🔤 The glossary](glossary.md)** — the product's own words: rack, device,
patch, jack, lane, aim, the ⇄ chip, KVASIR, and the NetBeans terms that
show through.

**In your language** — the whole guide is translated into every language
the IDE itself speaks. Each translation carries a language bar at the top,
so switching is one click from wherever you are.

[Español](user-guide.es.md) · [Français](user-guide.fr.md) · [Deutsch](user-guide.de.md) · [Русский](user-guide.ru.md) · [Українська](user-guide.uk.md) · [Polski](user-guide.pl.md) · [Português (Brasil)](user-guide.pt.md) · [Bahasa Indonesia](user-guide.id.md) · [Filipino](user-guide.tl.md) · [Tiếng Việt](user-guide.vi.md) · [简体中文](user-guide.zh.md) · [हिन्दी](user-guide.hi.md) · [עברית](user-guide.he.md) · [العربية](user-guide.ar.md)

## The rest of the docs

- **[🧭 Tutorials](tutorials/README.md)** — short, do-it-yourself
  walkthroughs, one per unique system: the Task Rack, the four studios,
  Block Studio, Infra Designer, polyglot debugging, Docker, KVASIR,
  Learning Spaces, and the wizards. Each is a single sitting.
- **[⛓️ Making a Smart Contract](making-a-smart-contract.md)** — a worked
  tutorial: build a real escrow contract the Contract Studio way, with
  Foundry tests, a gas gate, and the live local-chain loop. Every command
  and number in it is real.
- **[🎛️ The Device Reference](devices.md)** — every device in the rack,
  its knobs and its jacks. Generated from the source, so it never drifts.
- **[🗄️ The Rack Gallery](racks.md)** — every rack the product hands out:
  what each is for, what it needs, and how it is wired. Generated from the
  rack files themselves.
- **[📄 Rack files](rack-files.md)** — a rack as a portable, inspectable
  workflow: the format, what happens when a file travels between people, and
  how to contribute a rack to the gallery.
- **[🖼️ The visual tour](tour.md)** — every major feature on one page,
  with real screenshots; the phosphor-styled version is the website,
  <https://nmox.github.io/NMOX-Studio/>.
- **[🎬 The demo script](demo-script.md)** — the five-minute demo —
  beats, clicks, and what to say.
- **[🧪 The Kitchen Sink](kitchen-sink.md)** — every surface of the
  product exercised in one sitting: twenty-five do/see stations, each
  claim matched to a proof.
- **[🏙️ A Day at Meridian](a-day-at-meridian.md)** — one story, one real
  build through every area, screenshots from a live session.
- **[🎓 Learning Spaces: the community catalog](learning-spaces.md)** —
  the drop-in format for your own tutorials, the schema, the parser's
  refusals, and the exporter that turns a project into a space.
- **[🧩 Your own project templates](project-templates.md)** — the
  `~/.nmox/templates.d` drop-in that puts your templates in the New
  Project wizard beside the built-ins.
- **[🎛️ Device files — write your own rack device](device-files.md)** —
  the JSON device format: any `*.json` in `~/.nmox/devices.d` becomes a
  real device on the shelf, no Java, no plugin, no restart.
- **[🔌 Writing a Rack Device (the Device SPI)](device-spi.md)** — the
  plugin route: the frozen `core.spi.device` contract with a worked
  example that installs through Tools ▸ Plugins.
- **[⛓️ A Beginner's Guide to Smart Contracts](smart-contracts-beginners-guide.md)**
  — five contract shapes with the kit's chains as evidence, real refusal
  idioms, and a ten-minute first contract on three paths.

## Installing

Grab a build from the
**[latest release](https://github.com/NMOX/NMOX-Studio/releases/latest)** —
macOS `.dmg`, Windows installer, Debian/Ubuntu `.deb`, or generic Linux
`.tar.gz`, each bundling its own Java runtime. macOS users on Homebrew install with a one-time
`brew trust --cask nmox/nmox-studio/nmox-studio` and then
`brew install nmox/nmox-studio/nmox-studio` — see the
[User Guide](user-guide.md#1-install) for the notes on Gatekeeper. The
`-portable.zip` is the one bring-your-own-Java build.

## Building from source, or contributing

The repository [README](../README.md) covers building, the module layout,
and how to add a module; **[CLAUDE.md](../CLAUDE.md)** is the deep
architecture reference, and [CONTRIBUTING.md](../CONTRIBUTING.md#the-inner-loop)
has the inner loop: one-module rebuilds, single tests, throwaway
userdirs. Building needs JDK 25 and Maven 3.6.3+ (the build refuses an
older JDK by name):

```bash
git clone https://github.com/NMOX/NMOX-Studio.git
cd NMOX-Studio
./build.sh
./run.sh
```

> **Everything in this directory is current.** The early-era `product/`
> and `hack/` directories, and the v0.x papers under `engineering/`,
> were removed in 3.0.1 — **git is the archaeology.** If you want them,
> `git log -- docs/` has every one.
