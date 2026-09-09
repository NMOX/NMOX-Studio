package org.nmox.studio.tools.npm;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.core.spi.LiveRuns;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ■'s words (ledger 89, v2.101.0) — the English behaviour that moved out
 * of {@code core.spi.LiveRuns}, plus the thing the move was FOR: that every
 * language can say it.
 */
class StopRunTextTest {

    private static final List<String> LOCALES =
            List.of("es", "fr", "de", "ru", "uk", "pl", "pt", "id", "tl", "vi", "zh", "hi");

    @Test
    @DisplayName("the ■ tooltip names what a press would stop, with a count; nothing running says so (v2.71.0)")
    void tooltipNamesTheRuns() {
        assertThat(StopRunText.tooltip(List.of())).isEqualTo("Stop Running Command — nothing is running");
        LiveRuns.Run a = new LiveRuns.Run("a", "npm run dev — shop", () -> { });
        LiveRuns.Run b = new LiveRuns.Run("b", "Run — api", () -> { });
        // a, b were never added: no start stamp, so no "since" (v2.76.0 shows it when there is one)
        assertThat(StopRunText.tooltip(List.of(a))).isEqualTo("Stop the running command: npm run dev — shop");
        assertThat(StopRunText.tooltip(List.of(a, b)))
                .isEqualTo("Stop 2 running commands: npm run dev — shop, Run — api");
        // a run that is really added carries a real start stamp, which is all
        // the "since" needs — the clock seam is core's own and stays there
        LiveRuns.add(new LiveRuns.Run("t1", "Run — shop", () -> { }));
        try {
            assertThat(StopRunText.tooltip(LiveRuns.live())).as("a live run says since when (v2.76.0)")
                    .startsWith("Stop the running command: Run — shop (since ");
        } finally {
            LiveRuns.remove("t1");
        }
    }

    @Test
    @DisplayName("The status line after ■: every stopped label, or that nothing was running")
    void stoppedMessage() {
        assertThat(StopRunText.stopped(List.of())).isEqualTo("Nothing is running");
        assertThat(StopRunText.stopped(List.of(new LiveRuns.Run("a", "Run — one", () -> { }),
                new LiveRuns.Run("b", "Build — two", () -> { }))))
                .isEqualTo("Stopped: Run — one, Build — two");
    }

    @Test
    @DisplayName("every language says all six, in its own words — no key falls back to English")
    void everyLanguageHasItsOwnWords() {
        String[] keys = {"StopRunText_tooltipIdle", "StopRunText_tooltipOne", "StopRunText_tooltipMany",
            "StopRunText_runSince", "StopRunText_stoppedNone", "StopRunText_stopped"};
        ResourceBundle en = bundle(Locale.ENGLISH);
        for (String locale : LOCALES) {
            ResourceBundle b = bundle(Locale.of(locale));
            for (String key : keys) {
                String value = b.getString(key);
                assertThat(value).as("%s [%s] is present", key, locale).isNotBlank();
                // runSince is punctuation plus two arguments in several
                // languages, so only the prose keys must actually differ
                if (!key.equals("StopRunText_runSince")) {
                    assertThat(value).as("%s [%s] fell back to English", key, locale)
                            .isNotEqualTo(en.getString(key));
                }
                assertThat(value).as("%s [%s] uses a bare ASCII apostrophe, which opens a "
                        + "MessageFormat quote and eats the rest of the message", key, locale)
                        .doesNotContain("'");
            }
        }
    }

    @Test
    @DisplayName("the count is a real plural: Slavic inflects across 1/2-4/5+, the plural-less languages do not")
    void pluralsRenderInEveryLanguage() {
        for (String locale : LOCALES) {
            String pattern = bundle(Locale.of(locale)).getString("StopRunText_tooltipMany");
            for (int n : new int[] {2, 3, 5, 11, 21}) {
                String out = new MessageFormat(pattern, Locale.of(locale)).format(new Object[] {n, "a, b"});
                assertThat(out).as("%s renders %d", locale, n)
                        .contains(String.valueOf(n)).doesNotContain("{").doesNotContain("}");
            }
        }
        // the inflection itself, where it exists: 2 and 5 differ in Polish,
        // Russian and Ukrainian, and are deliberately identical in the four
        // languages with no grammatical plural (v2.99.0's rule)
        for (String slavic : List.of("pl", "ru", "uk")) {
            // normalise the digit away first: the question is whether the WORDS
            // change, and "2 polecenia" vs "5 polecenia" differ by the number
            // alone — which is how the first cut of this assertion passed while
            // Polish had stopped inflecting
            assertThat(render(slavic, 2).replace("2", "#")).as("%s inflects 2 vs 5", slavic)
                    .isNotEqualTo(render(slavic, 5).replace("5", "#"));
        }
        for (String none : List.of("id", "tl", "vi", "zh")) {
            assertThat(render(none, 2).replace("2", "#")).as("%s has no grammatical plural — same words", none)
                    .isEqualTo(render(none, 5).replace("5", "#"));
        }
    }

    private static String render(String locale, int n) {
        return new MessageFormat(bundle(Locale.of(locale)).getString("StopRunText_tooltipMany"),
                Locale.of(locale)).format(new Object[] {n, "x"});
    }

    private static ResourceBundle bundle(Locale locale) {
        return ResourceBundle.getBundle("org.nmox.studio.tools.npm.Bundle", locale);
    }
}
