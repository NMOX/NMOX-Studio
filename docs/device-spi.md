# Writing a Rack Device (the Device SPI)

Since v1.55.0, third-party NetBeans modules can contribute devices to the
Task Rack. The contract is deliberately declarative: you describe the
device and its jacks, assemble the control surface from the rack's own
widget vocabulary, and receive callbacks — the Studio hosts your device
and enforces the house laws for you. You cannot ship an unlabeled knob,
a red GO button, or a process that bypasses Workspace Trust, because the
API has no way to express them.

## The contract (`org.nmox.studio.core.spi.device`)

| Type | Role |
|---|---|
| `DeviceExtension` | The provider you register with `@ServiceProvider` |
| `DeviceDescriptor` | Identity: namespaced id, title, shelf category, how-to card, units, ports |
| `PortSpec` | One jack: id, label, IN/OUT, TRIGGER/DATA/GATE |
| `DeviceFace` | The faceplate builder: knob/button/toggle/led/lcd/vu, laid out by the host |
| `DeviceLogic` | Your behavior: `onTrigger`/`onData`/`onGate`/`onProjectChanged`/`onDispose` |
| `DeviceServices` | What the host does for you: trust-gated `exec`, `emit*`, the Serving Registry |

The package is **frozen API** — evolution is additive-only (`default`
methods), and your NBM's generated dependency (`org.nmox.NMOX.Studio.core
> 1.55.0`) means the module loader refuses installs too old for you
rather than failing at call time.

## The laws (enforced by the host, verified at load)

- **Ids are reverse-DNS** (`com.example.uptime`). Un-dotted ids belong to
  the built-in fleet; a collision or missing dot is skipped with a logged
  note. Ids are your saved-patch format — never rename one. If your
  plugin is uninstalled, patches that use it keep an inert `MISSING`
  placeholder (state and cables preserved verbatim) until you return.
- **The shelf law**: `usage` is at least two lines and >60 characters —
  what it does, then a concrete patch recipe.
- **The port lexicon**: an IN named `serve`/`start` requires an IN named
  `stop`; GATE outs are labeled `RUNNING`, `SERVING`, or `ENABLE`.
- **The color law**: buttons declare a role (GO/STOP/MUTATE/QUERY), LEDs
  a tone (OK/FAIL/BUSY/INFO); the host picks the color and tells
  assistive tech its name.
- **The accessibility law**: every control label becomes its accessible
  name; blank labels throw at mount.
- **Workspace Trust** gates every `exec` — no trust, no spawn, exit −1.
- **Fit**: a face that overflows its declared units (of 66px) fails at
  mount naming the control. Knobs and toggles need 2 units.

## A complete device

`pom.xml` (packaging `nbm`; note `-proc:full` — on JDK 21+ the
`@ServiceProvider` registration silently vanishes without it):

```xml
<packaging>nbm</packaging>
<dependencies>
    <dependency>
        <groupId>org.nmox</groupId>
        <artifactId>NMOX-Studio-core</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>org.netbeans.api</groupId>
        <artifactId>org-openide-util-lookup</artifactId>
        <version>RELEASE300</version>
    </dependency>
</dependencies>
<build><plugins>
    <plugin>
        <groupId>org.apache.netbeans.utilities</groupId>
        <artifactId>nbm-maven-plugin</artifactId>
        <version>14.5</version>
        <extensions>true</extensions>
        <configuration><codeNameBase>com.example.uptime</codeNameBase></configuration>
    </plugin>
    <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <configuration><proc>full</proc></configuration>
    </plugin>
    <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-jar-plugin</artifactId>
        <configuration><archive>
            <manifestFile>${project.build.outputDirectory}/META-INF/MANIFEST.MF</manifestFile>
        </archive></configuration>
    </plugin>
</plugins></build>
```

The device — CHECK runs `uptime`, the load line lands on the LCD, OK
fires onward so it chains like any built-in:

```java
@ServiceProvider(service = DeviceExtension.class)
public class UptimeDeviceExtension implements DeviceExtension {

    @Override
    public DeviceDescriptor descriptor() {
        return new DeviceDescriptor(
                "com.example.uptime", "UPTIME", "Host uptime & load on a button",
                new Color(110, 190, 160), DeviceCategory.OBSERVE,
                "CHECK runs uptime and shows the load line on the LCD.\n"
                        + "Patch OK onward to chain, or CHECK from a TEMPO tick for a clock.",
                1,
                List.of(new PortSpec("check", "CHECK", PortSpec.Direction.IN, PortSpec.Signal.TRIGGER),
                        new PortSpec("ok", "OK", PortSpec.Direction.OUT, PortSpec.Signal.TRIGGER)));
    }

    @Override
    public DeviceLogic build(DeviceFace face, DeviceServices services) {
        DeviceFace.LcdHandle screen = face.lcd("STATUS", 420, 1);
        DeviceFace.LedHandle eye = face.led("EYE", DeviceFace.LedTone.INFO);
        DeviceFace.ButtonHandle check = face.button("CHECK", DeviceFace.ButtonRole.QUERY);
        Runnable run = () -> {
            eye.setOn(true);
            services.exec(List.of("uptime"),
                    line -> screen.setText(line.strip()),
                    code -> {
                        eye.setOn(false);
                        services.emitTrigger("ok", code == 0);
                    });
        };
        check.onPress(run);
        return new DeviceLogic() {
            @Override
            public void onTrigger(String portId, boolean ok) {
                run.run();
            }
        };
    }
}
```

`mvn package` produces `target/*.nbm`. Install it via **Tools ▸ Plugins ▸
Downloaded ▸ Add Plugins…** (NBMs are unsigned unless you sign them; the
platform shows its unsigned-plugin warning) — the module turns on live,
and your device appears on the shelf, in Quick Search (⌘I), persists in
patches, and answers the same contract laws as the built-in 45. You can
also host your own update center: **Tools ▸ Plugins ▸ Settings** accepts
any catalog URL.

## Threading, in one paragraph

Control callbacks (`onPress`, `onChange`, `onEdit`) arrive on the EDT.
Signal callbacks (`onTrigger`/`onData`/`onGate`) arrive on the rack's
router thread. `exec` output lines arrive on a pump thread. All handle
mutators (`setText`, `setOn`, …) are safe from any thread — the host
marshals. Never block a callback; long work goes through `exec`.

## Deliberate v1 scope

Extension devices don't export as CI steps, can't paint custom graphics
(open a dialog from a button for rich views — the BLACKBOX pattern), and
don't participate in session resurrection. Ask if you need these; the
contract grows additively.
