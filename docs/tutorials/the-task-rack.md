# Tutorial: The Task Rack

The Task Rack is NMOX Studio's signature idea: your build/test/serve
tooling laid out as a rack of hardware devices you wire together with
patch cables. A device runs a real command; a cable carries a real
signal. This tutorial builds a tiny patch — run something, and light an
indicator when it finishes — so the metaphor clicks.

<!-- screenshot: the Rack tab with a MONITOR device mounted, rear flipped to show jacks -->

## Before you start

Open a project (any Node project works; `File ▸ New Project…` →
"Vanilla JS" if you need one). Opening a project **aims** the rack at it,
so every device runs in that project's directory.

## Steps

1. **Open the rack.** Click the **Rack** tab (or `Window ▸ Rack`). The
   starter rack has a single **MONITOR** — the console device that shows
   command output and error lines.

2. **Add a runner.** Drag **IGNITION** from the palette on the left onto
   the shelf. IGNITION is the polyglot "run" device; aimed at a Node
   project it runs `npm run dev` (it auto-detects your package manager
   and toolchain).

3. **Wire it to the monitor.** Click the **flip** control to see the
   rear, then click IGNITION's **OUT** jack and click MONITOR's **TAP**
   jack — a patch cable connects them. (Dragging between jacks works
   too; clicking is easier when the rack is wide.)

4. **Fire it.** Flip back to the front and press IGNITION's **GO**
   button. It spawns the process; output streams into MONITOR, and the
   status LEDs light. If the project isn't trusted yet, you'll get a
   one-time Workspace Trust prompt first — that's the guard that keeps a
   cloned repo from running its scripts without your say-so.

5. **Save the patch.** `⌘S` (or the Save Patch button) writes
   `.nmoxrack.json` beside your project. Reopen the project later and the
   patch — devices, cables, knob positions — comes back exactly.

## What you just learned

- **Devices are tools with faceplates.** Knobs pick options, GO buttons
  run, LEDs and LCDs report state — and every control is real (no dead
  knobs; a contract test enforces it).
- **Cables coordinate lanes.** OUT→TAP is the simplest wire; readiness
  gates (`ENABLE`), join barriers (`QUORUM`), and trigger cables let you
  compose a whole pipeline that reacts to itself.
- **Everything persists.** The patch is a committable file; the rack even
  resurrects a running session after a crash.

## Next

- There are 51 devices — browse them in [devices.md](../devices.md) or
  the palette's How-to-use cards.
- Ask [ORACLE](oracle.md) to explain a failed run.
- Export a patch to a GitHub Actions workflow: the rack's **CI export**.
