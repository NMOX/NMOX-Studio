package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;
import org.nmox.studio.rack.ui.controls.Knob;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;

/**
 * ANVIL Local EVM Chain: Foundry's anvil devnet as a rack long-runner.
 * START boots a chain on the dialed port with ten unlocked, funded
 * accounts; the screen shows the account banner as it streams past,
 * READY fires and the URL jack carries http://127.0.0.1:PORT the moment
 * anvil announces it is listening, and SERVING gates downstream devices
 * for as long as the chain lives. FORK-URL turns the fresh chain into a
 * fork of any live network; BLOCK-TIME switches instant mining to a
 * fixed cadence. The IDE never touches keys: anvil's own unlocked
 * accounts sign everything, on localhost only.
 */
public class AnvilDevice extends CommandDevice {

    private static final String[] PORTS = {"8545", "8546", "8547", "9545"};
    // "instant" mines per-transaction; the rest pass --block-time seconds
    private static final String[] BLOCK_TIMES = {"instant", "1", "5", "12"};
    /** anvil announces: "Listening on 127.0.0.1:8545" */
    private static final Pattern LISTENING = Pattern.compile(
            "Listening on ((?:\\d{1,3}\\.){3}\\d{1,3}:\\d+)");
    /** account banner rows: (0) 0xf39F... (10000.000000000000000000 ETH) */
    private static final Pattern ACCOUNT = Pattern.compile(
            "^\\((\\d+)\\)\\s+\"?(0x[0-9a-fA-F]{40})\"?\\s+\\((\\d+)(?:\\.\\d+)? ETH\\)");

    private final LcdDisplay screenLcd;
    private final Knob portKnob;
    private final Knob blockTimeKnob;
    private final LcdDisplay chainIdLcd;
    private final LcdDisplay forkUrlLcd;
    private final AtomicBoolean readyFired = new AtomicBoolean();
    private final AtomicInteger accountCount = new AtomicInteger();
    private volatile String firstAccount;
    private volatile String firstBalance;

    public AnvilDevice() {
        super("anvil", "ANVIL", "LOCAL EVM CHAIN", new Color(0x8A, 0x9B, 0xA8), 3);

        screenLcd = place(new LcdDisplay(420, 2), 44, 32);
        screenLcd.appendLine("anvil devnet — press START");
        screenLcd.setToolTipText("The chain's account banner: unlocked, funded accounts anvil signs with");

        RackButton start = place(new RackButton("START", RackStyle.GO), RackStyle.TRANSPORT_X, 96);
        start.setCommandPreview(this::commandPreview);
        RackButton stop = place(new RackButton("STOP", RackStyle.STOP), RackStyle.TRANSPORT_STOP_X, 96);
        portKnob = place(new Knob("PORT", PORTS, 0), 184, 84);
        blockTimeKnob = place(new Knob("BLK-TIME", BLOCK_TIMES, 0), 258, 84);
        blockTimeKnob.setToolTipText("Mining cadence: instant mines per transaction, else seconds per block");
        chainIdLcd = place(new LcdDisplay(80, 1), 340, 96);
        chainIdLcd.setText("31337");
        chainIdLcd.setEditable("Chain id the devnet reports (31337 is the anvil default)");
        forkUrlLcd = place(new LcdDisplay(230, 1), 434, 96);
        forkUrlLcd.setText("");
        forkUrlLcd.setEditable("Fork URL — blank starts a fresh chain, an RPC URL forks its state");

        start.addActionListener(e -> startChain());
        stop.addActionListener(e -> stopProcess());

        addInPort("start", "START", SignalType.TRIGGER);
        addInPort("stop", "STOP", SignalType.TRIGGER);
        addInPort("enable", "ENABLE", SignalType.GATE);
        addOutPort("url", "URL", SignalType.DATA);
        addOutPort("ready", "READY", SignalType.TRIGGER);
        addOutPort("serving", "SERVING", SignalType.GATE);

        param("port", portKnob);
        param("blockTime", blockTimeKnob);
        param("chainId", chainIdLcd);
        param("forkUrl", forkUrlLcd);
    }

    /** A devnet serves any directory: anvil reads nothing from the project. */
    @Override
    protected boolean requiresProjectManifest() {
        return false;
    }

    /** True when anvil resolves on the IDE's augmented PATH. */
    private static boolean anvilOnPath() {
        for (String dir : org.nmox.studio.core.process.ToolLocator.augmentedPath()
                .split(File.pathSeparator)) {
            if (new File(dir, "anvil").canExecute()) {
                return true;
            }
        }
        return false;
    }

    private void startChain() {
        if (!anvilOnPath()) {
            onEdt(() -> {
                statusLcd.setTextColor(RackStyle.LCD_AMBER);
                statusLcd.setText("anvil not found — curl -L https://foundry.paradigm.xyz | bash");
            });
            return;
        }
        readyFired.set(false);
        accountCount.set(0);
        firstAccount = null;
        firstBalance = null;
        emit("serving", Signal.gate(true));
        launch(buildCommand());
    }

    @Override
    protected void primaryAction() {
        startChain();
    }

    @Override
    protected List<String> buildCommand() {
        List<String> cmd = new ArrayList<>(List.of("anvil", "--port", portKnob.getSelectedOption()));
        String chainId = chainIdLcd.getText().trim();
        if (!chainId.isEmpty()) {
            cmd.addAll(List.of("--chain-id", chainId));
        }
        String blockTime = blockTimeKnob.getSelectedOption();
        if (!"instant".equals(blockTime)) {
            cmd.addAll(List.of("--block-time", blockTime));
        }
        String forkUrl = forkUrlLcd.getText().trim();
        if (!forkUrl.isEmpty()) {
            cmd.addAll(List.of("--fork-url", forkUrl));
        }
        return cmd;
    }

    /** Test seam: accounts parsed from the banner so far. */
    int accountsSeen() {
        return accountCount.get();
    }

    @Override
    protected void onLine(String line) {
        Matcher account = ACCOUNT.matcher(line.trim());
        if (account.find()) {
            int n = accountCount.incrementAndGet();
            if (firstAccount == null) {
                firstAccount = account.group(2);
                firstBalance = account.group(3);
            }
            showAccounts(n);
            return;
        }
        Matcher listening = LISTENING.matcher(line);
        if (listening.find()) {
            String url = "http://" + listening.group(1);
            onEdt(() -> statusLcd.setText("CHAIN UP  " + url + "  id " + chainIdLcd.getText().trim()));
            emit("url", Signal.data(url));
            if (readyFired.compareAndSet(false, true)) {
                emit("ready", Signal.trigger());
            }
        }
    }

    private void showAccounts(int count) {
        String account = firstAccount;
        String balance = firstBalance;
        onEdt(() -> {
            screenLcd.clear();
            screenLcd.appendLine(count + " unlocked accounts · " + balance + " ETH each");
            screenLcd.appendLine("(0) " + account);
        });
    }

    @Override
    protected void onFinished(int exitCode) {
        emit("serving", Signal.gate(false));
        onEdt(() -> {
            screenLcd.clear();
            screenLcd.appendLine("anvil devnet — press START");
        });
    }

    @Override
    public void receive(Port in, Signal signal) {
        switch (in.getId()) {
            case "start" -> startChain();
            case "stop" -> stopProcess();
            case "enable" -> enableGate(signal.high(), this::startChain, this::stopProcess);
            default -> super.receive(in, signal);
        }
    }
}
