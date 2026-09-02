package org.nmox.studio.rack.engine;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Review threads of a pull request, the pure half: the parse of
 * {@code gh api repos/{owner}/{repo}/pulls/N/comments} (review comments
 * anchored to a file and line) and the plain-text rendering the dialog
 * shows. Bounded twice — at most {@link #LIMIT} comments are kept and each
 * body is clipped at {@link #MAX_BODY_CHARS} with an honest marker — and
 * rendered as TEXT: the dialog is a text area, never a label, so a body
 * that begins with {@code <html>} can never make the IDE fetch a URL (the
 * v1.306.0 html-render class).
 */
public final class GitReviews {

    /** Comments kept per pull request. */
    public static final int LIMIT = 200;
    /** Characters of a comment body kept before the honest marker. */
    public static final int MAX_BODY_CHARS = 2_000;

    /** One review comment: where it hangs, who wrote it, what it says. */
    public record Comment(String path, int line, String author, String body, String createdAt) {
    }

    private GitReviews() {
    }

    /** Parses the API's array; malformed entries are skipped, the rest kept in order. */
    public static List<Comment> parse(String json) {
        JSONArray array = new JSONArray(json == null || json.isBlank() ? "[]" : json);
        List<Comment> out = new ArrayList<>();
        for (int i = 0; i < array.length() && out.size() < LIMIT; i++) {
            JSONObject o = array.optJSONObject(i);
            if (o == null || !o.has("body")) {
                continue;
            }
            // a comment on an outdated diff carries original_line only
            int line = o.optInt("line", o.optInt("original_line", 0));
            JSONObject user = o.optJSONObject("user");
            String body = o.optString("body", "");
            if (body.length() > MAX_BODY_CHARS) {
                body = body.substring(0, MAX_BODY_CHARS) + "\n[comment truncated]";
            }
            out.add(new Comment(o.optString("path", ""), line,
                    user == null ? "" : user.optString("login", ""),
                    body, o.optString("created_at", "")));
        }
        return out;
    }

    /** Whether the API returned more than we keep. */
    public static boolean truncated(String json) {
        JSONArray array = new JSONArray(json == null || json.isBlank() ? "[]" : json);
        return array.length() > LIMIT;
    }

    /** The dialog's text: one block per comment, path:line then author, then the body. */
    public static String render(List<Comment> comments) {
        StringBuilder sb = new StringBuilder();
        for (Comment c : comments) {
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(c.path().isBlank() ? "(no file)" : c.path());
            if (c.line() > 0) {
                sb.append(':').append(c.line());
            }
            sb.append(" — ").append(c.author().isBlank() ? "(unknown)" : c.author());
            if (!c.createdAt().isBlank()) {
                sb.append(" · ").append(c.createdAt());
            }
            sb.append('\n').append(c.body());
        }
        return sb.toString();
    }
}
