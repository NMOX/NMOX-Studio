package org.nmox.studio.editor.blame;

import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** "3 days ago" in the reader's language, with each language's plural forms. */
class RelativeTimeTest {

    static final long NOW = 1_790_000_000_000L;
    static final long MIN = 60_000L;
    static final long HOUR = 60 * MIN;
    static final long DAY = 24 * HOUR;

    private final Locale saved = Locale.getDefault();

    @AfterEach
    void restore() {
        Locale.setDefault(saved);
    }

    private static String ago(long millisBefore) {
        return RelativeTime.ago(NOW - millisBefore, NOW);
    }

    @Test
    @DisplayName("the ladder: just now, minutes, hours, days, weeks, months, years")
    void ladder() {
        assertThat(RelativeTime.span(NOW - 59_000, NOW).unit()).isEqualTo(RelativeTime.Unit.JUST_NOW);
        assertThat(RelativeTime.span(NOW - MIN, NOW)).isEqualTo(new RelativeTime.Span(RelativeTime.Unit.MINUTES, 1));
        assertThat(RelativeTime.span(NOW - 59 * MIN, NOW).count()).isEqualTo(59);
        assertThat(RelativeTime.span(NOW - HOUR, NOW)).isEqualTo(new RelativeTime.Span(RelativeTime.Unit.HOURS, 1));
        assertThat(RelativeTime.span(NOW - DAY, NOW)).isEqualTo(new RelativeTime.Span(RelativeTime.Unit.DAYS, 1));
        assertThat(RelativeTime.span(NOW - 6 * DAY, NOW).unit()).isEqualTo(RelativeTime.Unit.DAYS);
        assertThat(RelativeTime.span(NOW - 7 * DAY, NOW)).isEqualTo(new RelativeTime.Span(RelativeTime.Unit.WEEKS, 1));
        assertThat(RelativeTime.span(NOW - 29 * DAY, NOW)).isEqualTo(new RelativeTime.Span(RelativeTime.Unit.WEEKS, 4));
        assertThat(RelativeTime.span(NOW - 30 * DAY, NOW)).isEqualTo(new RelativeTime.Span(RelativeTime.Unit.MONTHS, 1));
        assertThat(RelativeTime.span(NOW - 364 * DAY, NOW)).isEqualTo(new RelativeTime.Span(RelativeTime.Unit.MONTHS, 11));
        assertThat(RelativeTime.span(NOW - 365 * DAY, NOW)).isEqualTo(new RelativeTime.Span(RelativeTime.Unit.YEARS, 1));
    }

    @Test
    @DisplayName("a commit from the future (a skewed clock) is just now, never a negative age")
    void future() {
        assertThat(RelativeTime.span(NOW + DAY, NOW).unit()).isEqualTo(RelativeTime.Unit.JUST_NOW);
    }

    @Test
    @DisplayName("English says one and many apart")
    void english() {
        Locale.setDefault(Locale.ENGLISH);
        assertThat(ago(10_000)).isEqualTo("just now");
        assertThat(ago(MIN)).isEqualTo("1 minute ago");
        assertThat(ago(5 * MIN)).isEqualTo("5 minutes ago");
        assertThat(ago(HOUR)).isEqualTo("1 hour ago");
        assertThat(ago(3 * DAY)).isEqualTo("3 days ago");
        assertThat(ago(14 * DAY)).isEqualTo("2 weeks ago");
        assertThat(ago(60 * DAY)).isEqualTo("2 months ago");
        assertThat(ago(365 * DAY)).isEqualTo("1 year ago");
        assertThat(ago(3 * 365 * DAY)).isEqualTo("3 years ago");
    }

    @Test
    @DisplayName("Russian inflects across 1 / 2–4 / 5+, and again at 21 and 22")
    void russian() {
        Locale.setDefault(Locale.of("ru"));
        assertThat(ago(MIN)).isEqualTo("1 минуту назад");
        assertThat(ago(3 * MIN)).isEqualTo("3 минуты назад");
        assertThat(ago(11 * MIN)).isEqualTo("11 минут назад");
        assertThat(ago(21 * MIN)).isEqualTo("21 минуту назад");
        assertThat(ago(22 * MIN)).isEqualTo("22 минуты назад");
        assertThat(ago(25 * MIN)).isEqualTo("25 минут назад");
    }

    @Test
    @DisplayName("Polish: only 1 is singular; 22 is 'few', 21 is 'many'")
    void polish() {
        Locale.setDefault(Locale.of("pl"));
        assertThat(ago(MIN)).isEqualTo("1 minutę temu");
        assertThat(ago(21 * MIN)).isEqualTo("21 minut temu");
        assertThat(ago(22 * MIN)).isEqualTo("22 minuty temu");
        assertThat(ago(2 * 365 * DAY)).isEqualTo("2 lata temu");
    }

    @Test
    @DisplayName("Arabic has a dual, and its digits stay the ones a reader types")
    void arabic() {
        Locale.setDefault(Locale.forLanguageTag("ar-u-nu-latn"));
        assertThat(ago(HOUR)).isEqualTo("من ساعة");
        assertThat(ago(2 * HOUR)).isEqualTo("من ساعتين");
        assertThat(ago(3 * HOUR)).isEqualTo("من ⁦3⁩ ساعات");
        assertThat(ago(12 * HOUR)).isEqualTo("من ⁦12⁩ ساعة");
    }
}
