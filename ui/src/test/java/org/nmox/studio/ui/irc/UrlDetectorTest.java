package org.nmox.studio.ui.irc;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * URL detection in chat text: scheme-to-whitespace with trailing
 * sentence punctuation shed, multiple URLs per line, and the honest
 * empties (no scheme, bare scheme).
 */
class UrlDetectorTest {

    private static List<String> found(String text) {
        return UrlDetector.find(text).stream().map(r -> r.of(text)).toList();
    }

    @Test
    @DisplayName("http and https URLs are found, bounded by whitespace")
    void basicDetection() {
        assertThat(found("see https://example.com/docs for details"))
                .containsExactly("https://example.com/docs");
        assertThat(found("http://a.io b"))
                .containsExactly("http://a.io");
    }

    @Test
    @DisplayName("Trailing sentence punctuation is shed from the link")
    void trailingPunctuationShed() {
        assertThat(found("read https://example.com.")).containsExactly("https://example.com");
        assertThat(found("go to https://example.com/x, then reload"))
                .containsExactly("https://example.com/x");
        assertThat(found("(https://example.com/y)")).containsExactly("https://example.com/y");
        assertThat(found("what about https://example.com/z?!"))
                .containsExactly("https://example.com/z");
        assertThat(found("colon: https://example.com/w:"))
                .containsExactly("https://example.com/w");
    }

    @Test
    @DisplayName("Query strings and fragments survive — only TRAILING punctuation goes")
    void innerPunctuationSurvives() {
        assertThat(found("try https://e.com/p?q=1&r=2#frag ok"))
                .containsExactly("https://e.com/p?q=1&r=2#frag");
    }

    @Test
    @DisplayName("Multiple URLs in one line are all found, in order")
    void multipleUrls() {
        assertThat(found("https://a.com and http://b.org/x."))
                .containsExactly("https://a.com", "http://b.org/x");
    }

    @Test
    @DisplayName("No scheme, bare scheme, and empty text yield no ranges")
    void honestEmpties() {
        assertThat(found("no links here, just www.example.com")).isEmpty();
        assertThat(found("dangling https:// only")).isEmpty();
        assertThat(found("")).isEmpty();
        assertThat(UrlDetector.find(null)).isEmpty();
    }

    @Test
    @DisplayName("Ranges carry exact offsets into the original text")
    void rangesAreExact() {
        String text = "x https://a.io y";
        List<UrlDetector.Range> ranges = UrlDetector.find(text);
        assertThat(ranges).hasSize(1);
        assertThat(ranges.get(0).start()).isEqualTo(2);
        assertThat(ranges.get(0).end()).isEqualTo(14);
    }
}
