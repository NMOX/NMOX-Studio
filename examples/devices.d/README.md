# Example rack devices

Six ready-made devices for the v2.0.0 programmable rack — and they
**ship installed and active**: the rack module bundles these exact
files at build time, so every install's shelf already holds all six.
Open the Task Rack and search for any of them — nothing to copy,
nothing to enable.

These files are also the perfect starting points for your own devices:
copy one into `~/.nmox/devices.d/`, change its `id`, and edit away —
the shelf picks up your version within a second, no restart. (A user
file that keeps a bundled id is skipped: the shipped gallery wins its
own ids.)

Every file here is a **build fixture, not prose**: `ExampleDevicesGateTest`
feeds each one to the real parser and mounts its face through the real
load path on every build, so an example that stops working fails CI.

| Device | Category | What it shows off |
|--------|----------|-------------------|
| **AUDITOR** | Build & Verify | `npm audit` with a severity knob; the FAIL lamp *is* the verdict (npm exits nonzero on findings). QUERY + MUTATE roles, DONE chaining. |
| **CHRONICLE** | Observe | Three git views on one plate — log span knob, porcelain status, author counts. Multiple buttons sharing one LCD. |
| **QUARTERMASTER** | Utility | One version button per tool. Deliberately *not* a TOOL knob: the format refuses a knob-built tool name, so the command can always be read before it runs. |
| **WATCHTOWER** | Serve & Expose | A true long-runner (`python3 -m http.server`) with a real STOP button — and the transport law on display: a `start` IN is refused unless a `stop` IN sits beside it. |
| **PROBE** | Observe | `curl` status probe with a port knob; UP pulses only on success, so a test lane can fire the moment the server answers. |
| **GROUNDSKEEPER** | Run & Automate | `git fsck` → `git gc` — patch DONE into GC's trigger and the tidy-up runs only after a clean check. The GC button wears the mutation colour by law. |

Wiring ideas: WATCHTOWER `START` ← a build device's `DONE`;
PROBE `UP` → VERITAS's test trigger; AUDITOR `DONE` → PREFLIGHT.
Every command runs behind the same Workspace Trust prompt as the
built-in fleet, and every `OUT` jack feeds MONITOR.

The full format reference is [docs/device-files.md](../../docs/device-files.md);
the from-scratch walkthrough is
[docs/tutorials/your-own-device.md](../../docs/tutorials/your-own-device.md).
