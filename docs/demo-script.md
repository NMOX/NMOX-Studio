# The five-minute demo

A Developer-Relations script for showing NMOX Studio to a room or on a
call. Seven beats, timed, each with what to click, what the audience
sees, and one line to say. Every menu label, chord and behaviour below
is true of 2.93.0 — it was checked against the [user guide](user-guide.md),
the [Kitchen Sink](kitchen-sink.md) and the [changelog](../CHANGELOG.md)
before it was written down. Chords are macOS: ⌘ command, ⌥ option,
⇧ shift, ⌃ control.

The shape of the demo is the product's own thesis: **the loop that
teaches is the loop that closes**. You run a page, break it, and the
mistake comes back to your editor with an explanation; you wire two
devices and a failing test explains itself; you point an outside agent
at the IDE and it answers from the same records. Nothing in the five
minutes is a mockup.

---

## Before you start

Do all of this before the audience is watching. Two minutes, once.

- [ ] **A project is aimed.** Create it fresh so there is no stale
      state — see *The demo project* below. Its dependencies are
      installed (the wizard's install ran; the status line said so).
- [ ] **It ran once already.** Press ▶ (F6), watch the ⇄ chip light,
      stop it with the ■ (⌥⌘.). This pre-answers the Workspace Trust
      question for a wizard project (it is pre-trusted anyway — you
      just created it) and proves the port is free.
- [ ] **ORACLE has a key and its consent.** In the Task Rack (⌘9),
      mount ORACLE and press **KEY…** on its faceplate (keychain-only),
      or have `ANTHROPIC_API_KEY` in the environment the app was
      launched from. Then press **EXPLAIN** once and answer the consent
      dialog with **Send to ORACLE** — a cable-triggered consult never
      prompts, so beat 4 needs that grant to exist already. Do **not**
      pre-answer the editor's *Ask ORACLE About Selection…* consent —
      each ORACLE flow earns its own, and that dialog is part of beat
      3's story.
- [ ] **View ▸ Presentation Mode** is on. Every open editor +10 pt, the
      in-app Browser's page at 150%, the Output window and every open
      Terminal grown with them — and all of it back exactly when you
      toggle off; nothing is written to your settings.
- [ ] **View ▸ Show Keystrokes** is on. The chord you press appears in a
      pill at the bottom of the window; typed text never does, so a
      password cannot end up on the projector.
- [ ] **The in-app Browser (⌥⌘4) is open** at the served page from the
      dry run, with the **DevTools** toggle already pressed once so the
      Angular pane appears instantly if you reach for it in beat 3.
- [ ] **The Welcome tab is frontmost** — that is beat 1's opening shot.
- [ ] **Wifi:** everything runs on localhost — the dev server, the
      Browser, the rack, the Task Board, the Agent Port. The one
      exception is ORACLE, which is a real call to the Anthropic API:
      beats 3 and 4 need a network. If the room has none, both degrade
      honestly (see *When something goes wrong*) and the rest of the
      demo is untouched.
- [ ] **Have a chat window open** (Slack, a Zoom chat, anything that
      pastes an image) for the close.

## The demo project

**File ▸ New Project…** (⇧⌘N) ▸ **Angular (standalone)**, accept the
defaults. The wizard scaffolds an Angular 21 workspace into `~/NMOX` —
zoneless, signals, the new control flow, the `start` script `ng serve`
— runs the install for you, and the project opens pre-trusted: you just
created it, so no trust prompt stands between you and the first ▶.
`ng serve` prints `Local: http://localhost:4200/` when its build lands;
that printed address is what lights the ⇄ chip and opens the Browser.

Open `src/app/app.html` once before the room is watching. The first
template you open raises a balloon — *Angular (templates) intelligence
unavailable — click to install ngserver into the project* — and that
click is the whole install (project-local, TypeScript 5 pinned, no
terminal). Beat 3 needs it: the squiggle you will show is the Angular
compiler's own, and it only speaks once the language service is there.

The faster door is **New Experiment…** (⇧⌘E — the first entry on the
Welcome tab): a throwaway under `~/.nmox/experiments`, no git, no
recents, pre-trusted, opening on its own `EXPERIMENT.md` walkthrough
with the install already done so the first Run works. Pick the Angular
template there too. Either is fine; the experiment is the better story
if your audience is learners.

For beat 3 the mistake you plant is in the template, not the code:
`src/app/app.html` interpolates `{{ title() }}` near the top — you will
misspell it. A template that names a property the component does not
have is exactly what the Angular Language Service type-checks, and
"Did you mean 'title'?" is a line the room understands.

---

## The beat sheet

| Time | Beat | Click | See | Say |
|---|---|---|---|---|
| 0:00 | 1 · Welcome and First Steps | The Welcome tab | START, FIRST STEPS, TOOLING columns; an empty checklist | "This is an IDE that keeps records — and grades itself on them." |
| 0:30 | 2 · Run it | ▶ (F6) | The ⇄ chip lights; the in-app Browser opens on the page | "One button, a real dev server, and the product knows it's serving." |
| 1:15 | 3 · Break it | Edit the template, ⌘S, **Ask ORACLE About Selection…** | The Angular compiler's own squiggle in your template; ORACLE's answer | "The template is type-checked against the class — and the product asks before it sends a line." |
| 2:15 | 4 · Wire it | Tab, click FAIL, click EXPLAIN, TEST | A cable; a failing test explains itself hands-free | "Failure is a signal. Signals go down cables." |
| 3:15 | 5 · Stand up | ⌥⌘1, **Standup…** | Today's report, written from the board's own records | "One click, and the standup is already written." |
| 3:45 | 6 · Point an agent at it | Tools ▸ **Agent Port (MCP)…**, one curl | Live IDE state as JSON; the ⌁ chip | "Read-only by construction — the build fails otherwise." |
| 4:30 | 7 · The close | Tools ▸ **Copy Editor Screenshot**, paste | A crisp 2x image of the editor tab in the chat | "Everything you saw is in the product. Go press the buttons." |

The rest of this document is the same seven beats, spelled out.

### 0:00 — Beat 1: the Welcome and First Steps

**Click:** nothing yet. The Welcome tab is already frontmost.

**See:** three columns. **START** leads with *New Experiment… ⇧⌘E*,
then *New Project…*, *New Learning Space…*, *Open Folder…*. **FIRST
STEPS** is a checklist that ticks itself from records the product keeps
— a recent project, a run, a server going live, an ORACLE consent, a
learning space — and every row is a door: click it and the thing
happens. **TOOLING** opens each window with its real chord. The footer
carries the version.

**Say:** "Nothing on this page is a tutorial you read. Every row is a
door, and the checklist checks itself from what you actually did."

Point at the FIRST STEPS row "Run something" — it is unticked. It will
tick itself during beat 2, and that is worth a glance back at 1:10.

### 0:30 — Beat 2: ▶ runs the project, the chip lights, the Browser opens

**Click:** ▶ on the toolbar (F6).

**See:** the Output tab streams `ng serve`'s build summary. The moment
it prints `Local: http://localhost:4200/`, the status line grows a
**⇄ serving** chip and the in-app Browser opens on the page — at
`[::1]:4200`, the loopback the CLI actually binds, because the Browser
resolves the printed address the way the server bound it — no copying a
URL, no switching apps.
The ■ right of Debug is now enabled: its tooltip names exactly what it
would stop. Glance at the Welcome: "Run something" and "See a server go
live" have ticked themselves.

**Say:** "The product doesn't guess that it's serving. The server
printed an address, the product heard it, and now every other surface —
the chip, Quick Search, the Browser, the audit devices — knows."

If the audience is technical, hover the ▶ or click the chip once: the
chip's menu offers Open and, for a run the ▶ started, a Stop of its own.
Do not stop it — beat 3 needs the page.

### 1:15 — Beat 3: break the template, the compiler's squiggle, Ask ORACLE

**Click:** in `src/app/app.html`, change `{{ title() }}` to
`{{ titel() }}` and press ⌘S. Then select that line, right-click it,
and choose **Ask ORACLE About Selection…**.

**See:** a squiggle under `titel` before you save — the Angular
Language Service type-checks the template against `App` and reports
the compiler's own message: *Property 'titel' does not exist on type
'App'. Did you mean 'title'?* On save, `ng serve` rebuilds and the
Output tab carries the same error in red. The Ask dialog's consent
names literally what leaves the machine: the selected line, the file
name, the language and your question — never the rest of the file;
the default is **Keep Local** — press **Send to ORACLE**. ORACLE
answers in a conversation window and names the misspelling; a follow-up
in the same window carries the history.

**Say:** "The template is not a string. It is checked against the
class, in the editor, before I save. And when I ask why, the product
tells me first exactly what it is about to send."

Then fix the spelling, ⌘S, and watch the squiggle clear and the Browser
reload on the rebuild. If the room is technical, press the Browser's
**DevTools** toggle and open its **Angular** pane: the live component
tree of the dev build, `App` with its `title` signal, click-to-highlight
on the page — the product reads it through Angular's own debug API.

### 2:15 — Beat 4: the rack — wire a FAIL jack to ORACLE's EXPLAIN

**Click:** the Task Rack tab (⌘9). From the palette, drag **VERITAS**
(the test runner) and **ORACLE** onto the rack if they are not already
mounted. Press **Tab** to flip the rack to its rear. Click VERITAS's
**FAIL** out-jack, then click ORACLE's **EXPLAIN** in-jack — a cable
connects them (click-to-click; dragging works too). Press Tab to flip
back. Hover VERITAS's **TEST** button — the tooltip shows the exact
command it will run — then press it. (Make the project's test fail
first: a wrong `expect` in `src/app/app.spec.ts` is enough, and it
is a fine thing to have planted before the demo.)

**See:** VERITAS runs the suite; its tally LCD turns red — `P:1 F:1`
— and **FAILURES** lists the failing test by name. The FAIL trigger
goes down the cable, and
ORACLE's LCD reads **CONSULTING ORACLE…** and then the verdict — with
no click on ORACLE at all. **VIEW** on ORACLE opens the diagnosis as a
conversation you can follow up in. The cable path never prompts: a
consult by cable rides the consent you granted in the checklist, and
rate-limits itself to one every thirty seconds.

**Say:** "Every device has jacks on the back — triggers, gates, data.
A test failing is just a trigger. I wired it to an explainer, so a
failing test now explains itself, hands-free. The rack has dozens of
devices, you can save this as a patch, export it as a GitHub Actions
workflow, and write your own device as one JSON file."

### 3:15 — Beat 5: the Task Board standup in one click

**Click:** ⌥⌘1. If the board is empty, make two cards and drag one to
Doing (the demo project is fresh; a board you prepared in the
checklist reads better). Press **Standup…**.

**See:** a report in markdown, written from the board's own records —
Yesterday and Today from the cards' done stamps and clocked time,
Blockers from the register, Commits since yesterday from `git log`.
Sections with nothing to say are simply absent. One button copies it
for Slack or a PR; the status line says "Standup copied".

**Say:** "The board lives in a JSON file beside your code, so the team
shares it through git. The standup is not a form you fill in — it is
the records you already made, read back."

### 3:45 — Beat 6: the Agent Port — Tools ▸ Agent Port (MCP)… and one curl

**Click:** Tools ▸ **Agent Port (MCP)…** ▸ **Start**. The dialog shows
the endpoint, a per-start bearer token, and a ready-made client config
— **Copy Config** puts the JSON on the clipboard for any MCP client's
`.mcp.json`. For the room, one curl is the better story. Put the URL
and token in shell variables from the dialog (never on a command line
you would paste anywhere), then:

```bash
curl -s -X POST "$URL" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Accept: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"ide_context","arguments":{}}}'
```

**See:** one JSON answer carrying the aimed project with its toolchain
and package manager, everything serving (your `ng serve`), everything
running with when it started, the file you are editing, the last
failure (the test from beat 4), a diagnostics count — and a line saying
Presentation Mode is on. On the status line, **⌁ agent port :N** for as
long as it listens; the chip's tooltip counts streaming agents and
names when the last request came.

**Say:** "This is the same record every other surface reads, offered to
an agent over MCP. It is read-only by construction: the build fails if
any class in that package so much as names a way to spawn a process,
write a file, or stop a run. A port that can read your IDE is never
invisible — that chip stays lit."

Close the dialog with **Stop Agent Port** when you are done, or leave
it: it dies with the IDE, and the token was never written anywhere.

### 4:30 — Beat 7: the close — Copy Editor Screenshot into the chat

**Click:** click into the editor tab with `src/app/app.html` (the one with
the squiggle story), then Tools ▸ **Copy Editor Screenshot**. Switch to
the chat window and paste.

**See:** a crisp 2x image of the editor tab alone — toolbar, gutter,
code, minimap, no IDE chrome — landing in the chat as a picture. No
file chooser, nothing written to disk (**Save Editor Screenshot…** is
the sibling that does).

**Say:** "Everything you saw is in the shipped product, and this is how
I'll send you the code we just looked at. The install is one line; the
first experiment is one keystroke. Go press the buttons."

Then toggle View ▸ Presentation Mode off and watch every editor, the
Browser page, the Output window and the Terminal come back to exactly
where they were.

---

## When something goes wrong

**No API key, no network, no consent.** ORACLE degrades honestly on its
LCD instead of failing quietly: `NO API KEY — PRESS KEY… TO SET ONE`,
`EXPLAIN NEEDS YOUR OK — PRESS AGAIN`, `OFFLINE — COULD NOT REACH
ORACLE`, `NOTHING TO EXPLAIN — NO FAILED RUN`, and for the cable path
`AUTO-EXPLAIN NEEDS CONSENT — PRESS EXPLAIN ONCE` or `AUTO-EXPLAIN
COOLING DOWN — 30s BETWEEN CONSULTS`. Read the LCD out loud — a refusal
that speaks is one of the house laws, and the room will get it. The
editor's **Ask ORACLE About Selection…** likewise asks for consent
before it opens anything and does nothing on Keep Local. Skip the
answer, keep the beat: the squiggle, the rebuild and the cable all work
without the API.

**The template shows no squiggle.** The Angular Language Service is not
installed in this project yet — open the template and click the
balloon's install link (or Tools ▸ Environment Doctor names `ngserver`
with its install line). It needs TypeScript 5 beside it; the door
installs the pinned pair.

**The port is busy.** `ng serve` pins 4200 and refuses a busy one in
the Output (*Port 4200 is already in use*) instead of moving. Press
the ■ (⌥⌘.) — it stops every command the product started — and ▶
again; if something else owns the port, SONAR in the rack names it,
and `ng serve --port 4300` in the project's `start` script is the
escape hatch. Servers that print a raw `EADDRINUSE` (an `http-server
-p 8080`) get the Output pump's human sentence under it: `↳ Port 8080
is already being used by another program — maybe an earlier run that
is still going. Stop that program and Run again, or change the port.
(Task Rack ▸ SONAR shows who owns every port.)`

**A Workspace Trust prompt appears.** You opened a project you did not
create in this session — a clone, a folder someone handed you. The
dialog names the project directory and says what trusting means: its
tasks (installs, watchers, scripts, compilers) will run on your
machine. The default is **Keep Safe**, and Keep Safe blocks the spawn
with the reason on the status line. Say what it is — "the product
never runs a stranger's code without asking me first" — and press
**Trust Workspace**. Every run, script, language server, formatter and
debugger sits behind that one prompt, so you will not see it twice for
the same project.

**The Browser did not open on Run.** The server has not printed a local
address yet, or it printed one the product did not recognize. Wait for
the ⇄ chip; click it and pick Open. If there is no chip at all, read
the Output tab — a run that could not start says so on the status line
and raises a balloon whose click opens the Environment Doctor.

**Run refused.** "Dependencies are still installing" — the wizard's
install is still going; the status line names the ■. "No node_modules"
— the install was skipped; the status line points at NPM Explorer ▸
Install and the balloon runs it on click.

**The Standup is empty.** Sections with nothing to say are omitted, and
a fresh board with no done stamps, no clock and no commits has nothing
to say. Prepare the board in the checklist: two cards, one dragged to
Done, one clocked in for a minute.

## Longer versions

- **Twenty minutes, every surface:** the [Kitchen Sink](kitchen-sink.md)
  is twenty-five do/see stations across the whole product — the
  studios, the kits, the learning loop, the rack's presets and
  resurrection, and station 25, *Show it to a room*, which is this
  script's presenting family in one sitting.
- **A story, not a tour:** [A Day at Meridian](a-day-at-meridian.md)
  follows one build through every area with screenshots captured live,
  and reads well aloud.
- **The Agent Port in depth:** the [Agent Port tutorial](tutorials/agent-port.md)
  walks every primitive — tools, resources, prompts, the event stream
  and log levels — by hand with curl and with the official SDK.
- **Every feature on one page:** the [visual tour](tour.md).

---

![▶ on an Angular workspace: ng serve's build summary in the Output, the ⇄ chip reading serving, and the page open in the in-app Browser at [::1]:4200](images/angular-serving.png)

![Pick element — the page, the DOM tree and the styles agree on one element](images/story-06-devtools-pick.png)
