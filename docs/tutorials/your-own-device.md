# Tutorial: write your own rack device

*One sitting. You'll add a device to the rack with a text editor, press
its button, watch it run a real command, and wire its output into
MONITOR — without writing a line of Java.*

New in 2.0.0. The rack shipped with fifty-three devices and, until now,
one way to add a fifty-fourth: author a NetBeans plugin. This is the
other way.

![The Task Rack: the device shelf on the left is where a device from ~/.nmox/devices.d appears, beside the built-in ones](../images/tabs/the-task-rack.png)

## 1. Make the folder

```bash
mkdir -p ~/.nmox/devices.d
```

That is the whole install step. The rack reads the folder lazily, so
there is nothing to restart.

## 2. Write the device

Put this in `~/.nmox/devices.d/counter.json`:

```json
{
  "id": "com.example.counter",
  "title": "COUNTER",
  "tagline": "counts the files in the project",
  "accent": "#7FB3D5",
  "category": "OBSERVE",
  "usage": "COUNT lists the project's files of the dialled KIND and shows how many.\nPatch OUT into MONITOR to read the list, or DONE onward to chain.",
  "knobs": [
    { "key": "kind", "label": "KIND", "options": ["js", "ts", "css", "md"] }
  ],
  "ports": [
    { "id": "count", "label": "COUNT", "direction": "IN", "signal": "TRIGGER" },
    { "id": "done", "label": "DONE", "direction": "OUT", "signal": "TRIGGER" },
    { "id": "out", "label": "OUT", "direction": "OUT", "signal": "DATA" }
  ],
  "buttons": [
    { "label": "COUNT", "role": "QUERY",
      "command": ["git", "ls-files", "*.{{kind}}"],
      "emit": "done", "trigger": "count" }
  ]
}
```

Everything in it is doing a job: the **knob** becomes `{{kind}}` in the
command, the **QUERY** role paints the button blue (the colour law: blue
asks, green does, red stops), and the two ports make it patchable.

## 3. Mount it

Open the **Rack** (⌥⌘6 family / the Rack tab) and look in the shelf's
**Observe** drawer. COUNTER is there, with your tagline under it. Drag
it onto a rail.

Hover its How-to-use card — that is your `usage` text, which is why the
format insists on two real lines.

## 4. Press it

> Notice there is no `units` line: the shelf measures the face and
> picks the smallest height that fits (this one needs 2U for the
> knob). Declare `units` only when you want extra room.

Aim the rack at a git project, dial **KIND** to `js`, and press
**COUNT**.

The first press raises the **Workspace Trust** prompt, because a device
file runs real commands and the host gates every spawn the same way it
gates a built-in. Grant it, and the LCD shows the command, then the last
line of output. The DONE jack pulses green.

Decline it instead and nothing spawns — the refusal is the feature.

## 5. Wire it up

Drag a cable from COUNTER's **OUT** to MONITOR's **TAP**. Press COUNT
again: every line lands on the monitor, because a declared `OUT`/`DATA`
port receives the run's output with no extra configuration.

Now drag from TEMPO's tick into COUNTER's **COUNT** input. The device
you wrote in a text editor is now on a clock.

## 6. Break it on purpose

Edit the file and change the command to something with a pipe:

```json
"command": ["sh", "-c", "git ls-files | wc -l"]
```

Save, and COUNTER *disappears* from the shelf. That is the format
refusing a shell line: a command is an argv array, so that a reader —
you, six months later, or a colleague reviewing the file — can see
exactly what will run. The IDE log says which file was skipped and why:

```
device file counter.json skipped: button "COUNT" command token
"git ls-files | wc -l" contains "|" — commands are argv, never a shell line
```

Put the array form back and it returns. The same is true of a tool named
by path (`./x.sh`), an unknown `{{variable}}`, or a one-line `usage`:
the file is skipped whole rather than half-loaded, because a device
whose label lies is worse than no device.

## What you just learned

- A device is a **file**: `~/.nmox/devices.d/*.json`, read lazily, no
  restart, no build.
- Knobs become `{{variables}}`; roles pick colours; ports make it
  patchable and its output readable.
- The **host keeps the laws** — workspace trust on every spawn, the
  colour law, the port lexicon, the shelf law — so a device file cannot
  express an ungated command or a red GO even if it tries.
- Refusals are loud in the log and total in effect.

## Next

- [device-files.md](../device-files.md) — the full reference
- [The Task Rack](the-task-rack.md) — patching, gates, and presets
- [device-spi.md](../device-spi.md) — the Java SPI, for devices that need
  real state: custom painting, polling, long-lived connections
