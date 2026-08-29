package org.nmox.studio.rack.engine;

import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.engine.OracleCommitEngine.Draft;
import org.nmox.studio.rack.engine.OracleCommitEngine.Status;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Draft Commit Message laws: the prompt is deterministic and
 * honest about truncation, the diff cap is code-point safe, the reply
 * unwrap is lenient exactly once (a whole-reply fence), and the
 * engine's key/consent gates provably send nothing.
 */
class OracleCommitMessageTest {

    @Test
    @DisplayName("The prompt states the boundary; truncation is confessed")
    void promptStatesTheBoundary() {
        String whole = OracleCommitMessage.assemblePrompt("myapp",
                " a.js | 2 +-", "diff --git a/a.js b/a.js", false);
        assertThat(whole).contains("Project: myapp")
                .contains("Files changed:\na.js | 2 +-") // stat is stripped
                .contains("Staged diff:\ndiff --git a/a.js b/a.js")
                .doesNotContain("TRUNCATED");
        String clipped = OracleCommitMessage.assemblePrompt("myapp",
                "", "d", true);
        assertThat(clipped).contains("TRUNCATED at "
                + OracleCommitMessage.MAX_DIFF_CHARS);
    }

    @Test
    @DisplayName("The diff cap cuts on a code-point boundary")
    void capIsCodePointSafe() {
        String under = "x".repeat(100);
        assertThat(OracleCommitMessage.capDiff(under)).isSameAs(under);
        assertThat(OracleCommitMessage.isTruncated(under)).isFalse();
        // an emoji straddling the cap must not leave a lone surrogate
        String pad = "y".repeat(OracleCommitMessage.MAX_DIFF_CHARS - 1);
        String hostile = pad + "😀zz";
        String capped = OracleCommitMessage.capDiff(hostile);
        assertThat(capped).hasSize(OracleCommitMessage.MAX_DIFF_CHARS - 1);
        assertThat(Character.isHighSurrogate(capped.charAt(capped.length() - 1)))
                .isFalse();
        assertThat(OracleCommitMessage.isTruncated(hostile)).isTrue();
    }

    @Test
    @DisplayName("A whole-reply fence unwraps; anything else passes trimmed")
    void unwrapIsLenientExactlyOnce() {
        assertThat(OracleCommitMessage.unwrapReply("  fix: tighten cap\n"))
                .isEqualTo("fix: tighten cap");
        assertThat(OracleCommitMessage.unwrapReply(
                "```\nfix: tighten cap\n\nBody line.\n```"))
                .isEqualTo("fix: tighten cap\n\nBody line.");
        // prose AROUND a fence is not a whole-reply fence — pass through
        assertThat(OracleCommitMessage.unwrapReply(
                "Here you go:\n```\nfix: x\n```"))
                .startsWith("Here you go:");
    }

    // ---- the engine gates ------------------------------------------------

    private static String canned(String text) {
        return new JSONObject().put("content", new JSONArray()
                .put(new JSONObject().put("type", "text").put("text", text)))
                .toString();
    }

    private record Spy(AtomicInteger posts, OracleClient client) {

        static Spy replying(String text) {
            AtomicInteger posts = new AtomicInteger();
            return new Spy(posts, new OracleClient((url, body, key) -> {
                posts.incrementAndGet();
                return canned(text);
            }));
        }
    }

    @Test
    @DisplayName("No key: the transport is never touched")
    void noKeySendsNothing() {
        Spy spy = Spy.replying("feat: x");
        OracleCommitEngine engine = new OracleCommitEngine(spy.client(),
                () -> null, p -> true);
        Draft d = engine.draft("app", "", "diff", OracleClient.MODEL_HAIKU);
        assertThat(d.status()).isEqualTo(Status.NO_KEY);
        assertThat(spy.posts()).hasValue(0);
    }

    @Test
    @DisplayName("Declined consent: the transport is never touched")
    void declinedConsentSendsNothing() {
        Spy spy = Spy.replying("feat: x");
        OracleCommitEngine engine = new OracleCommitEngine(spy.client(),
                () -> "k".toCharArray(), p -> false);
        Draft d = engine.draft("app", "", "diff", OracleClient.MODEL_HAIKU);
        assertThat(d.status()).isEqualTo(Status.NO_CONSENT);
        assertThat(spy.posts()).hasValue(0);
    }

    @Test
    @DisplayName("Happy path: one send, the unwrapped draft comes back")
    void happyPathDrafts() {
        Spy spy = Spy.replying("```\nfeat: add the Tests window\n```");
        OracleCommitEngine engine = new OracleCommitEngine(spy.client(),
                () -> "k".toCharArray(), p -> true);
        Draft d = engine.draft("app", " a | 1 +", "diff --git",
                OracleClient.MODEL_HAIKU);
        assertThat(d.status()).isEqualTo(Status.DRAFTED);
        assertThat(d.message()).isEqualTo("feat: add the Tests window");
        assertThat(spy.posts()).hasValue(1);
    }
}
