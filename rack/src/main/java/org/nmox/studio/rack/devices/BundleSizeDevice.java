package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.File;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;
import org.nmox.studio.rack.ui.controls.Knob;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;
import org.nmox.studio.rack.ui.controls.VuMeter;

/**
 * PRISM: the bundle-size gate. Bundles grow one innocent import at a
 * time; PRISM weighs the build output and holds the line. Patch
 * FORGE's OK into MEASURE and every build gets weighed - dial MAX and
 * a bundle over budget fires FAIL before it ships.
 */
public class BundleSizeDevice extends RackDevice {

    private static final String[] MAXIMUMS = {"off", "250 KB", "500 KB", "1 MB", "2 MB", "5 MB"};
    private static final long[] MAXIMUM_BYTES = {0, 250_000, 500_000, 1_000_000, 2_000_000, 5_000_000};

    private final LcdDisplay dirLcd;
    private final LcdDisplay resultLcd;
    private final Knob maxKnob;
    private final VuMeter meter;

    public BundleSizeDevice() {
        super("bundle-size", "PRISM", "BUNDLE-SIZE GATE", new Color(150, 120, 220), 2);

        RackButton measure = place(new RackButton("MEASURE", RackStyle.GO), RackStyle.TRANSPORT_X, 46);
        dirLcd = place(new LcdDisplay(160, 1), 150, 46);
        dirLcd.setText("dist");
        dirLcd.setEditable("Build output dir (relative to project)");
        maxKnob = place(new Knob("MAX", MAXIMUMS, 0), 330, 40);
        maxKnob.setToolTipText("Size budget: a build output over this fires FAIL");
        resultLcd = place(new LcdDisplay(200, 1), 404, 46);
        resultLcd.setText("—");
        meter = place(new VuMeter("WEIGHT", false), 620, 40);

        measure.addActionListener(e -> measure());

        addInPort("run", "MEASURE", SignalType.TRIGGER);
        addOutPort("ok", "OK", SignalType.TRIGGER);
        addOutPort("fail", "FAIL", SignalType.TRIGGER);

        param("dir", dirLcd);
        param("max", maxKnob);
    }

    long maximumBytes() {
        return MAXIMUM_BYTES[maxKnob.getSelectedIndex()];
    }

    /** Recursive on-disk weight of the build output. */
    static long totalBytes(File dir) {
        if (dir == null || !dir.exists()) {
            return -1;
        }
        if (dir.isFile()) {
            return dir.length();
        }
        long total = 0;
        File[] kids = dir.listFiles();
        if (kids != null) {
            for (File kid : kids) {
                long size = totalBytes(kid);
                if (size > 0) {
                    total += size;
                }
            }
        }
        return total;
    }

    static String human(long bytes) {
        if (bytes >= 1_000_000) {
            return String.format("%.1f MB", bytes / 1_000_000.0);
        }
        return (bytes / 1_000) + " KB";
    }

    private void measure() {
        File raw = new File(dirLcd.getText().trim());
        File dir = raw.isAbsolute() ? raw : new File(projectDir(), raw.getPath());
        long total = totalBytes(dir);
        long budget = maximumBytes();
        if (total < 0) {
            onEdt(() -> {
                resultLcd.setTextColor(RackStyle.LCD_AMBER);
                resultLcd.setText("NO " + dirLcd.getText().trim() + " — BUILD FIRST");
            });
            emit("fail", Signal.trigger(false));
            return;
        }
        boolean ok = budget == 0 || total <= budget;
        final long t = total;
        onEdt(() -> {
            meter.setLevel(budget > 0 ? Math.min(1.0, (double) t / budget)
                    : Math.min(1.0, t / 5_000_000.0));
            resultLcd.setTextColor(ok ? RackStyle.LCD_TEXT : new Color(255, 90, 80));
            resultLcd.setText(human(t) + (budget > 0
                    ? (ok ? " / " + human(budget) : " > MAX " + human(budget)) : ""));
        });
        emit(ok ? "ok" : "fail", Signal.trigger(ok));
    }

    @Override
    public void receive(Port in, Signal signal) {
        if ("run".equals(in.getId())) {
            measure();
        }
    }
}
