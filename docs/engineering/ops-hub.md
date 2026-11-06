# Ops Hub — the DevOps HUD

*Written with 3.11.0, the first vertical slice. Read this before touching
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
├── OpsHub     the engine: lanes, the provisioning watch, probing, deploy, the feed, manual hosts
└── ui         OpsHubTopComponent · HudCanvas · HudLayout · Motion · InspectorPanel · EventFeedPanel · OpsDialogs · TerminalDoor
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
   Terminal tab under a pty; a plain click chooses mosh when both ends
   have it and says why otherwise. The Hub keeps its own `known_hosts`
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
4. **Rollback** — the previous image and `src.prev` are kept on the host
   already; a button that runs the previous tag is the missing door.
5. **Logs** — `docker logs -f` into an Output tab from a service's menu.
6. **A chord** — every `⌥⌘` digit is taken; the Hub opens from the Window
   menu, the Welcome and Quick Search until a free family is chosen.
7. **Firewall and DNS** — a droplet's cloud firewall and a Cloudflare
   record as nodes with OBSERVED edges to the host.
8. **The bigger dream** — other studios as views over the same ecosystem:
   DB Studio offering a connection to a database the Hub observed, API
   Studio's `{{base_url}}` from a deployed service, the Task Board's
   cards linking to the deploy that shipped them.
