package org.nmox.studio.core.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

/**
 * Two clocks, and the difference between them is who is reading (v2.104.0).
 *
 * <p>A time a PERSON reads should be written the way their language writes
 * times: {@code 14:32} for a Ukrainian or a German, {@code 2:32 PM} for an
 * American. A time a MACHINE reads — a log line, a filename, a field an
 * agent parses — must not move at all, or the same event is stamped
 * differently on two desks and the two records stop lining up.
 *
 * <p>The product had one clock for both, a hard-coded {@code HH:mm}, so the
 * translated builds were showing a 24-hour clock to every locale that does
 * not use one. Externalizing the strings (v2.97.0 onward) was only half of
 * internationalization; this is some of the other half, where the shape of a
 * value depends on the reader rather than on words.
 *
 * <p>Every date-formatting site in the product is classified as one or the
 * other by {@code ClockSiteLedgerTest}, which fails the build on a new one
 * until it says which it is.
 */
public final class Clocks {

    /** Stable stamps never move: one pattern, everywhere, for every reader. */
    private static final DateTimeFormatter STABLE_TIME =
            DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private static final DateTimeFormatter STABLE_SECONDS =
            DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);

    private Clocks() {
    }

    /**
     * The time as this user's language writes it — for anything painted on
     * screen. Follows a live language switch, because it reads the default
     * locale at call time rather than caching a formatter.
     */
    public static String display(long epochMillis) {
        return display(epochMillis, ZoneId.systemDefault(), Locale.getDefault());
    }

    /** {@link #display(long)} in a given zone — the seam callers with a zone use. */
    public static String display(long epochMillis, ZoneId zone) {
        return display(epochMillis, zone, Locale.getDefault());
    }

    static String display(long epochMillis, ZoneId zone, Locale locale) {
        return DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                .withLocale(locale)
                .format(Instant.ofEpochMilli(epochMillis).atZone(zone));
    }

    /**
     * A locale-aware short-time formatter, for the few callers that hold a
     * {@code LocalTime} rather than an instant. Built per call on purpose:
     * a cached formatter would keep the language it was created in, and
     * language can now change while the IDE runs (v2.103.0).
     */
    public static DateTimeFormatter displayFormatter() {
        return DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault());
    }

    /**
     * The time as every machine reads it — logs, filenames, the fields an
     * agent parses. {@code Locale.ROOT} on purpose: a stamp that changed
     * with the reader would make two people's records incomparable.
     */
    public static String stable(long epochMillis) {
        return stable(epochMillis, ZoneId.systemDefault());
    }

    static String stable(long epochMillis, ZoneId zone) {
        return STABLE_TIME.format(Instant.ofEpochMilli(epochMillis).atZone(zone));
    }

    /** {@link #stable} with seconds, for records that need the finer grain. */
    public static String stableSeconds(long epochMillis) {
        return stableSeconds(epochMillis, ZoneId.systemDefault());
    }

    static String stableSeconds(long epochMillis, ZoneId zone) {
        return STABLE_SECONDS.format(Instant.ofEpochMilli(epochMillis).atZone(zone));
    }
}
