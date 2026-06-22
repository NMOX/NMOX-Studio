package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;
import org.nmox.studio.rack.ui.controls.Knob;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.Led;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;
import org.nmox.studio.rack.ui.controls.ToggleSwitch;

/**
 * HALO Angular Console: first-class Angular operations in one rack
 * unit. SERVE runs the dev server (URL out feeds SCOPE), BUILD and
 * TEST drive the ng builders, the GENERATE row scaffolds schematics,
 * and the version cluster tracks the installed @angular/core against
 * the registry's latest - the OUTDATED LED is the "stay current"
 * nudge, and UPDATE runs `ng update` to chase it.
 */
public class AngularDevice extends CommandDevice {

    private static final String[] SCHEMATICS = {
        "component", "service", "directive", "pipe", "guard", "interceptor", "resolver", "class"};
    private static final java.util.regex.Pattern LOCAL_URL =
            java.util.regex.Pattern.compile("(https?://(?:localhost|127\\.0\\.0\\.1):\\d+[^\\s\"']*)");

    private final LcdDisplay versionLcd;
    private final Led currentLed;
    private final Led outdatedLed;
    private final ToggleSwitch prodSwitch;
    private final Knob schematicKnob;
    private final LcdDisplay nameLcd;
    private final AtomicBoolean readyFired = new AtomicBoolean();
    private volatile String installedVersion;
    private volatile String latestVersion;
    private volatile String announcedUrl;

    public AngularDevice() {
        super("angular", "HALO", "ANGULAR CONSOLE", new Color(0xDD, 0x00, 0x31), 3);

        // ---- row 1: version currency ----
        versionLcd = place(new LcdDisplay(210, 1), 44, 40);
        versionLcd.setText("v? → ?");
        versionLcd.setToolTipText("installed @angular/core → latest on the registry");
        currentLed = place(new Led("CURRENT", RackStyle.GO), 262, 46);
        outdatedLed = place(new Led("OUTDATED", RackStyle.MUTATE), 318, 46);
        RackButton check = place(new RackButton("CHECK", RackStyle.QUERY), 382, 40);
        RackButton update = place(new RackButton("UPDATE", RackStyle.MUTATE), 446, 40);

        // ---- row 2: run/build/test + schematics ----
        RackButton serve = place(new RackButton("SERVE", RackStyle.GO), 44, 96);
        RackButton stop = place(new RackButton("STOP", RackStyle.STOP), 108, 96);
        prodSwitch = place(new ToggleSwitch("BUILD AS", true, "PROD", "DEV"), 174, 88);
        RackButton build = place(new RackButton("BUILD", RackStyle.GO), 240, 96);
        RackButton test = place(new RackButton("TEST", new Color(99, 197, 70)), 304, 96);
        schematicKnob = place(new Knob("SCHEMATIC", SCHEMATICS, 0), 380, 84);
        nameLcd = place(new LcdDisplay(130, 1), 452, 96);
        nameLcd.setText("widget");
        nameLcd.setEditable("Name for ng generate");
        RackButton generate = place(new RackButton("GEN", new Color(0xDD, 0x00, 0x31)), 592, 96);

        check.addActionListener(e -> refreshVersions());
        update.addActionListener(e -> launch(List.of("npx", "ng", "update",
                "@angular/cli", "@angular/core", "--allow-dirty")));
        serve.addActionListener(e -> serve());
        stop.addActionListener(e -> stopProcess());
        build.addActionListener(e -> primaryAction());
        test.addActionListener(e -> launch(List.of("npx", "ng", "test", "--watch=false")));
        generate.addActionListener(e -> {
            String name = nameLcd.getText().trim();
            if (!name.isEmpty()) {
                launch(List.of("npx", "ng", "generate",
                        SCHEMATICS[schematicKnob.getSelectedIndex()], name));
            }
        });

        addInPort("serve", "SERVE", SignalType.TRIGGER);
        addInPort("stop", "STOP", SignalType.TRIGGER);
        addInPort("enable", "ENABLE", SignalType.GATE);
        addOutPort("url", "URL", SignalType.DATA);
        addOutPort("ready", "READY", SignalType.TRIGGER);
        addOutPort("serving", "SERVING", SignalType.GATE);

        param("prod", prodSwitch);
        param("schematic", schematicKnob);
        param("genName", nameLcd);
    }

    /** ng always runs beside package.json, even in mixed monorepos. */
    @Override
    protected ProjectInspector.ProjectKind effectiveKind() {
        return ProjectInspector.ProjectKind.NODE;
    }

    @Override
    protected void onAttached() {
        refreshVersions();
    }

    @Override
    public void projectChanged(File dir) {
        refreshVersions();
    }

    /** Installed version from package.json; latest from the registry, async. */
    private void refreshVersions() {
        installedVersion = AngularVersions.installed(projectDir());
        showVersions();
        if (installedVersion == null) {
            onEdt(() -> statusLcd.setText(ProjectInspector.hasAngular(projectDir())
                    ? "ANGULAR WORKSPACE — DEPS NOT INSTALLED?"
                    : "NO ANGULAR.JSON IN PROJECT"));
            return;
        }
        StringBuilder out = new StringBuilder();
        CommandProbe.run(projectDir(),
                List.of(org.nmox.studio.rack.engine.ToolLocator.resolve("npm"),
                        "view", "@angular/core", "version"),
                out::append, code -> {
                    if (code == 0 && !out.isEmpty()) {
                        latestVersion = out.toString().trim();
                        showVersions();
                    }
                });
    }

    private void showVersions() {
        String installed = installedVersion;
        String latest = latestVersion;
        boolean outdated = AngularVersions.isOutdated(installed, latest);
        boolean majorBehind = AngularVersions.isMajorBehind(installed, latest);
        onEdt(() -> {
            versionLcd.setTextColor(outdated ? RackStyle.LCD_AMBER : RackStyle.LCD_TEXT);
            versionLcd.setText("v" + (installed == null ? "?" : installed)
                    + " → " + (latest == null ? "checking…" : latest)
                    + (latest == null ? "" : outdated ? (majorBehind ? "  MAJOR!" : "") : "  ✓"));
            currentLed.setOn(latest != null && !outdated);
            outdatedLed.setOn(outdated);
            if (outdated) {
                outdatedLed.setBlinking(majorBehind);
            }
        });
    }

    private void serve() {
        readyFired.set(false);
        emit("serving", Signal.gate(true));
        launch(List.of("npx", "ng", "serve"));
    }

    @Override
    protected List<String> buildCommand() {
        List<String> cmd = new ArrayList<>(List.of("npx", "ng", "build"));
        if (!prodSwitch.isOn()) {
            cmd.add("--configuration=development");
        }
        return cmd;
    }

    @Override
    protected void onLine(String line) {
        java.util.regex.Matcher m = LOCAL_URL.matcher(line);
        if (m.find()) {
            String url = m.group(1);
            if (readyFired.compareAndSet(false, true)) {
                emit("ready", Signal.trigger());
            }
            if (!url.equals(announcedUrl)) {
                announcedUrl = url;
                onEdt(() -> statusLcd.setText("SERVING  " + url));
                emit("url", Signal.data(url));
            }
        }
    }

    @Override
    protected void onFinished(int exitCode) {
        emit("serving", Signal.gate(false));
        announcedUrl = null;
        // a finished `ng update` may have bumped package.json
        refreshVersions();
    }

    @Override
    public void receive(Port in, Signal signal) {
        switch (in.getId()) {
            case "serve" -> serve();
            case "stop" -> stopProcess();
            case "enable" -> enableGate(signal.high(), this::serve, this::stopProcess);
            default -> super.receive(in, signal);
        }
    }
}
