package org.nmox.studio.rack.service;

import java.awt.Component;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.Timer;
import org.nmox.studio.rack.model.RackDevice;
import org.openide.awt.StatusLineElementProvider;
import org.openide.util.lookup.ServiceProvider;

/**
 * The status line answers "is anything running?" without opening the
 * rack: a green lane count appears while dev servers, tunnels, or
 * watch builds are live, a serving chip lists what is actually
 * reachable (click it to open a URL in the browser), and a transient
 * note appears when .env changes — running processes keep their
 * launch-time env, so the note says exactly that.
 */
@ServiceProvider(service = StatusLineElementProvider.class, position = 600)
public class RackStatusLine implements StatusLineElementProvider {

    @Override
    public Component getStatusLineElement() {
        return new RackStrip();
    }

    /** Chip text: "⇄ serving: <first url>" (+N for more); null when idle. */
    static String chipText(List<ServingRegistry.Serving> servings) {
        if (servings.isEmpty()) {
            return null;
        }
        int more = servings.size() - 1;
        return "⇄ serving: " + servings.get(0).url() + (more > 0 ? " +" + more : "");
    }

    /** Tooltip: every serving, one per line. */
    static String chipTooltip(List<ServingRegistry.Serving> servings) {
        if (servings.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("<html>");
        for (int i = 0; i < servings.size(); i++) {
            if (i > 0) {
                sb.append("<br>");
            }
            ServingRegistry.Serving s = servings.get(i);
            sb.append(s.deviceTitle()).append(" — ").append(s.url());
        }
        return sb.append("</html>").toString();
    }

    /** Polls and listens only while it is actually in the status bar. */
    private static final class RackStrip extends javax.swing.JPanel {

        private final JLabel liveLabel = new JLabel();
        private final JLabel servingLabel = new JLabel();
        private final JLabel envLabel = new JLabel();
        private final Timer poll = new Timer(2_000, e -> refresh());
        private final ServingRegistry.Listener servingListener =
                () -> javax.swing.SwingUtilities.invokeLater(this::refresh);

        RackStrip() {
            setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
            setOpaque(false);
            liveLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
            servingLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
            envLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
            servingLabel.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            servingLabel.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    showServingMenu();
                }
            });
            add(liveLabel);
            add(servingLabel);
            add(envLabel);
        }

        @Override
        public void addNotify() {
            super.addNotify();
            ServingRegistry.getDefault().addListener(servingListener);
            poll.start();
        }

        @Override
        public void removeNotify() {
            poll.stop();
            ServingRegistry.getDefault().removeListener(servingListener);
            super.removeNotify();
        }

        private void refresh() {
            int live = 0;
            StringBuilder names = new StringBuilder();
            try {
                for (RackDevice d : RackService.getDefault().getRack().getDevices()) {
                    if (d.isLive()) {
                        live++;
                        if (names.length() > 0) {
                            names.append(", ");
                        }
                        names.append(d.getTitle());
                    }
                }
            } catch (RuntimeException ex) {
                return; // rack unavailable mid-shutdown; keep the last text
            }
            if (live == 0) {
                liveLabel.setText("");
                liveLabel.setToolTipText(null);
            } else {
                liveLabel.setText("● " + live + " running");
                liveLabel.setForeground(new java.awt.Color(80, 200, 110));
                liveLabel.setToolTipText(names.toString());
            }
            List<ServingRegistry.Serving> servings = ServingRegistry.getDefault().snapshot();
            String chip = chipText(servings);
            servingLabel.setText(chip == null ? "" : chip);
            servingLabel.setForeground(new java.awt.Color(90, 170, 235));
            servingLabel.setToolTipText(chipTooltip(servings));
            boolean envNote = RackService.getDefault().envNoteActive();
            envLabel.setText(envNote ? "env changed — restarts pick it up" : "");
            envLabel.setForeground(new java.awt.Color(222, 178, 80));
        }

        /** One menu entry per serving; selecting opens the URL in the browser. */
        private void showServingMenu() {
            List<ServingRegistry.Serving> servings = ServingRegistry.getDefault().snapshot();
            if (servings.isEmpty()) {
                return;
            }
            JPopupMenu menu = new JPopupMenu();
            for (ServingRegistry.Serving s : servings) {
                JMenuItem item = new JMenuItem(s.deviceTitle() + " — " + s.url());
                item.addActionListener(e -> openInBrowser(s.url()));
                menu.add(item);
            }
            menu.show(servingLabel, 0, -menu.getPreferredSize().height);
        }

        /** The SCOPE device's open-URL idiom: the system browser. */
        private static void openInBrowser(String url) {
            try {
                if (java.awt.Desktop.isDesktopSupported()
                        && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                    java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
                }
            } catch (Exception ignored) {
                // no browser available; the click just does nothing
            }
        }
    }
}
