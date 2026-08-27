# Contributing to NMOX Studio

NMOX Studio is a NetBeans-Platform IDE for doing, learning, and
experimenting with web development: the Task Rack (53 hardware-styled
devices wired with patch cables), a polyglot editor (86 TextMate
grammars + LSP), seven per-project studios (Task Board, Block, API,
DB, Contract/Web3, Infra, Project), 92 Learning Spaces, experiments
that teach, and installers for all three OSes with an in-app update
center. Apache-2.0. The product even ships its own website — press
**Help ▸ NMOX Studio Website (local)** in a running build, or visit
<https://nmox.github.io/NMOX-Studio/>.

Read [docs/engineering/plan.md](docs/engineering/plan.md) first — the
living plan with the honest gaps — and
[docs/engineering/codebase-guide.md](docs/engineering/codebase-guide.md)
for the five platform ideas everything rides on.

## Build and run

- **JDK 25** to build (bytecode targets 21 — see the law at
  `maven.compiler.target` in the root pom before you touch it),
  Maven 3.6+.

```bash
git clone https://github.com/NMOX/NMOX-Studio.git
cd NMOX-Studio
mvn clean package -DskipTests       # fast build
./run.sh                            # or: application/target/nmoxstudio/bin/nmoxstudio
mvn clean verify                    # the whole gate: tests + SpotBugs + find-sec-bugs + JaCoCo floors
```

A fresh clone builds and boots in well under a minute of your
attention; if it doesn't, that's a bug — file it.

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
  watching: what changed, what proved it.
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
