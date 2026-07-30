package org.nmox.studio.ui.irc;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The input line's Up/Down history, one instance per chat target (a
 * shell-style recall: Up walks back through what you sent HERE, Down
 * walks forward and past the newest entry back to the line you were
 * drafting). Capped at {@link #CAP} entries and never persisted —
 * chat lines are conversation, not configuration. Pure and Swing-free;
 * the window owns the map from target to history.
 */
public final class InputHistory {

    /** Entries kept per target; older lines fall off the back. */
    static final int CAP = 100;

    private final Deque<String> lines = new ArrayDeque<>();
    private String[] snapshot;
    private int cursor = -1; // -1 = not browsing
    private String draft = "";

    /** Records a sent line (blank lines and immediate repeats are skipped). */
    public void add(String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        if (!line.equals(lines.peekLast())) {
            lines.addLast(line);
            while (lines.size() > CAP) {
                lines.removeFirst();
            }
        }
        resetCursor();
    }

    /**
     * One step back in time. The first Up stashes {@code current} as the
     * draft so a later Down can restore it. Returns the line to show,
     * or {@code null} when there is no history (leave the field alone).
     */
    public String up(String current) {
        if (lines.isEmpty()) {
            return null;
        }
        if (cursor < 0) {
            snapshot = lines.toArray(new String[0]);
            draft = current == null ? "" : current;
            cursor = snapshot.length - 1;
        } else if (cursor > 0) {
            cursor--;
        }
        return snapshot[cursor];
    }

    /**
     * One step forward; walking past the newest entry restores the
     * stashed draft. Returns {@code null} when not browsing.
     */
    public String down() {
        if (cursor < 0) {
            return null;
        }
        if (cursor >= snapshot.length - 1) {
            resetCursor();
            return draft;
        }
        cursor++;
        return snapshot[cursor];
    }

    /** Stops browsing (any ordinary keystroke does this). */
    public void resetCursor() {
        cursor = -1;
        snapshot = null;
    }

    /** How many lines are remembered (test hook). */
    int size() {
        return lines.size();
    }
}
