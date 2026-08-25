package org.nmox.studio.ui.tasks;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The next sprint, suggested from the one just closed (v2.38.1, the
 * scrum-master follow-up): teams run consecutive same-length sprints
 * with counted names, so the roll-over offer pre-fills exactly that —
 * the trailing number incremented ("Sprint 12" → "Sprint 13"; a name
 * with no number gains " 2"), the window starting the day after the
 * closed sprint ended and keeping its LENGTH. A suggestion only: the
 * dialog stays editable and Cancel starts no sprint. Pure so both
 * rules are unit tests.
 */
final class SprintRoll {

    private static final Pattern TRAILING_NUMBER = Pattern.compile("^(.*?)(\\d+)\\s*$");

    private SprintRoll() {
    }

    /** "Sprint 12" → "Sprint 13"; "Hardening" → "Hardening 2". */
    static String nextName(String closedName) {
        Matcher m = TRAILING_NUMBER.matcher(closedName);
        if (m.matches()) {
            try {
                return m.group(1) + (Long.parseLong(m.group(2)) + 1);
            } catch (NumberFormatException overflow) {
                // a 20-digit "number" is a name, not a counter
            }
        }
        return closedName + " 2";
    }

    /** Day after the closed end, same length. {start, end} as dates. */
    static LocalDate[] nextWindow(LocalDate closedStart, LocalDate closedEnd) {
        long length = java.time.temporal.ChronoUnit.DAYS.between(closedStart, closedEnd);
        LocalDate start = closedEnd.plusDays(1);
        return new LocalDate[] {start, start.plusDays(Math.max(0, length))};
    }
    /**
     * The planning line the Sprint dialog shows when history exists:
     * "Velocity — last 3 sprints: 14, 9, 12 done (avg 12)". Newest
     * first; fewer sprints show what there is; empty history returns
     * null (the dialog shows nothing rather than a zero that reads as
     * a verdict).
     */
    static String velocityLine(java.util.List<TaskBoard.ClosedSprint> history) {
        if (history == null || history.isEmpty()) {
            return null;
        }
        int n = Math.min(3, history.size());
        StringBuilder counts = new StringBuilder();
        int sum = 0;
        for (int i = 0; i < n; i++) {
            TaskBoard.ClosedSprint cs = history.get(history.size() - 1 - i);
            if (i > 0) {
                counts.append(", ");
            }
            counts.append(cs.done());
            sum += cs.done();
        }
        return "Velocity — last " + n + (n == 1 ? " sprint: " : " sprints: ")
                + counts + " done (avg " + Math.round(sum / (double) n) + ")";
    }

}
