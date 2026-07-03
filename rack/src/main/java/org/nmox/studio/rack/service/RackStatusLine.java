package org.nmox.studio.rack.service;

import java.awt.Component;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.Timer;
import org.nmox.studio.rack.model.RackDevice;
import org.openide.awt.StatusLineElementProvider;
import org.openide.util.lookup.ServiceProvider;

/**
 * The status line answers "is anything running?" without opening the
 * rack: a green lane count appears while dev servers, tunnels, or
 * watch builds are live, and vanishes when the rack is quiet.
 */
@ServiceProvider(service = StatusLineElementProvider.class, position = 600)
public class RackStatusLine implements StatusLineElementProvider {

    @Override
    public Component getStatusLineElement() {
        return new LiveCountLabel();
    }

    /** Polls only while it is actually in the status bar. */
    private static final class LiveCountLabel extends JLabel {

        private final Timer poll = new Timer(2_000, e -> refresh());

        LiveCountLabel() {
            setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        }

        @Override
        public void addNotify() {
            super.addNotify();
            poll.start();
        }

        @Override
        public void removeNotify() {
            poll.stop();
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
                setText("");
                setToolTipText(null);
            } else {
                setText("● " + live + " running");
                setForeground(new java.awt.Color(80, 200, 110));
                setToolTipText(names.toString());
            }
        }
    }
}
