# Tutorial: Project Studio

Project Studio is where projects are born and managed: templates, a
platform-native file tree, a package.json editor, and rack presets — plus
the IDE-native **Run / Build / Test / Clean** that work without you ever
opening a terminal.

<!-- screenshot: Project Studio with the file tree, a package.json editor, and the presets menu -->

## Open it

`⌥⌘6` region / the **Project Studio** surface, or `File ▸ New Project…`.

## Steps

1. **Scaffold a project.** `File ▸ New Project…` → pick a template
   (React, Vue, Vanilla JS, Angular, Elixir/Phoenix, PHP LEMP, and more).
   Choose a location (defaults to `~/NMOX`) and finish. The project opens
   and the rack aims at it.

2. **Browse the tree.** The file tree is a real platform tree — right
   file-type icons, a `[branch]` git annotation on the root, and the full
   Open/Cut/Copy/Delete/Rename/Tools/Properties menu. Heavy folders
   (`node_modules`, `.git`, `dist`) render childless so a huge repo stays
   fast.

3. **Run it — no terminal.** Use the IDE's **Run** (or press the rack's
   IGNITION GO). It resolves your package manager from the project's own
   lockfile/corepack pin and runs the right command; output streams into
   the rack. **Build**, **Test**, and **Clean** work the same way.

4. **Edit package.json.** The built-in editor gives structured editing of
   scripts and dependencies.

5. **Load a preset.** The presets menu wires a ready-made rack for a
   workflow — Uptime Watch, Ship Gate, Modern Web, Monorepo Lanes, Web3
   Bench, and more — so you don't build the patch by hand.

## What you just learned

- New projects are recognized by any of ~50 manifests (package.json,
  Cargo.toml, go.mod, pom.xml, gleam.toml, …) — even a script-tag site
  with no manifest opens as a STATIC project.
- Run/Build/Test/Clean and the rack are **one mechanism**; the first time
  they run project code you'll get a Workspace Trust prompt.

## Next

- Open the [Task Rack](the-task-rack.md) to see what the preset wired.
- Try a [Learning Space](learning-spaces.md) for a guided sandbox.
