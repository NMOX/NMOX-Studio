package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.ui.controls.Knob;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;

/**
 * GOVERNOR Gas Budget Gate: the quality-gates family's Web3 member.
 * Gas costs creep one require() at a time; GOVERNOR runs
 * {@code forge snapshot --check} against the committed .gas-snapshot
 * and holds the line - within budget fires OK, a regression fires FAIL
 * with the first offending per-test diff on the LCD. TOLERANCE allows
 * dialed drift before the gate closes; no snapshot fails closed, because
 * a gate that cannot measure must not wave things through.
 */
public class GovernorDevice extends CommandDevice {

    // append-only: persisted patches store the knob index, not the label
    private static final String[] TOLERANCES = {"0%", "1%", "2%", "5%", "10%", "25%"};

    private final Knob toleranceKnob;
    private volatile String firstDiff;

    public GovernorDevice() {
        super("gas-budget", "GOVERNOR", "GAS BUDGET GATE", new Color(0xC9, 0xA2, 0x27), 2);

        RackButton check = place(new RackButton("CHECK", RackStyle.GO), RackStyle.TRANSPORT_X, 52);
        check.setCommandPreview(this::commandPreview);
        RackButton stop = place(new RackButton("STOP", RackStyle.STOP), RackStyle.TRANSPORT_STOP_X, 52);
        toleranceKnob = place(new Knob("TOLERANCE", TOLERANCES, 0), 184, 40);
        toleranceKnob.setToolTipText("Allowed gas drift before the gate closes: 0% holds the snapshot exactly");

        check.addActionListener(e -> primaryAction());
        stop.addActionListener(e -> stopProcess());

        param("tolerance", toleranceKnob);
    }

    /** The gate always speaks Foundry: gas snapshots are a forge idea. */
    @Override
    protected ProjectInspector.ProjectKind effectiveKind() {
        return ProjectInspector.ProjectKind.FOUNDRY;
    }

    // No manifestChanged override: GOVERNOR checks .gas-snapshot at
    // action time only (primaryAction fails closed when it is absent);
    // there is no at-rest LCD state for a pulse to refresh.

    /** The faceplate context menu's "Open .gas-snapshot". */
    @Override
    public java.util.Optional<File> primaryManifest() {
        File snapshot = new File(commandDir(), ".gas-snapshot");
        return snapshot.isFile() ? java.util.Optional.of(snapshot) : java.util.Optional.empty();
    }

    /** Test seam: the dialed tolerance in percent (0 = exact). */
    int tolerancePercent() {
        String sel = toleranceKnob.getSelectedOption();
        return Integer.parseInt(sel.substring(0, sel.length() - 1));
    }

    @Override
    protected List<String> buildCommand() {
        List<String> cmd = new ArrayList<>(List.of("forge", "snapshot", "--check"));
        int tolerance = tolerancePercent();
        if (tolerance > 0) {
            cmd.addAll(List.of("--tolerance", String.valueOf(tolerance)));
        }
        return cmd;
    }

    @Override
    protected void primaryAction() {
        firstDiff = null;
        // no snapshot means nothing to hold the line against: fail closed
        if (!new File(commandDir(), ".gas-snapshot").isFile()) {
            onEdt(() -> {
                statusLcd.setTextColor(RackStyle.LCD_AMBER);
                statusLcd.setText("NO .gas-snapshot — RUN forge snapshot FIRST");
            });
            emit("fail", Signal.trigger(false));
            return;
        }
        launch(buildCommand());
    }

    /**
     * The first per-test gas diff in a line, or null. forge prints diffs
     * as {@code Diff in "..."} headers and per-test rows that carry a
     * gas figure with a signed change - a plain pass line has neither.
     * Hand-rolled scan, package-visible for tests.
     */
    static String gasDiffLine(String line) {
        String t = line.trim();
        if (t.startsWith("Diff in ")) {
            return t;
        }
        // per-test rows: testWithdraw() (gas: 31303 (prev: 30000)) or (gas: +1303 (+4.3%))
        int gas = t.indexOf("(gas:");
        if (gas >= 0 && (t.indexOf("prev:", gas) > 0 || t.indexOf('+', gas) > 0
                || t.indexOf('-', gas) > 0)) {
            return t;
        }
        return null;
    }

    @Override
    protected void onLine(String line) {
        if (firstDiff == null) {
            String diff = gasDiffLine(line);
            if (diff != null) {
                firstDiff = diff;
            }
        }
    }

    /**
     * The verdict LCD, written after the base status so it wins the
     * repaint: within budget reads as an open gate, a regression names
     * the first offender.
     */
    @Override
    protected void onFinished(int exitCode) {
        String diff = firstDiff;
        onEdt(() -> {
            if (exitCode == 0) {
                statusLcd.setTextColor(RackStyle.LCD_TEXT);
                statusLcd.setText("WITHIN BUDGET"
                        + (tolerancePercent() > 0 ? "  (±" + tolerancePercent() + "%)" : ""));
            } else {
                statusLcd.setTextColor(new Color(255, 90, 80));
                statusLcd.setText(diff != null ? "OVER BUDGET  " + diff
                        : "OVER BUDGET — GATE CLOSED");
            }
        });
    }
}
