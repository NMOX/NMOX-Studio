package org.nmox.studio.editor.present;

import java.awt.Color;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Window;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import org.openide.windows.WindowManager;

/**
 * The keystroke display's window: a non-focusable, transparent
 * {@link JWindow} owned by the main frame, painting one rounded dark
 * pill with the chord in a large face at the bottom-centre of the window,
 * hidden by a Swing timer after {@link #LINGER_MS}. It never takes focus
 * (a presenter's next keystroke must land where the last one did) and
 * paints only what {@link KeystrokeHud} hands it — text that is, by that
 * class's rule, a chord and never typed characters.
 */
final class KeystrokeOverlay {

    static final int LINGER_MS = 1600;

    private final JWindow window;
    private final JLabel label = new JLabel("", SwingConstants.CENTER);
    private final Timer hide = new Timer(LINGER_MS, e -> hideNow());
    private String last = "";
    private int repeats;

    KeystrokeOverlay() {
        Frame main = WindowManager.getDefault().getMainWindow();
        window = new JWindow(main);
        window.setType(Window.Type.POPUP);
        window.setFocusableWindowState(false);
        window.setAlwaysOnTop(true);
        window.setBackground(new Color(0, 0, 0, 0));
        JPanel pill = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(20, 20, 24, 225));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 22, 22);
                } finally {
                    g2.dispose();
                }
            }
        };
        pill.setOpaque(false);
        pill.setLayout(new java.awt.BorderLayout());
        pill.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 26, 10, 26));
        label.setForeground(Color.WHITE);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 34f));
        label.getAccessibleContext().setAccessibleName("Keystroke display");
        pill.add(label, java.awt.BorderLayout.CENTER);
        window.setContentPane(pill);
        hide.setRepeats(false);
    }

    /** EDT only. Shows the chord (coalescing a repeat) and re-arms the hide timer. */
    void show(String chord) {
        String text = KeystrokeHud.coalesce(last, repeats, chord);
        repeats = chord.equals(last) ? repeats + 1 : 1;
        last = chord;
        label.setText(text);
        window.pack();
        Frame main = WindowManager.getDefault().getMainWindow();
        Point at = main.getLocationOnScreen();
        window.setLocation(at.x + (main.getWidth() - window.getWidth()) / 2,
                at.y + main.getHeight() - window.getHeight() - 56);
        if (!window.isVisible()) {
            window.setVisible(true);
        }
        hide.restart();
    }

    void hideNow() {
        hide.stop();
        window.setVisible(false);
        last = "";
        repeats = 0;
    }

    /** Off: hidden and its native peer released; a fresh one is built when the mode returns. */
    void dispose() {
        hideNow();
        window.dispose();
    }
}
