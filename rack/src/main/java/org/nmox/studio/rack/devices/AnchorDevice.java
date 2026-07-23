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
 * ANCHOR Solana Console: Solana programs as a rack unit. START boots
 * {@code solana-test-validator} — a REAL foreground long-runner, so
 * unlike STELLAR's detached quickstart this device carries a truthful
 * SERVING gate: the URL jack fires the moment the validator announces
 * its JSON RPC URL (live-pinned against Agave 4.1.2), and the gate
 * drops when the process dies. The ACTION knob dials {@code anchor
 * build}/{@code anchor test} (test spins its own throwaway validator),
 * and the version cluster compares Cargo.lock's anchor-lang against
 * the newest crate on crates.io.
 *
 * <p>No private keys, by construction: the solana/anchor CLIs manage
 * their own keypairs in their own config (the test validator airdrops
 * localnet SOL), the ANVIL boundary. Deploys beyond localnet are
 * SOLDER's job — free-form args, real identities, named in the how-to.
 */
public class AnchorDevice extends CommandDevice {

    private static final String[] ACTIONS = {"build", "test"};
    /** The live-pinned banner: {@code JSON RPC URL: http://127.0.0.1:8899} */
    private static final Pattern RPC_URL = Pattern.compile(
            "JSON RPC URL: (https?://\\S+)");
    /** cargo search: {@code anchor-lang = "1.1.2"   # Solana Sealevel eDSL} */
    private static final Pattern CRATE_RELEASE = Pattern.compile(
            "^anchor-lang = \"(\\d+\\.\\d+\\.\\d+)\"", Pattern.MULTILINE);

    private final LcdDisplay versionLcd;
    private final Led currentLed;
    private final Led outdatedLed;
    private final Knob actionKnob;
    private final AtomicBoolean readyFired = new AtomicBoolean();
    private volatile String installedVersion;
    private volatile String latestVersion;

    public AnchorDevice() {
        super("anchor", "ANCHOR", "SOLANA CONSOLE", new Color(0x99, 0x45, 0xFF), 3);

        versionLcd = place(new LcdDisplay(200, 1), 44, 40);
        versionLcd.getAccessibleContext().setAccessibleName("version");
        versionLcd.setText("anchor-lang ? → ?");
        versionLcd.setToolTipText("anchor-lang in Cargo.lock → latest crate on crates.io");
        currentLed = place(new Led("CURRENT", RackStyle.GO), 252, 46);
        outdatedLed = place(new Led("OUTDATED", RackStyle.MUTATE), 308, 46);
        RackButton check = place(new RackButton("CHECK", RackStyle.QUERY), 372, 40);

        RackButton start = place(new RackButton("START", RackStyle.GO), RackStyle.TRANSPORT_X, 96);
        start.setCommandPreview(this::commandPreview);
        RackButton stop = place(new RackButton("STOP", RackStyle.STOP), RackStyle.TRANSPORT_STOP_X, 96);
        actionKnob = place(new Knob("ACTION", ACTIONS, 0), 184, 84);
        RackButton run = place(new RackButton("RUN", new Color(0x99, 0x45, 0xFF)), 262, 96);
        run.setCommandPreview(() -> String.join(" ", actionCommand()));

        check.addActionListener(e -> refreshVersions());
        start.addActionListener(e -> startValidator());
        stop.addActionListener(e -> stopProcess());
        run.addActionListener(e -> runAction());

        addInPort("stop", "STOP", SignalType.TRIGGER);
        addInPort("enable", "ENABLE", SignalType.GATE);
        addOutPort("url", "URL", SignalType.DATA);
        addOutPort("ready", "READY", SignalType.TRIGGER);
        addOutPort("serving", "SERVING", SignalType.GATE);

        param("action", actionKnob);
    }

    /** Anchor lives in the Rust subproject of mixed repos. */
    @Override
    protected ProjectInspector.ProjectKind effectiveKind() {
        return ProjectInspector.ProjectKind.RUST;
    }

    /** The validator serves any directory — like ANVIL, it reads nothing. */
    @Override
    protected boolean requiresProjectManifest() {
        return false;
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
        installedVersion = ProjectInspector.cargoLockVersion(commandDir(), "anchor-lang");
        showVersions();
        if (installedVersion == null) {
            return; // native-solana or non-anchor repos: version cluster stays dark
        }
        StringBuilder out = new StringBuilder();
        CommandProbe.run(commandDir(),
                List.of(org.nmox.studio.core.process.ToolLocator.resolve("cargo"),
                        "search", "anchor-lang", "--limit", "1"),
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
            versionLcd.setText("anchor-lang " + installed + " → " + latest);
            currentLed.setOn(known && current);
            outdatedLed.setOn(known && !current);
        });
    }

    private void startValidator() {
        if (!StellarDevice.toolOnPath("solana-test-validator")) {
            onEdt(() -> {
                statusLcd.setTextColor(RackStyle.LCD_AMBER);
                statusLcd.setText("solana-test-validator not found — brew install solana");
            });
            return;
        }
        readyFired.set(false);
        if (launch(buildCommand())) {
            emit("serving", Signal.gate(true));
        }
    }

    @Override
    protected void primaryAction() {
        startValidator();
    }

    @Override
    protected List<String> buildCommand() {
        return List.of("solana-test-validator");
    }

    /** Test seam: the ACTION knob, for dialing command shapes. */
    Knob actionKnob() {
        return actionKnob;
    }

    List<String> actionCommand() {
        return switch (actionKnob.getSelectedOption()) {
            case "build" -> List.of("anchor", "build");
            case "test" -> List.of("anchor", "test");
            default -> List.of("anchor", "build");
        };
    }

    private void runAction() {
        if (!new File(commandDir(), "Anchor.toml").isFile()) {
            onEdt(() -> {
                statusLcd.setTextColor(RackStyle.LCD_AMBER);
                statusLcd.setText("NO Anchor.toml IN PROJECT — anchor init FIRST");
            });
            return;
        }
        if (!StellarDevice.toolOnPath("anchor")) {
            onEdt(() -> {
                statusLcd.setTextColor(RackStyle.LCD_AMBER);
                statusLcd.setText("anchor not found — cargo install avm && avm install latest");
            });
            return;
        }
        launch(actionCommand());
    }

    @Override
    protected void onLine(String line) {
        Matcher rpc = RPC_URL.matcher(line);
        if (rpc.find()) {
            String url = rpc.group(1);
            onEdt(() -> statusLcd.setText("VALIDATOR UP  " + url));
            emit("url", Signal.data(url));
            registerServing(url, org.nmox.studio.rack.service.ServingRegistry.Kind.CHAIN);
            if (readyFired.compareAndSet(false, true)) {
                emit("ready", Signal.trigger());
            }
        }
    }

    @Override
    protected void onFinished(int exitCode) {
        deregisterServing();
        emit("serving", Signal.gate(false));
    }

    @Override
    public void receive(Port in, Signal signal) {
        switch (in.getId()) {
            case "run" -> runAction();
            case "stop" -> stopProcess();
            case "enable" -> enableGate(signal.high(), this::startValidator, this::stopProcess);
            default -> super.receive(in, signal);
        }
    }
}
