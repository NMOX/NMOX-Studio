package org.nmox.studio.ui.browser.devtools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** URL-bar normalization and untrusted tab-title shaping. */
class BrowserUrlsTest {

    @Test
    @DisplayName("bare hosts gain the https:// default")
    void bareHostGetsHttps() {
        assertThat(BrowserUrls.normalize("example.com")).isEqualTo("https://example.com");
        assertThat(BrowserUrls.normalize("  example.com/path?q=1  "))
                .isEqualTo("https://example.com/path?q=1");
    }

    @Test
    @DisplayName("existing schemes pass through untouched")
    void schemesPassThrough() {
        assertThat(BrowserUrls.normalize("http://localhost:3000")).isEqualTo("http://localhost:3000");
        assertThat(BrowserUrls.normalize("https://a.b")).isEqualTo("https://a.b");
        assertThat(BrowserUrls.normalize("file:///tmp/x.html")).isEqualTo("file:///tmp/x.html");
        assertThat(BrowserUrls.normalize("about:blank")).isEqualTo("about:blank");
    }

    @Test
    @DisplayName("host:port is NOT a scheme — localhost:3000 gets https://")
    void hostPortIsNotScheme() {
        assertThat(BrowserUrls.normalize("localhost:3000")).isEqualTo("https://localhost:3000");
        assertThat(BrowserUrls.normalize("127.0.0.1:8080")).isEqualTo("https://127.0.0.1:8080");
    }

    @Test
    @DisplayName("blank input loads nothing")
    void blankIsNull() {
        assertThat(BrowserUrls.normalize(null)).isNull();
        assertThat(BrowserUrls.normalize("   ")).isNull();
    }

    @Test
    @DisplayName("tab titles cap at 30 chars; blank falls back to Browser")
    void tabTitles() {
        assertThat(BrowserUrls.tabTitle(null)).isEqualTo("Browser");
        assertThat(BrowserUrls.tabTitle("  ")).isEqualTo("Browser");
        assertThat(BrowserUrls.tabTitle("Hacker News")).isEqualTo("Hacker News");
        String longTitle = "A very long page title that never seems to end at all";
        String capped = BrowserUrls.tabTitle(longTitle);
        assertThat(capped).hasSize(BrowserUrls.TITLE_CAP + 1); // 30 + ellipsis
        assertThat(capped).endsWith("…");
    }

    @Test
    @DisplayName("the cap never splits a surrogate pair (emoji-safe)")
    void capIsCodePointSafe() {
        // 29 chars then an emoji (2 chars as a surrogate pair) at the cut
        String title = "x".repeat(29) + "😀" + "tail";
        String capped = BrowserUrls.tabTitle(title);
        assertThat(capped.endsWith("…")).isTrue();
        // no lone surrogate directly before the ellipsis
        char beforeEllipsis = capped.charAt(capped.length() - 2);
        assertThat(Character.isHighSurrogate(beforeEllipsis)).isFalse();
    }
}
