import java.awt.Point;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * Does AltGr+7 fire a Ctrl+Alt+7 chord? (ledger 128)
 *
 * <p>Most of the product's window chords are Ctrl+Alt+digit on Windows and
 * Linux. On a German keyboard AltGr+7 types an opening brace, and Windows
 * presents AltGr as Left Ctrl + Right Alt. If Java hands Swing that key
 * press as plain Ctrl+Alt, every such chord steals a character the user
 * needs to write code.
 *
 * <p>This asks the real toolkit on a real desktop, with the keyboard layout
 * the caller has loaded. A text field binds an action to Ctrl+Alt+7 the way
 * a keymap does. The robot presses Ctrl+Alt+7, then AltGr+7, and the probe
 * prints what arrived: the modifiers of each key press, the stroke Swing
 * derives from it, whether the bound action ran, and what was typed.
 *
 * <p>Run by the Keyboard probe workflow: {@code java scripts/probes/AltGrProbe.java}.
 * Exit 0 always; the output is the result.
 */
public class AltGrProbe {

    public static void main(String[] args) throws Exception {
        List<String> seen = new ArrayList<>();
        int[] fired = {0};
        JFrame frame = new JFrame("AltGr probe");
        JTextField field = new JTextField(24);
        field.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_7,
                InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK), "chord");
        field.getActionMap().put("chord", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                fired[0]++;
            }
        });
        field.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                seen.add("  pressed  " + KeyEvent.getKeyText(e.getKeyCode())
                        + "  modifiers=[" + InputEvent.getModifiersExText(e.getModifiersEx()) + "]"
                        + "  stroke=[" + KeyStroke.getKeyStrokeForEvent(e) + "]");
            }

            @Override
            public void keyTyped(KeyEvent e) {
                seen.add("  typed    '" + e.getKeyChar() + "' (" + (int) e.getKeyChar() + ")"
                        + "  modifiers=[" + InputEvent.getModifiersExText(e.getModifiersEx()) + "]");
            }
        });
        SwingUtilities.invokeAndWait(() -> {
            frame.add(field);
            frame.pack();
            frame.setLocation(240, 240);
            frame.setAlwaysOnTop(true);
            frame.setVisible(true);
            frame.toFront();
            field.requestFocusInWindow();
        });
        Robot robot = new Robot();
        robot.setAutoDelay(120);
        robot.waitForIdle();
        Point at = field.getLocationOnScreen();
        robot.mouseMove(at.x + 12, at.y + 8);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();

        System.out.println("java " + System.getProperty("java.version") + " on " + System.getProperty("os.name"));
        System.out.println("keyboard layout of the focused field: " + field.getInputContext().getLocale());
        System.out.println("focus owner is the field: " + field.isFocusOwner());

        // what a plain 7 types tells which layout the keys really have
        robot.keyPress(KeyEvent.VK_SHIFT);
        robot.keyPress(KeyEvent.VK_7);
        robot.keyRelease(KeyEvent.VK_7);
        robot.keyRelease(KeyEvent.VK_SHIFT);
        robot.waitForIdle();
        System.out.println("\nShift+7 typed: \"" + text(field) + "\"  ('/' on a German layout, '&' on a US one)");
        clear(field, seen);

        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.keyPress(KeyEvent.VK_ALT);
        robot.keyPress(KeyEvent.VK_7);
        robot.keyRelease(KeyEvent.VK_7);
        robot.keyRelease(KeyEvent.VK_ALT);
        robot.keyRelease(KeyEvent.VK_CONTROL);
        robot.waitForIdle();
        System.out.println("\nCtrl+Alt+7 (the chord itself):");
        seen.forEach(System.out::println);
        System.out.println("  the bound action ran " + fired[0] + " time(s); typed \"" + text(field) + "\"");
        int chordRuns = fired[0];
        clear(field, seen);
        fired[0] = 0;

        robot.keyPress(KeyEvent.VK_ALT_GRAPH);
        robot.keyPress(KeyEvent.VK_7);
        robot.keyRelease(KeyEvent.VK_7);
        robot.keyRelease(KeyEvent.VK_ALT_GRAPH);
        robot.waitForIdle();
        System.out.println("\nAltGr+7 (what a German keyboard uses to type an opening brace):");
        seen.forEach(System.out::println);
        System.out.println("  the bound action ran " + fired[0] + " time(s); typed \"" + text(field) + "\"");

        System.out.println("\nRESULT chord-fires-on-its-own-keys=" + (chordRuns > 0)
                + " chord-fires-on-AltGr=" + (fired[0] > 0)
                + " AltGr-typed=\"" + text(field) + "\"");
        frame.dispose();
        System.exit(0);
    }

    private static String text(JTextField field) throws Exception {
        String[] out = new String[1];
        SwingUtilities.invokeAndWait(() -> out[0] = field.getText());
        return out[0];
    }

    private static void clear(JTextField field, List<String> seen) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            field.setText("");
            seen.clear();
        });
    }
}
