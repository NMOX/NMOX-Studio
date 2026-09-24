# Contributing to NMOX Studio

NMOX Studio is a NetBeans-Platform IDE for doing, learning, and
experimenting with web development: the Task Rack (53 hardware-styled
devices wired with patch cables), a polyglot editor (LSP plus
88 TextMate grammars), seven per-project studios (Task Board, Block, API,
DB, Contract/Web3, Infra, Project), 93 Learning Spaces, experiments
that teach, and installers for all three OSes with an in-app update
center. Apache-2.0. The product even ships its own website — press
**Help ▸ NMOX Studio Website (local)** in a running build, or visit
<https://nmox.github.io/NMOX-Studio/>.

Read [docs/engineering/plan.md](docs/engineering/plan.md) first — the
living plan with the honest gaps — and
[docs/engineering/codebase-guide.md](docs/engineering/codebase-guide.md)
for the five platform ideas everything rides on.

## Build and run

- **JDK 25** to build, and **Maven 3.6.3+**. The root pom refuses an
  older JDK at `validate` with the reason and where to get one, because
  on JDK 21 the failure is otherwise hundreds of misleading
  `cannot find symbol: Bundle` errors in the ui module (OpenJFX 26's
  jars are class-file 68). On macOS: `brew install openjdk@25`, then
  `export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`;
  anywhere, Temurin 25 or Zulu 25. Building on 25 is not targeting 25:
  the bytecode stays 21 (see the law at `maven.compiler.target` in the
  root pom before you touch it).

```bash
git clone https://github.com/NMOX/NMOX-Studio.git
cd NMOX-Studio
./build.sh             # mvn clean install -DskipTests, after checking the JDK Maven will use
./run.sh               # the assembled app, with its own userdir/ beside the checkout
./build.sh --verify    # the whole gate: tests + SpotBugs + find-sec-bugs + JaCoCo floors
```

A fresh clone builds and boots in well under a minute of your
attention; if it doesn't, that's a bug — file it.

## The inner loop

The commands you will type a hundred times, and the traps each one has
already sprung on somebody.

**Build once, from the root.** The first build needs the network for
dependencies; after that, `-o` keeps Maven offline and fast.

```bash
mvn install -DskipTests          # first time
mvn -o install -DskipTests       # every time after
```

**Rebuild one module** after changing it. Add `-am` when you also
changed a module it depends on (almost everything depends on `core`).

```bash
mvn -o install -DskipTests -pl editor
```

**Run one test, or a few.**

```bash
mvn -o -pl editor test -Dtest='EmmetTest,EmmetWiringGateTest' \
    -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true
```

- Separate classes with **commas**, never `+`.
- `-Dsurefire.failIfNoSpecifiedTests=false` lets a filter that matches
  nothing in some module of the reactor pass that module instead of
  failing it (needed with `-am`). It also means a misspelt class name
  runs nothing and says BUILD SUCCESS, which is why the next rule exists.
- **Never `-q`, and read the verdict line**:
  `Tests run: N, Failures: N, Errors: N, Skipped: N`. A missing verdict
  is a failed run, and a quiet run has hidden a failing test here more
  than once.
- `-Djacoco.skip=true` skips coverage instrumentation, which a run of a
  few classes does not need.

**Gates that read the assembled app** (anything in the application
module's `packaged-app-gates` execution: locale parity, typography, the
menu census) need a cluster to read. Build from the root first, then:

```bash
mvn -o -pl application verify -Dtest='LocaleBundleParityTest' \
    -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true -Dspotbugs.skip=true
```

**Boot the app you just built** with a throwaway user directory, so
your installed copy's settings, window layout and caches never meet a dev
build, and with the update check off, since a dev build's module
versions would be offered the latest release as an "update". (Workspace
Trust grants are the exception: they live in Java's user preferences on
purpose, so they survive a userdir reset, and a dev build shares them with
your installed copy.)

```bash
application/target/nmoxstudio/bin/nmoxstudio --userdir /tmp/nmox-ud --cachedir /tmp/nmox-cd \
    -J-Dplugin.manager.check.updates=false
```

**Always rebuild from the root before booting.** Resuming the reactor
at the application module (`-rf :NMOX-Studio-app`, or `-pl application`
alone) assembles whatever module jars are in `~/.m2`, which are not the
ones you just changed: the app boots, and it is running yesterday's
code. If a fix "does nothing" in the running app, check this first.

**Before you open a PR**, run the whole gate:

```bash
mvn clean verify
```

That is every test, plus SpotBugs, find-sec-bugs and the per-module
JaCoCo floors. A new SpotBugs or find-sec-bugs finding is usually a real
bug: fix it rather than excluding it. CI runs the same verify on
ubuntu, macOS and Windows, all three blocking.

**When a gate fails.** A test whose name ends in `GateTest`,
`LedgerTest`, `ParityTest` or `CensusTest` holds a house law rather than
a feature. [The gates index](docs/engineering/gates.md) says, in one
line each, what law it holds and where it came from; the test's own
javadoc tells the rest. A ledger failing on your change usually wants a
decision written down (classify the new site, with its reason), not a
workaround.

## How this house works

These aren't style preferences; each one was paid for by a shipped
bug. The reviews will hold your change to them, so knowing them saves
you a round trip.

### The laws

1. **Refusals speak.** Nothing fails silently, ever. A gesture that
   can't proceed says so where the user is looking (status line,
   dialog, LCD) with the reason — and writes nothing. A silent
   early-return is a bug even when it's "safe."
2. **Secrets live in the OS keychain**, never in a committable file.
   Every studio workspace file (`.nmoxapi.json`, `.nmoxdb.json`, …)
   is designed so a `git add .` can never stage a credential.
3. **Nothing runs a stranger's code without asking.** Every spawn
   that executes project-controlled commands (npm scripts,
   `node_modules/.bin`, build files) goes through Workspace Trust
   BEFORE the spawn. `SpawnSiteTrustLedgerTest` fails the build until
   a new spawn site is classified.
4. **Every read is bounded.** HTTP bodies, process output, file
   prefixes — capped with an honest truncation marker. An unbounded
   read is an OOM handed to a hostile endpoint.
5. **Writes are atomic and never clobber.** Workspace files write
   temp-sibling + `ATOMIC_MOVE`; generators write `.suggested`
   siblings rather than overwrite; corrupt files become `.bak`, never
   silently replaced.
6. **No disk or process work on the EDT.** Off-EDT via a named
   RequestProcessor, newest-wins on re-aims, results applied on the
   EDT. The paint thread walked `$HOME` once; never again.
7. **A result belongs to the workspace that produced it.** Re-aiming
   a project clears anything the old project could leak through
   (armed Explain buttons, stale result tabs, serving entries).
8. **Every runtime invariant over a checked-in file gets a parse-time
   heal** — a git merge can produce states no gesture can, and the
   parser is where they're caught.
9. **Accessibility is a contract test.** Every control on every
   device exposes an accessible name; the build fails otherwise.

### The method

- **Recon first.** Before building against the platform, read what it
  actually does — decompile the class, probe the behavior live, pin
  the evidence in the commit or ledger. Folklore about NetBeans
  internals has burned us more than any other single cause.
- **Tests ship in the same PR as the feature**, and the interesting
  ones are **mutation-proven**: break the code the specific way the
  test exists to catch, watch the named test fail, restore, watch it
  pass. Only full verdict lines count (`Tests run: N, Failures: N`) —
  a missing verdict is a failed run, and a `-q` grep has faked
  survivors before. Commit your work BEFORE mutating; a bare
  `git checkout --` restore has eaten uncommitted fixes three times.
- **Walk it where it ships.** Dev-tree green is not the product. The
  feature is done when it's been driven in the assembled app
  (`application/target/nmoxstudio/bin/nmoxstudio` with a throwaway
  `--userdir`) — most of this project's best finds came from walks,
  not reviews.
- **Fresh code gets a review.** Within a day or two of a feature arc,
  read it again with hostile lenses (what leaks on re-aim? what's
  unbounded? which claim has no test?). The review has found a real
  bug in day-old code almost every time it has run. A comment
  claiming a property IS a test not yet written.
- **Gate the outcome, not the mechanism.** A build gate should derive
  its population from generated artifacts (the layer, the jar, the
  census) so the case you didn't think of fails the build too.
- **Docs tell the truth.** README counts, user-guide claims, and
  CHANGELOG entries are gated (`DocsCountGateTest`, `ImageRefsTest`,
  docs-landed checks read COMMITTED content via `git show HEAD:`). A
  screenshot in the docs was captured from a real run, wired the same
  commit.

### Landing a change

- Branch from `main`; CI runs the full verify on ubuntu + macos +
  windows, all blocking. The windows lane is a real product surface,
  not a formality — it has found product bugs.
- PRs are squash-merged. Write the summary for a teammate who wasn't
  watching: what changed, what proved it. The pull-request template asks
  exactly that, plus whether you walked it in the assembled app and
  which docs you updated.
- The [deferred-debt ledger](docs/engineering/tech-debt.md) is the
  honest backlog — well-scoped items with written context, and the
  reasons things were deliberately NOT done. Great first
  contributions live there.
- If your change makes a claim ("faster", "covered", "refused"),
  land the proof beside it.

## Where to start

- Run the app, open a Learning Space (**File ▸ New Learning
  Space…**), break something, and follow the error — the loop that
  teaches is the loop that closes.
- `docs/devices.md` (generated, CI-gated) is the rack reference;
  `docs/user-guide.md` walks every surface.
- Questions and proposals: open a GitHub issue. Small fixes need no
  prior discussion.
