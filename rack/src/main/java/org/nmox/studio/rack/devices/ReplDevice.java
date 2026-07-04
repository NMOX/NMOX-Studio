package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import org.nmox.studio.rack.engine.InteractiveProcess;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;
import org.nmox.studio.rack.projectstudio.LearningCatalog;
import org.nmox.studio.rack.projectstudio.LearningSpace;
import org.nmox.studio.rack.ui.controls.Knob;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.Led;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;

/**
 * REPL: a real read-eval-print loop in the rack. Dial ENGINE to a
 * well-known interpreter (derived from the learning catalog, so new
 * spaces appear automatically) or leave it on CUSTOM and type your own
 * COMMAND; START launches it (clisp, python3, node, ghci, iex -
 * anything interactive), keeping its stdin open; you type an
 * expression, press Enter, and read the answer in the scrollback. HINTS
 * lists starter expressions a learning space (or the engine) seeds so
 * you always know what to try, and INSTALL runs the catalog's seeded
 * install command when the interpreter is missing. Unlike the command
 * devices - which run with a closed stdin so a prompt can never hang
 * them - this device IS the prompt.
 */
public class ReplDevice extends RackDevice {

    private static final int MAX_LINES = 5_000;
    private static final Color SCREEN_BG = new Color(7, 14, 7);
    private static final Color INPUT_ECHO = new Color(120, 200, 255);
    private static final Color ERR_TEXT = new Color(255, 110, 95);

    /** The knob position that means "the COMMAND LCD is hand-set". */
    static final String CUSTOM_ENGINE = "CUSTOM";

    private final Knob engineKnob;
    private final LcdDisplay commandLcd;
    private final LcdDisplay snippetsLcd; // persisted, not placed
    private final LcdDisplay installLcd;  // persisted, not placed; the OS-appropriate install command
    private final RackButton installBtn;
    private final JTextArea screen;
    private final JTextField input;
    private final Led runLed;
    private int lineCount;
    private boolean seedingFromEngine;
    private volatile boolean installing;

    private volatile InteractiveProcess session;

    public ReplDevice() {
        super("repl", "REPL", "READ-EVAL-PRINT LOOP", new Color(120, 230, 160), 5);

        int left = RackStyle.EAR_WIDTH + 14;
        int right = RackStyle.RACK_WIDTH - RackStyle.EAR_WIDTH;
        int fullW = right - left;

        engineKnob = place(new Knob("ENGINE", engineOptions(), 0), left, 40);
        engineKnob.setToolTipText("Dial a well-known interpreter — CUSTOM keeps whatever COMMAND says");

        snippetsLcd = new LcdDisplay(10, 1); // persisted only; carries seeded snippets
        installLcd = new LcdDisplay(10, 1);  // persisted only; carries the seeded install command

        commandLcd = place(new LcdDisplay(240, 1), left + 72, 40);
        commandLcd.setText("python3");
        commandLcd.setEditable("Interpreter command (e.g. clisp, python3, node, ghci)");
        commandLcd.setToolTipText("The interactive interpreter START launches — edit to taste.");
        commandLcd.addEditListener(this::commandEdited);

        RackButton start = place(new RackButton("START", RackStyle.GO), left + 320, 40);
        RackButton stop = place(new RackButton("STOP", RackStyle.STOP), left + 400, 40);
        RackButton hints = place(new RackButton("HINTS", RackStyle.QUERY), left + 480, 40);
        installBtn = place(new RackButton("INSTALL", RackStyle.MUTATE), left + 560, 40);
        installBtn.setToolTipText("Install the interpreter with the catalog's seeded command");
        installBtn.setCommandPreview(() -> installLcd.getText().isBlank() ? null
                : "<html><code>$ " + installLcd.getText() + "</code></html>");
        runLed = place(new Led("LIVE", RackStyle.GO), left + 648, 46);

        screen = new JTextArea();
        screen.setEditable(false);
        screen.setLineWrap(true);
        screen.setBackground(SCREEN_BG);
        screen.setForeground(RackStyle.LCD_TEXT);
        screen.setCaretColor(RackStyle.LCD_TEXT);
        screen.setSelectionColor(new Color(40, 90, 40));
        screen.setSelectedTextColor(Color.WHITE);
        screen.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        screen.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        JScrollPane scroll = new JScrollPane(screen);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(8, 8, 9), 2));
        scroll.getViewport().setBackground(SCREEN_BG);
        scroll.setPreferredSize(new Dimension(fullW, 5 * RackStyle.UNIT - 178));
        place(scroll, left, 122);

        input = new JTextField();
        input.setBackground(new Color(12, 22, 12));
        input.setForeground(RackStyle.LCD_TEXT);
        input.setCaretColor(RackStyle.LCD_TEXT);
        input.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        input.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(40, 90, 40), 1),
                BorderFactory.createEmptyBorder(3, 6, 3, 6)));
        input.setPreferredSize(new Dimension(fullW - 90, 26));
        input.setToolTipText("Type an expression and press Enter to evaluate");
        place(input, left, 5 * RackStyle.UNIT - 44);
        RackButton send = place(new RackButton("SEND", RackStyle.GO), right - 76, 5 * RackStyle.UNIT - 48);

        engineKnob.addChangeListener(this::engineTurned);
        start.addActionListener(e -> startRepl());
        stop.addActionListener(e -> stopRepl());
        hints.addActionListener(e -> showHints(hints));
        installBtn.addActionListener(e -> runInstall());
        send.addActionListener(e -> submit());
        input.addActionListener(e -> submit());

        addInPort("eval", "EVAL", SignalType.DATA);
        addOutPort("out", "OUT", SignalType.DATA);

        paramByName("engine", engineKnob);
        param("command", commandLcd);
        param("snippets", snippetsLcd);
        param("install", installLcd);

        refreshInstall();
    }

    void startRepl() {
        if (session != null && session.isAlive()) {
            return;
        }
        List<String> command = splitArgs(commandLcd.getText());
        if (command.isEmpty()) {
            append("Set an interpreter command first (e.g. python3).", ERR_TEXT);
            return;
        }
        append("$ " + String.join(" ", command), INPUT_ECHO);
        try {
            session = InteractiveProcess.start(command, projectDir(),
                    line -> onEdt(() -> emitLine(line, false)),
                    line -> onEdt(() -> emitLine(line, true)),
                    code -> onEdt(() -> {
                        append("[exited " + code + "]", code == 0 ? null : ERR_TEXT);
                        runLed.setOn(false);
                        session = null;
                        refreshInstall();
                    }));
            onEdt(() -> {
                runLed.setOn(true);
                input.requestFocusInWindow();
            });
            refreshInstall();
        } catch (IOException ex) {
            session = null;
            String install = installLcd.getText().isBlank() ? "" : " — or press INSTALL";
            append(command.get(0).toUpperCase(Locale.ROOT) + " would not start — install it, "
                    + "or check the tutorial's install command" + install
                    + ". (" + ex.getMessage() + ")", ERR_TEXT);
        }
    }

    private void stopRepl() {
        InteractiveProcess s = session;
        if (s != null) {
            s.stop();
        }
    }

    /**
     * Turning ENGINE to a named interpreter is the synth preset pattern:
     * it seeds the COMMAND LCD with that driver's full command
     * (force-interactive flags included), the HINTS snippets, and the
     * OS-appropriate install command for INSTALL. CUSTOM seeds nothing —
     * it means "keep whatever COMMAND says".
     */
    private void engineTurned() {
        if (seedingFromEngine) {
            return;
        }
        Engine engine = engineFor(engineKnob.getSelectedOption());
        if (engine == null) {
            return; // CUSTOM: the LCD is the truth
        }
        seedingFromEngine = true;
        try {
            commandLcd.setText(engine.command());
            snippetsLcd.setText(engine.snippets());
            installLcd.setText(engine.install());
        } finally {
            seedingFromEngine = false;
        }
        refreshInstall();
    }

    /** A hand edit of the COMMAND LCD means the engine is now custom. */
    void commandEdited() {
        if (!seedingFromEngine) {
            engineKnob.selectOption(CUSTOM_ENGINE);
        }
    }

    /**
     * Engine first, explicit values last: a patch that names an engine
     * gets its seeding, but a saved custom command (or snippets, or
     * install) in the same state map must win over what the knob seeds —
     * a resurrected patch keeps exactly what it saved.
     */
    @Override
    public void applyState(Map<String, String> state) {
        String engine = state.get("engine");
        if (engine != null) {
            super.applyState(Map.of("engine", engine));
            Map<String, String> rest = new LinkedHashMap<>(state);
            rest.remove("engine");
            super.applyState(rest);
        } else {
            super.applyState(state);
        }
        refreshInstall();
    }

    /**
     * INSTALL: runs the seeded install command — curated catalog data,
     * not user input — through the same CommandExecutor path every
     * one-shot device uses, streaming its output onto the REPL screen.
     * Never auto-starts the REPL afterwards: the user presses START.
     */
    private void runInstall() {
        if (!installActionAvailable()) {
            return;
        }
        List<String> command = splitArgs(installLcd.getText());
        if (command.isEmpty()) {
            return;
        }
        installing = true;
        refreshInstall();
        onEdt(() -> installBtn.setLit(true));
        append("$ " + String.join(" ", command), INPUT_ECHO);
        append("installing…", null);
        exec(command, line -> append(line, null), code -> {
            installing = false;
            onEdt(() -> installBtn.setLit(false));
            refreshInstall();
            if (code == 0) {
                append("installed — press START", null);
            } else {
                append("install failed [exit " + code + "] — see the lines above.", ERR_TEXT);
            }
        });
    }

    /** The INSTALL rule, pinned pure: a seeded command, REPL not live, no install in flight. */
    static boolean installEnabled(String installCommand, boolean live, boolean installing) {
        return installCommand != null && !installCommand.isBlank() && !live && !installing;
    }

    boolean installActionAvailable() {
        return installEnabled(installLcd.getText(), isLive(), installing);
    }

    private void refreshInstall() {
        onEdt(() -> installBtn.setEnabledLook(installActionAvailable()));
    }

    private void submit() {
        String line = input.getText();
        if (line.isBlank()) {
            return;
        }
        InteractiveProcess s = session;
        if (s == null || !s.isAlive()) {
            append("Nothing running — press START first.", ERR_TEXT);
            return;
        }
        append("› " + line, INPUT_ECHO);
        s.send(line);
        input.setText("");
    }

    private void showHints(java.awt.Component anchor) {
        List<String> snippets = seededSnippets();
        JPopupMenu menu = new JPopupMenu();
        if (snippets.isEmpty()) {
            JMenuItem none = new JMenuItem("No starter snippets for this space");
            none.setEnabled(false);
            menu.add(none);
        } else {
            for (String snippet : snippets) {
                JMenuItem item = new JMenuItem(snippet);
                item.addActionListener(e -> {
                    input.setText(snippet);
                    input.requestFocusInWindow();
                });
                menu.add(item);
            }
        }
        menu.show(anchor, 0, anchor.getHeight());
    }

    /** Snippets a learning space seeded, newline-separated. */
    private List<String> seededSnippets() {
        List<String> out = new ArrayList<>();
        for (String s : snippetsLcd.getText().split("\n")) {
            if (!s.isBlank()) {
                out.add(s.strip());
            }
        }
        return out;
    }

    private void emitLine(String line, boolean err) {
        append(line, err ? ERR_TEXT : null);
        emit("out", Signal.data(line));
    }

    private void append(String line, Color color) {
        onEdt(() -> {
            if (color != null) {
                screen.setForeground(color != ERR_TEXT ? RackStyle.LCD_TEXT : screen.getForeground());
            }
            screen.append(line);
            screen.append("\n");
            lineCount++;
            if (lineCount > MAX_LINES) {
                int cut = screen.getText().indexOf('\n', 0);
                for (int i = 1; i < MAX_LINES / 10 && cut >= 0; i++) {
                    cut = screen.getText().indexOf('\n', cut + 1);
                }
                if (cut > 0) {
                    screen.replaceRange("", 0, cut + 1);
                    lineCount -= MAX_LINES / 10;
                }
            }
            screen.setCaretPosition(screen.getDocument().getLength());
        });
    }

    // ---- the engine catalog: well-known interpreters, derived from the learning catalog ----

    /** A well-known interpreter the ENGINE knob can dial in. */
    record Engine(String label, String command, String snippets, String install) {
    }

    private static volatile List<Engine> engineCache;

    /** CUSTOM plus every distinct repl-kind driver in the learning catalog. */
    static String[] engineOptions() {
        List<Engine> engines = engineCatalog();
        String[] options = new String[engines.size() + 1];
        options[0] = CUSTOM_ENGINE;
        for (int i = 0; i < engines.size(); i++) {
            options[i + 1] = engines.get(i).label();
        }
        return options;
    }

    static List<Engine> engineCatalog() {
        List<Engine> local = engineCache;
        if (local == null) {
            local = deriveEngines(LearningCatalog.all());
            engineCache = local;
        }
        return local;
    }

    /**
     * The knob's engine list: every repl-kind catalog driver in catalog
     * order, deduplicated by full command (numpy/pandas/pytorch all ride
     * bare python3 — the first wins) and by label. New catalog spaces
     * appear on the knob automatically; the commands carry their
     * force-interactive flags because the catalog does.
     */
    static List<Engine> deriveEngines(List<LearningCatalog.Space> spaces) {
        List<Engine> engines = new ArrayList<>();
        Set<String> commands = new HashSet<>();
        Set<String> labels = new HashSet<>();
        for (LearningCatalog.Space space : spaces) {
            LearningCatalog.Driver driver = space.driver();
            if (driver.kind() != LearningCatalog.DriverKind.REPL || driver.command().isEmpty()) {
                continue;
            }
            String command = String.join(" ", driver.command());
            String label = engineLabel(space.slug());
            if (!commands.add(command) || !labels.add(label)) {
                continue;
            }
            engines.add(new Engine(label, command,
                    String.join("\n", driver.snippets()),
                    LearningSpace.installHint(space)));
        }
        return engines;
    }

    /**
     * The knob label for a space: the shortest hyphen-segment of its
     * slug ("lisp-clisp" → lisp, "javascript-node" → node, "python" →
     * python) — short enough for a knob readout, earlier segment on ties.
     */
    static String engineLabel(String slug) {
        String best = slug;
        for (String segment : slug.split("-")) {
            if (!segment.isBlank() && segment.length() < best.length()) {
                best = segment;
            }
        }
        return best;
    }

    private static Engine engineFor(String label) {
        for (Engine engine : engineCatalog()) {
            if (engine.label().equals(label)) {
                return engine;
            }
        }
        return null;
    }

    /** The scrollback text, for tests. */
    String screenText() {
        return screen.getText();
    }

    /** argv tokenizer: whitespace split, honoring simple double-quotes. */
    static List<String> splitArgs(String command) {
        List<String> args = new ArrayList<>();
        if (command == null) {
            return args;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"([^\"]*)\"|(\\S+)").matcher(command);
        while (m.find()) {
            args.add(m.group(1) != null ? m.group(1) : m.group(2));
        }
        return args;
    }

    @Override
    public void receive(Port in, Signal signal) {
        if (signal.type() == SignalType.DATA && signal.payload() != null) {
            InteractiveProcess s = session;
            if (s != null && s.isAlive()) {
                append("› " + signal.payload(), INPUT_ECHO);
                s.send(signal.payload());
            }
        }
    }

    @Override
    public boolean isLive() {
        InteractiveProcess s = session;
        return s != null && s.isAlive();
    }

    @Override
    public void panic() {
        super.panic(); // kills an in-flight INSTALL run
        stopRepl();
    }

    @Override
    public void dispose() {
        stopRepl();
        super.dispose();
    }
}
