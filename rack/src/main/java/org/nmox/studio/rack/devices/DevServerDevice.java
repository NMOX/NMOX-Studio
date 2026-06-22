package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;
import org.nmox.studio.rack.ui.controls.Knob;
import org.nmox.studio.rack.ui.controls.Led;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;

/**
 * SURGE Dev Server: starts and stops the local development server.
 * While the server lives, the RUNNING gate is high; the READY trigger
 * fires once on first output, and URL emits the local address - patch
 * it into SCOPE Browser Link to auto-open a tab.
 */
public class DevServerDevice extends CommandDevice {

    private static final String[] SERVERS = {"auto", "vite", "http-server", "serve"};
    private static final String[] PORTS = {"3000", "4200", "5173", "8000", "8080", "9000"};

    /** A local URL printed by the server (vite "Local:", CRA, serve...). */
    private static final java.util.regex.Pattern LOCAL_URL =
            java.util.regex.Pattern.compile("(https?://(?:localhost|127\\.0\\.0\\.1):\\d+[^\\s\"']*)");

    private final Knob serverKnob;
    private final Knob portKnob;
    private final Led liveLed;
    private final AtomicBoolean readyFired = new AtomicBoolean();
    private volatile String announcedUrl;

    public DevServerDevice() {
        super("dev-server", "SURGE", "DEV SERVER", new Color(64, 156, 255), 2);

        RackButton start = place(new RackButton("START", RackStyle.GO), RackStyle.TRANSPORT_X, 52);
        start.setCommandPreview(this::commandPreview);
        RackButton stop = place(new RackButton("STOP", RackStyle.STOP), RackStyle.TRANSPORT_STOP_X, 52);
        serverKnob = place(new Knob("SERVER", SERVERS, 0), 180, 40);
        portKnob = place(new Knob("PORT", PORTS, 2), 254, 40);
        liveLed = place(new Led("LIVE", new Color(64, 200, 255)), 330, 58);

        start.addActionListener(e -> primaryAction());
        stop.addActionListener(e -> shutdown());

        addInPort("stop", "STOP", SignalType.TRIGGER);
        addInPort("enable", "ENABLE", SignalType.GATE);
        addOutPort("running", "RUNNING", SignalType.GATE);
        addOutPort("ready", "READY", SignalType.TRIGGER);
        addOutPort("url", "URL", SignalType.DATA);

        param("server", serverKnob);
        param("port", portKnob);
    }

    /** Static servers (http-server, serve) happily serve plain folders. */
    @Override
    protected boolean requiresProjectManifest() {
        String server = serverKnob.getSelectedOption();
        return !"http-server".equals(server) && !"serve".equals(server);
    }

    private String port() {
        return PORTS[portKnob.getSelectedIndex()];
    }

    private String localUrl() {
        return "http://localhost:" + port();
    }

    @Override
    protected void primaryAction() {
        readyFired.set(false);
        emit("running", Signal.gate(true));
        onEdt(() -> liveLed.setBlinking(true));
        launch(buildCommand());
    }

    private void shutdown() {
        stopProcess();
    }

    /** AUTO resolves to the project's dev/start script, else vite, else a static server. */
    private String effectiveServer() {
        String server = serverKnob.getSelectedOption();
        if (!"auto".equals(server)) {
            return server;
        }
        if (ProjectInspector.hasScript(projectDir(), "dev")
                || ProjectInspector.hasScript(projectDir(), "start")) {
            return "npm-script";
        }
        if (ProjectInspector.firstDependency(projectDir(), "vite") != null) {
            return "vite";
        }
        return "http-server";
    }

    /** npm speaks from the package.json directory, monorepo or not. */
    @Override
    protected java.io.File commandDir() {
        return ProjectInspector.kindDir(projectDir(), ProjectInspector.ProjectKind.NODE);
    }

    @Override
    protected List<String> buildCommand() {
        List<String> cmd = new ArrayList<>();
        switch (effectiveServer()) {
            case "vite" -> cmd.addAll(List.of("npx", "vite", "--port", port()));
            case "http-server" -> cmd.addAll(List.of("npx", "http-server", "-p", port()));
            case "serve" -> cmd.addAll(List.of("npx", "serve", "-l", port()));
            default -> cmd.addAll(List.of("npm", "run",
                    ProjectInspector.hasScript(projectDir(), "dev") ? "dev" : "start"));
        }
        return cmd;
    }

    @Override
    protected void onLine(String line) {
        // the most common real-world serve failure deserves a real answer
        if (line.contains("EADDRINUSE") || line.toLowerCase().contains("address already in use")) {
            onEdt(() -> {
                statusLcd.setTextColor(new Color(255, 90, 80));
                statusLcd.setText("PORT " + port() + " IN USE — DIAL ANOTHER OR STOP THE OTHER SERVER");
            });
            return;
        }
        if (readyFired.compareAndSet(false, true)) {
            onEdt(() -> {
                liveLed.setBlinking(false);
                liveLed.setOn(true);
            });
            emit("ready", Signal.trigger());
            announcedUrl = localUrl();
            emit("url", Signal.data(announcedUrl));
        }
        // trust the server's own printed address over the knob: in AUTO
        // mode the npm script picks the port, not us. Re-emit on change
        // so a patched SCOPE follows the real URL.
        String plain = line.replaceAll("\\[[;\\d]*m", ""); // strip ANSI color
        java.util.regex.Matcher m = LOCAL_URL.matcher(plain);
        if (m.find()) {
            String real = m.group(1);
            if (!real.equals(announcedUrl)) {
                announcedUrl = real;
                onEdt(() -> statusLcd.setText("UP  " + real));
                emit("url", Signal.data(real));
            }
        }
    }

    @Override
    protected void onFinished(int exitCode) {
        emit("running", Signal.gate(false));
        onEdt(() -> {
            liveLed.setBlinking(false);
            liveLed.setOn(false);
        });
    }

    @Override
    public void receive(Port in, Signal signal) {
        if ("stop".equals(in.getId())) {
            shutdown();
        } else if ("enable".equals(in.getId())) {
            enableGate(signal.high(), this::primaryAction, this::shutdown);
        } else {
            super.receive(in, signal);
        }
    }

    @Override
    public void dispose() {
        super.dispose();
    }
}
