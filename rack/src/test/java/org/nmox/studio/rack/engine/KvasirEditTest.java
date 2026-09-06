package org.nmox.studio.rack.engine;

import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.text.DefaultStyledDocument;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.engine.KvasirEdit.EditRequest;
import org.nmox.studio.rack.engine.KvasirEditEngine.Proposal;
import org.nmox.studio.rack.engine.KvasirEditEngine.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Edit with KVASIR laws: the prompt is deterministic, the reply
 * parse accepts exactly one fenced block (ambiguity and prose refuse),
 * over-cap selections refuse at construction (never truncate — a
 * truncated edit deletes the un-sent tail), the engine's key/consent
 * gates provably send nothing, and the apply guard refuses a buffer
 * that moved while KVASIR was thinking.
 */
class KvasirEditTest {

    private static EditRequest req(String code, String instruction) {
        return new EditRequest("a.js", "text/javascript", code, instruction);
    }

    // ---- prompt ----------------------------------------------------------

    @Test
    @DisplayName("Edit prompt is deterministic and demands the one reply shape")
    void promptIsDeterministic() {
        String p = KvasirEdit.assembleEditPrompt(req("let x = 1;", "use const"));
        assertThat(p).contains("exactly one fenced code block")
                .contains("File: a.js")
                .contains("Language: text/javascript")
                .contains("Instruction: use const")
                .contains("Selected code:\nlet x = 1;\n")
                .contains("reply in prose (no fence) saying why");
        assertThat(p).isEqualTo(KvasirEdit.assembleEditPrompt(req("let x = 1;", "use const")));
    }

    // ---- fence extraction ------------------------------------------------

    @Test
    @DisplayName("A single fenced block extracts verbatim, fence newline dropped")
    void singleFenceExtracts() {
        String reply = "Here you go:\n```js\nconst x = 1;\n```\n";
        assertThat(KvasirEdit.extractFencedCode(reply)).isEqualTo("const x = 1;");
        // no trailing newline invented: the newline before the closing
        // fence belongs to the fence syntax, so a newline-less selection
        // round-trips
        assertThat(KvasirEdit.extractFencedCode("```\na\nb\n```"))
                .isEqualTo("a\nb");
    }

    @Test
    @DisplayName("A prose reply (zero fences) refuses — the model's honest no")
    void proseReplyRefuses() {
        assertThat(KvasirEdit.extractFencedCode(
                "I cannot apply that to this selection alone.")).isNull();
    }

    @Test
    @DisplayName("Two fenced blocks refuse — never guess which one")
    void twoBlocksRefuse() {
        String reply = "```\nfirst\n```\nor maybe\n```\nsecond\n```";
        assertThat(KvasirEdit.extractFencedCode(reply)).isNull();
    }

    @Test
    @DisplayName("An empty fence is a deliberate delete-the-selection")
    void emptyFenceIsDeletion() {
        assertThat(KvasirEdit.extractFencedCode("```\n```")).isEqualTo("");
    }

    // ---- the trailing-newline law (v2.49.1 review) -----------------------

    @Test
    @DisplayName("An edit never changes whether the selection ends in a newline")
    void trailingNewlinePreserved() {
        // the glue scenario: a mid-file selection ending in \n whose
        // replacement lost it would fuse the next line onto the tail
        assertThat(KvasirEdit.matchTrailingNewline("let x = 1;\n", "const x = 1;"))
                .isEqualTo("const x = 1;\n");
        // symmetric: an invented trailing newline is dropped
        assertThat(KvasirEdit.matchTrailingNewline("let x = 1;", "const x = 1;\n"))
                .isEqualTo("const x = 1;");
        // already matching states pass through untouched
        assertThat(KvasirEdit.matchTrailingNewline("a\n", "b\n")).isEqualTo("b\n");
        assertThat(KvasirEdit.matchTrailingNewline("a", "b")).isEqualTo("b");
    }

    @Test
    @DisplayName("The engine proposes the newline-matched replacement")
    void engineMatchesTrailingNewline() {
        Spy spy = Spy.replying("```js\nconst x = 1;\n```");
        KvasirEditEngine engine = new KvasirEditEngine(spy.client(),
                () -> "k".toCharArray(), r -> true);
        Proposal p = engine.propose(req("let x = 1;\n", "use const"),
                KvasirClient.MODEL_HAIKU);
        assertThat(p.status()).isEqualTo(Status.PROPOSED);
        assertThat(p.replacement()).isEqualTo("const x = 1;\n");
    }

    // ---- the refusal caps ------------------------------------------------

    @Test
    @DisplayName("An over-cap selection refuses at construction, never truncates")
    void overCapRefuses() {
        String big = "x".repeat(KvasirEdit.MAX_CODE_CHARS + 1);
        assertThatThrownBy(() -> req(big, "shrink it"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too large");
    }

    @Test
    @DisplayName("A blank instruction refuses — an edit needs a what")
    void blankInstructionRefuses() {
        assertThatThrownBy(() -> req("let x;", "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("instruction");
    }

    // ---- the engine gates ------------------------------------------------

    private static String cannedReply(String text) {
        return new JSONObject().put("content", new JSONArray()
                .put(new JSONObject().put("type", "text").put("text", text)))
                .toString();
    }

    private record Spy(AtomicInteger posts, KvasirClient client) {

        static Spy replying(String text) {
            AtomicInteger posts = new AtomicInteger();
            KvasirClient client = new KvasirClient((url, body, key) -> {
                posts.incrementAndGet();
                return cannedReply(text);
            });
            return new Spy(posts, client);
        }
    }

    @Test
    @DisplayName("No key: the transport is never touched")
    void noKeySendsNothing() {
        Spy spy = Spy.replying("```\nx\n```");
        KvasirEditEngine engine = new KvasirEditEngine(spy.client(),
                () -> null, r -> true);
        Proposal p = engine.propose(req("a", "b"), KvasirClient.MODEL_HAIKU);
        assertThat(p.status()).isEqualTo(Status.NO_KEY);
        assertThat(spy.posts()).hasValue(0);
    }

    @Test
    @DisplayName("Declined consent: the transport is never touched")
    void declinedConsentSendsNothing() {
        Spy spy = Spy.replying("```\nx\n```");
        KvasirEditEngine engine = new KvasirEditEngine(spy.client(),
                () -> "k".toCharArray(), r -> false);
        Proposal p = engine.propose(req("a", "b"), KvasirClient.MODEL_HAIKU);
        assertThat(p.status()).isEqualTo(Status.NO_CONSENT);
        assertThat(spy.posts()).hasValue(0);
    }

    @Test
    @DisplayName("A prose reply is NOT_CODE and carries the model's words")
    void proseReplyIsNotCode() {
        Spy spy = Spy.replying("That instruction needs the rest of the file.");
        KvasirEditEngine engine = new KvasirEditEngine(spy.client(),
                () -> "k".toCharArray(), r -> true);
        Proposal p = engine.propose(req("a", "b"), KvasirClient.MODEL_HAIKU);
        assertThat(p.status()).isEqualTo(Status.NOT_CODE);
        assertThat(p.message()).contains("needs the rest of the file");
        assertThat(p.replacement()).isNull();
    }

    @Test
    @DisplayName("An identical reply is NO_CHANGE, not a busywork apply")
    void identicalReplyIsNoChange() {
        Spy spy = Spy.replying("```\nlet x = 1;\n```");
        KvasirEditEngine engine = new KvasirEditEngine(spy.client(),
                () -> "k".toCharArray(), r -> true);
        Proposal p = engine.propose(req("let x = 1;", "keep it"),
                KvasirClient.MODEL_HAIKU);
        assertThat(p.status()).isEqualTo(Status.NO_CHANGE);
    }

    @Test
    @DisplayName("Happy path: one fenced block becomes a PROPOSED replacement")
    void happyPathProposes() {
        Spy spy = Spy.replying("```js\nconst x = 1;\n```");
        KvasirEditEngine engine = new KvasirEditEngine(spy.client(),
                () -> "k".toCharArray(), r -> true);
        Proposal p = engine.propose(req("let x = 1;", "use const"),
                KvasirClient.MODEL_HAIKU);
        assertThat(p.status()).isEqualTo(Status.PROPOSED);
        assertThat(p.replacement()).isEqualTo("const x = 1;");
        assertThat(spy.posts()).hasValue(1);
    }

    // ---- the stale-buffer apply guard ------------------------------------

    @Test
    @DisplayName("A buffer that moved while KVASIR thought refuses, untouched")
    void staleBufferRefusesUntouched() throws Exception {
        DefaultStyledDocument doc = new DefaultStyledDocument();
        doc.insertString(0, "aaa let x = 1; zzz", null);
        // the user edits while the request is in flight
        doc.insertString(0, "// new line\n", null);
        boolean applied = KvasirEdit.replaceIfUnchanged(doc, 4,
                "let x = 1;", "const x = 1;");
        assertThat(applied).isFalse();
        assertThat(doc.getText(0, doc.getLength()))
                .isEqualTo("// new line\naaa let x = 1; zzz");
    }

    @Test
    @DisplayName("An unchanged buffer applies exactly the selection span")
    void cleanBufferApplies() throws Exception {
        DefaultStyledDocument doc = new DefaultStyledDocument();
        doc.insertString(0, "aaa let x = 1; zzz", null);
        boolean applied = KvasirEdit.replaceIfUnchanged(doc, 4,
                "let x = 1;", "const x = 1;");
        assertThat(applied).isTrue();
        assertThat(doc.getText(0, doc.getLength()))
                .isEqualTo("aaa const x = 1; zzz");
    }
}
