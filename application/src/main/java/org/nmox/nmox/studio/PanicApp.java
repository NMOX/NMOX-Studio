package org.nmox.nmox.studio;

import java.awt.BorderLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * Simple UI that shows a "DON'T PANIC" message with an OK button to quit.
 */
public class PanicApp {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("NMOX Studio");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            JLabel label = new JLabel("DON'T PANIC", SwingConstants.CENTER);
            label.setFont(label.getFont().deriveFont(20f));
            JButton ok = new JButton("OK");
            ok.addActionListener(e -> System.exit(0));

            JPanel panel = new JPanel(new BorderLayout());
            panel.add(label, BorderLayout.CENTER);
            panel.add(ok, BorderLayout.SOUTH);

            frame.getContentPane().add(panel);
            frame.setSize(300, 150);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
