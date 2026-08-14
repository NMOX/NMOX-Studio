package org.nmox.studio.ui.tasks;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * The daily standup, generated from data the product already records
 * (v2.8.0): the board's done stamps and time sessions say what you
 * worked on and for how long, the blocker register says what is stuck,
 * and the git log says what actually landed. Pure — the window gathers
 * the commit lines and the clock, this class only assembles markdown —
 * so every rule a standup lives by is a plain unit test:
 * <ul>
 *   <li>Yesterday and today are CALENDAR days in the given zone, and a
 *       work session spanning midnight is clipped per day, exactly like
 *       the overview's TIME report — the two must never disagree.</li>
 *   <li>A running clock counts up to now and is marked as running.</li>
 *   <li>Sections with nothing to say are OMITTED, not rendered empty —
 *       a standup that reads "Blockers: none" invents information the
 *       reader must still parse.</li>
 *   <li>Card titles and commit subjects are external text; the output
 *       is plain markdown, and anything rendering it goes through the
 *       PLAIN law like every other board string.</li>
 * </ul>
 */
final class StandupReport {

    /** One git commit line, already formatted as "abc1234 subject". */
    record Commit(String line, long whenMillis) {
    }

    private StandupReport() {
    }

    /**
     * Builds the report. {@code commits} may be empty (no repo, git
     * absent) — the section simply doesn't appear.
     */
    static String build(TaskBoard board, List<Commit> commits,
            long nowMillis, ZoneId zone) {
        LocalDate today = LocalDate.ofInstant(
                Instant.ofEpochMilli(nowMillis), zone);
        LocalDate yesterday = today.minusDays(1);
        long todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli();
        long yesterdayStart = yesterday.atStartOfDay(zone)
                .toInstant().toEpochMilli();

        StringBuilder md = new StringBuilder();
        md.append("## Standup — ").append(today).append('\n');

        List<String> yest = new ArrayList<>();
        List<String> tod = new ArrayList<>();
        int last = board.columnCount() - 1;
        for (int i = 0; i < board.columnCount(); i++) {
            for (TaskBoard.Card c : board.column(i).cards()) {
                long tYest = tracked(c, yesterdayStart, todayStart, nowMillis);
                long tToday = tracked(c, todayStart, nowMillis, nowMillis);
                boolean doneYest = inDay(c.done(), yesterdayStart, todayStart);
                boolean doneToday = inDay(c.done(), todayStart, nowMillis + 1);
                if (doneYest || tYest > 0) {
                    yest.add(line(c, doneYest, tYest, false));
                }
                if (doneToday || tToday > 0 || (c.clockedIn() && i < last)) {
                    tod.add(line(c, doneToday, tToday, c.clockedIn()));
                }
            }
        }
        section(md, "Yesterday", yest);
        section(md, "Today", tod);

        List<String> blockers = new ArrayList<>();
        for (int i = 0; i < last; i++) {
            for (TaskBoard.Card c : board.column(i).cards()) {
                if (c.blocked()) {
                    String owner = c.blockOwner().isEmpty()
                            ? "unowned" : c.blockOwner();
                    blockers.add("- " + c.title() + " — " + owner
                            + " · unblock: " + c.blockAction());
                }
            }
        }
        section(md, "Blockers", blockers);

        List<String> commitLines = new ArrayList<>();
        for (Commit c : commits) {
            if (c.whenMillis() >= yesterdayStart && c.whenMillis() <= nowMillis) {
                commitLines.add("- " + c.line());
            }
        }
        section(md, "Commits (since yesterday)", commitLines);
        return md.toString();
    }

    /** Time this card tracked inside [from, to), running counted to now. */
    private static long tracked(TaskBoard.Card c, long from, long to, long now) {
        long sum = 0;
        for (long[] sn : c.sessions()) {
            long end = sn[1] == 0L ? now : sn[1];
            sum += Math.max(0L, Math.min(end, to) - Math.max(sn[0], from));
        }
        return sum;
    }

    private static boolean inDay(long stamp, long from, long to) {
        return stamp >= from && stamp < to;
    }

    private static String line(TaskBoard.Card c, boolean done, long trackedMs,
            boolean running) {
        StringBuilder b = new StringBuilder("- ");
        b.append(c.title());
        List<String> notes = new ArrayList<>();
        if (done) {
            notes.add("done");
        }
        if (trackedMs > 0) {
            notes.add(BoardStats.duration(trackedMs));
        }
        if (running) {
            notes.add("clock running");
        }
        if (!notes.isEmpty()) {
            b.append(" (").append(String.join(", ", notes)).append(')');
        }
        return b.toString();
    }

    /** Appends "### title" + items; an empty section appends NOTHING. */
    private static void section(StringBuilder md, String title,
            List<String> items) {
        if (items.isEmpty()) {
            return;
        }
        md.append('\n').append("### ").append(title).append('\n');
        for (String item : items) {
            md.append(item).append('\n');
        }
    }
}
