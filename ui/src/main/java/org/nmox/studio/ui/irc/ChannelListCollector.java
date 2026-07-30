package org.nmox.studio.ui.irc;

import java.util.ArrayList;
import java.util.List;
import org.nmox.studio.ui.irc.protocol.IrcMessage;
import org.nmox.studio.ui.irc.protocol.MircFormat;

/**
 * Collects a {@code /list} reply — 321 (start), a 322 per channel,
 * 323 (end) — into rows for the channel-browser dialog. BOUNDED: a big
 * network's LIST is tens of thousands of channels, so only the first
 * {@link #CAP} rows are kept while every 322 past the cap still counts
 * ({@link #totalSeen()}), letting the dialog say "showing first 2000 of
 * 48123" instead of silently lying or growing without limit (the house
 * bounded-collection law). Pure message-in/rows-out; one collector per
 * {@code /list} request.
 */
public final class ChannelListCollector {

    /** Rows kept in memory; everything past this is counted, not stored. */
    static final int CAP = 2000;

    /** One channel from a 322: name, visible-user count, topic. */
    public record Row(String name, int users, String topic) {
    }

    private final List<Row> rows = new ArrayList<>();
    private int totalSeen;
    private boolean complete;

    /**
     * Feeds one numeric; returns {@code true} exactly when 323 completed
     * the list. Non-LIST numerics are ignored.
     */
    public boolean accept(IrcMessage msg) {
        switch (msg.command()) {
            case "321" -> {
                // list header: ignorable
            }
            case "322" -> {
                // :srv 322 me #chan 42 :topic text
                totalSeen++;
                if (rows.size() < CAP) {
                    int users;
                    try {
                        users = Integer.parseInt(msg.param(2));
                    } catch (NumberFormatException notANumber) {
                        users = 0;
                    }
                    String topic = msg.trailing() == null ? "" : MircFormat.stripToText(msg.trailing());
                    rows.add(new Row(msg.param(1), users, topic));
                }
            }
            case "323" -> {
                complete = true;
                return true;
            }
            default -> {
                // not ours
            }
        }
        return false;
    }

    /** The collected rows (at most {@link #CAP}). */
    public List<Row> rows() {
        return List.copyOf(rows);
    }

    /** Every 322 that arrived, including those past the cap. */
    public int totalSeen() {
        return totalSeen;
    }

    /** True once 323 arrived. */
    public boolean complete() {
        return complete;
    }

    /** True when rows were dropped past the cap (the dialog's honesty flag). */
    public boolean truncated() {
        return totalSeen > rows.size();
    }
}
