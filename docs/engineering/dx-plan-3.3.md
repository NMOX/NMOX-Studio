# The 3.3 developer-experience plan (draft)

*Started 2026-09-25, the night 3.2.0 shipped. 3.1 asked how long the first
hour takes; 3.2 asked what a developer who stayed does all day. 3.3 asks
what happens when the project is big.*

## The question

The developer who stayed works on their company's repository: a monorepo
with tens of thousands of tracked files, a `node_modules` twice that size,
two hundred workspace packages. Every feature NMOX Studio has was built and
walked on projects of a few hundred files. What does it cost to leave the
IDE open on the big one: idle, aiming it, finding a file, searching it,
watching it?

## How it is measured

A fixture a script can rebuild in a minute rather than a borrowed
repository: an npm-workspaces monorepo of 200 packages × 250 source files
(50,202 tracked files, 800 directories) with an ignored `node_modules` of
60,000 files, committed so git has real work. The assembled app is aimed at
it under a 150-second JFR recording (`settings=profile`), and the same boot
on a five-file repository is the control. CPU samples and allocation are
attributed to their first product frame; the platform's own log lines
(indexing, warnings) are read beside them.

On the control, `git status` on the fixture takes 0.24 s from a shell, so
the git chip's one spawn is not where a big repository hurts.

## What the first survey found

1. **The file watchers re-walked the whole tree every poll.** The Project
   Studio tree polled every 1.5 s and the manifest pulse every second, each
   walking and `stat`-ing every file and rebuilding a 50,000-entry map. They
   took 305 of 572 CPU samples and 6.8 of 9.8 GB of allocation (≈45 MB/s of
   garbage), and the heap swung between 226 and 462 MB.
2. **The watcher's 50,000-file cap was silent.** A tree with more files
   stopped the walk at the cap, so which files went unwatched depended on
   walk order — an external edit there never refreshed the tree, and REFLEX
   never saw the save.
3. **Go to File, Go to Symbol and Find in Projects on the fixture.** Go to
   File's first query showed "Searching…" and answered in about 5 s (four
   matches, each labelled with its workspace package), a second query in
   under a second. Find in Projects for a literal in two files of 50,202
   answered correctly in 10–34 s cold (between two samples) and in 4 s or
   less warm, the heap climbing to about 700 MB. Profiled (JFR, a cold
   search on a fresh userdir, done in about 12 s): the cost is the
   platform's — a FileObject and a DataObject per file, then a
   reference-queue thread spending 213 of the recording's 746 samples
   removing the weak file listeners those DataObjects registered, once they
   are collected; the product's own share (the `.gitignore` answer 3.2
   added, the project lookup) is a handful of samples. (The platform's
   indexer sees no source roots in a web project — `Complete indexing of 0
   source roots` — so none of these has its index to lean on.) Go to
   Symbol's project index stops at 2,000 files (`ProjectSymbols.MAX_FILES`)
   and says it is partial, but it walked alphabetically: on 200 packages it
   held the first eight by name, about 4% of the fixture, and none of the
   package being edited.
4. **The project-wide editor scans look only in the file's own package.**
   Design-token and CSS-class completion (and ⌘-click to a token or class)
   read at most 60 stylesheet and markup files, and the fetch-path →
   Express-route jump at most 80 source files — but each starts from the
   nearest `package.json` above the file (`ProjectRoot.of`), which in a
   monorepo is the file's own package, so the caps rarely bite there. (The
   first draft of this finding said the scans walked the whole repository;
   reading their callers said otherwise.) The gap is the other way round:
   a token declared in a shared design-tokens package, or the route a
   sibling `api` package serves to a `web` package's `fetch`, is never
   looked for. Rename Class refuses at its cap (a partial rename is
   corruption), which is right.
5. **Find in Projects' results read as empty on first open — on any
   project.** The Search Results window opens with its preview pane showing
   and the results tree squeezed to a column about 10 pixels wide, so a
   search that found its matches looks like one that found nothing. The
   platform places the divider at `max(saved, 250)`, but its first layout
   happens while the window has almost no width, Swing clamps the divider,
   and the panel's own listener saves the clamp (`replace_results_divider=14`
   in a fresh userdir's preferences) — so every later session reopens
   squeezed too. Reproduced on the five-file walk repository.

## The plan

| # | Unit | Answers | Proof |
|---|------|---------|-------|
| 1 | The watcher becomes incremental: a poll stats the directories it knows and relists only those whose time moved (how git, an atomic save and a generator write), stats the tracked files for in-place edits (the tree every 10th poll), reconciles with a full walk every 20 s, waits at least eight times its own last duration, and says once when the cap is reached | 1, 2 | **Done on `claude/after-3.2`.** The same 150 s boot: the watchers' CPU samples 305 → 44, their allocation 6.8 → 0.6 GB. The first cut measured 14 and 0.19 GB, and a hostile review proved it could go blind where the old walk healed (a file written in the tick its directory was listed: 9–21 of 9,000 lost in a real race; a failed listing; a root that came back; quadratic mass changes); a second found the walk still stopping at the cap and future-dated directories relisted forever. The reconcile, git's racily-clean rule and one-pass removal are the price. `IncrementalWatcherTest` (fourteen), a mutant by name for each |
| 2 | Go to Symbol reads the package being edited first | 3 | **Done on `claude/after-3.2`.** Walked on the fixture with a file of `pkg-150` open: a function in it is found where the alphabetical 2,000 never reach; the partial-index message still shows. `ProjectSymbolsTest`, two mutants by name. A larger cap, and its cost, still to measure |
| 3 | Find in Projects' results open readable: the first real layout of a results split gives a tree under 120 px the platform's own 250, once, watching only the Search Results window, and the platform's listener saves that | 5 | **Done on `claude/after-3.2`.** Walked three times: a fresh userdir's first search reads "Found 1 match of three in 1 file" with the tree at 250 px, and a userdir already carrying the saved 14 opens healed. A second review caught the first cut listening to every component in the IDE (25,909 events for ten relayouts), saving 40% of a wide window as an absolute width, and measuring the preview in a mirrored window; all three fixed. `SearchResultsSplitTest` (seven), a mutant by name for each |
| 4 | The project-wide editor scans across a monorepo's packages: a token, class or route declared in another package is found, the file's own package first | 4 | **Tokens and classes done on `claude/after-3.2`**, and the question "which siblings" answered by the package itself: the workspace packages its `package.json` depends on, found where the package manager linked them (else among the declared packages), inside the workspace and outside `node_modules`, eight at most, sharing a second 60-file budget. Walked on a two-package workspace: `var(--s` in `packages/web` offers `--space-lg` from `@acme/tokens`, and an unrelated sibling's `--stranger-hue` stays out. Rename Class still edits only its own package and refuses a name a dependency declares. `WorkspaceDependenciesTest` (eight), six mutants by name. The route jump says when it stopped at its cap (`RoutesLookupTest`, a mutant by name). **Routes still to design:** a `web` package's `fetch` rarely depends on the `api` package that serves it, so the dependency list is the wrong question there |
| 5 | Project Studio's tree expanding a 250-file folder, and the Workbench's detection sweep, on the fixture | — | to measure (the tree's shape and live refresh were walked: a file made from outside appears within 3 s, and a deleted one goes) |

## Already on the branch

Written after 3.2.0 shipped, held for the release this plan becomes:

- An edit request (`nmox -w`, `nmox -d`) is read off the EDT before any of
  it opens; an unchecked failure there is refused instead of leaving git
  waiting; walked with a real `git commit` and `git difftool`.
- *Terminal: Create New Terminal* always starts a shell.
- A left-over git message is recognised in a git folder under any name
  (`--separate-git-dir`, a bare repository), stopping at a worktree's root.
- Every translated mnemonic reaches a key: mnemonic tables for Russian,
  Ukrainian and the eight Latin-script languages, Hindi's appended letters,
  the top menu bar's uniqueness law (ledger 122 closed).
- The file watchers (row 1).
- Find in Projects' results open readable (row 3).
- The route jump says when it stopped at its cap (row 4, in part).
- DB Studio watches its `.nmoxdb.json` with one stat a poll instead of a
  walk of the whole project.
- Go to Symbol indexes the package being edited first (row 2).
- Design tokens and CSS classes reach the workspace packages a package
  depends on (row 4, in part).
- ⌃Space at the top of a stylesheet offers selectors instead of throwing a
  `StringIndexOutOfBoundsException` (and no longer drops the file's first
  character from the prefix — a test had pinned that as a quirk). Found
  walking row 4.
