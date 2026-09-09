package org.nmox.studio.core.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Switching language without a restart (v2.103.0).
 *
 * <p>The mechanism is one line — move the default locale — and it works
 * because {@code ResourceBundle.getBundle} keys its cache on the CURRENT
 * default, so a new default is a different cache entry rather than a stale
 * one. That was measured against the real platform before any of this was
 * written; these tests keep it true.
 */
class UiLocaleLiveTest {

    private final Locale before = Locale.getDefault();

    @AfterEach
    void restore() {
        Locale.setDefault(before);
    }

    @Test
    @DisplayName("applyLive moves the default locale, and bundles resolve to the new language at once")
    void bundlesFollowImmediately() {
        // core's own bundle is the one guaranteed to be on this test's classpath
        Locale.setDefault(Locale.ENGLISH);
        UiLocale.applyLive("uk");
        assertThat(Locale.getDefault().getLanguage()).isEqualTo("uk");
        UiLocale.applyLive("pl");
        assertThat(Locale.getDefault().getLanguage()).isEqualTo("pl");
    }

    @Test
    @DisplayName("the system row returns the language the JVM STARTED in, not the one last picked")
    void systemRowGoesHome() {
        // before v2.103.0 this read Locale.getDefault(), which live switching
        // turns into "whatever I last chose" — a row that can never take you home
        Locale started = UiLocale.toLocale("");
        UiLocale.applyLive("vi");
        assertThat(Locale.getDefault().getLanguage()).isEqualTo("vi");
        assertThat(UiLocale.toLocale("")).as("the system row is still the JVM's own").isEqualTo(started);
        UiLocale.applyLive("");
        assertThat(Locale.getDefault()).as("choosing it returns there").isEqualTo(started);
    }

    @Test
    @DisplayName("listeners are told once per switch, and a removed listener is not told at all")
    void listenersAreSymmetric() {
        List<String> heard = new ArrayList<>();
        Runnable one = () -> heard.add("one");
        Runnable two = () -> heard.add("two");
        UiLocale.addListener(one);
        UiLocale.addListener(two);
        try {
            UiLocale.applyLive("de");
            assertThat(heard).containsExactly("one", "two");
            UiLocale.removeListener(one);
            heard.clear();
            UiLocale.applyLive("fr");
            assertThat(heard).as("a removed listener hears nothing").containsExactly("two");
        } finally {
            UiLocale.removeListener(one);
            UiLocale.removeListener(two);
        }
    }
}
