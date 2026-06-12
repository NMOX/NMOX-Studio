package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;
import org.nmox.studio.rack.ui.controls.Knob;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.Led;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;

/**
 * PHOENIX Framework Console: Elixir's web framework as a rack unit.
 * SERVER runs mix phx.server (URL out feeds SCOPE), the GEN row drives
 * mix phx.gen.* schematics, the ECTO row migrates and rolls back, and
 * the version cluster compares mix.exs's :phoenix against the newest
 * release on Hex.
 */
public class PhoenixDevice extends CommandDevice {

    private static final String[] GENERATORS = {"html", "live", "json", "context", "schema", "auth"};
    private static final Pattern LOCAL_URL =
            Pattern.compile("(https?://(?:localhost|127\\.0\\.0\\.1):\\d+[^\\s\"']*)");
    /** mix.exs dependency line: {:phoenix, "~> 1.8.1"} */
    private static final Pattern MIX_PHOENIX =
            Pattern.compile("\\{:phoenix\\s*,\\s*\"[~><= ]*([0-9][^\"]*)\"");
    /** hex.info output: "Releases: 1.8.3, 1.8.2, ..." */
    private static final Pattern HEX_RELEASE = Pattern.compile("Releases?:\\s*([0-9][\\w.-]*)");

    private final LcdDisplay versionLcd;
    private final Led currentLed;
    private final Led outdatedLed;
    private final Knob genKnob;
    private final LcdDisplay genArgsLcd;
    private final AtomicBoolean readyFired = new AtomicBoolean();
    private volatile String installedVersion;
    private volatile String latestVersion;
    private volatile String announcedUrl;

    public PhoenixDevice() {
        super("phoenix", "PHOENIX", "FRAMEWORK CONSOLE", new Color(0xFD, 0x4F, 0x00), 3);

        versionLcd = place(new LcdDisplay(200, 1), 44, 40);
        versionLcd.setText("phx ? → ?");
        versionLcd.setToolTipText(":phoenix in mix.exs → latest on Hex");
        currentLed = place(new Led("CURRENT", new Color(80, 235, 100)), 252, 46);
        outdatedLed = place(new Led("OUTDATED", new Color(255, 190, 60)), 308, 46);
        RackButton check = place(new RackButton("CHECK", new Color(70, 170, 235)), 372, 40);
        RackButton migrate = place(new RackButton("MIGRATE", new Color(99, 197, 70)), 444, 40);
        RackButton rollback = place(new RackButton("ROLLBACK", new Color(255, 190, 60)), 508, 40);

        RackButton server = place(new RackButton("SERVER", new Color(80, 235, 100)), 44, 96);
        RackButton stop = place(new RackButton("STOP", new Color(255, 70, 60)), 108, 96);
        RackButton test = place(new RackButton("TEST", new Color(99, 197, 70)), 172, 96);
        genKnob = place(new Knob("GENERATOR", GENERATORS, 0), 248, 84);
        genArgsLcd = place(new LcdDisplay(220, 1), 322, 96);
        genArgsLcd.setText("Blog Post posts title:string");
        genArgsLcd.setEditable("Arguments for mix phx.gen.*");
        RackButton gen = place(new RackButton("GEN", new Color(0xFD, 0x4F, 0x00)), 552, 96);

        check.addActionListener(e -> refreshVersions());
        migrate.addActionListener(e -> launch(List.of("mix", "ecto.migrate")));
        rollback.addActionListener(e -> launch(List.of("mix", "ecto.rollback")));
        server.addActionListener(e -> server());
        stop.addActionListener(e -> stopProcess());
        test.addActionListener(e -> launch(List.of("mix", "test")));
        gen.addActionListener(e -> {
            String args = genArgsLcd.getText().trim();
            if (!args.isEmpty()) {
                List<String> cmd = new ArrayList<>(List.of("mix",
                        "phx.gen." + GENERATORS[genKnob.getSelectedIndex()]));
                cmd.addAll(List.of(args.split("\\s+")));
                launch(cmd);
            }
        });

        addInPort("serve", "SERVE", SignalType.TRIGGER);
        addInPort("stop", "STOP", SignalType.TRIGGER);
        addOutPort("url", "URL", SignalType.DATA);
        addOutPort("ready", "READY", SignalType.TRIGGER);
        addOutPort("serving", "SERVING", SignalType.GATE);

        param("generator", genKnob);
        param("genArgs", genArgsLcd);
    }

    /** Phoenix lives in the Elixir subproject of mixed repos. */
    @Override
    protected ProjectInspector.ProjectKind effectiveKind() {
        return ProjectInspector.ProjectKind.ELIXIR;
    }

    @Override
    protected void onAttached() {
        refreshVersions();
    }

    @Override
    public void projectChanged(File dir) {
        refreshVersions();
    }

    private void refreshVersions() {
        installedVersion = ProjectInspector.mixDependencyVersion(commandDir(), MIX_PHOENIX);
        showVersions();
        if (installedVersion == null) {
            onEdt(() -> statusLcd.setText("NO :phoenix IN mix.exs — mix phx.new FIRST"));
            return;
        }
        StringBuilder out = new StringBuilder();
        CommandProbe.run(commandDir(),
                List.of(org.nmox.studio.rack.engine.ToolLocator.resolve("mix"), "hex.info", "phoenix"),
                line -> out.append(line).append('\n'), code -> {
                    Matcher m = HEX_RELEASE.matcher(out.toString());
                    if (code == 0 && m.find()) {
                        latestVersion = m.group(1);
                        showVersions();
                    }
                });
    }

    private void showVersions() {
        String installed = installedVersion;
        String latest = latestVersion;
        boolean outdated = AngularVersions.isOutdated(installed, latest);
        onEdt(() -> {
            versionLcd.setTextColor(outdated ? RackStyle.LCD_AMBER : RackStyle.LCD_TEXT);
            versionLcd.setText("phx " + (installed == null ? "?" : installed)
                    + " → " + (latest == null ? "checking…" : latest)
                    + (latest != null && !outdated ? "  ✓" : ""));
            currentLed.setOn(latest != null && !outdated);
            outdatedLed.setOn(outdated);
        });
    }

    private void server() {
        readyFired.set(false);
        emit("serving", Signal.gate(true));
        launch(List.of("mix", "phx.server"));
    }

    @Override
    protected List<String> buildCommand() {
        return List.of("mix", "phx.server");
    }

    @Override
    protected void onLine(String line) {
        Matcher m = LOCAL_URL.matcher(line);
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
    }

    @Override
    public void receive(Port in, Signal signal) {
        switch (in.getId()) {
            case "serve" -> server();
            case "stop" -> stopProcess();
            default -> super.receive(in, signal);
        }
    }
}
