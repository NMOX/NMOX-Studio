package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;

/**
 * SOLDER: the custom joint. Every other device runs a known tool; this
 * one runs whatever you type - "make seed-db", "./scripts/fixtures.sh
 * --reset", "cargo sqlx migrate run" - with the full standard
 * treatment: OK/FAIL/DONE jacks, output on the bus, CI export. The
 * command is parsed into argv (quotes respected), never handed to a
 * shell - no injection, no surprises.
 */
public class CommandLineDevice extends CommandDevice {

    private final LcdDisplay commandLcd;

    public CommandLineDevice() {
        super("cmd", "SOLDER", "CUSTOM COMMAND", new Color(176, 141, 87), 2);

        RackButton run = place(new RackButton("RUN", RackStyle.GO), RackStyle.TRANSPORT_X, 52);
        run.setCommandPreview(this::commandPreview);
        RackButton stop = place(new RackButton("STOP", RackStyle.STOP), RackStyle.TRANSPORT_STOP_X, 52);
        commandLcd = place(new LcdDisplay(400, 1), 180, 52);
        commandLcd.setText("");
        commandLcd.setEditable("Command (argv, quotes ok — no shell)");

        run.addActionListener(e -> primaryAction());
        stop.addActionListener(e -> stopProcess());

        param("command", commandLcd);
    }

    @Override
    protected List<String> buildCommand() {
        List<String> argv = splitArgs(commandLcd.getText());
        return argv.isEmpty() ? null : argv;
    }

    /**
     * Whitespace-splits into argv with single/double quotes grouping -
     * the useful 90% of shell tokenizing with none of the shell:
     * no expansion, no redirection, no chaining.
     */
    static List<String> splitArgs(String line) {
        List<String> args = new ArrayList<>();
        if (line == null) {
            return args;
        }
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean started = false;
        for (char c : line.toCharArray()) {
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                } else {
                    current.append(c);
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
                started = true;
            } else if (Character.isWhitespace(c)) {
                if (started || current.length() > 0) {
                    args.add(current.toString());
                    current.setLength(0);
                    started = false;
                }
            } else {
                current.append(c);
            }
        }
        if (started || current.length() > 0) {
            args.add(current.toString());
        }
        return args;
    }
}
