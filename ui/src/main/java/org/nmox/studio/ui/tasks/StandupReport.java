package org.nmox.studio.ui.tasks;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.openide.util.NbBundle.Messages;

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
 *   <li>The report speaks the reader's language (v2.153.0). Until then its
 *       section names were English in every build, under an Overview and a
 *       button already translated; the tutorials' translators found it in
 *       all fourteen languages. Numbers go in as text so a locale's own
 *       digits never reach a report pasted into a team's chat.</li>
 * </ul>
 */
@Messages({
    "StandupReport_heading=Standup — {0}",
    "StandupReport_sprintDay=day {0} of {1}",
    "StandupReport_yesterday=Yesterday",
    "StandupReport_today=Today",
    "StandupReport_blockers=Blockers",
    "StandupReport_commits=Commits (since yesterday)",
    "StandupReport_done=done",
    "StandupReport_clockRunning=clock running",
    "StandupReport_unowned=unowned",
    "StandupReport_unblock=unblock: {0}",
    "# {0} a card title, {1} its notes",
    "StandupReport_withNotes={0} ({1})",
    "# two notes on one card",
    "StandupReport_noteJoin={0}, {1}"
})
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
        md.append("## ").append(Bundle.StandupReport_heading(today.toString()));
        // the sprint context (v2.38.2): inside the window the header
        // carries "Sprint 8 · day 3 of 14" — the one number a standup
        // opens with; outside the window (a sprint set for next week)
        // the name alone, and no sprint means no clause at all
        if (board.hasSprint()) {
            LocalDate ss = LocalDate.ofInstant(
                    Instant.ofEpochMilli(board.sprintStart()), zone);
            LocalDate se = LocalDate.ofInstant(
                    Instant.ofEpochMilli(board.sprintEnd()), zone);
            md.append(" · ").append(board.sprintName());
            if (!today.isBefore(ss) && !today.isAfter(se)) {
                long day = java.time.temporal.ChronoUnit.DAYS.between(ss, today) + 1;
                long len = java.time.temporal.ChronoUnit.DAYS.between(ss, se) + 1;
                md.append(" · ").append(Bundle.StandupReport_sprintDay(
                        String.valueOf(day), String.valueOf(len)));
            }
        }
        md.append('\n');

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
        section(md, Bundle.StandupReport_yesterday(), yest);
        section(md, Bundle.StandupReport_today(), tod);

        List<String> blockers = new ArrayList<>();
        for (int i = 0; i < last; i++) {
            for (TaskBoard.Card c : board.column(i).cards()) {
                if (c.blocked()) {
                    String owner = c.blockOwner().isEmpty()
                            ? Bundle.StandupReport_unowned() : c.blockOwner();
                    blockers.add("- " + c.title() + " — " + owner
                            + " · " + Bundle.StandupReport_unblock(c.blockAction()));
                }
            }
        }
        section(md, Bundle.StandupReport_blockers(), blockers);

        List<String> commitLines = new ArrayList<>();
        for (Commit c : commits) {
            if (c.whenMillis() >= yesterdayStart && c.whenMillis() <= nowMillis) {
                commitLines.add("- " + c.line());
            }
        }
        section(md, Bundle.StandupReport_commits(), commitLines);
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
        List<String> notes = new ArrayList<>();
        if (done) {
            notes.add(Bundle.StandupReport_done());
        }
        if (trackedMs > 0) {
            notes.add(BoardStats.duration(trackedMs));
        }
        if (running) {
            notes.add(Bundle.StandupReport_clockRunning());
        }
        if (notes.isEmpty()) {
            return "- " + c.title();
        }
        // folded pairwise so a language that joins with a full-width comma or
        // wraps in full-width brackets says so in its bundle, not here
        String joined = notes.get(0);
        for (int i = 1; i < notes.size(); i++) {
            joined = Bundle.StandupReport_noteJoin(joined, notes.get(i));
        }
        return "- " + Bundle.StandupReport_withNotes(c.title(), joined);
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
