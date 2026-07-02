package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.util.List;
import org.json.JSONObject;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;
import org.nmox.studio.rack.ui.controls.Knob;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;
import org.nmox.studio.rack.ui.controls.VuMeter;

/**
 * VITALS: the web-quality gate. Runs Lighthouse headless against the
 * dialed URL and puts the four scores - performance, accessibility,
 * best practices, SEO - on real meters. The MIN knob makes quality a
 * pipeline citizen: below the floor, FAIL fires instead of OK, so
 * "serve → audit → deploy" ships nothing slow, inaccessible, or
 * sloppy. Patch SURGE's URL out into URL in and READY into RUN and
 * every serve gets scored.
 */
public class VitalsDevice extends CommandDevice {

    private static final String[] MINIMUMS = {"off", "50", "70", "80", "90", "95"};

    private final LcdDisplay urlLcd;
    private final Knob minKnob;
    private final VuMeter perfMeter = new VuMeter("PERF", false);
    private final VuMeter a11yMeter = new VuMeter("A11Y", false);
    private final VuMeter bestMeter = new VuMeter("BEST", false);
    private final VuMeter seoMeter = new VuMeter("SEO", false);
    private volatile Scores scores;

    public VitalsDevice() {
        super("vitals", "VITALS", "WEB QUALITY GATE", new Color(255, 105, 97), 2);

        RackButton audit = place(new RackButton("AUDIT", RackStyle.GO), RackStyle.TRANSPORT_X, 52);
        audit.setCommandPreview(this::commandPreview);
        RackButton stop = place(new RackButton("STOP", RackStyle.STOP), RackStyle.TRANSPORT_STOP_X, 52);
        urlLcd = place(new LcdDisplay(230, 1), 180, 46);
        urlLcd.setText("http://localhost:5173");
        urlLcd.setEditable("URL to audit");
        minKnob = place(new Knob("MIN", MINIMUMS, 0), 424, 40);
        minKnob.setToolTipText("Performance floor: below it, FAIL fires instead of OK");
        place(perfMeter, 480, 34);
        place(a11yMeter, 480, 74);
        place(bestMeter, 560, 34);
        place(seoMeter, 560, 74);

        audit.addActionListener(e -> primaryAction());
        stop.addActionListener(e -> stopProcess());

        addInPort("url", "URL", SignalType.DATA);

        param("url", urlLcd);
        param("min", minKnob);
    }

    /** Auditing a running URL needs no manifest - serve anything, score it. */
    @Override
    protected boolean requiresProjectManifest() {
        return false;
    }

    @Override
    protected void primaryAction() {
        scores = null;
        launch(buildCommand());
    }

    @Override
    protected List<String> buildCommand() {
        String url = urlLcd.getText().trim();
        if (url.isEmpty()) {
            return null;
        }
        return List.of("npx", "lighthouse", url,
                "--output=json", "--output-path=stdout", "--quiet",
                "--chrome-flags=--headless --no-sandbox");
    }

    @Override
    protected void onLine(String line) {
        // the report is one huge JSON line on stdout; grab it when it passes
        if (line.startsWith("{") && line.contains("\"categories\"")) {
            Scores s = parseScores(line);
            if (s != null) {
                scores = s;
                onEdt(() -> {
                    perfMeter.setLevel(s.performance());
                    a11yMeter.setLevel(s.accessibility());
                    bestMeter.setLevel(s.bestPractices());
                    seoMeter.setLevel(s.seo());
                    statusLcd.setText(String.format("PERF %d  A11Y %d  BEST %d  SEO %d",
                            pct(s.performance()), pct(s.accessibility()),
                            pct(s.bestPractices()), pct(s.seo())));
                });
            }
        }
    }

    /**
     * The gate itself: a clean exit is not enough - the performance
     * score must clear the dialed floor. FAIL LED and jack fire on a
     * slow page exactly as they would on a crashed tool.
     */
    @Override
    protected boolean overallSuccess(int exitCode) {
        if (exitCode != 0) {
            return false;
        }
        Scores s = scores;
        int floor = minimum();
        boolean gated = s != null && floor > 0 && pct(s.performance()) < floor;
        if (gated) {
            onEdt(() -> {
                statusLcd.setTextColor(new Color(255, 90, 80));
                statusLcd.setText("PERF " + pct(s.performance()) + " < MIN " + floor + " — GATE CLOSED");
            });
        }
        return !gated;
    }

    int minimum() {
        String sel = minKnob.getSelectedOption();
        return "off".equals(sel) ? 0 : Integer.parseInt(sel);
    }

    static int pct(double score) {
        return (int) Math.round(score * 100);
    }

    /** Category scores from a Lighthouse JSON report; null if not one. */
    static Scores parseScores(String json) {
        try {
            JSONObject categories = new JSONObject(json).getJSONObject("categories");
            return new Scores(
                    categories.getJSONObject("performance").optDouble("score", 0),
                    categories.getJSONObject("accessibility").optDouble("score", 0),
                    categories.getJSONObject("best-practices").optDouble("score", 0),
                    categories.getJSONObject("seo").optDouble("score", 0));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    record Scores(double performance, double accessibility, double bestPractices, double seo) {
    }

    @Override
    public void receive(Port in, Signal signal) {
        if ("url".equals(in.getId())) {
            if (signal.payload() != null && signal.payload().startsWith("http")) {
                onEdt(() -> urlLcd.setText(signal.payload()));
            }
            return;
        }
        super.receive(in, signal);
    }
}
