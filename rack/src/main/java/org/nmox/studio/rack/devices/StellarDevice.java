package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.File;
import java.util.List;
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
 * STELLAR Soroban Console: Stellar smart contracts as a rack unit.
 * Soroban contracts are Rust compiled to WASM, driven end to end by the
 * {@code stellar} CLI — BUILD runs {@code stellar contract build}, the
 * ACTION knob dials {@code cargo test} (Soroban's real test story — the
 * SDK's testutils run contracts natively, no network needed) and the
 * local quickstart network verbs ({@code stellar container start/stop
 * local}, Docker under the hood, RPC on localhost:8000). The version
 * cluster compares Cargo.lock's soroban-sdk against the newest crate on
 * crates.io.
 *
 * <p>The no-private-keys law holds by construction: the stellar CLI
 * manages its own identities in its own config, exactly as anvil's
 * unlocked accounts sign for ANVIL — this device never sees, stores, or
 * passes key material. Deploy/invoke need identities and free-form
 * arguments, which is SOLDER's job, not a knob's; the how-to says so.
 */
public class StellarDevice extends CommandDevice {

    /** cargo test first: the zero-setup lane every Soroban repo has. */
    private static final String[] ACTIONS = {"test", "net-start", "net-stop"};
    /** cargo search: {@code soroban-sdk = "23.0.2"   # Soroban SDK.} */
    private static final Pattern CRATE_RELEASE = Pattern.compile(
            "^soroban-sdk = \"(\\d+\\.\\d+\\.\\d+)\"", Pattern.MULTILINE);
    /** The quickstart's soroban-rpc endpoint once the container is up. */
    static final String LOCAL_RPC_URL = "http://localhost:8000/rpc";

    private final LcdDisplay versionLcd;
    private final Led currentLed;
    private final Led outdatedLed;
    private final Knob actionKnob;
    private volatile String installedVersion;
    private volatile String latestVersion;
    /** The verb that actually launched — onFinished must never consult the
     *  knob, which the user can turn mid-run: a plain test exit would then
     *  announce LOCAL NET UP for a network that was never started (the
     *  v1.135.0 arc-review finding, the serving-truth bug class). */
    private volatile String launchedVerb = "";

    public StellarDevice() {
        super("stellar", "STELLAR", "SOROBAN CONSOLE", new Color(0xFD, 0xDA, 0x24), 3);

        versionLcd = place(new LcdDisplay(200, 1), 44, 40);
        versionLcd.getAccessibleContext().setAccessibleName("version");
        versionLcd.setText("soroban-sdk ? → ?");
        versionLcd.setToolTipText("soroban-sdk in Cargo.lock → latest crate on crates.io");
        currentLed = place(new Led("CURRENT", RackStyle.GO), 252, 46);
        outdatedLed = place(new Led("OUTDATED", RackStyle.MUTATE), 308, 46);
        RackButton check = place(new RackButton("CHECK", RackStyle.QUERY), 372, 40);

        RackButton build = place(new RackButton("BUILD", RackStyle.GO), RackStyle.TRANSPORT_X, 96);
        build.setCommandPreview(this::commandPreview);
        RackButton stop = place(new RackButton("STOP", RackStyle.STOP), RackStyle.TRANSPORT_STOP_X, 96);
        actionKnob = place(new Knob("ACTION", ACTIONS, 0), 184, 84);
        RackButton run = place(new RackButton("RUN", new Color(0xFD, 0xDA, 0x24)), 262, 96);
        run.setCommandPreview(() -> String.join(" ", actionCommand()));

        check.addActionListener(e -> refreshVersions());
        build.addActionListener(e -> primaryAction());
        stop.addActionListener(e -> stopProcess());
        run.addActionListener(e -> runAction());

        // the base CommandDevice already declares the RUN in-jack; our
        // receive() routes it to the dialed ACTION verb like the RUN button
        addInPort("stop", "STOP", SignalType.TRIGGER);
        addInPort("enable", "ENABLE", SignalType.GATE);
        addOutPort("url", "URL", SignalType.DATA);
        addOutPort("ready", "READY", SignalType.TRIGGER);

        param("action", actionKnob);
    }

    /** Soroban lives in the Rust subproject of mixed repos. */
    @Override
    protected ProjectInspector.ProjectKind effectiveKind() {
        return ProjectInspector.ProjectKind.RUST;
    }

    @Override
    protected void onAttached() {
        refreshVersions();
    }

    @Override
    public void projectChanged(File dir) {
        refreshVersions();
    }

    /** Grey honestly before any spawn; null means good to go. */
    private String refusal(boolean needsStellarCli) {
        if (!ProjectInspector.hasSorobanSdk(commandDir())) {
            return "NO soroban-sdk IN Cargo.toml — stellar contract init FIRST";
        }
        if (needsStellarCli && !toolOnPath("stellar")) {
            return "stellar not found — brew install stellar-cli";
        }
        return null;
    }

    static boolean toolOnPath(String tool) {
        for (String dir : org.nmox.studio.core.process.ToolLocator.augmentedPath()
                .split(File.pathSeparator)) {
            if (new File(dir, tool).canExecute() || new File(dir, tool + ".exe").canExecute()) {
                return true;
            }
        }
        return false;
    }

    private void refreshVersions() {
        installedVersion = ProjectInspector.cargoLockVersion(commandDir(), "soroban-sdk");
        showVersions();
        if (!ProjectInspector.hasSorobanSdk(commandDir())) {
            onEdt(() -> statusLcd.setText("NO soroban-sdk IN Cargo.toml — stellar contract init FIRST"));
            return;
        }
        StringBuilder out = new StringBuilder();
        CommandProbe.run(commandDir(),
                List.of(org.nmox.studio.core.process.ToolLocator.resolve("cargo"),
                        "search", "soroban-sdk", "--limit", "1"),
                line -> out.append(line).append('\n'), code -> {
                    Matcher m = CRATE_RELEASE.matcher(out.toString());
                    if (code == 0 && m.find()) {
                        latestVersion = m.group(1);
                        showVersions();
                    }
                });
    }

    private void showVersions() {
        String installed = installedVersion == null ? "?" : installedVersion;
        String latest = latestVersion == null ? "?" : latestVersion;
        boolean known = installedVersion != null && latestVersion != null;
        boolean current = known && installedVersion.equals(latestVersion);
        onEdt(() -> {
            versionLcd.setText("soroban-sdk " + installed + " → " + latest);
            currentLed.setOn(known && current);
            outdatedLed.setOn(known && !current);
        });
    }

    /** BUILD: stellar contract build (cargo → wasm under the hood). */
    @Override
    protected void primaryAction() {
        String refusal = refusal(true);
        if (refusal != null) {
            onEdt(() -> {
                statusLcd.setTextColor(RackStyle.LCD_AMBER);
                statusLcd.setText(refusal);
            });
            return;
        }
        launchedVerb = "build"; // never inherit a stale net-start attribution
        launch(buildCommand());
    }

    @Override
    protected List<String> buildCommand() {
        return List.of("stellar", "contract", "build");
    }

    /** Test seam: the ACTION knob, for dialing command shapes. */
    Knob actionKnob() {
        return actionKnob;
    }

    List<String> actionCommand() {
        return switch (actionKnob.getSelectedOption()) {
            case "test" -> List.of("cargo", "test");
            case "net-start" -> List.of("stellar", "container", "start", "local");
            case "net-stop" -> List.of("stellar", "container", "stop", "local");
            default -> List.of("cargo", "test");
        };
    }

    private void runAction() {
        String verb = actionKnob.getSelectedOption();
        // cargo test needs no stellar CLI — the SDK's testutils run natively
        String refusal = refusal(!"test".equals(verb));
        if (refusal != null) {
            onEdt(() -> {
                statusLcd.setTextColor(RackStyle.LCD_AMBER);
                statusLcd.setText(refusal);
            });
            return;
        }
        launchedVerb = verb;
        launch(actionCommand());
    }

    /** Test seam for the exit-attribution law. */
    void setLaunchedVerbForTest(String verb) {
        launchedVerb = verb;
    }

    /** Test seam: the status LCD's current text. */
    String statusText() {
        return statusLcd.getText();
    }

    @Override
    protected void onFinished(int exitCode) {
        // `stellar container start local` starts a DETACHED Docker container
        // and exits — the network outlives this process, so no SERVING gate
        // is declared (a gate we cannot keep truthful stays off the plate;
        // the v1.93.0 law). The URL still flows for SCOPE/Contract-style
        // consumers, and net-stop tears the container down.
        if (exitCode == 0 && "net-start".equals(launchedVerb)) {
            onEdt(() -> statusLcd.setText("LOCAL NET UP (docker) — " + LOCAL_RPC_URL));
            emit("url", Signal.data(LOCAL_RPC_URL));
            emit("ready", Signal.trigger());
        }
    }

    @Override
    public void receive(Port in, Signal signal) {
        switch (in.getId()) {
            case "run" -> runAction();
            case "stop" -> stopProcess();
            case "enable" -> enableGate(signal.high(), this::runAction, this::stopProcess);
            default -> super.receive(in, signal);
        }
    }
}
