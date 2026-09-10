package org.nmox.studio.ui.shortcuts;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shortcut sheet is a list of the reader's own words (v2.104.0).
 *
 * <p>Its action names are translated, so its alphabetical order belongs to
 * the reader's language. Under code-point order a Polish reader looking for
 * {@code Ćwiczenie} between C and D found it exiled past Z — the same class
 * as the 24-hour clock every Hindi user was reading: a value whose SHAPE
 * depends on who is looking, invisible to every bundle gate.
 */
class ShortcutSheetOrderTest {

    private static List<String> actionsInOrder(Locale locale, List<String> names) {
        Locale was = Locale.getDefault();
        try {
            Locale.setDefault(locale);
            List<ShortcutSheet.Row> rows = names.stream()
                    .map(n -> new ShortcutSheet.Row("⌘K", n)).toList();
            return ShortcutSheet.sorted(rows).stream().map(ShortcutSheet.Row::action).toList();
        } finally {
            Locale.setDefault(was);
        }
    }

    @Test
    @DisplayName("a Polish reader's sheet reads in Polish order")
    void polishSheetIsInPolishOrder() {
        assertThat(actionsInOrder(Locale.forLanguageTag("pl"),
                List.of("Zamknij", "Ćwiczenie", "Cofnij", "Życie")))
                .containsExactly("Cofnij", "Ćwiczenie", "Zamknij", "Życie");
    }

    @Test
    @DisplayName("a German reader's sheet puts Ä with A, not after Z")
    void germanSheetIsInGermanOrder() {
        assertThat(actionsInOrder(Locale.GERMANY,
                List.of("Zoom", "Ändern", "Öffnen", "Auswahl")))
                .containsExactly("Ändern", "Auswahl", "Öffnen", "Zoom");
    }

    @Test
    @DisplayName("the chord still breaks ties, so one action on two chords stays stable")
    void chordBreaksTies() {
        Locale was = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("uk"));
            List<ShortcutSheet.Row> rows = List.of(
                    new ShortcutSheet.Row("⌘Z", "Скасувати"),
                    new ShortcutSheet.Row("⌘A", "Скасувати"));
            assertThat(ShortcutSheet.sorted(rows).stream().map(ShortcutSheet.Row::chord).toList())
                    .containsExactly("⌘A", "⌘Z");
        } finally {
            Locale.setDefault(was);
        }
    }
}
