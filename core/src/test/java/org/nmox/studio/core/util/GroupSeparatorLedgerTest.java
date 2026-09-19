package org.nmox.studio.core.util;

import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the JDK writes between the digits, per shipped language (ledger 107).
 *
 * <p>{@code docs/i18n/conventions.md} settles the narrow no-break space for
 * text this product AUTHORS. It does not govern text the JDK writes: a bare
 * numeric {@code {1}} in a MessageFormat is grouped by the platform, so the
 * separator arrives below the bundle layer where no bundle gate can see it,
 * and it reaches every bare numeric argument in the product at once. On JDK
 * 25.0.4.1 French's is <b>U+202F</b>, the very character the conventions file
 * decided against — which is why ledger 107 exists and why its decision (keep
 * the platform's own data) needs the numbers under it to stay true.
 *
 * <p>This is the first of the pair. It answers <b>what the JDK says</b>, on
 * every platform the matrix runs, so a JDK or CLDR bump that moves a
 * separator fails the build naming the old value and the new one instead of
 * shipping. {@link NarrowNoBreakSpaceInkTest} answers the other half — what
 * the pixels do with it.
 *
 * <p>The population is DERIVED from {@link UiLocale#SUPPORTED}: a language
 * added tomorrow fails here until somebody measures it, rather than being
 * quietly unchecked (the v2.151.0 lesson — eleven gates once hand-kept the
 * language list and Hebrew would have walked past all of them).
 */
class GroupSeparatorLedgerTest {

    private static final char NNBSP = ' ';
    private static final char NBSP = ' ';

    /**
     * Measured 2026-09-18 on JDK 25.0.4.1, byte-identical on macOS (Azul and
     * Homebrew builds) and Linux (Zulu, {@code debian:trixie-slim}). This is
     * CLDR data carried by the JDK, not a platform property — which is the
     * claim the three-OS matrix now checks rather than assumes.
     */
    private static final Map<String, Character> RECORDED = new LinkedHashMap<>();

    static {
        RECORDED.put("en", ',');
        RECORDED.put("es", '.');
        RECORDED.put("fr", NNBSP);   // the one this ledger is about
        RECORDED.put("de", '.');
        RECORDED.put("ru", NBSP);
        RECORDED.put("uk", NBSP);
        RECORDED.put("pl", NBSP);
        RECORDED.put("pt", '.');
        RECORDED.put("id", '.');
        RECORDED.put("tl", ',');
        RECORDED.put("vi", '.');
        RECORDED.put("zh", ',');
        RECORDED.put("hi", ',');
        RECORDED.put("he", ',');
        RECORDED.put("ar", ',');
    }

    @Test
    @DisplayName("every shipped language's grouping separator is the one that was measured")
    void everyShippedLanguageSeparatorIsRecorded() {
        List<String> drift = new ArrayList<>();
        int checked = 0;
        for (UiLocale.Choice choice : UiLocale.SUPPORTED) {
            if (choice.isSystem()) {
                continue; // the JVM's own locale: not a language this ships bundles for
            }
            String code = choice.code();
            Character expected = RECORDED.get(code);
            if (expected == null) {
                drift.add(code + " (" + choice.nativeName() + "): a shipped language nobody "
                        + "has measured — its JDK grouping separator is "
                        + name(separatorOf(code)) + "; record it here, and if it is U+202F "
                        + "check ledger 107's pixel half for that language's chrome font");
                continue;
            }
            checked++;
            char actual = separatorOf(code);
            if (actual != expected) {
                drift.add(code + ": recorded " + name(expected) + ", this JDK says "
                        + name(actual) + " — a CLDR change under ledger 107; re-read the "
                        + "decision there before editing this table");
            }
        }
        assertThat(checked)
                .as("the recorded table must actually cover the shipped languages")
                .isEqualTo(RECORDED.size());
        assertThat(drift).as("grouping separators that moved under us").isEmpty();
    }

    @Test
    @DisplayName("French specifically — and fr-CA is the control that proves it")
    void frenchIsTheNarrowNoBreakSpaceAndCanadaIsNot() {
        assertThat(separatorOf("fr"))
                .as("ledger 107's whole subject: metropolitan French groups with U+202F")
                .isEqualTo(NNBSP);
        assertThat(DecimalFormatSymbols.getInstance(Locale.forLanguageTag("fr-CA"))
                .getGroupingSeparator())
                .as("fr-CA groups with U+00A0 — so this is `fr`, not `French`, "
                        + "and a fix aimed at the language would have missed")
                .isEqualTo(NBSP);

        // end to end, through the formatter a MessageFormat actually reaches
        assertThat(NumberFormat.getIntegerInstance(Locale.FRANCE).format(1234567))
                .as("the separator arrives in the rendered string, not just in the symbols")
                .isEqualTo("1" + NNBSP + "234" + NNBSP + "567");
    }

    @Test
    @DisplayName("Arabic reports a Western zero, so readableDigits is a correct no-op here")
    void arabicZeroDigitKeepsReadableDigitsANoOp() {
        Locale ar = Locale.forLanguageTag("ar");
        assertThat(DecimalFormatSymbols.getInstance(ar).getZeroDigit())
                .as("v2.152.0 wrote UiLocale.readableDigits against a CLDR that gave `ar` "
                        + "Arabic-Indic digits; on this JDK it reports U+0030")
                .isEqualTo('0');
        assertThat(UiLocale.readableDigits(ar))
                .as("so the rule reads the data and returns the locale untouched — a live "
                        + "rewrite here would mean the CLDR data moved back")
                .isEqualTo(ar);
    }

    private static char separatorOf(String languageTag) {
        return DecimalFormatSymbols.getInstance(Locale.forLanguageTag(languageTag))
                .getGroupingSeparator();
    }

    private static String name(char c) {
        return String.format("U+%04X", (int) c)
                + (c > ' ' && c < 0x7F ? " ('" + c + "')" : "");
    }
}
