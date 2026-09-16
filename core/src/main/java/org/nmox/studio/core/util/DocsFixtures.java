package org.nmox.studio.core.util;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Reads the documentation forge's fixture content (v2.163.0).
 *
 * <p>The forge stages windows with data in them so a translated guide shows
 * a translated working context: cards on a board, rows in a grid, labels on
 * a canvas. That content lives in {@code docs/i18n/forge-fixtures.json} and
 * is handed to each {@code DocsScene} as text, because the scenes live in
 * four different modules and the fallback rule must be spelled once rather
 * than four times differently.
 *
 * <p>The rule: a language's own section when it has one, English otherwise,
 * decided per SECTION rather than per file, so a language half-translated
 * still shows everything it does have. English is the fixture file's own
 * fallback and is required to be complete — {@code ForgeFixturesGateTest}
 * fails the build when a shipped language is missing.
 *
 * <p>Forge-only: nothing on a normal boot reads fixtures. The content here
 * is documentation, deliberately NOT one of the product's bundles, because
 * the product's translated surface should hold only what a user can see.
 */
public final class DocsFixtures {

    private DocsFixtures() {
    }

    /**
     * The demo project every scene stages into, so one aim serves them all:
     * the board, the infrastructure design, the database workspace and the
     * API workspace all belong to one shop the reader is shown working on.
     * A repository name, so it is NOT translated — the v2.130.0 line between
     * the product's words and the user's own.
     */
    public static final String PROJECT = "storefront";

    /** The demo project's directory under the forge's throwaway home. */
    public static java.io.File projectDir(java.io.File home) {
        return new java.io.File(new java.io.File(home, "NMOX"), PROJECT);
    }

    /** The English section every language falls back to. */
    public static final String FALLBACK = "en";

    /**
     * One section of one language's fixtures — {@code board}, {@code db},
     * {@code infra} — falling back to English when this language has no
     * entry for it.
     *
     * @param fixtures the text of {@code docs/i18n/forge-fixtures.json}
     * @param lang     the language being painted, e.g. {@code de}; blank means English
     * @param section  the section name
     */
    public static JSONObject section(String fixtures, String lang, String section) {
        JSONObject root = new JSONObject(fixtures);
        JSONObject english = root.getJSONObject(FALLBACK);
        JSONObject mine = lang == null || lang.isBlank() || FALLBACK.equals(lang)
                ? english : root.optJSONObject(lang);
        JSONObject own = mine == null ? null : mine.optJSONObject(section);
        return own != null ? own : english.getJSONObject(section);
    }

    /** A string array of a section, in file order; empty when absent. */
    public static List<String> strings(JSONObject section, String key) {
        List<String> out = new ArrayList<>();
        JSONArray arr = section.optJSONArray(key);
        for (int i = 0; arr != null && i < arr.length(); i++) {
            out.add(arr.getString(i));
        }
        return out;
    }

    /** An array of string arrays — the rows of a grid; empty when absent. */
    public static List<List<String>> rows(JSONObject section, String key) {
        List<List<String>> out = new ArrayList<>();
        JSONArray arr = section.optJSONArray(key);
        for (int i = 0; arr != null && i < arr.length(); i++) {
            JSONArray row = arr.getJSONArray(i);
            List<String> cells = new ArrayList<>();
            for (int c = 0; c < row.length(); c++) {
                cells.add(row.getString(c));
            }
            out.add(cells);
        }
        return out;
    }
}
