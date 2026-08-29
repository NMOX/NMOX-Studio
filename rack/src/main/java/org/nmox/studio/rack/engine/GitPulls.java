package org.nmox.studio.rack.engine;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The pure parse behind the git chip's Pull Requests list
 * (competitive-lens R6: "no PR surface — PRs live in the browser").
 * The chip runs the user's own {@code gh pr list --json …} (their
 * auth, their CLI — the product stores no forge token, honoring the
 * keychain-only law by never holding a credential at all) and this
 * class turns the JSON into rows. Hostile titles stay DATA — the
 * dialog renders through PlainTables, and nothing here interprets a
 * string.
 */
public final class GitPulls {

    private GitPulls() {
    }

    /** The fetch cap — a list past this is a browser job, not a menu. */
    public static final int LIMIT = 30;

    /** One open pull request, exactly as gh reported it. */
    public record Pull(int number, String title, String author,
            String branch, String url) {
    }

    /**
     * Parses {@code gh pr list --json number,title,author,headRefName,url}
     * output. A malformed element loses itself, not the list; malformed
     * JSON overall throws and the caller phrases the refusal.
     */
    public static List<Pull> parse(String json) {
        JSONArray array = new JSONArray(json == null ? "" : json);
        List<Pull> pulls = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.optJSONObject(i);
            if (o == null || !o.has("number")) {
                continue;
            }
            JSONObject author = o.optJSONObject("author");
            pulls.add(new Pull(o.optInt("number"),
                    o.optString("title", "(untitled)"),
                    author == null ? "" : author.optString("login", ""),
                    o.optString("headRefName", ""),
                    o.optString("url", "")));
        }
        return pulls;
    }
}
