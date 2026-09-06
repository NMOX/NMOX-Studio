# Learning Spaces: the community catalog

*New Learning Space…* (⇧⌘L) ships 93 built-in tutorials, and the whole
catalog is data — a JSON file, not code. This page documents that
format so you can add your own spaces (or improve a built-in) by
dropping a file into a directory. No plugin, no rebuild, no restart.

## Where drop-ins live

```
~/.nmox/learn-catalog.d/
```

Every `*.json` file in that directory contributes learning spaces to
the picker. The directory is read when the catalog first loads (opening
the picker, turning the REPL's ENGINE knob, running the Environment
Doctor — never at boot), and re-read whenever a file in it changes, so
editing a drop-in and reopening the picker shows the change without
restarting the IDE.

Ordering is deterministic: **built-ins first, in catalog order, then
drop-in spaces sorted by filename** (then by position within each
file). Prefix your filenames (`10-mine.json`, `20-team.json`) if you
care about the sequence.

## The override rule

A drop-in space whose `slug` matches a built-in **replaces** that
built-in, keeping its position in the picker — this is how the
community improves a shipped tutorial. A new `slug` appends after the
built-ins. If two drop-in files claim the same slug, the later filename
wins.

## The schema

A drop-in file has exactly the built-in catalog's shape: a top-level
`spaces` array. Encoding is UTF-8.

```json
{ "spaces": [ { …space… }, { …space… } ] }
```

Each space:

| Field | Required | Type | Meaning |
|---|---|---|---|
| `slug` | **yes** | string | Unique id, also the directory name under `~/.nmox/learn` and the REPL engine key. Lower-case, no spaces (it is matched literally by the picker's search). Matching a built-in slug overrides it. |
| `name` | **yes** | string | Display name in the picker. |
| `category` | **yes** | string | One of `LANGUAGE`, `STACK`, `FRAMEWORK`, `LIBRARY` (case-insensitive). Groups the picker. |
| `driver` | **yes** | object | How the space runs — see below. |
| `family` | no | string | A grouping label shown next to the category (e.g. `"Lisp"`, `"Systems"`). Defaults to empty. |
| `blurb` | no | string | One picker line: why this space is worth an hour. Defaults to empty. |
| `install` | no | object | Per-OS install commands for the driver's tool: keys `mac`, `linux`, `windows`, values shell commands. Shown by the picker's availability line, the tutorial's Install section, and the REPL's INSTALL button. The `mac` entry doubles as the fallback when the running OS has no entry. |
| `files` | no | array | Sample files to generate: objects with `path` (project-relative) and `content` (the full text). Generation never overwrites an existing file. |
| `tutorial` | no | string | Markdown for the generated `TUTORIAL.md` — the file the space opens on. The OS-appropriate install hint is appended automatically. |

The `driver` object:

| Field | Required | Type | Meaning |
|---|---|---|---|
| `kind` | no | string | `"repl"` (default) or `"run"`. REPL spaces get a rack REPL device pre-seeded with the command; run spaces get a SOLDER command wired to a MONITOR. |
| `command` | **yes** | array of strings | The command tokens. For REPLs, include the force-interactive flag (`python3 -i -q`, `node -i`) — interpreters that buffer piped stdin hang without it. The first token is what the picker probes for on PATH (a token containing `/` or `\` is not probed — it can't exist before generation). |
| `prompt` | for REPLs | string | The interpreter's prompt text (e.g. `">>>"`), shown in the tutorial. |
| `snippets` | no | array of strings | Starter expressions loaded behind the REPL's HINTS button. |

Interpreter availability probing works identically for drop-in spaces:
the picker resolves the driver's first command token with the same
ToolLocator the built-ins use and shows found / not-found plus your
`install` hint.

## A complete worked example

Save this as `~/.nmox/learn-catalog.d/zig.json` — it loads as written
(and the test suite pins it: `LearningCatalogDropInTest` round-trips
this exact space).

```json
{
  "spaces": [
    {
      "slug": "zig",
      "name": "Zig",
      "category": "LANGUAGE",
      "family": "Systems",
      "blurb": "Manual memory without the footguns.",
      "driver": {
        "kind": "run",
        "command": ["zig", "run", "hello.zig"],
        "prompt": "",
        "snippets": []
      },
      "install": {
        "mac": "brew install zig",
        "linux": "sudo apt install zig",
        "windows": "choco install zig"
      },
      "files": [
        { "path": "hello.zig",
          "content": "const std = @import(\"std\");\npub fn main() !void {\n    std.debug.print(\"Hello, Zig!\\n\", .{});\n}\n" }
      ],
      "tutorial": "# Zig\n\nPress GO to run hello.zig."
    }
  ]
}
```

Open *New Learning Space…*, type `zig`, and it's there — with the
availability line probing for `zig` on your PATH.

## Troubleshooting

- **My space doesn't appear.** The file must end in `.json` and sit
  directly in `~/.nmox/learn-catalog.d` (subdirectories are ignored).
  Check the status line and the IDE log: a malformed file is skipped
  with a warning naming it — the picker never blocks on a bad drop-in,
  it just loads everything else.
- **"skipped malformed …" on the status line.** The named file failed
  to parse. The usual causes: a missing required field (`slug`, `name`,
  `category`, `driver`), an invalid `category` value, raw newlines
  inside a JSON string (use `\n`), or a trailing comma. Fix the file
  and reopen the picker — the fix is picked up immediately, and one
  bad file never hides the good ones beside it.
- **My edit isn't showing.** The catalog re-reads a file when its
  modification time or size changes. Any real save does that; if a tool
  of yours preserves mtimes, touch the file.
- **The REPL's ENGINE knob doesn't list my space.** The knob derives
  its engine list from repl-kind catalog entries the first time it's
  used in a session, so a drop-in added mid-session appears on the knob
  after a restart (the picker itself always shows the current merged
  catalog). Run-kind spaces never appear on the knob — that's the
  knob's law, not a drop-in limitation.
- **Overriding a built-in didn't take.** Slugs must match exactly
  (they're case-sensitive). `LearningCatalog` slugs are all lower-case;
  see the picker's search field — it matches slugs literally.

## Checkpoints — Check My Work

A space may carry a `checkpoints` array; **File ▸ Check My Work** runs
them against the learner's space and answers ✓ or ✗ with the entry's
own `hint`. Two kinds, chosen by what the claim needs:

```json
"checkpoints": [
  {"label": "You changed the heading",
   "hint": "index.html still has the sample's exact heading — edit the text between <h1> and </h1>.",
   "file": {"path": "index.html", "contains": "<h1>", "absent": "<h1>Hello, web!</h1>"}},
  {"label": "You added a third list item",
   "hint": "The list needs a third <li>…</li> line — copy an existing one.",
   "file": {"path": "index.html", "contains": "</li>", "atLeast": 3}},
  {"label": "The stylesheet is still linked",
   "hint": "Put the <link rel=\"stylesheet\" href=\"style.css\"> line back.",
   "file": {"path": "index.html", "contains": "style.css"}},
  {"label": "The tests pass",
   "hint": "cargo test names the failing assertion — read it, fix it, run again.",
   "command": ["cargo", "test"], "expect": "test result: ok"}
]
```

- **file** — pure-Java, toolchain-free: the file at a relative `path`
  (inside the space; `..` refuses) must contain the `contains`
  substring — at least `atLeast` times when given — and/or be free of
  the `absent` one. One of `contains`/`absent` is required; `atLeast`
  needs a `contains`.
- **command** — an argv (never a shell) whose tool is a BARE name the
  space already declares (its driver or install hint); it runs in the
  space and must exit 0, with `expect` appearing in its output when
  given. A malformed entry is skipped whole with its reason.

**Write a task so it fails on your own seed.** A checkpoint whose label
starts with "You …" is a task: the learner has to do something before
it passes, so on the untouched sample it must fail — that is what
`absent` (the sample's original text must be gone) and `atLeast` (the
sample already contains the substring twice) are for. A label like
"The stylesheet is still linked" is a guard: it passes on the seed and
exists to say "still true". The built-in seeds are gated exactly this
way; before you share a drop-in, run Check My Work on the untouched
space — every "You …" line should be ✗ with your hint.

## Authoring without writing JSON — the exporter

Since v2.39.3 you don't hand-write any of this: build the exercise as
a normal project, then **File ▸ Export as Learning Space…** turns the
AIMED project into a drop-in — text files gathered (heavy dirs and
binaries excluded, every skip spoken), `TUTORIAL.md` as the
walkthrough, the run command you give as the driver, and an optional
`.nmox-checkpoints.json` (the checkpoint array, exactly this page's
schema) as Check My Work checkpoints. The export is refused whole
unless it parses with the same code your students' picker uses, and a
broken checkpoint refuses at export time with its reasons — you hear
about it, never your class. Re-exporting overwrites only your own
previous export of the same space; anything else refuses.

## Sharing

A drop-in file is self-contained and readable — commit it to a team
repo, hand it around, or PR the best ones against the built-in catalog
(`rack/src/main/resources/org/nmox/studio/rack/projectstudio/learn-catalog.json`).
