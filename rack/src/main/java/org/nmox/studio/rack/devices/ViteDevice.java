package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.Led;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;

/**
 * VELOCITY Vite Console: the dev-server and bundler under most modern
 * React/Vue/Svelte/Solid SPAs. DEV serves with the URL out feeding
 * SCOPE, BUILD compiles the production bundle, PREVIEW serves that
 * built bundle - and the version cluster tracks the installed vite
 * against the registry so the rack nags when the tool moves.
 */
public class ViteDevice extends CommandDevice {

    private final LcdDisplay versionLcd;
    private final Led currentLed;
    private final Led outdatedLed;
    private final AtomicBoolean readyFired = new AtomicBoolean();
    private volatile String installedVersion;
    private volatile String latestVersion;
    private volatile String announcedUrl;

    public ViteDevice() {
        super("vite", "VELOCITY", "VITE CONSOLE", new Color(0x64, 0x6C, 0xFF), 2);

        versionLcd = place(new LcdDisplay(190, 1), 44, 40);
        versionLcd.getAccessibleContext().setAccessibleName("version");
        versionLcd.setText("vite ? → ?");
        currentLed = place(new Led("CURRENT", RackStyle.GO), 242, 46);
        outdatedLed = place(new Led("OUTDATED", RackStyle.MUTATE), 298, 46);
        RackButton check = place(new RackButton("CHECK", RackStyle.QUERY), 360, 40);

        RackButton dev = place(new RackButton("DEV", RackStyle.GO), 44, 88);
        RackButton stop = place(new RackButton("STOP", RackStyle.STOP), 108, 88);
        RackButton build = place(new RackButton("BUILD", RackStyle.GO), 172, 88);
        RackButton preview = place(new RackButton("PREVIEW", new Color(64, 156, 255)), 236, 88);

        check.addActionListener(e -> refreshVersions());
        dev.addActionListener(e -> dev());
        stop.addActionListener(e -> stopProcess());
        build.addActionListener(e -> primaryAction());
        preview.addActionListener(e -> preview());

        addInPort("serve", "DEV", SignalType.TRIGGER);
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
    protected void onAttached() {
        refreshVersions();
    }

    @Override
    public void projectChanged(File dir) {
        refreshVersions();
    }

    private void refreshVersions() {
        String raw = ProjectInspector.dependencyVersion(projectDir(), "vite");
        installedVersion = raw == null ? null : AngularVersions.clean(raw);
        showVersions();
        if (installedVersion == null) {
            onEdt(() -> statusLcd.setText("NO vite IN package.json"));
            return;
        }
        StringBuilder out = new StringBuilder();
        CommandProbe.run(projectDir(),
                List.of(org.nmox.studio.core.process.ToolLocator.resolve("npm"),
                        "view", "vite", "version"),
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
            versionLcd.setText("vite " + (installed == null ? "?" : installed)
                    + " → " + (latest == null ? "checking…" : latest)
                    + (latest == null ? "" : outdated ? (majorBehind ? "  MAJOR!" : "") : "  ✓"));
            currentLed.setOn(latest != null && !outdated);
            outdatedLed.setOn(outdated);
            if (outdated) {
                outdatedLed.setBlinking(majorBehind);
            }
        });
    }

    private void dev() {
        readyFired.set(false);
        emit("serving", Signal.gate(true));
        launch(List.of("npx", "vite"));
    }

    private void preview() {
        readyFired.set(false);
        emit("serving", Signal.gate(true));
        launch(List.of("npx", "vite", "preview"));
    }

    @Override
    protected List<String> buildCommand() {
        return List.of("npx", "vite", "build");
    }

    @Override
    protected void onLine(String line) {
        String url = ServeUrls.firstLocalUrl(line);
        if (url != null) {
            if (readyFired.compareAndSet(false, true)) {
                emit("ready", Signal.trigger());
            }
            if (!url.equals(announcedUrl)) {
                announcedUrl = url;
                onEdt(() -> statusLcd.setText("SERVING  " + url));
                emit("url", Signal.data(url));
                registerServing(url, org.nmox.studio.rack.service.ServingRegistry.Kind.WEB);
            }
        }
    }

    @Override
    protected void onFinished(int exitCode) {
        // the dev/preview server stopped: drop the serving registry entry
        // (⇄ chip, ⌘I Live Servers, VITALS/BEACON auto-target) and the
        // SERVING gate, and clear announcedUrl so a restart re-announces
        // even on the same port
        deregisterServing();
        emit("serving", Signal.gate(false));
        announcedUrl = null;
    }

    @Override
    public void receive(Port in, Signal signal) {
        switch (in.getId()) {
            case "serve" -> dev();
            case "stop" -> stopProcess();
            case "enable" -> enableGate(signal.high(), this::dev, this::stopProcess);
            default -> super.receive(in, signal);
        }
    }
}
