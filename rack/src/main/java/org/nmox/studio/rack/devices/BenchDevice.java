package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.nmox.studio.rack.ui.controls.Knob;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;
import org.nmox.studio.rack.ui.controls.VuMeter;

/**
 * GAUNTLET Load Bench: hammers an endpoint with autocannon and reports
 * throughput. The REQ/S meter is normalized to 10k req/s full scale;
 * the LCD shows the parsed requests-per-second and data rate. Patch
 * SURGE's READY into RUN to bench the dev server the moment it's up.
 */
public class BenchDevice extends CommandDevice {

    private static final String[] DURATIONS = {"5s", "10s", "30s"};
    private static final String[] CONNECTIONS = {"10", "50", "100", "250"};
    /** autocannon summary: "120k requests in 10.02s, 24 MB read" */
    private static final Pattern SUMMARY =
            Pattern.compile("([\\d.]+)([km]?) requests in ([\\d.]+)s, ([\\d.]+ \\w+) read");

    private final Knob durationKnob;
    private final Knob connectionsKnob;
    private final LcdDisplay urlLcd;
    private final LcdDisplay resultLcd;
    private final VuMeter reqMeter;

    public BenchDevice() {
        super("bench", "GAUNTLET", "LOAD BENCH", new Color(224, 122, 47), 2);

        durationKnob = place(new Knob("RUN FOR", DURATIONS, 0), 180, 40);
        connectionsKnob = place(new Knob("CONNS", CONNECTIONS, 0), 254, 40);
        urlLcd = place(new LcdDisplay(150, 1), 328, 46);
        urlLcd.setText("http://localhost:5173");
        urlLcd.setEditable("URL to bench");
        RackButton fire = place(new RackButton("FIRE", RackStyle.GO), RackStyle.TRANSPORT_X, 46);
        RackButton stopBench = place(new RackButton("STOP", RackStyle.STOP), RackStyle.TRANSPORT_STOP_X, 46);
        stopBench.addActionListener(e -> stopProcess());
        resultLcd = place(new LcdDisplay(120, 1), 328, 82);
        resultLcd.setText("—");
        reqMeter = place(new VuMeter("REQ/S", false), 460, 82);

        fire.addActionListener(e -> primaryAction());

        // accept a URL by cable, like PING and SCOPE: patch SURGE's or
        // WORMHOLE's URL out straight into the bench
        addInPort("url", "URL", org.nmox.studio.rack.model.SignalType.DATA);

        param("duration", durationKnob);
        param("connections", connectionsKnob);
        param("url", urlLcd);
    }

    @Override
    public void receive(org.nmox.studio.rack.model.Port in, org.nmox.studio.rack.model.Signal signal) {
        if ("url".equals(in.getId())) {
            if (signal.payload() != null && signal.payload().startsWith("http")) {
                onEdt(() -> urlLcd.setText(signal.payload()));
            }
        } else {
            super.receive(in, signal);
        }
    }

    /** Benching needs a URL, not a package.json. */
    @Override
    protected boolean requiresProjectManifest() {
        return false;
    }

    @Override
    protected void primaryAction() {
        onEdt(() -> {
            resultLcd.setTextColor(RackStyle.LCD_AMBER);
            resultLcd.setText("FIRING…");
        });
        launch(buildCommand());
    }

    @Override
    protected List<String> buildCommand() {
        String duration = DURATIONS[durationKnob.getSelectedIndex()].replace("s", "");
        return List.of("npx", "autocannon",
                "-d", duration,
                "-c", CONNECTIONS[connectionsKnob.getSelectedIndex()],
                urlLcd.getText().trim());
    }

    @Override
    protected void onLine(String line) {
        Matcher m = SUMMARY.matcher(line);
        if (!m.find()) {
            return;
        }
        double count = Double.parseDouble(m.group(1));
        if ("k".equals(m.group(2))) {
            count *= 1_000;
        } else if ("m".equals(m.group(2))) {
            count *= 1_000_000;
        }
        double seconds = Double.parseDouble(m.group(3));
        long reqPerSec = seconds > 0 ? Math.round(count / seconds) : 0;
        String read = m.group(4);
        reqMeter.setLevel(Math.min(1.0, reqPerSec / 10_000.0));
        onEdt(() -> {
            resultLcd.setTextColor(RackStyle.LCD_TEXT);
            resultLcd.setText(reqPerSec + " r/s " + read);
        });
    }
}
