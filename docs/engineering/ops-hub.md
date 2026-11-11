# Ops Hub — the DevOps HUD

*Written with 3.11.0, the first vertical slice; brought current by 3.12.0,
the second (the camera, the service doors, rollback, and the review of
the first). Read this before touching
`infra/src/main/java/org/nmox/studio/infra/ops`.*

## The dream, and the rule that keeps it honest

The brief (2026-11-06): the WarGames terminal crossed with a Hollywood
command centre, where **everything on the screen is real** — real servers,
real deployments, real telemetry, real controls — and the extraordinary
feels effortless. Create a server from inside the IDE, watch it
materialise on a living topology, click it to connect over mosh (ssh as
the fallback), deploy the project to it, watch the services appear, keep
watching.

The rule that makes the picture trustworthy: **a mark on the HUD is a
measurement or it is labeled.** Every edge carries its evidence
(`OBSERVED` from a live system, `INFERRED` from facts that usually mean a
relationship, `DECLARED` by someone who wrote it down), drawn solid,
dashed and dotted so the distinction survives colour-blindness; every
host status is a transition the Hub observed, never a timer; a pulse runs
along a link only when something real crossed it; and the simulation —
which exists so the loop can be seen without an account — is an explicit
switch with a banner across the top and a badge on every node.

## What exists already, and what Ops Hub builds on

| Capability | Where | How Ops Hub uses it |
|---|---|---|
| DigitalOcean / Hetzner / Cloudflare tokens in the OS keychain | `infra.api.CloudTokens`, `CloudProvider` | The same token; the Hub's **Token…** and the Infra Designer's **Tokens…** set one value |
| The bearer-token round trip with its leash and bounded read | `infra.api.CloudHttp` (extracted in 3.11.0 from `DigitalOceanClient`) | `DigitalOceanOps` sends through it; a test transport answers from a map |
| The platform's pty Terminal | `dlight.terminal` / `nativeexecution` (friend-only) | `ui.TerminalDoor` walks the platform's own path by reflection — the git chip's idiom — and degrades to the clipboard plus the plain terminal |
| The aimed project | `core.spi.ProjectAim` | What a deploy bundles |
| Hardened process spawn, bounded reads, atomic writes, heavy-dir census | `core.process.ProcessSupport`, `core.util.*` | The ssh runner, the bundle, the known_hosts prune |
| The in-app Browser | `core.spi.EmbeddedBrowser` | **Open in Browser** on a service with a published port |
| Workspace Trust, the spawn ledger, the painted-surface ledger, fifteen languages | the house laws | Every spawn classified, every surface decided, every string in a bundle |

Not reused on purpose: the Infra Designer's graph. It is a *design* —
what you want to exist — and the Hub is a *picture of what exists*. The
roadmap joins them (a designed wire becomes a DECLARED edge); the slice
keeps them apart so neither lies about the other.

## Architecture

```
infra.ops
├── model      Topology · OpsNode · OpsEdge(+Evidence) · HostStatus · Telemetry   pure
├── provider   OpsProvider ─ DigitalOceanOps (Lookup)  ·  SimulatedOps + SimulatedWorld (behind the switch)
├── remote     SshTarget · RemoteRunner ─ ProcessRemoteRunner · HostProbe · ProjectBundle/TarGz · DeployPlan · Deployer
│              ContainerDoors (logs, shell) · Rollback
├── OpsHub     the engine: lanes, the provisioning watch, probing, deploy, rollback, the feed, manual hosts
├── search     OpsSearchProvider (Quick Search over the Hub's own copy for off-thread readers)
└── ui         OpsHubTopComponent · HudCanvas · HudCamera · HudLayout · Motion · InspectorPanel · EventFeedPanel · OpsDialogs · TerminalDoor
```

**Providers make hosts; ssh does everything else.** An `OpsProvider`
lists, creates, reads and destroys hosts and uploads an ssh key. After a
host has an address it is an `SshTarget` like any other — a droplet, a
Hetzner server tomorrow, or a box you already own added by hand — and
`HostProbe`, `Deployer` and the terminal door know no provider. This is
also why the ssh half can be proven against a real sshd on this machine
without a bill.

**One topology, EDT-confined.** Workers observe on named lanes (`Ops Hub
cloud` ×2, `Ops Hub ssh` ×4) and hand every result to the EDT before it
touches a node — the Infra Designer's ledger-53d rule from the first
line. Tests hand the engine an inline EDT, a queue for each lane and a
clock they advance by hand.

**The provisioning watch is observation, not a timer.** REQUESTED (the
provider accepted) → PROVISIONING (it lists it, not active) → BOOTING
(active with an address, ssh silent) → CONFIGURING (ssh answers,
cloud-init running) → READY (cloud-init done). Each step is a fact the
Hub read; the HUD sweeps the ring while any of them is pending.

**The deploy is five remote lines decided before anything runs**
(`DeployPlan`): unpack the bundle from stdin, swap it in beside the
previous source, `docker build` tagged with a stamp, replace the one
container by name with the `nmox.ops.app` label, read it back. The only
values spliced in are a validated slug, two validated ports and a digit
stamp; the bundle leaves `node_modules`, `.git`, build output and every
`.env` at home, skips symlinks, and is refused whole past its caps.

**The HUD** is one painted surface (`HudCanvas`, classified GEOMETRY in
the painted-surface ledger) over a deterministic layout (`HudLayout`:
FLEET fans hosts out from this machine, HOST rings one server's services,
SERVICES stacks a column per host) and critically damped springs
(`Motion`) so a view change is a glide with spatial continuity, not a cut.
The frame timer runs only while a spring is unsettled, a sweep is due or
a pulse is alive; under reduced motion (the user's switch, a system
property, or macOS's own setting read once) every spring snaps.

**The camera** (`HudCamera`, 3.12.0) stands between the layout's world
and the pixels: `screen = world × zoom + pan`. It has two modes. Until
the user moves it, it FITS — every relayout frames the view's content,
and wherever the content already fits it is the identity, so the picture
is exactly the layout's. The first wheel, drag or zoom key makes it
MANUAL, and it then stays where it was put (clamped so the content cannot
be lost off an edge) until Fit is asked for or the view changes. The
Infra Designer's canvas set the conventions and the Hub keeps them: the
wheel zooms about the cursor, a drag on empty space pans, the range is
0.35–2.5, a fit never enlarges. Three rules are worth knowing before
touching it:

- **Positions and radii follow the camera; text never does.** A label is
  the same size at every zoom, and a node too small for its readings
  stops printing them — zooming out is a level of abstraction, not a
  squint.
- **Every reader of a node's position goes through `sx`/`sy`/`sr`**: the
  paint, the hit test, the context-menu key, and the accessible bounds a
  screen reader is pointed at. A reader that takes a glide's raw value is
  in the world while the user is on the screen (`HudCanvasCameraTest`
  holds the hit test and the accessible bounds to it).
- **A pan must not cost a selection.** A press on empty space may become
  a drag, so the deselect waits for the release and happens only if
  nothing was dragged.

## The vertical slice (3.11.0)

Create → watch → connect → deploy → observe, end to end:

1. **New Server…** reads the provider's regions, sizes with prices,
   Ubuntu/Debian images and account keys off the EDT; offers to upload
   this machine's `~/.ssh/*.pub` when the account has none; shows the
   monthly and hourly price; a safe-default confirm names the bill.
2. The host appears at this machine's side and glides into the fan; the
   feed narrates each observed transition; cloud-init installs docker,
   mosh-server and curl.
3. **mosh** / **ssh** buttons (and the context menu) open the IDE's own
   Terminal tab under a pty; the inspector says which of the two is
   recommended and why (both ends have mosh, or which end lacks it). The
   Hub keeps its own `known_hosts`
   under `~/.nmox/ops/` with `accept-new`, so a fresh server is trusted on
   first contact, a changed key is refused, and the user's file is never
   written; a destroyed host's key is pruned so its reused address does
   not refuse the next tenant.
4. **Deploy…** shows the exact remote lines and the port the Dockerfile
   `EXPOSE`s, confirms with Cancel as the default, streams the build into
   the inspector's tracker and the feed, then probes.
5. Every reachable host is probed every 15 s while the window shows:
   load, memory, disk, uptime, `docker ps` → service nodes, `runs`
   (OBSERVED), `depends` (DECLARED from compose's labels), `reaches`
   (INFERRED from a shared user-defined network). A silent host becomes
   UNREACHABLE and says so once.

**Add Host…** reaches any server over ssh (persisted one preference entry
per host, label and optional identity file), which is also how the slice
was validated for real without a droplet: a user-mode `sshd` on this
machine, probed for its real load and memory, connected to over mosh and
ssh from the IDE's Terminal.

**Simulation** (a checkbox): a provider in the documentation address range
203.0.113.0/24 (RFC 5737 — unroutable, never a real server), a scripted
timeline (active after 5 s, ssh after 9 s, cloud-init done after 16 s),
scripted build output, a container that appears in the next probe. Its
gauges are invented and the banner says so.

## The second slice (3.12.0)

What a server that is up needs next, and what the first slice got wrong.

- **The camera** — zoom, pan, fit, and a keyboard selection that is
  brought into view (above).
- **A service's doors.** **Logs** follows `docker logs -f --tail 200` and
  **Shell** runs `docker exec -it … sh` (bash when the image has one),
  each in a Terminal tab over `ssh -t`. The container's name is the one
  string in those lines a HOST chose — it came from `docker ps` — so
  `ContainerDoors` holds it to docker's own alphabet for a name before it
  nears a shell and refuses anything else by name.
- **Rollback.** A deploy tags every build `nmox/<app>:<stamp>` and removes
  none, so the host holds the history. **Roll Back…** asks the host which
  tags it has (`Rollback.imagesCommand`), picks the newest one OLDER than
  what runs, shows the two lines it will run, and on a yes runs the last
  two steps of a deploy with that stamp — nothing uploaded, nothing built,
  the newer image left in place. Offered only on a service that carries
  the `nmox.ops.app` label AND runs an image of ours; the label is the
  host's word and a label that is not a slug is "not ours", never quoted.
  The tracker shows the two steps a rollback has.
- **Quick Search** (⌘I) finds a server by name, address or status and a
  service by name or host, and opens the Hub on it. It reads the Hub's
  own immutable copy (`OpsHub.findable()`), rebuilt on the event thread;
  it never makes a Hub from a keystroke (`OpsHub.peek()`).
- **The inspector's action row is stable.** It was rebuilt on every probe
  of the node it showed — every fifteen seconds a focused button was
  replaced under the user's hand. It is rebuilt only when the set of
  actions or their enablement changes, focus returns to the same action,
  and a button acts on the node shown when it is pressed.

### What the review of 3.11.0 found

A hostile read of the four-day-old code, each finding traced and then
fixed with a test that fails on the code as shipped:

| Found | Now |
|---|---|
| A probe collected every line a host printed for 25 s, every 15 s | 2,000 lines, then refused by name; 200 containers drawn, the rest counted |
| A host that never answered sat BOOTING forever, sweeping, with ssh's reason thrown away | UNREACHABLE on the first failed probe unless a watch is bringing it up; ssh's own last words are the node's problem and the feed's sentence, said once |
| The provisioning watch ended silently when its probe met another in flight (`then` was dropped) — on a provider whose server is active at once, immediately | A second ask WAITS for the first's answer; probes are keyed by node, not id |
| A file NAMED `..\..\x` in a cloned repository was read from two levels above the project on macOS/Linux (`\` was rewritten to `/` everywhere) | Rewritten only where `\` is the separator; every path re-proven inside the project when opened; opened without following a link; exactly the planned size copied |
| A failed destroy turned an unreachable host READY | It is what it was |
| A powered-off server swept as "provisioning" forever | UNREACHABLE, with the provider's word as the reason |
| cloud-init ending in `error` was announced "ready (cloud-init done)" | Announced as what it is, and kept as the node's problem |
| A server deleted elsewhere mid-watch was watched for 15 minutes; an unreadable provider said so every 5 s | Removed and said; said once |
| One page of 100 servers was listed, and whatever a listing does not name is removed | Every page; an account past the cap is refused whole |
| A server destroyed elsewhere kept its key on file, refusing the next tenant of its address | Pruned on listing removal too |
| Each line of a build was its own event-thread task, each rebuilding the tracker | One task per burst |
| The upload's key was sent to the account before the price was confirmed, and waited for ON the event thread | After the yes, on a worker |
| A known_hosts path with a space (a home named "First Last") was split by ssh — measured: the key went to a file named for the path up to the space | Quoted; measured to land in the right file |
| A crash-looping container was "up" (docker lists `Restarting`) | Only `Up …` is up; docker's own status is the reason |
| The two gravest questions printed a literal `\n\n` (a doubled backslash in the message, copied into fourteen translations) | One backslash; `BundleEncodingGateTest` reads every shipped bundle for it |
| Every event with a node pulsed its link, a silence included | Only what the host answered (`OpsEvent.overLink`) |

## What was verified, and how

- 40 unit tests over the pure cores: the ssh argv, the connect rule, the
  probe parser against real `docker ps` JSON (including compose's
  comma-bearing `depends_on` label, which the first parser split), the
  bundle's skips and refusals, the tar against both a Java reader and the
  system's `tar`, the plan's lines, the deployer's order and stops, the
  DigitalOcean parsing and request bodies through a fake transport (the
  token is proven absent from errors), the simulation's timeline, and the
  engine's listing merge, probe-to-services sync, provisioning watch,
  deploy refusals and manual-host persistence — all with a hand-driven
  clock and lanes.
- Walked in the assembled app (see the changelog for the run): the
  simulation end to end, and a real host (the local sshd) probed,
  connected and — where docker was available — deployed to.
- **Not verified live**: a DigitalOcean droplet was not created, because
  that bills David's account; the request body, the listing, the
  provisioning watch and the destroy are proven against a fake API whose
  answers are the API's documented shapes. The first live create is a
  one-click walk once David decides to spend the few cents.

## Roadmap (not done, written down)

1. **Hetzner as the second provider** — `HetznerOps` is a class; the
   dialog, watch and HUD need nothing new.
2. **Designed vs. observed** — the Infra Designer's wires as DECLARED edges
   on the Hub, drift between them highlighted.
3. **Service telemetry** — `docker stats` per container, request pulses
   from a real traffic delta rather than from the Hub's own operations.
4. ~~**Rollback**~~ — done in 3.12.0 (above). Still missing: a rollback
   leaves `src` on the host as the NEWER source, so the folder and the
   running image disagree until the next deploy; and old images are never
   pruned.
5. ~~**Logs**~~ — done in 3.12.0, with a shell beside it.
6. **A chord** — every `⌥⌘` digit is taken; the Hub opens from the Window
   menu, the Welcome and (since 3.12.0) Quick Search until a free family
   is chosen.
7. **Firewall and DNS** — a droplet's cloud firewall and a Cloudflare
   record as nodes with OBSERVED edges to the host.
8. **The bigger dream** — other studios as views over the same ecosystem:
   DB Studio offering a connection to a database the Hub observed, API
   Studio's `{{base_url}}` from a deployed service, the Task Board's
   cards linking to the deploy that shipped them.
