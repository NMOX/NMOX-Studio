# Device files — write your own rack device

> **Gallery:** six ready-made devices live in
> [`examples/devices.d/`](../examples/devices.d/) and **ship installed and
> active** — every install's shelf already holds them. Copy one as a
> template for your own. Each is parsed and mounted by the build on
> every commit.


*New in 2.0.0.*

A **device file** is a JSON file in `~/.nmox/devices.d/`. Drop one there
and a real device appears on the rack's shelf: draggable, patchable,
undoable, saved into `.nmoxrack.json`, found by ⌘I, and recorded by the
flight recorder — indistinguishable from the fifty-three built in.

No Java, no build, no restart-with-a-plugin. The rack was the last part
of NMOX Studio you could not extend with a text editor; now it isn't.

## The shortest device that works

```json
{
  "id": "com.example.hello",
  "title": "HELLO",
  "tagline": "greets the project",
  "category": "AUTOMATE",
  "usage": "GO prints a greeting using the project's own node.\nPatch DONE onward to chain another device after it.",
  "buttons": [
    { "label": "GO", "role": "GO", "command": ["node", "-e", "console.log('hello')"] }
  ]
}
```

Save that as `~/.nmox/devices.d/hello.json`, open the rack, and HELLO is
on the shelf under **Automate**.

## A fuller one

```json
{
  "id": "com.example.deploy",
  "title": "SHIPIT",
  "tagline": "builds and uploads the site",
  "accent": "#E0A458",
  "category": "SHIP",
  "units": 2,
  "usage": "GO builds for the dialled TARGET and uploads it; STOP halts a run in flight.\nPatch DONE into PREFLIGHT's check input to gate a release on it.",
  "knobs": [
    { "key": "target", "label": "TARGET", "options": ["staging", "production"] }
  ],
  "ports": [
    { "id": "go", "label": "GO", "direction": "IN", "signal": "TRIGGER" },
    { "id": "done", "label": "DONE", "direction": "OUT", "signal": "TRIGGER" },
    { "id": "out", "label": "OUT", "direction": "OUT", "signal": "DATA" }
  ],
  "lcd": { "label": "STATUS", "widthPx": 420 },
  "buttons": [
    { "label": "GO", "role": "GO", "command": ["npm", "run", "deploy:{{target}}"],
      "emit": "done", "trigger": "go" },
    { "label": "STOP", "role": "STOP" }
  ]
}
```

## Reference

### The device

| Key | Required | Meaning |
|-----|----------|---------|
| `id` | yes | Reverse-DNS, must contain a dot. Un-dotted ids belong to the built-in fleet. This is what a saved patch stores. |
| `title` | yes | The faceplate name, in caps by convention. |
| `tagline` | yes | One line, shown on the shelf. |
| `usage` | yes | The How-to-use card. Two lines or more, over 60 characters: what it does, then a patch recipe. |
| `category` | yes | `AUTOMATE`, `VERIFY`, `SERVE`, `FRAMEWORKS`, `OBSERVE`, `SHIP`, or `UTILITY` — which shelf drawer it sits in. |
| `accent` | no | `#RRGGBB` faceplate accent. Defaults to a neutral green. |
| `units` | no | Faceplate height, 1–3. When absent, the shelf picks the smallest height whose face fits — declare it only to reserve extra room. A declared height too small for the controls refuses at load. |
| `lcd` | no | `{ "label": ..., "widthPx": 60–900 }`. Defaults to `STATUS` at 420. |

### Knobs

```json
{ "key": "target", "label": "TARGET", "options": ["staging", "production"] }
```

The `key` is what `{{key}}` substitutes in a command. The first option is
the default; the dialled position is saved with the patch.

### Ports

```json
{ "id": "done", "label": "DONE", "direction": "OUT", "signal": "TRIGGER" }
```

`direction` is `IN` or `OUT`; `signal` is `TRIGGER`, `DATA`, or `GATE`.
A `GATE` output must be labelled `RUNNING`, `SERVING`, or `ENABLE` —
gate outputs speak one vocabulary across the whole rack.

If you declare an `OUT`/`DATA` port, every output line is emitted on it,
so patching it into MONITOR shows the run with no extra wiring.

### Buttons

```json
{ "label": "GO", "role": "GO", "command": ["npm", "run", "{{target}}"],
  "emit": "done", "trigger": "go" }
```

| Key | Meaning |
|-----|---------|
| `label` | Required. Every control is named — that is also its accessible name. |
| `role` | `GO`, `STOP`, `MUTATE`, or `QUERY`. The role picks the colour; you cannot paint a red GO. |
| `command` | Required except on `STOP`: an **argv array**, not a shell line. |
| `emit` | Optional: an OUT `TRIGGER` port pulsed on exit, carrying pass/fail. |
| `trigger` | Optional: an IN `TRIGGER` port that presses this button by cable. |

A `STOP`-role button stops the running command and must not declare one
of its own.

## What a device file cannot do, and why

The file is judged before it reaches the shelf, and **a file that breaks
any rule is skipped whole** — never half-loaded — with the reason logged
against its filename. The rules exist because a device file names
commands that will really run:

| Refused | Reason |
|---------|--------|
| `"command": ["sh", "a \| b"]`, `;`, `&&`, `` ` ``, `$( )`, newlines | A command is argv, never a shell line. |
| `"command": ["./deploy.sh"]` or `["/usr/local/bin/x"]` | The tool must be a bare name found on PATH, so it is readable before it runs. |
| `"command": ["{{tool}}", …]` | A knob cannot build the tool name — the tool is a literal. |
| `{{nope}}` with no such knob | An unknown variable would run as a literal argument; better to refuse. |
| `"emit": "ghost"` | Emit and trigger must name ports the device declares. |
| A one-line or short `usage` | The shelf law: every device explains itself. |
| An un-dotted `id`, or one already taken | Ids are identity; a patch file resolves devices by them. |

And the laws the **host** keeps, which no file can opt out of:

- **Workspace trust gates every spawn.** The first time a device file's
  command runs in a project, you get the same trust prompt every other
  runner raises. Declining means no process starts.
- Button colour comes from the role, not the file.
- Ports are capped and validated against the same lexicon as built-ins.

## Living with them

- Files are read **lazily** and cached; edit one and the shelf picks it
  up within a second.
- One malformed file never blocks the others, or the built-in fleet —
  look in the IDE log for `device file <name> skipped: <reason>`.
- Device files are **not** exported to CI (`SOLDER` and the CI export
  cover the built-ins; extensions are deliberately out of that scope).

## See also

- [Write your own device](tutorials/your-own-device.md) — the tutorial
- [devices.md](devices.md) — the fifty-three built in
- [device-spi.md](device-spi.md) — the Java SPI, when a file is not enough
  (custom painting, live polling, anything with real state)
