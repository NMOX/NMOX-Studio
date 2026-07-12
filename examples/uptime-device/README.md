# Uptime Device — an NMOX Studio rack plugin

A complete, buildable third-party device for the NMOX Studio task rack:
press **CHECK** and it runs `uptime`, shows the load line on its LCD, and
fires **OK** onward so it chains like any built-in device.

This is the worked example from [`docs/device-spi.md`](../../docs/device-spi.md)
— the same plugin that was installed live to validate the Device SPI in
v1.55.0. It is a standalone Maven project, **not** part of the NMOX Studio
reactor: a plugin compiles against the published `core` module and ships as
its own NBM.

## Build

```bash
# once, from the repo root — publish the core module (the SPI) to ~/.m2
mvn -q -pl core -am install -DskipTests

# then, here
cd examples/uptime-device
mvn -q package
# -> target/uptime-device-1.0.0.nbm
```

## Install

**Tools ▸ Plugins ▸ Downloaded ▸ Add Plugins…**, pick the `.nbm`, and turn
it on — no restart. `UPTIME` appears on the OBSERVE shelf, mounts into the
rack, and its CHECK press gates on Workspace Trust before it runs. NBMs are
unsigned unless you sign them, so the platform shows its unsigned-plugin
dialog; that is expected.

## What to look at

`UptimeDeviceExtension.java` is ~40 lines and shows the whole contract:

- `@ServiceProvider(service = DeviceExtension.class)` — how the rack finds it.
- `descriptor()` — the identity card: a **reverse-DNS id** (`com.example.uptime`),
  shelf category, the how-to text, units, and typed ports.
- `build(face, services)` — assembles the faceplate from the rack's own
  widgets (an LCD, an INFO led, a QUERY button) and wires the button to
  `services.exec(...)`, which is trust-gated and streams output for you.

The host enforces every house law: you can't ship an unlabelled control, a
red GO button, or a process that skips the trust prompt, because the API
has no way to express them. See [`docs/device-spi.md`](../../docs/device-spi.md)
for the laws, the threading contract, and the additive-evolution guarantee.
