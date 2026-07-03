package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.Led;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;

/**
 * REPL: a real read-eval-print loop in the rack. START launches the
 * interpreter named in the COMMAND field (clisp, python3, node, ghci,
 * iex - anything interactive), keeping its stdin open; you type an
 * expression, press Enter, and read the answer in the scrollback. HINTS
 * lists starter expressions a learning space seeds so you always know
 * what to try. Unlike the command devices - which run with a closed
 * stdin so a prompt can never hang them - this device IS the prompt.
 */
public class ReplDevice extends RackDevice {

    private static final int MAX_LINES = 5_000;
    private static final Color SCREEN_BG = new Color(7, 14, 7);
    private static final Color INPUT_ECHO = new Color(120, 200, 255);
    private static final Color ERR_TEXT = new Color(255, 110, 95);

    private final LcdDisplay commandLcd;
    private final LcdDisplay snippetsLcd; // persisted, not placed
    private final JTextArea screen;
    private final JTextField input;
    private final Led runLed;
    private int lineCount;

    private volatile InteractiveProcess session;

    public ReplDevice() {
        super("repl", "REPL", "READ-EVAL-PRINT LOOP", new Color(120, 230, 160), 5);

        int left = RackStyle.EAR_WIDTH + 14;
        int right = RackStyle.RACK_WIDTH - RackStyle.EAR_WIDTH;
        int fullW = right - left;

        commandLcd = place(new LcdDisplay(300, 1), left, 40);
        commandLcd.setText("python3");
        commandLcd.setEditable("Interpreter command (e.g. clisp, python3, node, ghci)");
        commandLcd.setToolTipText("The interactive interpreter START launches — edit to taste.");

        RackButton start = place(new RackButton("START", RackStyle.GO), left + 320, 40);
        RackButton stop = place(new RackButton("STOP", RackStyle.STOP), left + 400, 40);
        RackButton hints = place(new RackButton("HINTS", RackStyle.QUERY), left + 480, 40);
        runLed = place(new Led("LIVE", RackStyle.GO), left + 570, 46);

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
        scroll.setPreferredSize(new Dimension(fullW, 5 * RackStyle.UNIT - 132));
        place(scroll, left, 76);

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

        snippetsLcd = new LcdDisplay(10, 1); // persisted only; carries seeded snippets

        start.addActionListener(e -> startRepl());
        stop.addActionListener(e -> stopRepl());
        hints.addActionListener(e -> showHints(hints));
        send.addActionListener(e -> submit());
        input.addActionListener(e -> submit());

        addInPort("eval", "EVAL", SignalType.DATA);
        addOutPort("out", "OUT", SignalType.DATA);

        param("command", commandLcd);
        param("snippets", snippetsLcd);
    }

    private void startRepl() {
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
                    }));
            onEdt(() -> {
                runLed.setOn(true);
                input.requestFocusInWindow();
            });
        } catch (IOException ex) {
            session = null;
            append(command.get(0).toUpperCase() + " would not start — install it, "
                    + "or check the tutorial's install command. (" + ex.getMessage() + ")", ERR_TEXT);
        }
    }

    private void stopRepl() {
        InteractiveProcess s = session;
        if (s != null) {
            s.stop();
        }
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
        stopRepl();
    }

    @Override
    public void dispose() {
        stopRepl();
        super.dispose();
    }
}
