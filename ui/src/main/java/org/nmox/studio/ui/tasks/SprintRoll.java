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
}
