package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;
import org.nmox.studio.rack.ui.controls.ToggleSwitch;

/**
 * PHOSPHOR Terminal: a real scrollback console in the rack. Where
 * MONITOR is a glanceable 8-line readout, PHOSPHOR keeps five
 * thousand lines, lets you scroll back and select text, and follows
 * the tail unless you switch FOLLOW off. Patch any OUT jack in.
 */
public class TerminalDevice extends RackDevice {

    private static final int MAX_LINES = 5_000;

    private final JTextArea screen;
    private final JScrollPane scroll;
    private final ToggleSwitch followSwitch;
    private int lineCount;

    public TerminalDevice() {
        super("terminal", "PHOSPHOR", "SCROLLBACK TERMINAL", new Color(57, 255, 20), 5);

        screen = new JTextArea();
        screen.setEditable(false);
        screen.setLineWrap(false);
        screen.setBackground(new Color(7, 14, 7));
        screen.setForeground(RackStyle.LCD_TEXT);
        screen.setCaretColor(RackStyle.LCD_TEXT);
        screen.setSelectionColor(new Color(40, 90, 40));
        screen.setSelectedTextColor(Color.WHITE);
        screen.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        screen.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        scroll = new JScrollPane(screen);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(8, 8, 9), 2));
        scroll.getViewport().setBackground(screen.getBackground());
        int w = RackStyle.RACK_WIDTH - 2 * RackStyle.EAR_WIDTH - 120;
        int h = 5 * RackStyle.UNIT - 60;
        scroll.setPreferredSize(new java.awt.Dimension(w, h));
        place(scroll, RackStyle.EAR_WIDTH + 14, 44);

        int sideX = RackStyle.RACK_WIDTH - RackStyle.EAR_WIDTH - 92;
        RackButton clear = place(new RackButton("CLEAR", new Color(255, 190, 60)), sideX, 44);
        followSwitch = place(new ToggleSwitch("FOLLOW", true), sideX, 96);

        clear.addActionListener(e -> {
            screen.setText("");
            lineCount = 0;
        });

        addInPort("in", "IN", SignalType.DATA);

        param("follow", followSwitch);
    }

    @Override
    public void receive(Port in, Signal signal) {
        if (signal.type() != SignalType.DATA) {
            return;
        }
        String line = signal.payload() == null ? "" : signal.payload();
        onEdt(() -> {
            screen.append(line);
            screen.append("\n");
            lineCount++;
            if (lineCount > MAX_LINES) {
                // drop the oldest tenth in one cut; trimming per line thrashes
                int cut = 0;
                String text = screen.getText();
                for (int dropped = 0; dropped < MAX_LINES / 10 && cut >= 0; dropped++) {
                    cut = text.indexOf('\n', cut + 1);
                }
                if (cut > 0) {
                    screen.replaceRange("", 0, cut + 1);
                    lineCount -= MAX_LINES / 10;
                }
            }
            if (followSwitch.isOn()) {
                screen.setCaretPosition(screen.getDocument().getLength());
            }
        });
    }
}
