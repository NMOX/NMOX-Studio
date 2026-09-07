package org.nmox.studio.rack.service;

import org.nmox.studio.core.util.PlainText;
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
@org.openide.util.NbBundle.Messages({
    "RackStatusLine_servingChip=⇄ serving: {0}",
    "RackStatusLine_moreSuffix= +{0}",
    "RackStatusLine_agentChip=⌁ agent port :{0}",
    "RackStatusLine_agentTooltip=The Agent Port is listening on 127.0.0.1:{0} (read-only) — {1}{2}. Click for the config, or to stop it.",
    "RackStatusLine_noAgentStreaming=no agent streaming",
    "RackStatusLine_oneAgentStreaming=one agent streaming",
    "RackStatusLine_agentsStreaming={0} agents streaming",
    "RackStatusLine_noRequestYet=; no request yet",
    "RackStatusLine_requestJustNow=; a request just now",
    "RackStatusLine_lastRequestAgo=; last request {0} ago",
    "RackStatusLine_seconds={0} s",
    "RackStatusLine_minutes={0} min",
    "RackStatusLine_hours={0} h",
    "RackStatusLine_servingLine={0} — {1}",
    "RackStatusLine_running=● {0} running",
    "RackStatusLine_envChanged=env changed — restarts pick it up",
    "RackStatusLine_stopItem=    Stop {0}",
    "RackStatusLine_alreadyStopped={0} had already stopped",
    "RackStatusLine_stopped=Stopped: {0}"
})
public class RackStatusLine implements StatusLineElementProvider {

    @Override
    public Component getStatusLineElement() {
        return new RackStrip();
    }

    /** The live run that registered this serving under its own id (the ▶, the NPM lane), or null for a rack device's server. */
    static org.nmox.studio.core.spi.LiveRuns.Run runOwning(ServingRegistry.Serving s,
            java.util.List<org.nmox.studio.core.spi.LiveRuns.Run> live) {
        for (org.nmox.studio.core.spi.LiveRuns.Run r : live) {
            if (r.id().equals(s.deviceId())) {
                return r;
            }
        }
        return null;
    }

    /** Chip text: "⇄ serving: <first url>" (+N for more); null when idle. */
    static String chipText(List<ServingRegistry.Serving> servings) {
        if (servings.isEmpty()) {
            return null;
        }
        int more = servings.size() - 1;
        return Bundle.RackStatusLine_servingChip(servings.get(0).url()) + (more > 0 ? Bundle.RackStatusLine_moreSuffix(String.valueOf(more)) : "");
    }

    /** Tooltip: every serving, one per line. */
    /** HTML-escapes an external string for the tooltip that means its markup. */
    static String esc(String s) {
        return PlainText.escape(s);
    }

    /** Agent Port chip: "⌁ agent port :N" while it listens (v2.84.0) — a port that can read the IDE is never invisible; null when off. */
    static String agentChipText(int[] listening) {
        return listening == null ? null : Bundle.RackStatusLine_agentChip(String.valueOf(listening[0]));
    }

    static String agentChipTooltip(int[] listening) {
        if (listening == null) {
            return null;
        }
        int n = listening[1];
        int since = listening.length > 2 ? listening[2] : -1;
        return Bundle.RackStatusLine_agentTooltip(String.valueOf(listening[0]),
                n == 0 ? Bundle.RackStatusLine_noAgentStreaming() : n == 1 ? Bundle.RackStatusLine_oneAgentStreaming() : Bundle.RackStatusLine_agentsStreaming(String.valueOf(n)),
                since < 0 ? Bundle.RackStatusLine_noRequestYet() : since < 2 ? Bundle.RackStatusLine_requestJustNow() : Bundle.RackStatusLine_lastRequestAgo(sinceText(since)));
    }

    /** "12 s", "3 min", "2 h" — the coarse clock a tooltip wants. */
    static String sinceText(int seconds) {
        if (seconds < 90) {
            return Bundle.RackStatusLine_seconds(String.valueOf(seconds));
        }
        if (seconds < 5_400) {
            return Bundle.RackStatusLine_minutes(String.valueOf(Math.round(seconds / 60.0)));
        }
        return Bundle.RackStatusLine_hours(String.valueOf(Math.round(seconds / 3600.0)));
    }

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
            // the tooltip MEANS its markup (<br> between servings), so every
            // external string is escaped (v2.75.0): since v2.70.0 a title can
            // carry an npm SCRIPT NAME from a cloned package.json, and a
            // JToolTip renders <html> text — the v1.208.0 fetch class, one
            // component over from the trees the v1.306 gate covers
            sb.append(esc(s.deviceTitle())).append(" — ").append(esc(s.url()));
        }
        return sb.append("</html>").toString();
    }

    /** Polls and listens only while it is actually in the status bar. */
    private static final class RackStrip extends javax.swing.JPanel {

        private final JLabel liveLabel = new JLabel();
        private final JLabel servingLabel = new JLabel();
        private final JLabel envLabel = new JLabel();
        private final JLabel agentLabel = new JLabel();
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
            agentLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
            agentLabel.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            agentLabel.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    // the action's own dialog: config + Stop; a no-op when nothing listens
                    new org.nmox.studio.rack.mcp.AgentPortAction().actionPerformed(null);
                }
            });
            add(liveLabel);
            add(servingLabel);
            add(agentLabel);
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
                liveLabel.setText(Bundle.RackStatusLine_running(String.valueOf(live)));
                liveLabel.setForeground(new java.awt.Color(80, 200, 110));
                liveLabel.setToolTipText(PlainText.plain(names.toString()));
            }
            List<ServingRegistry.Serving> servings = ServingRegistry.getDefault().snapshot();
            String chip = chipText(servings);
            servingLabel.setText(PlainText.plain(chip == null ? "" : chip));
            servingLabel.setForeground(new java.awt.Color(90, 170, 235));
            servingLabel.setToolTipText(PlainText.plain(chipTooltip(servings)));
            int[] listening = org.nmox.studio.rack.mcp.AgentPortAction.listening();
            String agent = agentChipText(listening);
            agentLabel.setText(PlainText.plain(agent == null ? "" : agent));
            agentLabel.setForeground(new java.awt.Color(200, 150, 235));
            agentLabel.setToolTipText(PlainText.plain(agentChipTooltip(listening)));
            boolean envNote = RackService.getDefault().envNoteActive();
            envLabel.setText(PlainText.plain(envNote ? Bundle.RackStatusLine_envChanged() : ""));
            envLabel.setForeground(new java.awt.Color(222, 178, 80));
        }

        /** One menu entry per serving; selecting opens the URL in the browser. */
        private void showServingMenu() {
            List<ServingRegistry.Serving> servings = ServingRegistry.getDefault().snapshot();
            if (servings.isEmpty()) {
                return;
            }
            JPopupMenu menu = new JPopupMenu();
            java.util.List<org.nmox.studio.core.spi.LiveRuns.Run> live = org.nmox.studio.core.spi.LiveRuns.live();
            for (ServingRegistry.Serving s : servings) {
                JMenuItem item = new JMenuItem(PlainText.plain(Bundle.RackStatusLine_servingLine(s.deviceTitle(), s.url())));
                item.addActionListener(e -> ServingLinks.open(s.url()));
                menu.add(item);
                // a serving a run owns gets its Stop beside its Open (v2.73.0):
                // the chip is where the eye already is when a server is up
                if (runOwning(s, live) != null) {
                    JMenuItem stop = new JMenuItem(Bundle.RackStatusLine_stopItem(s.deviceTitle()));
                    stop.addActionListener(e -> {
                        org.nmox.studio.core.spi.LiveRuns.Run r = org.nmox.studio.core.spi.LiveRuns.stop(s.deviceId());
                        org.openide.awt.StatusDisplayer.getDefault().setStatusText(
                                org.nmox.studio.core.util.PlainStatus.text(r == null ? Bundle.RackStatusLine_alreadyStopped(s.deviceTitle()) : Bundle.RackStatusLine_stopped(s.deviceTitle())));
                    });
                    menu.add(stop);
                }
            }
            menu.show(servingLabel, 0, -menu.getPreferredSize().height);
        }
    }
}
