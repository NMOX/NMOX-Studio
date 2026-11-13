# Ops Hub — the DevOps HUD

*Written with 3.11.0, the first vertical slice; brought current by 3.12.0,
the second (the camera, the service doors, rollback, and the review of
the first), and by the second provider (Hetzner Cloud, below). Read this
before touching
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
| The bearer-token round trip with its leash and bounded read | `infra.api.CloudHttp` (extracted in 3.11.0 from `DigitalOceanClient`) | `DigitalOceanOps` and `HetznerOps` send through it; a test transport answers from a map |
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
├── provider   OpsProvider ─ DigitalOceanOps · HetznerOps (Lookup)  ·  SimulatedOps + SimulatedWorld (behind the switch)
├── remote     SshTarget · RemoteRunner ─ ProcessRemoteRunner · HostProbe · ProjectBundle/TarGz · DeployPlan · Deployer
│              ContainerDoors (logs, shell) · Rollback
├── OpsHub     the engine: lanes, the provisioning watch, probing, deploy, rollback, the feed, manual hosts
├── search     OpsSearchProvider (Quick Search over the Hub's own copy for off-thread readers)
└── ui         OpsHubTopComponent · HudCanvas · HudCamera · HudLayout · Motion · InspectorPanel · EventFeedPanel · OpsDialogs · TerminalDoor
```

**Providers make hosts; ssh does everything else.** An `OpsProvider`
lists, creates, reads and destroys hosts and uploads an ssh key. After a
host has an address it is an `SshTarget` like any other — a droplet, a
Hetzner server, or a box you already own added by hand — and
`HostProbe`, `Deployer` and the terminal door know no provider. This is
also why the ssh half can be proven against a real sshd on this machine
without a bill.

**One topology, EDT-confined.** Workers observe on named lanes (`Ops Hub
cloud` ×2, `Ops Hub ssh` ×4) and hand every result to the EDT before it
touches a node — the Infra Designer's ledger-53d rule from the first
line. Tests hand the engine an inline EDT, a queue for each lane and a
clock they advance by hand.

**The provisioning watch is observation, not a timer.** REQUESTED (the
provider accepted) → PROVISIONING (it lists it, still coming up) → BOOTING
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
  none, so the host holds the history. **Roll back…** asks the host which
  tags it has (`Rollback.imagesCommand`), picks the newest one OLDER than
  what runs, shows the two lines it will run in a left-to-right box of
  their own (a shell line set inside a sentence is reordered by a
  right-to-left window), and on a yes (Enter answers No) runs the last
  two steps of a deploy with that stamp — nothing uploaded, nothing built,
  the newer image left in place. Offered only on a service that carries
  the `nmox.ops.app` label AND runs an image of ours; the label is the
  host's word and a label that is not a slug is "not ours", never quoted.
  The tracker shows the two steps a rollback has.
- **Quick Search** (⌘I) finds a host by name, address or status and a
  service by name or host, and opens the Hub on it. It reads the Hub's
  own immutable copy (`OpsHub.findable()`), rebuilt on the event thread;
  it never makes a Hub from a keystroke (`OpsHub.peek()`).
- **The inspector's action row is stable.** It was rebuilt on every probe
  of the node it showed — every fifteen seconds a focused button was
  replaced under the user's hand. It is rebuilt only when the set of
  actions or their enablement changes, focus returns to the same action,
  and a button acts on the node shown when it is pressed.

- **The upload honours `.dockerignore`, one-sidedly.** `DockerIgnore`
  applies the lines it is certain about and nothing else (no file with
  an exception line; no character classes or escapes; no name holding
  `+ ( ) | { } ^`, which docker's releases turn into a regular
  expression differently), because leaving out less than docker would
  is always safe and leaving out more builds another image. It reads
  the file as moby's `ignorefile.ReadAll` does and not as Java would:
  lines end at a line feed only, a byte-order mark comes off the first
  line only, `#` is a comment, and trimming is Go's `unicode.IsSpace`.
  A review of the class against that reader found each of Java's own
  notions could hide an exception line (a mark or a no-break space
  before the `!`) or split one name into two (a form feed), and either
  one leaves out more than docker would. Two whole files are not
  applied for the same reason: one beside a `Dockerfile.dockerignore`
  (BuildKit reads that file INSTEAD and the classic builder reads this
  one; the host's builder is not known here), and one whose name on
  disk is not exactly `.dockerignore` (a filesystem that ignores case
  answers to `.DockerIgnore`; the host's does not). What it leaves out does not count against the bundle's
  caps. It matches segment by segment with a memo instead of a regular
  expression: a line is a stranger's text. A refused bundle travels as
  `ProjectBundle.Refusal` (kind, figure, where) and is worded by the Hub.
- **The inspector is a column the width of its pane.** `ColumnLayout`
  gives every row its width FIRST and asks its height second, which is
  what a row of buttons that wraps and a sentence that wraps both need;
  a BoxLayout asks first and gets the one-line answer. Nothing in the
  inspector can be behind a horizontal scrollbar, a fact value is cut in
  its middle with the whole on the tooltip, the last problem and the
  evidence of every edge wrap whole, and a refresh neither rebuilds the
  action row nor scrolls the pane.
- **Services stand beside their host** in the fleet view, three to a
  column on its outer side with their names beside them (cut to the
  column at the current zoom). They orbited it before, and a host's
  name, address and readings are written under it.
- **Words are chosen where they are shown.** The engine's fact keys, an
  edge's kind and source, a probe's own three reasons for having no
  answer and the Simulation's name were English built in the engine;
  they are data now (`HostProbe.Trouble`, the fact key, the edge kind)
  or come from the engine's bundle, and the inspector says them.

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

### What walking the second slice found

Three walks of the assembled build, in a throwaway home: the simulation
end to end, then a real host over real ssh (a user-mode sshd on this
machine whose `docker` was a labeled stub that keeps one container's
state in a file — the Hub's ssh, probe parser, deployer and Terminal
door were the real ones). Each row was fixed and walked again.

| Seen | Now |
|---|---|
| **Roll back… was not on screen.** The inspector's action row ran past the pane: on a service the fourth button was behind a horizontal scrollbar and absent from the accessibility tree; on a host, Probe now and Destroy… had been since 3.11.0 | The row wraps; the column is the pane's width |
| The inspector jumped down past its own title on a refresh | Its wrapped labels keep their carets; another node starts at the top, the same node stays where it was scrolled |
| On a real host with two containers the lower one covered the host's address | Services stand beside the host |
| A second host's ring covered the first one's readings when the Terminal took half the window | A fan is used only when no ring reaches into a caption; otherwise the grid |
| `HTTP 000none` after a rollback: curl prints `000` when nothing answers and the verify line appended `none` to it | The line prints curl's own word; the reader takes only a real status |
| The deploy dialog previewed `nmox/project:0`, then built another tag | One stamp for the dialog's life |
| "Billable: create a server?" in the Simulation | Its own question: nothing real is made, nothing is billed |
| The first Logs of a session opened behind the Terminal's own local tab | Brought forward (ledger 141 says how, and that it is a timer) |
| A host never probed: "the host has no mosh-server" | "has not said yet" (`ConnectPlan.SSH_REMOTE_UNKNOWN`) |
| A host forgotten and added again under another name showed the forgotten one's last rollback as its own | A manual host's run goes when the host does |
| The deploy log showed the middle of every line (its caret sat at the end of docker's longest) | The caret goes to the last line's start |
| The translators, reading the new English in five rounds: a refusal untrue in one of its two uses, a pronoun with no antecedent, one action under three verbs, yes/no answering "does it have docker?", the rollback question as one string with shell lines inside it, an ISO instant shown raw, and five kinds of English built in the engine | Reworded, split, typed, or moved to the bundle; each later round translated the result and found the next layer (an edge that read backwards from its far end, an arrow that pointed against a right-to-left line, the verify step's own two sentences) |

## Hetzner Cloud, the second provider

`HetznerOps` is found through Lookup beside `DigitalOceanOps`; a
configured provider is offered by **New Server…** (with two configured,
the existing "Create the server at" question picks one). The token is the
keychain entry the Infra Designer's **Tokens…** keeps for Hetzner
(`CloudProvider.HETZNER`, or `HCLOUD_TOKEN`); it is a PROJECT's token,
so the Hub sees that project's servers.

| The Hub's | Hetzner's (`https://api.hetzner.cloud/v1`) |
|---|---|
| region | a **location** (`GET /locations`): slug `fsn1`, shown as city and country |
| size | a **server type at one location** (`GET /server_types`): one entry per `prices[]` row whose location the type's `locations[]` lists as available and not deprecated — the price differs by location |
| price | `price_monthly.gross` / `price_hourly.gross`, decimal STRINGS, **plus the primary IPv4** at that location (`GET /pricing` → `primary_ips[type=ipv4]`), which Hetzner prices apart from the server; the currency is `pricing.currency` |
| image | a **system image** (`GET /images?type=system&status=available`) of `os_flavor` ubuntu or debian, not deprecated, once per name (the API lists a name once per architecture) |
| ssh key | `GET /ssh_keys`; an upload is `POST /ssh_keys`, labeled `nmox-ops` |
| the `nmox-ops` tag | the **label** `nmox-ops=true` on the server (Hetzner has labels, not tags). As on DigitalOcean it is the fact "made here", never a filter: every server of the project is listed, and one made in Hetzner's console is as real as one made here |
| create | `POST /servers` with `name`, `server_type`, `image`, `location`, `ssh_keys` (ids as integers), `labels`, `user_data` (the same cloud-init), `public_net.enable_ipv4` (ssh needs the address), `start_after_create` |
| list | `GET /servers?page=N&per_page=50`, following `meta.pagination.next_page` until it is null; past 2,000 entries, on a page that points back, or on a full page with no pagination, the listing is refused whole |
| read | `GET /servers/{id}`: the API's 404 is "gone"; anything else (500, 429, a 200 with no server in it, no token) is "unknown" and raised |
| destroy | `DELETE /servers/{id}` |
| REQUESTED → PROVISIONING | `status` `initializing`, `starting` (`HostRecord.Phase.COMING_UP`) |
| → BOOTING and on | `status` `running` with `public_net.ipv4.ip`; from there it is ssh, as for any host |
| UNREACHABLE, with Hetzner's word as the reason | `off`, `stopping`, `deleting`, `migrating`, `rebuilding`, `unknown`, and any word the product was not taught — never guessed to be "coming up", which would sweep forever |
| a refusal | `{"error":{"code","message"}}` said as `HTTP 422: <message> (<code>)` in the feed or the dialog |

What the second provider changed outside its own class — the roadmap had
said nothing would be needed:

- **The engine no longer knows a provider's words.** It compared a
  status with DigitalOcean's `new`; a provider now hands over a
  `HostRecord.Phase` beside its own word, and a not-running status
  reaches the feed when the word changes, once.
- **A price carries its currency.** `ProviderOptions.currency` is the
  ISO 4217 code; the three price sentences take the sign as an argument
  where they printed `$`. A Hetzner price is euros unless Hetzner's
  price list says otherwise, and is never shown under a dollar sign.
- **The chip names every real provider**, each with or without its token.
- **Only a number is spliced into a path**, and an answer to a create is
  never quoted in an error: a create without keys is answered with a
  root password.

**No live server was created, and no request was sent to Hetzner with a
token**: a server bills the account's owner. Every shape above was read
from Hetzner's published OpenAPI document
(`https://docs.hetzner.cloud/cloud.spec.json`, 2026-11-13) and none was
observed; `HetznerOpsTest` answers from a fake API in those shapes and
its javadoc lists which. The first live create is a walk for whoever
owns the token.

## What was verified, and how

- **149 unit tests** in `infra` over the Hub (44 with 3.11.0): the pure
  cores (the ssh argv and its quoting, the connect rule, the probe
  parser against real `docker ps` JSON, its bounds and its typed
  reasons, the bundle's skips, refusals and containment, the tar against
  a Java reader and the system's `tar`, the plan's lines, the deployer's
  order, stops and its reading of curl's status, the rollback's choice
  of image, the container-name rule, the DigitalOcean parsing, paging
  and request bodies through a fake transport, the simulation), the
  engine with a hand-driven clock and lanes (listing merge, the watch
  and each way it ends, probe-to-services sync, deploy and rollback with
  every refusal, the search copy), the camera's arithmetic, the canvas's
  gestures and named key actions, the layout's two caption laws, and the
  inspector laid out at its pane's own width.
- **Mutants by name**: 10 on the camera, 31 on the review's fixes, 31 on
  the walks' and the translators' fixes, 14 on the bundle's ignore file
  and refusals, 17 on the ignore file's reading after its own review;
  every one killed by a named
  test except one written down as equivalent (the column's `tracksViewportWidth`, which its own
  preferred width already implies). Several were killed only after the
  TEST was repaired — the wrap test and the fact-word test first let
  their mutants live (the plan's addendum of 2026-11-10 says why).
- **Walked in the assembled app, three times, in a throwaway home**:
  the simulation end to end (create with its own confirm, two deploys, a
  rollback, the wheel to 250% about the pointer and Fit back), and a
  real host over real ssh — a user-mode sshd on this machine — probed
  (real load and disk), a host that refuses connections added beside it
  ("does not answer ssh: … Connection refused", wrapped in the
  inspector), Logs and Shell opened as Terminal tabs under a real pty,
  Roll back… run to the earlier tag and read back, and a deploy stopped
  at upload in the host's own words (`mkdir: /srv: Read-only file
  system`). Zero SEVERE in the log.
- **What stood in for docker**: no daemon ran on the walking machine, so
  the sshd's `docker` was a stub script, labeled in its own output, that
  keeps one container's state in a file and prints docker's shapes. The
  Hub's ssh, probe parser, deployer and Terminal door were the real
  ones; docker was not. A real daemon is the next walk.
- **Not pressed**: the zoom and pan keys and the Quick Search row (the
  background tools deliver neither; tests fire the canvas's named
  actions and the provider, and the layer entry was read out of the
  built jar).
- **Not verified live**: a Hetzner server was not created or listed (the
  section above says what stands in); a DigitalOcean droplet was not created, because
  that bills David's account; the request body, the paged listing, the
  provisioning watch and the destroy are proven against a fake API whose
  answers are the API's documented shapes. The first live create is a
  one-click walk once David decides to spend the few cents.

## Roadmap (not done, written down)

1. ~~**Hetzner as the second provider**~~ — done (the Hetzner section
   above), and not run against Hetzner. It was a class, and three things
   besides: the engine's status word, the currency sign, the chip.
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
