# The Agent Port's execution surface — a design dossier (not a decision)

*Written 2026-09-05 at the close of the v2.84.0 shift, as the recorded
next arc of futures bet F4. It designs; it builds nothing. The build
is David's call, because it changes what a token holder can DO.*

## What exists (recon, with file:line)

- **The read-only law is structural.** `McpReadOnlyLedgerTest` fails the
  build if any class under `rack/mcp` names a spawn, write, trust or
  stop primitive (`CommandExecutor`, `ProcessSupport`, `WorkspaceTrust`,
  `requestTrust`, `LiveRuns.stop`, `Files.write`, …). The port cannot
  grow teeth by accident; an execution surface is a DELIBERATE
  amendment of that list, one name at a time, each with its reason.
- **Inward execution is gated by `WorkspaceTrust`** (rack/service):
  `isTrusted(dir)` silent, `requestTrust(dir)` prompts once per
  project (Keep Safe default). Every spawn site is classified by
  `SpawnSiteTrustLedgerTest`. The gate answers "may THIS PROJECT's code
  run?" — it says nothing about WHO asked.
- **Outward data flows have their own consent** (`OracleConsent`,
  rack/service): `requestKindConsent(kind, what)` — a per-KIND grant,
  a dialog that states exactly what leaves, the safe default. The
  v1.171.0 law: a flow that is not the failure flow needs its own
  grant; a grant never widens.
- **Every run is a `LiveRuns.Run`** (core/spi): `add` before the
  spawn, `stop(id)` kills the tree, `wasStoppedByUser`; the ■ is total
  (ledger 87). The IDE's own lanes are `WebProjectActionProvider`'s
  Run/Build/Test/Clean, which already route trust → InstallGuard →
  `CommandExecutor.run` → `LiveRuns.add` → `BuildExecutionSupport`.
- **The port already tells an agent everything a verb would need:**
  `project_state` (kind, package manager), `live_runs` (ids),
  `run_history` (verdicts incl. `stopped`), the push stream and the
  log messages for the run's own output.

## The claim

An agent that can read what is running will ask to run and to stop.
The competitive lens will say so within a release of the read-only
port shipping widely. The house answer cannot be "hand it
WorkspaceTrust": that gate covers the PROJECT's code, and the caller
here is not the user at the keyboard — the v1.103.0 inward law and
the v1.171.0 consent-kind law both say a new caller needs its own
consent model.

## The design

**Verbs, not commands.** The surface exposes the IDE's OWN lanes and
nothing else: `run_lane {lane: run|build|test|clean}` and
`stop_run {id}`. No argv. No shell. No arbitrary script names — an
agent that wants `npm run lint` asks the user to add it as a lane
(Project Configuration) or does not get it. The lanes are the four
the toolbar already offers, so the port can never do what the user
could not do with one click.

**A per-session grant the user arms at the keyboard.** The Agent Port
dialog grows a row: *Allow this agent to* ☐ run lanes ☐ stop runs —
both OFF at every start, never persisted, dying with the port. Arming
is a click in the IDE; nothing over the wire can arm it (no MCP
method touches the grant). This is the consent-kind law applied to a
caller: the grant names the kinds (run, stop) and the dialog states
the whole truth ("an agent holding the token may start this project's
Run/Build/Test/Clean lanes and stop runs it can see; each lane still
passes Workspace Trust; every start and stop is announced on the
status line and recorded in the flight recorder as `agent`").

**Tools appear only while armed.** `tools/list` includes `run_lane`
only while the run grant is on, `stop_run` only while the stop grant
is on, and the port sends `notifications/tools/list_changed` when the
grant changes — so an agent's picture of the port is always the
user's current decision, and a disarmed grant makes the verb vanish
mid-session rather than start refusing (a refusal tells an agent to
retry; an absent tool tells it to stop asking).

**Every execution keeps every existing law, in order.** A `run_lane`
call: the grant is on → the project is trusted (`requestTrust`
prompts the USER if not — the agent waits on the user's answer, never
answers for them) → InstallGuard → the same `invokeAction` path the
toolbar uses → the run registers in `LiveRuns` with a label that
names its origin (*Run — app (agent)*) → the status line says
"Agent started: Run — app" → the flight recorder's LAUNCH line
carries the origin. A `stop_run`: the grant is on → the id is in
`live_runs` → `LiveRuns.stop(id)` (the exit reads `stopped`, v2.84.0)
→ status line "Agent stopped: Run — app". The read-only ledger keeps
every other primitive banned; the two verbs' classes live in a
sibling package (`rack.mcp.exec`) the ledger walks with an explicit
allowlist of exactly the seams named above, each a one-line entry
with its reason — the ledger stays the law, the allowlist is the
amendment.

**Annotated honestly.** `run_lane` carries `readOnlyHint:false,
destructiveHint:false, idempotentHint:false`; `stop_run` carries
`destructiveHint:true`. A well-behaved client will confirm the
destructive one with its user too — belt and braces, two keyboards.

**Audit.** `run_history` rows gain `origin: user|agent`. BLACKBOX's
timeline paints agent rows in the QUERY blue so a glance at the rack
shows what the agent did. Report a Problem's failure context names
the origin.

## What it must never do

- Run anything the toolbar cannot (no argv, no shell, no script by
  name).
- Answer a Workspace Trust prompt, or arm its own grant, over the wire.
- Persist a grant across ports, sessions, or restarts.
- Stop a run it cannot see in `live_runs`, or any process that is not
  a `LiveRuns` run.
- Grow the read-only ledger's banned list shorter: the exec package is
  a named exception, walked, with an allowlist that fails the build on
  any un-reasoned addition.

## The proof plan

Unit tests: the grant is off at start; a wire call cannot flip it;
`tools/list` omits the verbs while off and includes them while on;
`list_changed` fires on every flip; `run_lane` refuses an untrusted
project by waiting on the user's prompt (seam) and never spawns on
Keep Safe; `stop_run` refuses an unknown id; the ledger allowlist
names every seam. Mutants: grant check removed; trust check removed;
the ledger allowlist widened. The walk: the official client tries
`run_lane` unarmed (absent tool), the user arms it in the dialog, the
agent starts the dev server, `live_runs` shows *(agent)*, the user
presses ■, the agent's `stop_run` on a stale id refuses.

## The decision

Three ways to go, David's call:

1. **Build it as designed** (one shift: the exec package, the dialog
   row, the ledger amendment, the walk).
2. **Build the stop verb only** — the smaller step that answers "the
   agent started nothing, but the server I asked about is the one I
   want gone"; still a grant, still announced.
3. **Stay read-only** and record the lens item as OPEN BY POSITION,
   the way always-on ghost text is (competitive lens R4).

The dossier's own recommendation is 1, because a half-verb surface
teaches agents to work around the port (they will run the CLI
themselves, with no trust gate, no announcement and no record) and
the read-only law's whole point was that everything the agent does
through the IDE is seen.
