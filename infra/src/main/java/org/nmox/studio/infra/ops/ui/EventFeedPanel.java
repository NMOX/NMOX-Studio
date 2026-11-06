package org.nmox.studio.infra.ops.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import org.nmox.studio.core.util.Clocks;
import org.nmox.studio.core.util.TextDirection;
import org.nmox.studio.infra.ops.OpsEvent;
import org.nmox.studio.infra.ops.OpsHub;
import org.openide.util.NbBundle.Messages;

/**
 * The feed: every event the Hub recorded, newest at the bottom, with a
 * time a person reads. A plain read-only text area — selectable,
 * copyable, spoken by a screen reader — because a log is text.
 */
@Messages({
    "EventFeed_name=Ops Hub events"
})
public final class EventFeedPanel extends JPanel {

    private final JTextArea area = TextDirection.followsReader(new JTextArea(5, 40));
    private final OpsHub hub;
    private final OpsHub.Listener listener = new OpsHub.Listener() {
        @Override
        public void event(OpsEvent event) {
            append(event);
        }

        @Override
        public void stateChanged() {
        }
    };

    public EventFeedPanel(OpsHub hub) {
        super(new BorderLayout());
        this.hub = hub;
        area.setEditable(false);
        area.setFont(HudStyle.mono(11f));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.getAccessibleContext().setAccessibleName(Bundle.EventFeed_name());
        add(new JScrollPane(area), BorderLayout.CENTER);
        setPreferredSize(new Dimension(400, 110));
    }

    @Override
    public void addNotify() {
        super.addNotify();
        area.setText("");
        for (OpsEvent e : hub.feed()) {
            append(e);
        }
        hub.addListener(listener);
    }

    @Override
    public void removeNotify() {
        hub.removeListener(listener);
        super.removeNotify();
    }

    private void append(OpsEvent e) {
        String mark = switch (e.kind()) {
            case OK -> "●";
            case FAIL -> "✕";
            case STEP -> "▸";
            default -> "·";
        };
        String time = Clocks.display(e.atMillis());
        area.append(time + "  " + mark + " " + e.text() + "\n");
        area.setCaretPosition(area.getDocument().getLength());
    }
}
