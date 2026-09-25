package org.nmox.studio.editor.blame;

import org.openide.util.NbBundle.Messages;

/**
 * "3 days ago", in the reader's language (3.2.0, line blame).
 *
 * <p>The product had one relative age before this, Contract Studio's
 * address-book column ({@code web3.engine.DisplayValues.age}: "5 min ago",
 * "2 d ago"). It is abbreviated for a table column, carries no plural forms
 * and lives in a module the editor does not depend on, so it could not be
 * reused; this one speaks in sentences with each language's plural branches.
 *
 * <p>The ladder: under a minute (or a time in the future — a skewed clock) is
 * "just now"; then minutes, hours, days up to six, weeks up to four, months up
 * to eleven, then years. Each count arrives in a {@code {0,choice,…}} value so
 * a translation carries its own plural forms (Polish, Russian and Ukrainian
 * inflect across 1 / 2–4 / 5+, repeating at 21, 22, 31…; Arabic has a dual).
 */
@Messages({
    "RelativeTime_justNow=just now",
    "# {0} - minutes",
    "RelativeTime_minutes={0,choice,1#1 minute ago|1<{0,number,0} minutes ago}",
    "# {0} - hours",
    "RelativeTime_hours={0,choice,1#1 hour ago|1<{0,number,0} hours ago}",
    "# {0} - days",
    "RelativeTime_days={0,choice,1#1 day ago|1<{0,number,0} days ago}",
    "# {0} - weeks",
    "RelativeTime_weeks={0,choice,1#1 week ago|1<{0,number,0} weeks ago}",
    "# {0} - months",
    "RelativeTime_months={0,choice,1#1 month ago|1<{0,number,0} months ago}",
    "# {0} - years",
    "RelativeTime_years={0,choice,1#1 year ago|1<{0,number,0} years ago}"
})
public final class RelativeTime {

    private RelativeTime() {
    }

    /** The unit and count a moment falls into; pure, so the ladder is testable without a bundle. */
    public enum Unit { JUST_NOW, MINUTES, HOURS, DAYS, WEEKS, MONTHS, YEARS }

    /** A rung of the ladder: the unit and its count (0 for {@link Unit#JUST_NOW}). */
    public record Span(Unit unit, long count) {
    }

    /** Which rung {@code thenMillis} sits on, seen from {@code nowMillis}. */
    public static Span span(long thenMillis, long nowMillis) {
        long seconds = (nowMillis - thenMillis) / 1000;
        if (seconds < 60) {
            return new Span(Unit.JUST_NOW, 0); // includes a future time: never "-3 minutes ago"
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return new Span(Unit.MINUTES, minutes);
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return new Span(Unit.HOURS, hours);
        }
        long days = hours / 24;
        if (days < 7) {
            return new Span(Unit.DAYS, days);
        }
        if (days < 30) {
            return new Span(Unit.WEEKS, days / 7);
        }
        if (days < 365) {
            return new Span(Unit.MONTHS, Math.min(11, Math.max(1, days / 30)));
        }
        return new Span(Unit.YEARS, days / 365);
    }

    /** "3 days ago" in the reader's language. */
    public static String ago(long thenMillis, long nowMillis) {
        Span s = span(thenMillis, nowMillis);
        // an Integer, not a Long: ChoiceFormat reads any Number, and the counts are small
        Integer n = (int) Math.min(Integer.MAX_VALUE, s.count());
        return switch (s.unit()) {
            case JUST_NOW -> Bundle.RelativeTime_justNow();
            case MINUTES -> Bundle.RelativeTime_minutes(n);
            case HOURS -> Bundle.RelativeTime_hours(n);
            case DAYS -> Bundle.RelativeTime_days(n);
            case WEEKS -> Bundle.RelativeTime_weeks(n);
            case MONTHS -> Bundle.RelativeTime_months(n);
            case YEARS -> Bundle.RelativeTime_years(n);
        };
    }
}
