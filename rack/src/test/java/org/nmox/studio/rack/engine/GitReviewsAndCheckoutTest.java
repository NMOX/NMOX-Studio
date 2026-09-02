package org.nmox.studio.rack.engine;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.engine.GitCheckoutGuard.Verdict;
import org.nmox.studio.rack.engine.GitReviews.Comment;

import static org.assertj.core.api.Assertions.assertThat;

class GitReviewsAndCheckoutTest {

    private static JSONObject comment(String path, Integer line, Integer originalLine,
            String login, String body) {
        JSONObject o = new JSONObject().put("path", path).put("body", body)
                .put("created_at", "2026-09-02T14:00:00Z");
        if (line != null) {
            o.put("line", line.intValue());
        }
        if (originalLine != null) {
            o.put("original_line", originalLine.intValue());
        }
        if (login != null) {
            o.put("user", new JSONObject().put("login", login));
        }
        return o;
    }

    @Test
    @DisplayName("Review comments parse with path, line (original_line as the fallback), author, body")
    void parseComments() {
        String json = new JSONArray()
                .put(comment("src/a.js", 12, null, "reviewer", "Use const here."))
                .put(comment("src/b.js", null, 40, "other", "Outdated diff, still true."))
                .put(comment("src/c.js", 3, null, null, "No user object."))
                .put(new JSONObject().put("not", "a comment"))
                .toString();
        List<Comment> c = GitReviews.parse(json);
        assertThat(c).hasSize(3);
        assertThat(c.get(0)).isEqualTo(new Comment("src/a.js", 12, "reviewer", "Use const here.",
                "2026-09-02T14:00:00Z"));
        assertThat(c.get(1).line()).isEqualTo(40);
        assertThat(c.get(2).author()).isEmpty();
        assertThat(GitReviews.parse("")).isEmpty();
    }

    @Test
    @DisplayName("Bounded twice: the comment cap and the body clip with an honest marker")
    void bounded() {
        JSONArray many = new JSONArray();
        for (int i = 0; i < GitReviews.LIMIT + 5; i++) {
            many.put(comment("f", i, null, "u", "b" + i));
        }
        assertThat(GitReviews.parse(many.toString())).hasSize(GitReviews.LIMIT);
        assertThat(GitReviews.truncated(many.toString())).isTrue();
        String longBody = "x".repeat(GitReviews.MAX_BODY_CHARS + 50);
        Comment clipped = GitReviews.parse(new JSONArray().put(comment("f", 1, null, "u", longBody))
                .toString()).get(0);
        assertThat(clipped.body()).hasSize(GitReviews.MAX_BODY_CHARS + "\n[comment truncated]".length())
                .endsWith("[comment truncated]");
    }

    @Test
    @DisplayName("Rendering is plain text: path:line, author, date, body — a markup body stays characters")
    void render() {
        String text = GitReviews.render(List.of(
                new Comment("src/a.js", 12, "reviewer", "<html><img src=x>", "2026-09-02T14:00:00Z"),
                new Comment("", 0, "", "no anchor", "")));
        assertThat(text).startsWith("src/a.js:12 — reviewer · 2026-09-02T14:00:00Z\n<html><img src=x>");
        assertThat(text).contains("\n\n(no file) — (unknown)\nno anchor");
    }

    @Test
    @DisplayName("A failed attempt's tracked leftovers are restored; untracked files never count")
    void leftovers() {
        assertThat(GitCheckoutGuard.leftoversToRestore("", "M  CHANGELOG.md\nM  README.md\n")).isTrue();
        assertThat(GitCheckoutGuard.leftoversToRestore("?? notes.txt\n", "?? notes.txt\nM  a.js\n")).isTrue();
        // nothing new: the attempt left the tree as it was
        assertThat(GitCheckoutGuard.leftoversToRestore("", "")).isFalse();
        assertThat(GitCheckoutGuard.leftoversToRestore("?? notes.txt\n", "?? notes.txt\n")).isFalse();
        // the guard never lets a dirty tree reach an attempt, but the helper stays honest
        assertThat(GitCheckoutGuard.leftoversToRestore(" M a.js\n", " M a.js\nM  b.js\n")).isFalse();
    }

    @Test
    @DisplayName("A checkout refuses modified or staged files, allows untracked ones, and says so")
    void checkoutGuard() {
        assertThat(GitCheckoutGuard.judge("")).isEqualTo(new Verdict(true, ""));
        assertThat(GitCheckoutGuard.judge(null).allowed()).isTrue();
        Verdict dirty = GitCheckoutGuard.judge(" M src/a.js\nA  src/new.js\n?? notes.txt\n");
        assertThat(dirty.allowed()).isFalse();
        assertThat(dirty.reason()).startsWith("2 uncommitted changes").contains("never carries");
        Verdict untracked = GitCheckoutGuard.judge("?? notes.txt\n?? scratch/\n");
        assertThat(untracked.allowed()).isTrue();
        assertThat(untracked.reason()).isEqualTo("2 untracked files stay in place.");
    }
}
