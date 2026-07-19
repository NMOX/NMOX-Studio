package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;
import org.nmox.studio.rack.service.ServingRegistry;
import org.nmox.studio.rack.ui.controls.Knob;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.Led;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;
import org.nmox.studio.rack.ui.controls.ToggleSwitch;

/**
 * SPECTER E2E Console: the end-to-end suite as a first-class rack
 * citizen. VERITAS runs unit suites; SPECTER speaks the E2E workflow —
 * RUN the suite (HEADED flips the browser visible), REPORT serves the
 * Playwright HTML report (a real serving: URL/READY out, ⇄ chip, ⌘I),
 * RECORD launches Playwright codegen aimed at the project's live dev
 * server from the ServingRegistry, and BROWSERS installs the runtime
 * browsers. ENGINE=auto resolves playwright vs cypress from the
 * project's own config file, then its dependencies; verbs an engine
 * doesn't have grey honestly instead of running the wrong thing.
 */
public class SpecterDevice extends CommandDevice {

    // append-only: persisted patches store the knob index, not the label
    static final String[] ENGINES = {"auto", "playwright", "cypress"};

    private static final List<String> PLAYWRIGHT_CONFIGS = List.of(
            "playwright.config.ts", "playwright.config.js", "playwright.config.mjs");
    private static final List<String> CYPRESS_CONFIGS = List.of(
            "cypress.config.ts", "cypress.config.js", "cypress.config.mjs");

    private final Knob engineKnob;
    final ToggleSwitch headed;
    private final LcdDisplay versionLcd;
    private final Led currentLed;
    private final Led outdatedLed;
    private final AtomicBoolean readyFired = new AtomicBoolean();
    private volatile String installedVersion;
    private volatile String latestVersion;
    private volatile String announcedUrl;

    public SpecterDevice() {
        // 3 units: the ENGINE knob + HEADED toggle + six buttons need the
        // height the 2U consoles don't (DeviceContractTest's fit law)
        super("e2e", "SPECTER", "E2E CONSOLE", new Color(0x2E, 0xAD, 0x33), 3);

        engineKnob = place(new Knob("ENGINE", ENGINES, 0), 44, 36);
        versionLcd = place(new LcdDisplay(150, 1), 110, 40);
        versionLcd.getAccessibleContext().setAccessibleName("version");
        versionLcd.setText("e2e ? → ?");
        currentLed = place(new Led("CURRENT", RackStyle.GO), 268, 46);
        outdatedLed = place(new Led("OUTDATED", RackStyle.MUTATE), 324, 46);
        RackButton check = place(new RackButton("CHECK", RackStyle.QUERY), 384, 40);

        RackButton run = place(new RackButton("RUN", RackStyle.GO), 44, 130);
        RackButton stop = place(new RackButton("STOP", RackStyle.STOP), 100, 130);
        RackButton report = place(new RackButton("REPORT", new Color(64, 156, 255)), 158, 130);
        RackButton record = place(new RackButton("RECORD", RackStyle.QUERY), 226, 130);
        RackButton browsers = place(new RackButton("BROWSERS", RackStyle.MUTATE), 294, 130);
        headed = place(new ToggleSwitch("HEADED", false), 372, 124);

        check.addActionListener(e -> refreshVersions());
        run.addActionListener(e -> primaryAction());
        stop.addActionListener(e -> stopProcess());
        report.addActionListener(e -> report());
        record.addActionListener(e -> record());
        browsers.addActionListener(e -> browsers());
        engineKnob.addChangeListener(this::refreshVersions);

        addInPort("stop", "STOP", SignalType.TRIGGER);
        addInPort("enable", "ENABLE", SignalType.GATE);
        addOutPort("url", "URL", SignalType.DATA);
        addOutPort("ready", "READY", SignalType.TRIGGER);
        addOutPort("serving", "SERVING", SignalType.GATE);
    }

    @Override
    protected ProjectInspector.ProjectKind effectiveKind() {
        return ProjectInspector.ProjectKind.NODE;
    }

    @Override
    public void receive(org.nmox.studio.rack.model.Port in, Signal signal) {
        switch (in.getId()) {
            case "stop" -> stopProcess();
            // gate semantics: the suite runs while the gate is high and is
            // killed when it drops — wire VELOCITY SERVING → ENABLE and
            // the E2E run dies with the dev server instead of hanging
            case "enable" -> enableGate(signal.high(), this::primaryAction, this::stopProcess);
            default -> super.receive(in, signal);
        }
    }

    // ---- engine resolution (pure, test-pinned) ----

    /**
     * The engine an "auto" knob resolves to for {@code dir}: the
     * project's own config file wins (Playwright checked first), then
     * its declared dependencies; null when the project has no E2E setup
     * at all — the honest-grey case.
     */
    static String resolveEngine(File dir, String knobValue) {
        if (!"auto".equals(knobValue)) {
            return knobValue;
        }
        if (dir == null) {
            return null;
        }
        // config files live beside the NODE manifest — the same kindDir
        // the dependency fallback (and the launched command) uses, so in
        // a monorepo both halves of this method read one directory and
        // "config beats dependencies" cannot invert
        File nodeDir = ProjectInspector.kindDir(dir, ProjectInspector.ProjectKind.NODE);
        for (String name : PLAYWRIGHT_CONFIGS) {
            if (new File(nodeDir, name).isFile()) {
                return "playwright";
            }
        }
        for (String name : CYPRESS_CONFIGS) {
            if (new File(nodeDir, name).isFile()) {
                return "cypress";
            }
        }
        if (ProjectInspector.dependencyVersion(dir, "@playwright/test") != null
                || ProjectInspector.dependencyVersion(dir, "playwright") != null) {
            return "playwright";
        }
        if (ProjectInspector.dependencyVersion(dir, "cypress") != null) {
            return "cypress";
        }
        return null;
    }

    /**
     * The argv for a verb on an engine; null means the engine has no
     * such verb (cypress ships no report server, and {@code cypress
     * open} IS its recorder) — the caller greys with the reason.
     */
    static List<String> command(String engine, String verb, boolean headed, String recordUrl) {
        if (engine == null) {
            return null;
        }
        if ("playwright".equals(engine)) {
            return switch (verb) {
                case "run" -> headed
                        ? List.of("npx", "playwright", "test", "--headed")
                        : List.of("npx", "playwright", "test");
                case "report" -> List.of("npx", "playwright", "show-report", "--host", "127.0.0.1");
                case "record" -> {
                    List<String> cmd = new ArrayList<>(List.of("npx", "playwright", "codegen"));
                    if (recordUrl != null) {
                        cmd.add(recordUrl);
                    }
                    yield List.copyOf(cmd);
                }
                case "browsers" -> List.of("npx", "playwright", "install");
                default -> null;
            };
        }
        return switch (verb) {
            case "run" -> headed
                    ? List.of("npx", "cypress", "open")
                    : List.of("npx", "cypress", "run");
            case "browsers" -> List.of("npx", "cypress", "install");
            default -> null; // report/record: no such verb on cypress
        };
    }

    /** The dependency whose version the currency cluster tracks. */
    static String versionPackage(String engine) {
        return "cypress".equals(engine) ? "cypress" : "@playwright/test";
    }

    private String engine() {
        return resolveEngine(projectDir(), engineKnob.getSelectedOption());
    }

    private boolean greyIfNoEngine(String engine) {
        if (engine == null) {
            onEdt(() -> statusLcd.setText("NO E2E CONFIG — ADD PLAYWRIGHT OR CYPRESS"));
            return true;
        }
        return false;
    }

    // ---- verbs ----

    @Override
    protected List<String> buildCommand() {
        String engine = engine();
        if (greyIfNoEngine(engine)) {
            return null;
        }
        return command(engine, "run", headed.isOn(), null);
    }

    /**
     * CI export is always headless: the HEADED toggle is a local
     * convenience, and a leaked {@code --headed} (or {@code cypress
     * open}) aborts on X-less runners (the v1.89.0 review's HIGH).
     */
    @Override
    protected List<String> ciCommand() {
        String engine = resolveEngine(projectDir(), engineKnob.getSelectedOption());
        return engine == null ? null : command(engine, "run", false, null);
    }

    private void report() {
        String engine = engine();
        if (greyIfNoEngine(engine)) {
            return;
        }
        List<String> cmd = command(engine, "report", false, null);
        if (cmd == null) {
            onEdt(() -> statusLcd.setText("NO REPORT SERVER FOR CYPRESS"));
            return;
        }
        readyFired.set(false);
        emit("serving", Signal.gate(true));
        launch(cmd);
    }

    private void record() {
        String engine = engine();
        if (greyIfNoEngine(engine)) {
            return;
        }
        List<String> cmd = command(engine, "record", false, liveServingUrl());
        if (cmd == null) {
            onEdt(() -> statusLcd.setText("USE HEADED — CYPRESS OPEN IS ITS RECORDER"));
            return;
        }
        launch(cmd);
    }

    private void browsers() {
        String engine = engine();
        if (greyIfNoEngine(engine)) {
            return;
        }
        launch(command(engine, "browsers", false, null));
    }

    /**
     * The project's live dev-server URL from the ServingRegistry — the
     * VITALS/BEACON auto-target idea: RECORD aims codegen at what the
     * rack is actually serving; absent a serving, codegen opens blank.
     */
    String liveServingUrl() {
        File dir = projectDir();
        for (ServingRegistry.Serving s : ServingRegistry.getDefault().snapshot()) {
            // never our own entry: with REPORT serving, RECORD would aim
            // codegen at the static HTML report page instead of the app
            if (s.deviceId().equals(servingId())) {
                continue;
            }
            if (s.kind() == ServingRegistry.Kind.WEB
                    && (dir == null || dir.equals(s.projectDir()))) {
                return s.url();
            }
        }
        return null;
    }

    // ---- version currency (the console-family cluster) ----

    @Override
    protected void onAttached() {
        refreshVersions();
    }

    @Override
    public void projectChanged(File dir) {
        refreshVersions();
    }

    private void refreshVersions() {
        String engine = engine();
        if (engine == null) {
            installedVersion = null;
            latestVersion = null;
            onEdt(() -> {
                versionLcd.setText("no e2e engine");
                currentLed.setOn(false);
                outdatedLed.setOn(false);
            });
            return;
        }
        String pkg = versionPackage(engine);
        String raw = ProjectInspector.dependencyVersion(projectDir(), pkg);
        if (raw == null && "playwright".equals(engine)) {
            raw = ProjectInspector.dependencyVersion(projectDir(), "playwright");
        }
        installedVersion = raw == null ? null : AngularVersions.clean(raw);
        showVersions(engine);
        if (installedVersion == null) {
            onEdt(() -> statusLcd.setText("NO " + pkg + " IN package.json"));
            return;
        }
        StringBuilder out = new StringBuilder();
        CommandProbe.run(projectDir(),
                List.of(org.nmox.studio.core.process.ToolLocator.resolve("npm"),
                        "view", pkg, "version"),
                out::append, code -> {
                    if (code == 0 && !out.isEmpty()) {
                        latestVersion = out.toString().trim();
                        showVersions(engine);
                    }
                });
    }

    private void showVersions(String engine) {
        String installed = installedVersion;
        String latest = latestVersion;
        boolean outdated = AngularVersions.isOutdated(installed, latest);
        boolean majorBehind = AngularVersions.isMajorBehind(installed, latest);
        onEdt(() -> {
            versionLcd.setTextColor(outdated ? RackStyle.LCD_AMBER : RackStyle.LCD_TEXT);
            versionLcd.setText(engine + " " + (installed == null ? "?" : installed)
                    + " → " + (latest == null ? "checking…" : latest)
                    + (latest == null ? "" : outdated ? (majorBehind ? "  MAJOR!" : "") : "  ✓"));
            currentLed.setOn(latest != null && !outdated);
            outdatedLed.setOn(outdated);
            if (outdated) {
                outdatedLed.setBlinking(majorBehind);
            }
        });
    }

    // ---- serving truth (the REPORT server is a real serving) ----

    @Override
    protected void onLine(String line) {
        String url = ServeUrls.firstLocalUrl(line);
        if (url != null) {
            if (readyFired.compareAndSet(false, true)) {
                emit("ready", Signal.trigger());
            }
            if (!url.equals(announcedUrl)) {
                announcedUrl = url;
                onEdt(() -> statusLcd.setText("REPORT  " + url));
                emit("url", Signal.data(url));
                registerServing(url, ServingRegistry.Kind.WEB);
            }
        }
    }

    @Override
    protected void onFinished(int exitCode) {
        // report server (or codegen) stopped: drop the registry entry and
        // the SERVING gate; clear announcedUrl so a restart re-announces.
        // readyFired resets here too — RUN can also serve (Playwright's
        // report-on-failure), and a latched one-shot would let a later
        // serving emit url without ever emitting ready (the v1.89.0
        // review's lifecycle find)
        deregisterServing();
        emit("serving", Signal.gate(false));
        announcedUrl = null;
        readyFired.set(false);
    }
}
