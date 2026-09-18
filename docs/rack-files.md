# Rack files

A rack is not a settings page. It is a development workflow you can see, hand
to someone else, and read before you run: devices that each do one job, and
patch cables that say what happens next. A **rack file** is that workflow as a
small JSON document — portable between machines and people, inspectable in a
text editor, and mounted by NMOX Studio only after you have been shown what is
inside it.

This page is the format, what happens when a file moves between people, and how
to add a rack to the gallery that ships with the product. The racks themselves
are listed in [racks.md](racks.md), which is generated from the files.

## One rack, read top to bottom

This is `rust-save-loop.nmoxrack.json`, one of the racks that ships in the
gallery. The build compares this block with the shipped file byte for byte, so
what you read here is what mounts.

```json
{
  "version": 1,
  "shared": {
    "product": "2.179.0",
    "name": "Rust save loop",
    "description": "Every Rust save checks formatting and runs clippy side by side; cargo test runs only when both pass, and a failed test run goes to KVASIR for an explanation. For a crate you are changing all day.",
    "kinds": ["RUST"],
    "requires": ["cargo"]
  },
  "devices": [
    {"type": "reflex", "state": {"armed": "false", "filter": "0", "glob": "rs"}},
    {"type": "format", "state": {"write": "false"}},
    {"type": "lint", "state": {"fix": "false"}},
    {"type": "join", "state": {"mode": "0"}},
    {"type": "test"},
    {"type": "kvasir"},
    {"type": "console"}
  ],
  "cables": [
    {"fromDevice": 0, "fromPort": "changed", "toDevice": 1, "toPort": "run"},
    {"fromDevice": 0, "fromPort": "changed", "toDevice": 2, "toPort": "run"},
    {"fromDevice": 1, "fromPort": "done", "toDevice": 3, "toPort": "in1"},
    {"fromDevice": 2, "fromPort": "done", "toDevice": 3, "toPort": "in2"},
    {"fromDevice": 3, "fromPort": "ok", "toDevice": 4, "toPort": "run"},
    {"fromDevice": 4, "fromPort": "fail", "toDevice": 5, "toPort": "explain"},
    {"fromDevice": 1, "fromPort": "out", "toDevice": 6, "toPort": "in"},
    {"fromDevice": 2, "fromPort": "out", "toDevice": 6, "toPort": "in"},
    {"fromDevice": 4, "fromPort": "out", "toDevice": 6, "toPort": "in"},
    {"fromDevice": 5, "fromPort": "out", "toDevice": 6, "toPort": "in"}
  ]
}
```

Read as a workflow: REFLEX watches `.rs` files. A save fires GLOSS (format
check) and PURITY (clippy) **at the same time**. QUORUM waits for both and only
lets VERITAS run `cargo test` when both passed. If the tests fail, the failure
goes to KVASIR for an explanation. Everything any of them prints lands on one
MONITOR. That is a shell script's worth of orchestration — parallel steps, a
join, a conditional, an error path — and you can see all of it at once, change
it by moving a cable, and send it to a colleague as a file.

## The format

| Key | What it holds |
| --- | --- |
| `version` | The format number. This install reads format **1**. A file with a higher number is refused by name — its fields cannot be trusted to mean what they meant — and nothing mounts. |
| `shared` | Present only in a file made to travel (Share…, the gallery). Absent in the `.nmoxrack.json` Save Patch writes beside a project. |
| `devices` | The devices, top of the rack first. Each has a `type` (the ids are in [devices.md](devices.md)) and an optional `state`: the settings on its faceplate, all strings. |
| `cables` | Each cable names its two ends by device **position** in `devices` and by jack id. |

### The `shared` header: what a rack says about itself

| Field | Meaning |
| --- | --- |
| `product` | The NMOX Studio version the file was shared from. Import tells you when that is newer than your install. |
| `name` | Up to 60 characters. |
| `description` | Up to 400 characters: what it does and when you would want it. |
| `author` | Optional. Share… leaves it empty unless you type something — a name is never read from your machine. |
| `kinds` | Project kinds the rack is made for (`RUST`, `NODE`, `GO`, …). The gallery lists racks that fit the aimed project first. |
| `requires` | Bare tool names the rack runs (`cargo`, `docker`). The gallery looks each up on your PATH and tells you which are missing. It never runs them. Anything that is not a bare name — a path, a command line — is dropped. |
| `name.<lang>`, `description.<lang>` | Optional translations (`name.de`, `description.fr`). A reader sees their own language where the file has it and the base text where it does not, field by field. |

Every field is optional, and every field is treated as a stranger's text: a
wrong type reads as absent, control characters are folded to spaces (a newline
in a name cannot forge a line of the import page), and long text is clipped.

## When a rack travels

**Leaving.** Share… opens one dialog: you name the rack, say what it does, and
read **what leaves with it** before it goes — every command, path and address
in the rack. Two things are flagged at the top if they are found: a value that
looks like a credential (shown masked), and a path that still names somebody's
home directory. Your own home directory is rewritten to `~` wherever it
appears, including inside a command such as `tail -f /Users/you/logs/app.log`,
because a home path is a username. Then it goes to a file, to the clipboard
(a rack is small enough to paste into a chat or an issue), or into **My Racks**
(`~/.nmox/presets.d`), where it joins your Presets menu and the gallery.

**Arriving.** Import… — from a file, from the clipboard, or by choosing a file
rack in the gallery — shows a page before anything mounts:

- what the rack says it is, and who shared it if they said;
- every device, with the ones this install does not have marked (they mount as
  placeholders that keep their slot, settings and cables);
- every setting that reads like a command, a path or an address;
- **what this install cannot give it**, found by mounting the file into a
  throwaway rack first: cables into jacks that do not exist here and settings
  the devices here do not have, each by name. A rack that silently arrives one
  cable short looks complete and does something else, so you are told;
- whether it was made with a newer NMOX Studio than yours.

Cancel is the default. If you mount it, `~` becomes *your* home, and the rack
arrives **at rest**: every switch that would start work by itself — a REFLEX
watching, a TEMPO ticking, a TAIL following — is off, and nothing runs until
you press it. Every GO is still behind Workspace Trust, as always. The mounted
rack is unsaved work until you press Save Patch.

**Older and newer files.** Jacks are occasionally renamed; a file that names
the old id keeps its cable (the alias table lives in `RackIO`). Knob positions
are saved by index and option lists only ever grow at the end, so an old file's
knobs still point at what they pointed at.

## Contribute a rack to the gallery

The gallery's community racks live in one directory in this repository:

```
rack/src/main/resources/org/nmox/studio/rack/gallery/racks/
```

1. Build the rack in NMOX Studio and press **Share…**, then **Save to File…**. Give it a
   name and a description that says what it does and when you would want it.
2. Put the file in that directory as `<short-name>.nmoxrack.json` and add its
   filename to the `index` file beside it.
3. Run `mvn -pl rack -am test -Dtest=CommunityRacksGateTest`. The gate mounts
   your rack for real and fails by name if a device, a jack or a cable does not
   exist, if anything would start by itself, if a setting holds an absolute or
   home path, or if the card is missing.
4. Regenerate the listing with `-Dnmox.docs.write=true` on `RackGalleryDocsTest`
   and open a pull request.

A community rack may use only built-in devices, keeps its commands relative
(`cargo test`, not `/usr/local/bin/cargo test`), and points any URL at
`localhost`. There is no account, no upload and no server: a rack is a file,
and the gallery is a directory of them that has been reviewed.
