package org.nmox.studio.ui.browser.fx;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The locality predicate is a HOST check (v1.234.0 review). The old
 * substring test's two false positives are pinned here so they cannot
 * come back.
 */
class LocalUrlsTest {

    @Test
    @DisplayName("loopback hosts are local — with ports, paths, and either spelling")
    void loopbackIsLocal() {
        assertThat(LocalUrls.isLocal("http://localhost:3000/")).isTrue();
        assertThat(LocalUrls.isLocal("http://127.0.0.1:8000/index.html")).isTrue();
        assertThat(LocalUrls.isLocal("https://LOCALHOST/app")).isTrue();
        assertThat(LocalUrls.isLocal("http://[::1]:5173/")).isTrue();
    }

    @Test
    @DisplayName("the substring bug's false positives are refused")
    void substringImpostorsAreNotLocal() {
        assertThat(LocalUrls.isLocal("http://localhost.evil.example/"))
                .as("a HOSTNAME merely starting with localhost").isFalse();
        assertThat(LocalUrls.isLocal("https://example.com/?next=//localhost:3000"))
                .as("//localhost in the query, not the authority").isFalse();
    }

    @Test
    @DisplayName("blanks, garbage, and remote pages are not local")
    void everythingElseRefuses() {
        assertThat(LocalUrls.isLocal(null)).isFalse();
        assertThat(LocalUrls.isLocal("  ")).isFalse();
        assertThat(LocalUrls.isLocal("not a url at all %%")).isFalse();
        assertThat(LocalUrls.isLocal("https://news.ycombinator.com/")).isFalse();
        assertThat(LocalUrls.isLocal("about:blank")).isFalse();
    }
}
