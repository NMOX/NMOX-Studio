package org.nmox.studio.apiclient.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The response pack (v1.198.0): the pure find half, plus the wiring
 * laws source-gated — save writes the RAW body off the EDT (the
 * pretty-printed display is a VIEW, saving it would corrupt binaryish
 * payloads), and a truncated capture is announced, never hidden.
 */
class ResponseSearchTest {

    @Test
    @DisplayName("Matches are case-insensitive, overlapping, and capped honestly")
    void matchSemantics() {
        assertThat(ResponseSearch.matches("aAbAa", "a")).containsExactly(0, 1, 3, 4);
        assertThat(ResponseSearch.matches("aaa", "aa"))
                .as("overlaps count").containsExactly(0, 1);
        assertThat(ResponseSearch.matches("anything", "")).isEmpty();
        assertThat(ResponseSearch.matches(null, "x")).isEmpty();

        String big = "x".repeat(ResponseSearch.MAX_MATCHES + 500);
        assertThat(ResponseSearch.matches(big, "x")).hasSize(ResponseSearch.MAX_MATCHES);
    }

    @Test
    @DisplayName("next() wraps past the last match")
    void nextWraps() {
        List<Integer> m = List.of(3, 10, 20);
        assertThat(ResponseSearch.next(m, 0)).isEqualTo(3);
        assertThat(ResponseSearch.next(m, 3)).isEqualTo(10);
        assertThat(ResponseSearch.next(m, 25)).as("wraps").isEqualTo(3);
        assertThat(ResponseSearch.next(List.of(), 0)).isEqualTo(-1);
    }

    @Test
    @DisplayName("Save writes the RAW body off the EDT and announces truncation")
    void saveLaws() throws Exception {
        String s = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/apiclient/ui/ApiClientTopComponent.java"));
        int save = s.indexOf("private void saveResponseBody()");
        int end = s.indexOf("\n    // ---- sending ----", save);
        String body = s.substring(save, end);
        assertThat(body).as("raw body, not the display text").contains("toSave.body()");
        assertThat(body).as("never the pretty view").doesNotContain("responseBody.getText()");
        assertThat(body).as("disk write off the EDT").contains("RP.post(");
        assertThat(body).as("truncation announced").contains("truncated()");
    }

    @Test
    @DisplayName("A fresh response re-runs the find — stale highlights can't survive")
    void freshResponseRefinds() throws Exception {
        String s = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/apiclient/ui/ApiClientTopComponent.java"));
        int show = s.indexOf("private void showResponse(");
        int end = s.indexOf("\n    private ", show + 10);
        assertThat(s.substring(show, end).split("refindInBody\\(\\)", -1).length - 1)
                .as("both setText paths refind")
                .isGreaterThanOrEqualTo(2);
    }
}
