package org.nmox.studio.core.util;

import java.time.ZoneId;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Two clocks: one for a person, one for a record (v2.104.0). */
class ClocksTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final long AFTERNOON = java.time.Instant.parse("2026-09-09T14:32:00Z").toEpochMilli();

    @Test
    @DisplayName("the display clock follows the reader: 24-hour where that is written, 12-hour where it is not")
    void displayFollowsTheReader() {
        assertThat(Clocks.display(AFTERNOON, UTC, Locale.GERMANY)).isEqualTo("14:32");
        assertThat(Clocks.display(AFTERNOON, UTC, Locale.forLanguageTag("uk"))).isEqualTo("14:32");
        // Hindi writes a 12-hour clock, and we shipped it a 24-hour one until
        // v2.104.0 — the whole reason this class exists
        assertThat(Clocks.display(AFTERNOON, UTC, Locale.forLanguageTag("hi")))
                .startsWith("2:32").doesNotStartWith("14:");
        assertThat(Clocks.display(AFTERNOON, UTC, Locale.US)).startsWith("2:32").endsWith("PM");
    }

    @Test
    @DisplayName("the stable clock does not move for anyone — a record two people compare must line up")
    void stableIsTheSameEverywhere() {
        String reference = Clocks.stable(AFTERNOON, UTC);
        assertThat(reference).isEqualTo("14:32");
        Locale was = Locale.getDefault();
        try {
            for (String tag : new String[] {"en-US", "hi", "ar", "th", "ja", "uk"}) {
                Locale.setDefault(Locale.forLanguageTag(tag));
                assertThat(Clocks.stable(AFTERNOON, UTC)).as("%s must not move the record", tag)
                        .isEqualTo(reference);
                assertThat(Clocks.stableSeconds(AFTERNOON, UTC)).as("%s, with seconds", tag)
                        .isEqualTo("14:32:00");
            }
        } finally {
            Locale.setDefault(was);
        }
    }

    @Test
    @DisplayName("a stable stamp stays Western digits even where the locale numbers differently")
    void stableResistsLocaleDigits() {
        Locale was = Locale.getDefault();
        try {
            // some locales render digits in their own script; a filename or a
            // log line written that way would not sort, match or compare
            Locale.setDefault(Locale.forLanguageTag("hi-IN-u-nu-deva"));
            assertThat(Clocks.stable(AFTERNOON, UTC)).as("Locale.ROOT keeps the record readable")
                    .isEqualTo("14:32").containsPattern("[0-9]{2}:[0-9]{2}");
        } finally {
            Locale.setDefault(was);
        }
    }

    @Test
    @DisplayName("the display formatter is built per call, so a live language switch is picked up")
    void displayFormatterIsNotCached() {
        Locale was = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            String german = Clocks.displayFormatter()
                    .format(java.time.Instant.ofEpochMilli(AFTERNOON).atZone(UTC));
            Locale.setDefault(Locale.US);
            String american = Clocks.displayFormatter()
                    .format(java.time.Instant.ofEpochMilli(AFTERNOON).atZone(UTC));
            assertThat(german).isEqualTo("14:32");
            assertThat(american).as("a cached formatter would still say 14:32").isNotEqualTo(german);
        } finally {
            Locale.setDefault(was);
        }
    }
}
