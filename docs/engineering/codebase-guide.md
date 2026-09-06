# A Beginner's Guide to the NMOX Studio Codebase

This guide is for someone who knows some Java but has never seen a
NetBeans Rich Client Platform (RCP) application. It explains the ideas
the whole codebase leans on, then traces four real user actions through
the actual files, so you can follow any flow in the product the same
way.

Companion documents: [CLAUDE.md](../../CLAUDE.md) (module table, build
commands, how-to-add recipes), [plan.md](plan.md) (current state and
house laws), [devices.md](../devices.md) (the rack device reference).

---

## 1. The 30-second map

NMOX Studio is a **NetBeans Platform application**: we did not write a
window system, an editor framework, or a plugin loader — the platform
ships those, and our eleven Maven modules plug features into them.

| Module | What it owns |
|---|---|
| `core` | Shared plumbing every module uses: process spawning (`ProcessSupport`), tool discovery (`ToolLocator`), atomic file writes (`AtomicFiles`), capped HTTP reads (`HttpBodies`), and the **SPI seams** other modules discover at runtime (`core.spi.*`) |
| `editor` | Everything about editing text: 87 TextMate grammars, lexers, completion, Emmet, the Navigator outline, LSP clients, the JS/TS debugger |
| `tools` | Project recognition (60 manifest types) and the NPM explorer |
| `rack` | The signature UI: a synth-style rack of "devices" that run real dev tools, wired with patch cables — including devices defined by plain JSON files in `~/.nmox/devices.d/` (v2.0.0) |
| `apiclient` / `dbstudio` / `web3` / `infra` | The studios: Postman-style API client, database suite, smart-contract suite, cloud-infra designer |
| `project` / `ui` | Project Explorer/Workbench windows; the Welcome launchpad, wizards ("Kits"), Options panels, update checking — plus the in-app Browser, the IRC client, and the Task Board (`ui.browser`, `ui.irc`, `ui.tasks`) |
| `branding` / `application` | Icons/splash/name; the final assembly that packages everything into one installable app |

## 2. The five RCP ideas everything rides on

### 2.1 Modules and the layer (how features get registered)

The platform boots by reading **registration metadata**, not by our
code calling `new`. Each module contributes entries to a virtual
filesystem called the *system filesystem* (the "layer"). An entry can
say "there is a menu item here", "this class handles `.nim` files",
"this window opens with ⌥⌘4".

You will see two ways of writing registrations:

- **Annotations** (the common way). For example
  [NimGrammar.java](../../editor/src/main/java/org/nmox/studio/editor/grammars/NimGrammar.java)
  carries `@MIMEResolver.ExtensionRegistration(extension = {"nim", …})`.
  At **compile time** an annotation processor turns that into XML in
  `META-INF/generated-layer.xml` inside the module's jar. The app never
  reflects over your classes at startup — it reads that XML.
  (Consequence: if annotation processing is misconfigured, features
  *silently vanish*; our root `pom.xml` pins `-proc:full` for exactly
  this reason.)
- **Hand-written `layer.xml`** for things annotations don't cover, e.g.
  [ui/layer.xml](../../ui/src/main/resources/org/nmox/studio/ui/layer.xml)
  registers keyboard-shortcut "shadows" and the update center.

When a test needs to prove a registration exists, we assert against the
**built jar's generated layer** (see `HttpEditorGestureTest`) — the
bytes the platform will actually read, not the annotation we hope it
read.

### 2.2 Lookup (how modules find each other without depending on each other)

`Lookup` is the platform's service registry — think "dependency
injection, but discovery-based". A module *publishes* an implementation:

```java
@ServiceProvider(service = EmbeddedBrowser.class)
public final class EmbeddedBrowserProvider implements EmbeddedBrowser { … }
```

and any module *finds* it without compiling against the publisher:

```java
EmbeddedBrowser b = Lookup.getDefault().lookup(EmbeddedBrowser.class);
if (b == null) { /* feature absent — degrade gracefully */ }
```

This is how the rack's SCOPE device opens the in-app browser
([BrowserDevice.java](../../rack/src/main/java/org/nmox/studio/rack/devices/BrowserDevice.java))
without the rack module depending on the ui module: the *interface*
lives in `core.spi`
([EmbeddedBrowser.java](../../core/src/main/java/org/nmox/studio/core/spi/EmbeddedBrowser.java)),
the *implementation* in ui, and a null lookup simply means "fall back
to the system browser". We call these **soft-dependency seams**; the
other big ones are `OracleAsk` (AI), `ProjectAim`, and `LiveServings`.

### 2.3 TopComponents (windows)

Every dockable window — the rack, each studio, the browser — is a
`TopComponent` subclass. Its annotations declare where it docks and how
it opens:

```java
@TopComponent.Registration(mode = "editor", openAtStartup = false)
@ActionReference(path = "Shortcuts", name = "DA-4")   // ⌥⌘4
```

Two lifecycle rules the codebase enforces everywhere:

- **Zero boot cost**: constructors do nothing expensive. Real work
  waits for `componentOpened()` or, stricter, `componentShowing()` —
  a tab that is open-but-hidden must not spawn processes or walk disks.
  (Measured law: a fresh boot starts *zero* child processes.)
- **Symmetric listeners**: whatever `componentOpened()` subscribes,
  `componentClosed()` unsubscribes, or a closed window keeps reacting
  to events forever (a real bug class we've fixed more than once).

### 2.4 DataObjects and MIME types (how files become features)

When you open a file, the platform resolves its **MIME type** (our
resolvers map `.nim` → `text/x-nim`), finds the registered
**DataObject** type for it, and builds an editor with every feature
registered *for that MIME*: the TextMate grammar colors it, the CSL
language config
([NimLanguage.java](../../editor/src/main/java/org/nmox/studio/editor/languages/NimLanguage.java))
gives it comment-toggling and brace logic, completion providers and
spellcheckers attach — all discovered from registrations, none of it
hard-wired.

### 2.5 The EDT and RequestProcessors (the threading law)

Swing has **one** UI thread: the Event Dispatch Thread. The iron rules:

- **Never block the EDT.** No disk reads, no process spawns, no
  network, no keychain calls on it — any of those freezes the whole UI.
- Background work runs on a **`RequestProcessor`** (the platform's
  named thread pool). You'll see dedicated lanes everywhere:
  `RequestProcessor("API Studio")`, save lanes, send lanes.
- Results come **back to the EDT** via `SwingUtilities.invokeLater`
  before touching any component.

A typical round-trip, from
[ApiClientTopComponent.java](../../apiclient/src/main/java/org/nmox/studio/apiclient/ui/ApiClientTopComponent.java):

```java
RP.post(() -> {                       // 1. hop OFF the EDT
    hydrateAuthNow(r);                // 2. keychain read (may block on an OS prompt)
    SwingUtilities.invokeLater(() ->  // 3. hop BACK to the EDT
        authField.setText(r.authToken)); // 4. now it's safe to touch Swing
});
```

When you read any class in this codebase and wonder "why the dance?",
it is almost always this law.

## 3. Five flows, traced through real files

### 3.1 Boot

1. The launcher starts the platform, which scans every module jar's
   manifest + layer (~90% of measured startup time — the price of the
   feature set, by design).
2. The window system restores the last layout; on first run the suite
   tabs (Workbench → Rack → DB → Web3 → Infra → API → Docker) open by
   default, but each one defers its real work per §2.3.
3. [MainWindow](../../ui/src/main/java/org/nmox/studio/ui/MainWindow.java)
   renders the Welcome launchpad.
4. The rack aims at your most recent project — or, on a truly fresh
   machine, a created-on-first-run `~/NMOX` folder (never `$HOME`:
   walking the home directory triggers macOS privacy prompts, a bug we
   fixed in v1.33.1).
5. Nothing spawns, nothing hits the network. The update check runs
   post-UI on the platform's schedule.

### 3.2 Opening `hello.nim`

1. MIME resolution: the generated-layer entry from `NimGrammar`'s
   annotation maps the `nim` extension to `text/x-nim`.
2. The platform builds the editor; the TextMate engine (TM4E) loads
   [nim.tmLanguage.json](../../editor/src/main/resources/org/nmox/studio/editor/grammars/nim.tmLanguage.json)
   and tokenizes each line into colored spans.
3. `NimLanguage` (a CSL config registered for the same MIME) supplies
   `#` comment toggling; `PolyglotCompletionProvider` supplies keyword
   completion; the Navigator outline attaches.
4. Failure mode worth knowing: a *broken grammar anywhere in the
   include graph* kills tokenization for every grammar that includes
   it — colors vanish while every registration still looks perfect.
   Diagnose in `messages.log` (look for `TextmateLexer` exceptions),
   not in the registrations. `GrammarCapturesShapeTest` gates the known
   shape of this failure.

### 3.3 Pressing GO on a rack device

1. Every device is a
   [RackDevice](../../rack/src/main/java/org/nmox/studio/rack/model/RackDevice.java)
   subclass; its GO button calls `exec(...)`.
2. `exec` hops off the EDT onto a RequestProcessor lane, then asks
   **Workspace Trust**: running a cloned repo's `package.json` scripts
   is arbitrary code execution, so the first run in a project prompts
   ("Keep Safe" refuses and *no process starts* — and the device's
   serving lights are wired to tell the truth about that).
3. `CommandExecutor` spawns the process and pumps its output
   line-by-line (bounded — a single endless line cannot eat the heap)
   onto the **RackBus**, where MONITOR displays it and the
   **FlightRecorder** journals it.
4. Exit fires the device's `onFinished`: LEDs update, gates drop, and a
   failure becomes structured context ORACLE's EXPLAIN can send to the
   Anthropic API — but only after its own explicit consent dialog,
   because *outbound data* needs a different permission than *inbound
   execution*.

### 3.4 Sending a request in API Studio

1. Send buttons must never wedge the UI: `send()` posts the work to a
   dedicated, interruptible send lane (the button becomes **Cancel**).
2. On that lane, the request's auth token is **lazily hydrated** from
   the OS keychain — first use only, once per session (v1.201.0; bulk
   reads at startup caused macOS password prompts after every upgrade).
   Secrets live *only* in the keychain; the committable
   `.nmoxapi.json` never contains them (the secrets law, and tests
   assert it structurally — the history entry *has no token field*).
3. `ApiClient` resolves `{{variables}}`, sends over the shared HTTP
   client, and **reads the response through a capped stream** — a
   runaway endpoint cannot out-of-memory the IDE; truncation is flagged
   on the status line, never silent.
4. Back on the EDT: response grids, security-header grades, history.
   When you later switch projects, `applyWorkspace` clears the
   response, the find bar, and the hydration cache — **a result
   belongs to the workspace that produced it** (a disclosure bug class
   we closed twice before writing it into law).

### 3.5 Click "Clock In" on a task card

The newest flow is also the best tour of the studio-file laws, because
one click crosses all of them. Open the Tasks tab (⌥⌘1), right-click a
card, choose Clock In:

1. The context menu was installed by
   [TasksTopComponent](../../ui/src/main/java/org/nmox/studio/ui/tasks/TasksTopComponent.java)
   with the *clicked-card-wins* helper (`Popups.popupTargetList`) — the
   verb acts on the card under the pointer, not a stale selection.
2. The menu item calls `mutate(() -> board.clockIn(id, now))`. Every
   board change goes through this ONE `mutate()` method — it first
   re-checks the file on disk for a foreign edit (never-clobber: if
   someone else wrote it, the file wins and your gesture is dropped
   with a status note), then runs the model change.
3. The model half lives in
   [TaskBoard](../../ui/src/main/java/org/nmox/studio/ui/tasks/TaskBoard.java) —
   pure Java, no Swing: one running session board-wide, a double
   clock-in refused, sub-minute sessions dropped. All of that is plain
   unit tests in `TimeClockTest`.
4. The save posts to a single-threaded `RequestProcessor` lane (the
   EDT never touches disk), writes atomically via `AtomicFiles`, and
   notes the file's new mtime+size in a `SelfWriteTracker`.
5. A `FilePulse` (core.util) is stat-polling that same file. It fires
   on the save — and does nothing, because the tracker says "that was
   me". When a TEAMMATE's pull changes the file instead, the same
   pulse reloads the board within ~1.5 s. Self vs. foreign is the
   entire trick, and it is one `isForeign(mtime, size)` call.
6. Back on the EDT, the strip rebuilds (your selection restored via
   `focusCardId`), the card wears ⏱, and the header shows the running
   elapsed — ticked by a label-only Swing timer, because rebuilding
   the strip every 30 s would drop your selection.

Read those five files in that order and you have seen every persistence
law in the product; the other five studios are the same shape, larger.

## 4. The house laws (why the code looks the way it does)

Recurring rules you will meet in comments; each earned its place from a
real bug, and most are pinned by a test that fails the build if broken:

1. **Never block the EDT** (§2.5) — and mutate Swing *only* on it.
2. **Zero boot cost** — deferred to `componentShowing()`, measured at
   zero child processes per fresh boot.
3. **Secrets are keychain-only** — never in a committable file, never
   in logs; deleting the owner deletes the secret.
4. **Every outside read is bounded** — HTTP bodies, process output,
   even single lines, all capped with honest truncation markers.
5. **Workspace writes are atomic** — temp sibling + atomic move via
   `AtomicFiles`; a crash mid-save can't leave a torn file. Corrupt
   files are `.bak`'d, never clobbered.
6. **Trust gates before spawns** — a cloned repo's code never runs
   without the user saying so, in any of the ~six places that spawn.
7. **Honest degradation** — a missing tool/engine/key produces a plain
   status ("NO DEBUGGER FOR <KIND>"), never a dead click, a lie, or a
   crash dialog.
8. **Registrations are byte-verified** — tests read the built jar's
   generated layer, not the source annotation.
9. **Fixes are mutation-proven** — after writing a test for a fix, we
   re-break the code to watch the test fail; a test that can't fail
   proves nothing. You'll see "mutation-proven" throughout the
   changelog meaning exactly this.

**Every background thread is a named daemon** (v2.63.0). Raw threads are
born in exactly one place, `core.util.Threads` — named, because a thread
dump is the first instrument in an EDT-hang or exit-hang investigation
and an anonymous `Thread-17` says nothing; daemon, because a pump or
watcher that outlives the platform's shutdown must never be what keeps
the JVM alive. Shutdown hooks are the written exception (named, never
daemon). `DaemonThreadGateTest` reads every module's sources and fails
the build on any other `new Thread(`. Origin: the senior-RCP census found
eight non-daemon pumps and drains among twenty-two raw threads.

**A verdict belongs where the knowledge is** (v2.84.0). The executor is
the only party that knows a kill from a crash, so its handle marks its
own kill and the exit line reads `[exit N] stopped`; every consumer —
the flight recorder, `run_history`, BLACKBOX, ORACLE's failure context —
learns the difference from that one line instead of guessing from the
code. Origin: the Angular re-walk found the user's own ■ recorded as
`failed [1]`.

**Every new read surface is a disclosure path for every old secret
law** (v2.84.0). The editor's env-key completion had refused to show
`.env` VALUES since v2.31.0; `search_text` read the same file whole one
tool over. Secret-bearing files are a named class
(`TextSearch.isSecretBearing`: the `.env` family, rc files carrying
auth tokens, private keys and certificates, the `--env-file` shape) —
never searched, never counted, never completed, refused by `outline`
by name. When a tool grows, sweep the laws, not the feature.

**The first SHOW serves the deferred work** (v2.85.0, the other half of
zero boot cost). A default-open tab that defers its refresh must also
RUN it on `componentShowing()` — once — or a tab reached by its own
tab or a Welcome door shows its placeholder forever. Origin: the Docker
Panel read "ENGINE: checking…" over an empty pane until Refresh All.
`DockerPanelFirstShowTest` pins the shape.

**A user-visible count reads right** (v2.85.0). "1 card", "0 cards",
"1 match": `core.util.Plural` at every count site; `PluralCopyGateTest`
fails on a bare `+ " cards"` shape returning for the nouns the sweep
fixed. Origin: the first-show sweep read "1 pieces" on a fresh Block
Studio canvas, and a census found the same shape seven times.

**No external text renders as markup** (v2.86.0). Swing paints any
component text that BEGINS with `<html>` as HTML, so a directory name,
a git branch, a cloned ABI or script name, or a drop-in catalog string
that starts with `<html><img src=…>` would make the IDE's own JVM fetch
a URL at paint time (the v1.208.0 fetch class). The whole sink family is
closed: labels, buttons, menu items, tooltips, table/tree/list and combo
cell renderers, option-pane String messages, and the status line. The
two homes are `core.util.PlainText` (`plain` prepends a space when the
head reads as markup — a superset of Swing's trigger, so it never
misses; `escape` entities `& < > " '`) and `core.util.PlainTables`
(`plain` sets `html.disable` on a *renderer*, whose text is set per
paint). The trap the live walk exposed: `PlainTables.plain(new
JLabel(text))` is too LATE — `BasicHTML` installs the view when the text
is set, so a plain label/button guards its TEXT instead
(`new JLabel(PlainText.plain(x))`), order-independent; the component
property is right only for a renderer. A tooltip is special — Swing
builds a fresh `JToolTip` per hover and never reads the property on the
component carrying the text, so its text is always guarded. Authored
markup that splices external text escapes each piece. Five failing-first
gates (`PlainMessageGateTest`, `PlainStatusGateTest`, `PlainLabelGateTest`,
`PlainButtonGateTest`, `PlainTooltipGateTest`) hold it. Origin: a project
opened from a directory named `<html>PWNED` rendered its name as markup
in the Project Studio header and the recent-projects row.

## 5. Where to go next

- **Every important package now carries a `package-info.java`**
  (v2.7.1): a neighborhood map naming what lives there, the RCP
  mechanism it rides, and the reading order. Your IDE shows it when
  you hover the package — start with `core.util`, `core.spi`, and
  `ui.tasks`.
- Add a window / device / grammar / service: recipes in
  [CLAUDE.md §Development Workflow](../../CLAUDE.md).
- The rack's device catalog and what each jack does:
  [devices.md](../devices.md).
- Current priorities and the full law list:
  [plan.md](plan.md).
- The platform's own docs:
  [NetBeans Platform Developer Guide](https://netbeans.apache.org/kb/docs/platform/).
