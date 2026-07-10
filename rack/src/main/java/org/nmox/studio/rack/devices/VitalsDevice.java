package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.util.ArrayList;
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
    // append-only: persisted patches store the knob index, not the label
    private static final String[] GATES = {"perf", "a11y", "both", "best", "seo", "all"};
    /** Factory URL; while the LCD still shows it, RUN may auto-aim. */
    private static final String DEFAULT_URL = "http://localhost:5173";

    private final LcdDisplay urlLcd;
    private final Knob minKnob;
    private final Knob gateKnob;
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
        urlLcd = place(new LcdDisplay(170, 1), 180, 46);
        urlLcd.setText(DEFAULT_URL);
        urlLcd.setEditable("URL to audit");
        minKnob = place(new Knob("MIN", MINIMUMS, 0), 360, 40);
        minKnob.setToolTipText("Score floor: below it, FAIL fires instead of OK");
        gateKnob = place(new Knob("GATE", GATES, 0), 424, 40);
        gateKnob.setToolTipText("Which standard the floor holds: performance, accessibility (WCAG), both, best practices, SEO, or all four");
        place(perfMeter, 480, 34);
        place(a11yMeter, 480, 74);
        place(bestMeter, 560, 34);
        place(seoMeter, 560, 74);

        audit.addActionListener(e -> primaryAction());
        stop.addActionListener(e -> stopProcess());

        addInPort("url", "URL", SignalType.DATA);

        param("url", urlLcd);
        param("min", minKnob);
        param("gate", gateKnob);
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
        String url = effectiveUrl();
        if (url.isEmpty()) {
            return null;
        }
        return List.of("npx", "lighthouse", url,
                "--output=json", "--output-path=stdout", "--quiet",
                "--chrome-flags=--headless --no-sandbox");
    }

    /**
     * The audit target: an explicitly dialed URL always wins; a blank,
     * factory-default, or auto LCD aims at the project's live WEB server
     * from the serving registry (read at RUN time, never polled), with
     * the LCD showing the pick as "auto: &lt;url&gt;".
     */
    String effectiveUrl() {
        String dialed = urlLcd.getText().trim();
        if (!AutoUrl.isAuto(dialed, DEFAULT_URL)) {
            return dialed;
        }
        String auto = AutoUrl.firstWebServing(projectDir());
        if (auto != null) {
            // Visible feedback only: label the pick as "auto: <url>". The
            // write is marshalled to the EDT, where the guard re-reads the
            // LCD — an explicit dial typed (or applied) between this read and
            // the label write must never be clobbered by a stale auto value.
            onEdt(() -> {
                String label = AutoUrl.autoLabelOrKeep(urlLcd.getText(), DEFAULT_URL, auto);
                if (label != null) {
                    urlLcd.setText(label);
                }
            });
            return auto;
        }
        return AutoUrl.fallback(dialed);
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
     * The gate itself: a clean exit is not enough - every score the GATE
     * knob holds must clear the dialed floor. FAIL LED and jack fire on a
     * slow, inaccessible, sloppy, or unfindable page exactly as they
     * would on a crashed tool.
     */
    @Override
    protected boolean overallSuccess(int exitCode) {
        if (exitCode != 0) {
            return false;
        }
        Scores s = scores;
        int floor = minimum();
        if (s == null || floor == 0) {
            return true;
        }
        String gate = gateKnob.getSelectedOption();
        String[] labels = {"PERF", "A11Y", "BEST", "SEO"};
        int[] pcts = {pct(s.performance()), pct(s.accessibility()),
                pct(s.bestPractices()), pct(s.seo())};
        boolean[] held = {
            "perf".equals(gate) || "both".equals(gate) || "all".equals(gate),
            "a11y".equals(gate) || "both".equals(gate) || "all".equals(gate),
            "best".equals(gate) || "all".equals(gate),
            "seo".equals(gate) || "all".equals(gate)};
        List<String> closed = new ArrayList<>();
        int closedScore = 0;
        for (int i = 0; i < labels.length; i++) {
            if (held[i] && pcts[i] < floor) {
                closed.add(labels[i]);
                closedScore = pcts[i];
            }
        }
        if (!closed.isEmpty()) {
            String which = closed.size() == 1
                    ? closed.get(0) + " " + closedScore
                    : String.join("+", closed);
            onEdt(() -> {
                statusLcd.setTextColor(new Color(255, 90, 80));
                statusLcd.setText(which + " < MIN " + floor + " — GATE CLOSED");
            });
            return false;
        }
        return true;
    }

    /** Test seam: which standards the floor currently holds. */
    String gate() {
        return gateKnob.getSelectedOption();
    }

    /** Test seam: inject parsed scores as if a report streamed past. */
    void scoresForTest(Scores s) {
        this.scores = s;
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
