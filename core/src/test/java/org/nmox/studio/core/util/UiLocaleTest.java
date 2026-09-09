package org.nmox.studio.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The language switch writes exactly one shell-safe block into the
 * per-user launcher conf and touches nothing else (v2.97.0).
 */
class UiLocaleTest {

    @Test
    @DisplayName("A fresh conf gains the block; the block carries --locale in default_options")
    void freshConf() {
        String out = UiLocale.apply("", "fr");
        assertThat(out).startsWith(UiLocale.BEGIN + "\n")
                .contains("default_options=\"${default_options} --locale fr\"\n")
                .endsWith(UiLocale.END + "\n");
        assertThat(UiLocale.current(out)).contains("fr");
    }

    @Test
    @DisplayName("Every other line survives; a second apply replaces the block, never doubles it")
    void preservesAndReplaces() {
        String user = "# my own settings\nnetbeans_default_options=\"-J-Xmx4g\"\n";
        String fr = UiLocale.apply(user, "fr");
        String ru = UiLocale.apply(fr, "ru");
        assertThat(ru).startsWith(user);
        assertThat(ru.split(java.util.regex.Pattern.quote(UiLocale.BEGIN), -1)).hasSize(2);
        assertThat(ru).contains("--locale ru").doesNotContain("--locale fr");
        assertThat(UiLocale.current(ru)).contains("ru");
        String back = UiLocale.apply(ru, "");
        assertThat(back).as("system default removes the block and keeps the user's lines").isEqualTo(user);
        assertThat(UiLocale.current(back)).isEmpty();
    }

    @Test
    @DisplayName("Only launcher locale codes are ever written — the conf is sourced by a shell")
    void shellSafety() {
        for (String bad : new String[] {"fr; rm -rf ~", "$(id)", "FR", "fra", "fr:ca", "fr\n"}) {
            assertThatThrownBy(() -> UiLocale.apply("", bad)).as(bad)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(UiLocale.apply("", "fr:CA")).contains("--locale fr:CA");
    }

    @Test
    @DisplayName("Choices resolve by language; unknown or country-suffixed codes land on their row")
    void choices() {
        assertThat(UiLocale.choiceFor("fr:CA").code()).isEqualTo("fr");
        assertThat(UiLocale.choiceFor("xx")).isEqualTo(UiLocale.SYSTEM);
        assertThat(UiLocale.choiceFor(null)).isEqualTo(UiLocale.SYSTEM);
        assertThat(UiLocale.SUPPORTED).extracting(UiLocale.Choice::code)
                .containsExactly("", "en", "es", "fr", "de", "ru", "uk", "pl", "pt", "id", "tl", "vi", "zh", "hi");
        assertThat(UiLocale.toLocale("de")).isEqualTo(java.util.Locale.GERMAN);
        assertThat(UiLocale.userConf(java.nio.file.Path.of("/u")))
                .isEqualTo(java.nio.file.Path.of("/u/etc/nmoxstudio.conf"));
    }
}
