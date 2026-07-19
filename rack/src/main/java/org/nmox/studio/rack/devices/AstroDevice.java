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
 * COSMOS Astro Console: the content-first framework's dev/build loop.
 * DEV serves with the URL out feeding SCOPE, BUILD compiles the static
 * site, PREVIEW serves that build, and CHECK runs astro's type/content
 * diagnostics - while the version cluster tracks the installed astro
 * against the registry so the rack nags when the framework moves.
 */
public class AstroDevice extends CommandDevice {

    private final LcdDisplay versionLcd;
    private final Led currentLed;
    private final Led outdatedLed;
    private final AtomicBoolean readyFired = new AtomicBoolean();
    private volatile String installedVersion;
    private volatile String latestVersion;
    private volatile String announcedUrl;

    public AstroDevice() {
        super("astro", "COSMOS", "ASTRO CONSOLE", new Color(0xFF, 0x5D, 0x01), 2);

        versionLcd = place(new LcdDisplay(190, 1), 44, 40);
        versionLcd.getAccessibleContext().setAccessibleName("version");
        versionLcd.setText("astro ? → ?");
        currentLed = place(new Led("CURRENT", RackStyle.GO), 242, 46);
        outdatedLed = place(new Led("OUTDATED", RackStyle.MUTATE), 298, 46);
        RackButton check = place(new RackButton("CHECK", RackStyle.QUERY), 360, 40);

        RackButton dev = place(new RackButton("DEV", RackStyle.GO), 44, 88);
        RackButton stop = place(new RackButton("STOP", RackStyle.STOP), 108, 88);
        RackButton build = place(new RackButton("BUILD", RackStyle.GO), 172, 88);
        RackButton preview = place(new RackButton("PREVIEW", new Color(64, 156, 255)), 236, 88);
        RackButton diag = place(new RackButton("DIAG", new Color(168, 110, 221)), 320, 88);

        check.addActionListener(e -> refreshVersions());
        dev.addActionListener(e -> dev());
        stop.addActionListener(e -> stopProcess());
        build.addActionListener(e -> primaryAction());
        preview.addActionListener(e -> preview());
        diag.addActionListener(e -> launch(List.of("npx", "astro", "check")));

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
        String raw = ProjectInspector.dependencyVersion(projectDir(), "astro");
        installedVersion = raw == null ? null : AngularVersions.clean(raw);
        showVersions();
        if (installedVersion == null) {
            onEdt(() -> statusLcd.setText("NO astro IN package.json"));
            return;
        }
        StringBuilder out = new StringBuilder();
        CommandProbe.run(projectDir(),
                List.of(org.nmox.studio.core.process.ToolLocator.resolve("npm"),
                        "view", "astro", "version"),
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
            versionLcd.setText("astro " + (installed == null ? "?" : installed)
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
        if (launch(List.of("npx", "astro", "dev"))) {
            emit("serving", Signal.gate(true));
        }
    }

    private void preview() {
        readyFired.set(false);
        if (launch(List.of("npx", "astro", "preview"))) {
            emit("serving", Signal.gate(true));
        }
    }

    @Override
    protected List<String> buildCommand() {
        return List.of("npx", "astro", "build");
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
