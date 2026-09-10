package org.nmox.studio.core.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Alphabetical order belongs to the reader's language (v2.104.0). */
class CollateTest {

    private static List<String> sorted(List<String> words, Comparator<String> by) {
        List<String> out = new ArrayList<>(words);
        out.sort(by);
        return out;
    }

    @Test
    @DisplayName("Polish puts ć, ł and ż where Polish puts them, not after z")
    void polishOrder() {
        Locale was = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("pl"));
            List<String> words = List.of("Zamknij", "Ćwiczenie", "Łącze", "Cofnij", "Życie");
            assertThat(sorted(words, Collate.byDisplayName(s -> s)))
                    .containsExactly("Cofnij", "Ćwiczenie", "Łącze", "Zamknij", "Życie");
            assertThat(sorted(words, Collate.stableBy(s -> s)))
                    .as("code points exile every accented word to the end — the bug this fixes")
                    .endsWith("Ćwiczenie", "Łącze", "Życie");
        } finally {
            Locale.setDefault(was);
        }
    }

    @Test
    @DisplayName("German, Spanish, Ukrainian and Vietnamese each get their own order")
    void theOtherLanguagesToo() {
        assertThat(sorted(List.of("Zoom", "Ändern", "Öffnen", "Auswahl"),
                withLocale(Locale.GERMANY)))
                .containsExactly("Ändern", "Auswahl", "Öffnen", "Zoom");
        assertThat(sorted(List.of("Zoom", "Ñu", "Abrir", "Ánimo"),
                withLocale(Locale.forLanguageTag("es"))))
                .containsExactly("Abrir", "Ánimo", "Ñu", "Zoom");
        assertThat(sorted(List.of("Явище", "Ідея", "Аркуш", "Ґанок"),
                withLocale(Locale.forLanguageTag("uk"))))
                .containsExactly("Аркуш", "Ґанок", "Ідея", "Явище");
        assertThat(sorted(List.of("Đóng", "Xem", "Ăn", "Bản"),
                withLocale(Locale.forLanguageTag("vi"))))
                .containsExactly("Ăn", "Bản", "Đóng", "Xem");
    }

    @Test
    @DisplayName("the order follows a live language switch rather than the startup language")
    void followsTheLiveLanguage() {
        Locale was = Locale.getDefault();
        try {
            // Swedish sorts ä and ö AFTER z; German sorts them with a and o
            List<String> words = List.of("Zoo", "Äpfel");
            Locale.setDefault(Locale.GERMANY);
            assertThat(sorted(words, Collate.byDisplayName(s -> s)))
                    .containsExactly("Äpfel", "Zoo");
            Locale.setDefault(Locale.forLanguageTag("sv"));
            assertThat(sorted(words, Collate.byDisplayName(s -> s)))
                    .as("a cached collator would still be speaking German")
                    .containsExactly("Zoo", "Äpfel");
        } finally {
            Locale.setDefault(was);
        }
    }

    @Test
    @DisplayName("equal-by-collation names still land in one fixed order, so a list cannot wobble")
    void tiesAreBrokenDeterministically() {
        Comparator<String> by = withLocale(Locale.forLanguageTag("fr"));
        // at SECONDARY strength a collator calls these equal; the sort must
        // still be total, or two renderings of the same list disagree
        assertThat(by.compare("resume", "Resume")).isNotZero();
        assertThat(sorted(List.of("Resume", "resume"), by))
                .isEqualTo(sorted(List.of("resume", "Resume"), by));
    }

    @Test
    @DisplayName("a null name sorts as empty rather than throwing mid-paint")
    void nullNamesAreSurvivable() {
        assertThat(sorted(new ArrayList<>(java.util.Arrays.asList("b", null, "a")),
                Collate.byDisplayName(s -> s))).containsExactly(null, "a", "b");
    }

    private static Comparator<String> withLocale(Locale locale) {
        Locale was = Locale.getDefault();
        try {
            Locale.setDefault(locale);
            return Collate.byDisplayName(s -> s);
        } finally {
            Locale.setDefault(was);
        }
    }
}
