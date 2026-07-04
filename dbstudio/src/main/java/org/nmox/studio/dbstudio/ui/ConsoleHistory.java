package org.nmox.studio.dbstudio.ui;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * The console's run history: the last {@value #CAPACITY} texts that were
 * actually executed, newest first, each stamped with the engine it ran
 * against and when. Re-running a text the history already holds (same
 * text, same engine) moves it to the front instead of duplicating it —
 * shell-history semantics. Blank texts are never recorded.
 *
 * <p>Pure model, no Swing — the History tab renders {@link #entries()}
 * and this class stays unit-testable headless. Thread-safe: RUN
 * completions land from the EDT today, but the synchronization costs
 * nothing and removes the assumption.
 */
final class ConsoleHistory {

    /** How many runs are kept; the oldest falls off beyond this. */
    static final int CAPACITY = 50;

    /**
     * One remembered run.
     *
     * @param text      the console text exactly as executed
     * @param engine    display name of the engine it ran against
     * @param timestamp epoch millis of the run
     */
    record Entry(String text, String engine, long timestamp) {
    }

    private final Deque<Entry> entries = new ArrayDeque<>();

    /** Records a run; blank text is ignored, duplicates move to the front. */
    synchronized void add(String text, String engine, long timestamp) {
        if (text == null || text.isBlank()) {
            return;
        }
        String engineName = engine == null ? "" : engine;
        entries.removeIf(e -> e.text().equals(text) && e.engine().equals(engineName));
        entries.addFirst(new Entry(text, engineName, timestamp));
        while (entries.size() > CAPACITY) {
            entries.removeLast();
        }
    }

    /** The remembered runs, newest first — an immutable snapshot. */
    synchronized List<Entry> entries() {
        return List.copyOf(entries);
    }

    /**
     * Forgets everything — the project switched, and the new project's
     * persisted history is about to be re-seeded via {@link #add}.
     */
    synchronized void clear() {
        entries.clear();
    }
}
