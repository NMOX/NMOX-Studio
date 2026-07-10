package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.File;
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
 * ARTISAN Laravel Console: php artisan as a rack unit. SERVE runs the
 * built-in server (URL out feeds SCOPE), the ACTION knob dials the
 * other artisan verbs (test/migrate/fresh/queue/routes) for RUN, and
 * the version cluster compares composer.lock's laravel/framework
 * against the newest release on Packagist. Tinker is deliberately not
 * a knob position - it is interactive, which is the REPL device's job
 * (COMMAND: php artisan tinker).
 */
public class ArtisanDevice extends CommandDevice {

    private static final String[] ACTIONS = {"serve", "test", "migrate", "fresh", "queue", "routes"};
    /**
     * artisan announces: "Server running on [http://127.0.0.1:8000]." —
     * deliberately NOT the shared {@link ServeUrls} scan: this variant
     * must also stop at {@code ]} or the bracket leaks into the URL.
     */
    private static final Pattern LOCAL_URL =
            Pattern.compile("(https?://(?:localhost|127\\.0\\.0\\.1):\\d+[^\\s\"'\\]]*)");
    /** composer show --available output: "versions : dev-master, 12.x-dev, v12.1.1, ..." */
    private static final Pattern PACKAGIST_RELEASE = Pattern.compile("\\bv(\\d+\\.\\d+\\.\\d+)\\b");

    private final LcdDisplay versionLcd;
    private final Led currentLed;
    private final Led outdatedLed;
    private final Knob actionKnob;
    private final AtomicBoolean readyFired = new AtomicBoolean();
    private volatile String installedVersion;
    private volatile String latestVersion;
    private volatile String announcedUrl;

    public ArtisanDevice() {
        super("artisan", "ARTISAN", "LARAVEL CONSOLE", new Color(0xFF, 0x2D, 0x20), 3);

        versionLcd = place(new LcdDisplay(200, 1), 44, 40);
        versionLcd.getAccessibleContext().setAccessibleName("version");
        versionLcd.setText("laravel ? → ?");
        versionLcd.setToolTipText("laravel/framework in composer.lock → latest on Packagist");
        currentLed = place(new Led("CURRENT", RackStyle.GO), 252, 46);
        outdatedLed = place(new Led("OUTDATED", RackStyle.MUTATE), 308, 46);
        RackButton check = place(new RackButton("CHECK", RackStyle.QUERY), 372, 40);

        RackButton serve = place(new RackButton("SERVE", RackStyle.GO), RackStyle.TRANSPORT_X, 96);
        RackButton stop = place(new RackButton("STOP", RackStyle.STOP), RackStyle.TRANSPORT_STOP_X, 96);
        actionKnob = place(new Knob("ACTION", ACTIONS, 0), 184, 84);
        RackButton run = place(new RackButton("RUN", new Color(0xFF, 0x2D, 0x20)), 262, 96);
        run.setCommandPreview(this::commandPreview);

        check.addActionListener(e -> refreshVersions());
        serve.addActionListener(e -> serve());
        stop.addActionListener(e -> stopProcess());
        run.addActionListener(e -> primaryAction());

        addInPort("serve", "SERVE", SignalType.TRIGGER);
        addInPort("stop", "STOP", SignalType.TRIGGER);
        addInPort("enable", "ENABLE", SignalType.GATE);
        addOutPort("url", "URL", SignalType.DATA);
        addOutPort("ready", "READY", SignalType.TRIGGER);
        addOutPort("serving", "SERVING", SignalType.GATE);

        param("action", actionKnob);
    }

    /** artisan lives in the PHP subproject of mixed repos. */
    @Override
    protected ProjectInspector.ProjectKind effectiveKind() {
        return ProjectInspector.ProjectKind.PHP;
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
        installedVersion = ProjectInspector.composerLockVersion(commandDir(), "laravel/framework");
        showVersions();
        if (!new File(commandDir(), "artisan").isFile()) {
            onEdt(() -> statusLcd.setText("NO artisan IN PROJECT — laravel new FIRST"));
            return;
        }
        if (installedVersion == null) {
            onEdt(() -> statusLcd.setText("NO laravel/framework IN composer.lock — composer install FIRST"));
            return;
        }
        StringBuilder out = new StringBuilder();
        CommandProbe.run(commandDir(),
                List.of(org.nmox.studio.core.process.ToolLocator.resolve("composer"),
                        "show", "--available", "laravel/framework"),
                line -> out.append(line).append('\n'), code -> {
                    Matcher m = PACKAGIST_RELEASE.matcher(out.toString());
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
            versionLcd.setText("laravel " + (installed == null ? "?" : installed)
                    + " → " + (latest == null ? "checking…" : latest)
                    + (latest != null && !outdated ? "  ✓" : ""));
            currentLed.setOn(latest != null && !outdated);
            outdatedLed.setOn(outdated);
        });
    }

    private void serve() {
        readyFired.set(false);
        emit("serving", Signal.gate(true));
        launch(List.of("php", "artisan", "serve"));
    }

    /** RUN fires the dialed action; serve gets the long-runner treatment. */
    @Override
    protected void primaryAction() {
        if ("serve".equals(actionKnob.getSelectedOption())) {
            serve();
        } else {
            launch(buildCommand());
        }
    }

    @Override
    protected List<String> buildCommand() {
        return switch (actionKnob.getSelectedOption()) {
            case "test" -> List.of("php", "artisan", "test");
            case "migrate" -> List.of("php", "artisan", "migrate");
            case "fresh" -> List.of("php", "artisan", "migrate:fresh", "--seed");
            case "queue" -> List.of("php", "artisan", "queue:work");
            case "routes" -> List.of("php", "artisan", "route:list");
            default -> List.of("php", "artisan", "serve");
        };
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

    /**
     * Manifest pulse: composer.json/composer.lock edits re-check version
     * currency — but only when the locked laravel/framework actually
     * moved, so a `composer install` that changes nothing stays silent.
     */
    @Override
    public void manifestChanged(java.util.List<java.nio.file.Path> changed) {
        if (anyNamed(changed, "composer.json", "composer.lock")) {
            offEdt(() -> {
                if (!java.util.Objects.equals(installedVersion,
                        ProjectInspector.composerLockVersion(commandDir(), "laravel/framework"))) {
                    refreshVersions();
                }
            });
        }
    }

    /** The faceplate context menu's "Open composer.json". */
    @Override
    public java.util.Optional<File> primaryManifest() {
        File composer = new File(commandDir(), "composer.json");
        return composer.isFile() ? java.util.Optional.of(composer) : java.util.Optional.empty();
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
