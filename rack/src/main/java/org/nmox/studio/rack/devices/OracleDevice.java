package org.nmox.studio.rack.devices;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.swing.JDialog;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.nmox.studio.rack.engine.FlightRecorder;
import org.nmox.studio.rack.engine.OracleClient;
import org.nmox.studio.rack.engine.OracleClient.FailureContext;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.service.OracleConsent;
import org.nmox.studio.rack.service.OracleKeys;
import org.nmox.studio.rack.ui.controls.Knob;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.Led;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;

/**
 * ORACLE: AI assistance through the rack's metaphor. It taps the flight
 * recorder for the error currently on the MONITOR bus and, on an explicit
 * EXPLAIN press, asks the Anthropic Messages API what went wrong and how
 * to fix it — visible, wired, unpluggable.
 *
 * <p><b>The laws it lives inside:</b> zero boot cost (attach only
 * registers a recorder change-listener; no keyring read, no network);
 * <b>no network without a button press</b> (only {@link #consult} ever
 * calls the API, and only EXPLAIN calls consult); its own <b>one-time
 * consent</b> for the outward data flow (WorkspaceTrust does not cover
 * sending output off the machine); the <b>API key in the OS keychain
 * only</b>; and <b>honest degradation</b> for every failure state — no
 * key, no consent, nothing to explain, offline, refusal — each an honest
 * LCD line, never a throw.
 *
 * <p>EXPLAIN is {@code QUERY}-blue: it reads a failed run and asks a
 * question, mutating nothing in the project (the color law). This is the
 * BLACKBOX shape — a recorder consumer with a QUERY button and a modeless
 * popup viewer — not a command device.
 */
public class OracleDevice extends RackDevice {

    // Honest LCD lines for each terminal state (dossier §4).
    private static final String IDLE_READY = "READY — LAST RUN FAILED, PRESS EXPLAIN";
    private static final String IDLE_NOTHING = "NOTHING TO EXPLAIN — NO FAILED RUN";
    private static final String NO_KEY = "NO API KEY — PRESS KEY… TO SET ONE";
    private static final String NO_CONSENT = "EXPLAIN NEEDS YOUR OK — PRESS AGAIN";
    private static final String THINKING = "CONSULTING ORACLE…";
    private static final String OFFLINE = "OFFLINE — COULD NOT REACH ORACLE";
    static final String COOLING = "AUTO-EXPLAIN COOLING DOWN — 30s BETWEEN CONSULTS";
    static final String AUTO_NO_CONSENT = "AUTO-EXPLAIN NEEDS CONSENT — PRESS EXPLAIN ONCE";

    /** Cable-triggered consults are rate-limited: a REFLEX save loop or a
     *  flapping suite must never hammer a paid API. */
    static final long AUTO_COOLDOWN_MS = 30_000;

    private final RackButton explain;
    private final RackButton view;
    private final Led thinkLed;
    private final LcdDisplay verdict;
    private final Knob modelKnob;
    private final Runnable recorderListener = this::onRecorderChange;

    // ---- seams: production wiring, overridable by tests ----
    /** The model call. Package-private so a test can inject a canned transport. */
    OracleClient client = new OracleClient();
    /** The failure to explain. Defaults to the live recorder; tests inject. */
    Supplier<Optional<FailureContext>> failureSource = this::readFailureFromRecorder;
    /** The API key source. Defaults to the keychain; tests inject. */
    Supplier<char[]> keySource = OracleKeys::read;
    /** Whether consent is already granted (no prompt). Tests inject. */
    BooleanSupplier consentCheck = OracleConsent::isGranted;
    /** Clock for the auto-explain cooldown. Tests inject. */
    java.util.function.LongSupplier clock = System::currentTimeMillis;
    /** Recorder-vs-signal race grace on the cable path (ms). Tests set 0. */
    long autoRetryDelayMs = 400;

    /** The last explanation, shown in full by the VIEW popup. */
    private volatile String lastExplanation;
    private volatile boolean consulting;
    private volatile long lastAutoConsultAt = Long.MIN_VALUE / 2;

    public OracleDevice() {
        super("oracle", "ORACLE", "ERROR EXPLAINER", new Color(120, 90, 220), 2);

        explain = place(new RackButton("EXPLAIN", RackStyle.QUERY), RackStyle.TRANSPORT_X, 46);
        explain.setToolTipText("Ask ORACLE to explain the last failed run (sends it to the Anthropic API)");
        explain.addActionListener(e -> onExplain());

        view = place(new RackButton("VIEW", RackStyle.QUERY), 110, 46);
        view.setToolTipText("Open the full explanation in a scrollable window");
        view.addActionListener(e -> showFullText());

        thinkLed = place(new Led("THINK", RackStyle.QUERY), 176, 52);

        verdict = place(new LcdDisplay(430, 3), 230, 30);
        verdict.getAccessibleContext().setAccessibleName("explanation verdict");
        verdict.setToolTipText("A short verdict; VIEW shows the full explanation");
        verdict.appendLine(IDLE_NOTHING);

        RackButton key = place(new RackButton("KEY…", RackStyle.MUTATE), 676, 46);
        key.setToolTipText("Set the Anthropic API key — stored in the OS keychain, never on disk");
        key.addActionListener(e -> promptForKey());

        // HAIKU is the cheap default (index 0); SONNET upgrades the model.
        modelKnob = place(new Knob("MODEL", new String[]{"HAIKU", "SONNET"}, 0), 760, 26);
        modelKnob.setToolTipText("Which model answers: HAIKU (cheap, fast) or SONNET (stronger)");
        param("model", modelKnob);

        // v1.91.0: auto-explain by cable — patch VERITAS FAIL → EXPLAIN and
        // the verdict arrives hands-free; OUT carries the full text onward
        addInPort("explain", "EXPLAIN", org.nmox.studio.rack.model.SignalType.TRIGGER);
        addOutPort("out", "OUT", org.nmox.studio.rack.model.SignalType.DATA);
    }

    @Override
    public void receive(org.nmox.studio.rack.model.Port in,
            org.nmox.studio.rack.model.Signal signal) {
        if ("explain".equals(in.getId())
                && signal.type() == org.nmox.studio.rack.model.SignalType.TRIGGER) {
            onAutoExplain();
        }
    }

    /**
     * The cable path (signal-router thread). A cable must NEVER prompt —
     * no consent dialog storm from automation — so it refuses honestly on
     * the LCD when consent hasn't been granted by a button press yet, and
     * it rate-limits consults ({@link #AUTO_COOLDOWN_MS}) so a flapping
     * suite can't hammer a paid API. The threading rides the same off-EDT
     * lane as the button; the checks here are the synchronous core the
     * tests drive via {@link #autoConsultNow}.
     */
    private void onAutoExplain() {
        if (!autoPreflight()) {
            return;
        }
        thinking(true);
        offEdt(() -> {
            try {
                autoConsultBody();
            } finally {
                onEdt(() -> thinking(false));
            }
        });
    }

    /** Cooldown + in-flight + consent checks; true = go consult. */
    private boolean autoPreflight() {
        if (consulting) {
            return false; // a consult is already in flight
        }
        if (clock.getAsLong() - lastAutoConsultAt < AUTO_COOLDOWN_MS) {
            setVerdict(COOLING, RackStyle.LCD_AMBER);
            return false;
        }
        if (!consentCheck.getAsBoolean()) {
            // consent is granted by a human on the button path only
            setVerdict(AUTO_NO_CONSENT, RackStyle.LCD_AMBER);
            return false;
        }
        lastAutoConsultAt = clock.getAsLong();
        return true;
    }

    /**
     * The consult body for the cable path: the FAIL trigger and the
     * FlightRecorder tap both ride the same exit event through different
     * listeners, so the tape may be a beat behind the cable — one bounded
     * grace wait before giving up on "nothing to explain".
     */
    private void autoConsultBody() {
        if (failureSource.get().isEmpty() && autoRetryDelayMs > 0) {
            try {
                Thread.sleep(autoRetryDelayMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
        consult(true);
    }

    /** Test seam: the cable path's synchronous core (no threads). */
    String autoConsultNow() {
        if (!autoPreflight()) {
            return null;
        }
        autoConsultBody();
        return lastExplanation;
    }

    @Override
    protected void onAttached() {
        // Zero boot cost: only a change-listener, no keyring read, no network.
        FlightRecorder.getDefault().addChangeListener(recorderListener);
        refreshIdle();
    }

    @Override
    public void dispose() {
        FlightRecorder.getDefault().removeChangeListener(recorderListener);
        super.dispose();
    }

    private void onRecorderChange() {
        // A new run landed on the tape; if we're idle, keep the hint honest.
        onEdt(this::refreshIdle);
    }

    /** The live-recorder failure source (production). */
    private Optional<FailureContext> readFailureFromRecorder() {
        return FailureContext.fromRecorder(FlightRecorder.getDefault(), projectDir().getName());
    }

    /** Sets the idle hint from whether there is a failed run to explain. */
    private void refreshIdle() {
        if (thinkLed.isOn()) {
            return; // a consult is in flight; don't stomp its status
        }
        boolean explainable = failureSource.get().isPresent();
        setVerdict(explainable ? IDLE_READY : IDLE_NOTHING,
                explainable ? RackStyle.LCD_AMBER : RackStyle.LCD_TEXT);
    }

    // ---- the EXPLAIN flow --------------------------------------------------

    /**
     * The button handler: quick honest feedback, the one-time consent
     * prompt, then the network call off the EDT. The authoritative gates
     * that keep the API un-called live in {@link #consult}; this method
     * adds the interactive prompt, the thinking LED, and the threading.
     */
    private void onExplain() {
        // The failure lookup is an in-memory FlightRecorder read — cheap and
        // safe on the EDT, and lets the button refuse instantly with nothing
        // to explain. Everything past it (the keychain peek, the consent
        // prompt, the network call) runs off the EDT: reading the OS keychain
        // can block on an unlock prompt (v1.56 review F4).
        Optional<FailureContext> maybe = failureSource.get();
        if (maybe.isEmpty()) {
            setVerdict(IDLE_NOTHING, RackStyle.LCD_TEXT);
            return;
        }
        thinking(true);
        offEdt(() -> {
            try {
                // Peek whether a key exists so we refuse before prompting for
                // consent or hitting the network — wipe the peek immediately.
                char[] peek = keySource.get();
                boolean haveKey = peek != null && peek.length > 0;
                if (peek != null) {
                    Arrays.fill(peek, '\0');
                }
                if (!haveKey) {
                    setVerdict(NO_KEY, RackStyle.LCD_AMBER);
                    return;
                }
                boolean consent = consentCheck.getAsBoolean();
                if (!consent) {
                    consent = OracleConsent.requestConsent(maybe.get());
                    if (!consent) {
                        setVerdict(NO_CONSENT, RackStyle.LCD_AMBER);
                        return;
                    }
                }
                consult(consent);
            } finally {
                onEdt(() -> thinking(false));
            }
        });
    }

    /**
     * The single source of truth for the model call: it never touches the
     * API unless there is a failure, a key, and consent. Synchronous and
     * Swing-safe (it marshals its own UI), so tests drive it directly with
     * injected seams. Returns the status line it set, for assertions.
     *
     * <p><b>The two gates, mutation-proven:</b> remove the key gate and a
     * spy transport sees a post with an empty key; remove the consent gate
     * and it sees a post without consent. Either way a test catches it.
     */
    String consult(boolean consentGranted) {
        Optional<FailureContext> maybe = failureSource.get();
        if (maybe.isEmpty()) {
            return setVerdict(IDLE_NOTHING, RackStyle.LCD_TEXT);
        }
        char[] key = keySource.get();
        try {
            if (key == null || key.length == 0) {           // ---- KEY GATE ----
                return setVerdict(NO_KEY, RackStyle.LCD_AMBER);
            }
            if (!consentGranted) {                          // ---- CONSENT GATE ----
                return setVerdict(NO_CONSENT, RackStyle.LCD_AMBER);
            }
            String text = client.explain(maybe.get(), currentModel(), key);
            lastExplanation = text;
            // composability (v1.91.0): the full text rides the OUT jack —
            // patch into MONITOR/PHOSPHOR to read explanations in the rack
            emit("out", org.nmox.studio.rack.model.Signal.data(text));
            return setVerdict(firstLines(text), RackStyle.LCD_TEXT);
        } catch (java.io.IOException e) {
            // honest, never a throw: the message is already key-free
            return setVerdict(OFFLINE, new Color(255, 90, 80));
        } finally {
            if (key != null) {
                Arrays.fill(key, '\0');
            }
        }
    }

    private String currentModel() {
        return modelKnob.getSelectedIndex() == 1
                ? OracleClient.MODEL_SONNET : OracleClient.MODEL_HAIKU;
    }

    private void thinking(boolean on) {
        consulting = on;
        // the cable path reaches here on the signal-router thread; the
        // LED is Swing — marshal (setVerdict marshals itself already)
        onEdt(() -> {
            thinkLed.setOn(on);
            thinkLed.setBlinking(on);
        });
        if (on) {
            setVerdict(THINKING, RackStyle.QUERY);
        }
    }

    /** Sets the verdict LCD (marshaled to the EDT) and returns the text set. */
    private String setVerdict(String text, Color color) {
        onEdt(() -> {
            verdict.clear();
            verdict.setTextColor(color);
            for (String line : text.split("\n")) {
                verdict.appendLine(line, color);
            }
        });
        return text;
    }

    /** The first few lines of the explanation — the glanceable verdict. */
    private static String firstLines(String text) {
        String[] lines = text.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(3, lines.length); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(lines[i]);
        }
        return sb.toString();
    }

    // ---- the key dialog (password field, never InputLine) ------------------

    /**
     * A password dialog for the API key — never the LcdDisplay double-click
     * editor, whose {@code InputLine} echoes plaintext. On OK the key goes
     * straight to the keychain off the EDT; an empty value clears it.
     */
    private void promptForKey() {
        javax.swing.JPasswordField field = new javax.swing.JPasswordField(28);
        javax.swing.JPanel panel = new javax.swing.JPanel(new BorderLayout(8, 8));
        panel.add(new javax.swing.JLabel("Anthropic API key (stored in the OS keychain):"),
                BorderLayout.NORTH);
        panel.add(field, BorderLayout.CENTER);
        org.openide.NotifyDescriptor nd = new org.openide.NotifyDescriptor(
                panel, "ORACLE — set API key",
                org.openide.NotifyDescriptor.OK_CANCEL_OPTION,
                org.openide.NotifyDescriptor.PLAIN_MESSAGE, null, null);
        if (org.openide.DialogDisplayer.getDefault().notify(nd)
                != org.openide.NotifyDescriptor.OK_OPTION) {
            return;
        }
        char[] entered = field.getPassword();
        offEdt(() -> {
            try {
                OracleKeys.save(entered); // null/empty is a delete
            } finally {
                Arrays.fill(entered, '\0');
            }
            onEdt(() -> setVerdict(
                    OracleKeys.hasKey() ? "KEY SET — PRESS EXPLAIN" : NO_KEY,
                    RackStyle.LCD_TEXT));
        });
    }

    // ---- the full-text popup (BLACKBOX dialog shape) -----------------------

    private void showFullText() {
        String text = lastExplanation;
        JDialog dialog = new JDialog(
                (java.awt.Frame) SwingUtilities.getWindowAncestor(this),
                "ORACLE — explanation", false);
        dialog.setLayout(new BorderLayout());
        JTextArea area = new JTextArea(
                text == null || text.isBlank()
                        ? "No explanation yet — press EXPLAIN on a failed run." : text);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 13));
        area.setMargin(new java.awt.Insets(10, 12, 10, 12));
        area.getAccessibleContext().setAccessibleName("ORACLE explanation");
        dialog.add(new JScrollPane(area), BorderLayout.CENTER);
        dialog.setSize(680, 460);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }
}
