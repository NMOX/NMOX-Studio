package org.nmox.studio.ui.browser.devtools;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The DevTools Console tab's data: a bounded ring of log entries.
 * Pure model, no Swing — the panel renders it and registers one change
 * listener. HOSTILE-PAGE LAWS live here: every string that arrives
 * from the page is untrusted, so text is truncated at
 * {@link #TEXT_CAP} chars (honest marker appended) BEFORE storing,
 * and the ring holds at most {@link #CAP} entries — the oldest are
 * dropped and counted so the UI can show an honest
 * "N older entries dropped" row instead of silently forgetting.
 *
 * <p>Threading: confined to one thread (the EDT in production — the
 * bridge marshals FX-thread upcalls before touching this model).
 */
public final class ConsoleModel {

    /** Maximum entries kept; oldest dropped (and counted) past this. */
    public static final int CAP = 1000;

    /** Maximum chars per entry text; longer input is truncated with a marker. */
    public static final int TEXT_CAP = 8000;

    /** Appended when text was cut at {@link #TEXT_CAP}. */
    public static final String TRUNCATED = "…[truncated]";

    /** One console row. */
    public record Entry(String level, String text, long atMillis) {
    }

    private final Deque<Entry> entries = new ArrayDeque<>();
    private long dropped;
    private Runnable listener;

    /** Adds one entry, truncating and evicting per the laws above. */
    public void add(String level, String text, long atMillis) {
        entries.addLast(new Entry(normalizeLevel(level), truncate(text), atMillis));
        while (entries.size() > CAP) {
            entries.removeFirst();
            dropped++;
        }
        fire();
    }

    /** Snapshot of the current entries, oldest first. */
    public List<Entry> entries() {
        return new ArrayList<>(entries);
    }

    /** How many older entries were evicted since the last clear. */
    public long droppedCount() {
        return dropped;
    }

    /** Empties the ring and resets the dropped counter. */
    public void clear() {
        entries.clear();
        dropped = 0;
        fire();
    }

    /** The one change listener (the rendering panel). */
    public void setListener(Runnable r) {
        this.listener = r;
    }

    /** Caps untrusted page text; null becomes an empty string. */
    public static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > TEXT_CAP ? text.substring(0, TEXT_CAP) + TRUNCATED : text;
    }

    /** Unknown/hostile level strings collapse to "log". */
    public static String normalizeLevel(String level) {
        if (level == null) {
            return "log";
        }
        switch (level) {
            case "log":
            case "info":
            case "warn":
            case "error":
            case "debug":
            case "result":
                return level;
            default:
                return "log";
        }
    }

    private void fire() {
        if (listener != null) {
            listener.run();
        }
    }
}
