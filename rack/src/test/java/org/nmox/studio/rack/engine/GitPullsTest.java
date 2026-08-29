package org.nmox.studio.rack.engine;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.engine.GitPulls.Pull;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The PR-list parse laws: gh's shape maps field-for-field, a hostile
 * title stays data, a malformed element loses itself (not the list),
 * and non-JSON throws for the caller to phrase.
 */
class GitPullsTest {

    private static String sample() {
        return new JSONArray()
                .put(new JSONObject()
                        .put("number", 630)
                        .put("title", "v2.50.0 batch: the Tests window")
                        .put("author", new JSONObject().put("login", "claude"))
                        .put("headRefName", "tests-2500")
                        .put("url", "https://github.com/NMOX/NMOX-Studio/pull/630"))
                .put(new JSONObject()
                        .put("number", 7)
                        .put("title", "<html><img src=x> hostile title")
                        .put("author", new JSONObject().put("login", "mallory"))
                        .put("headRefName", "evil")
                        .put("url", "https://example.com/7"))
                .toString();
    }

    @Test
    @DisplayName("gh's JSON maps field-for-field; hostile titles stay data")
    void parsesFieldForField() {
        List<Pull> pulls = GitPulls.parse(sample());
        assertThat(pulls).hasSize(2);
        Pull first = pulls.get(0);
        assertThat(first.number()).isEqualTo(630);
        assertThat(first.title()).isEqualTo("v2.50.0 batch: the Tests window");
        assertThat(first.author()).isEqualTo("claude");
        assertThat(first.branch()).isEqualTo("tests-2500");
        assertThat(first.url()).endsWith("/pull/630");
        // the hostile title survives VERBATIM as data — rendering
        // plainness is the dialog's law (PlainTables), not a parse edit
        assertThat(pulls.get(1).title()).startsWith("<html>");
    }

    @Test
    @DisplayName("A malformed element loses itself, not the list")
    void malformedElementDropsAlone() {
        String json = "[{\"number\":1,\"title\":\"ok\"},\"not-an-object\",{\"title\":\"no number\"}]";
        List<Pull> pulls = GitPulls.parse(json);
        assertThat(pulls).hasSize(1);
        assertThat(pulls.get(0).number()).isEqualTo(1);
        assertThat(pulls.get(0).author()).isEmpty();
    }

    @Test
    @DisplayName("Non-JSON throws — the caller phrases the refusal")
    void nonJsonThrows() {
        assertThatThrownBy(() -> GitPulls.parse("gh: command not found"))
                .isInstanceOf(RuntimeException.class);
    }
}
