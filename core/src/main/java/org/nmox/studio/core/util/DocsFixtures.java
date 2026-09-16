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
 * decided per SECTION, so a half-translated language still shows everything
 * it does have. {@code ForgeFixturesGateTest} fails the build when a shipped
 * language is missing.
 *
 * <p><b>Only JDK types cross this class's boundary.</b> Every NMOX module
 * bundles its own copy of org.json in its own classloader (tech-debt ledger
 * 3), so a {@code JSONObject} returned from core is a DIFFERENT class from the
 * {@code JSONObject} a scene in ui, infra, dbstudio or apiclient compiles
 * against. The first cut returned one: every unit test passed on a flat
 * classpath, and in the assembled app the first scene died with a linkage
 * error that stopped the forge dead. {@code DocsFixturesTest} holds the
 * signature rule by reflection.
 *
 * <p>Forge-only: nothing on a normal boot reads fixtures.
 */
public final class DocsFixtures {

    private DocsFixtures() {
    }

    /** The English section every language falls back to. */
    public static final String FALLBACK = "en";

    /**
     * The demo project every scene stages into, so one aim serves them all.
     * A repository name, so it is NOT translated.
     */
    public static final String PROJECT = "storefront";

    /** The demo project's directory under the forge's throwaway home. */
    public static java.io.File projectDir(java.io.File home) {
        return new java.io.File(new java.io.File(home, "NMOX"), PROJECT);
    }

    /** One string value of a language's section, falling back to English. */
    public static String text(String fixtures, String lang, String section, String key) {
        return section(fixtures, lang, section).getString(key);
    }

    /** A string array of a language's section, in file order; empty when absent. */
    public static List<String> strings(String fixtures, String lang, String section, String key) {
        List<String> out = new ArrayList<>();
        JSONArray arr = section(fixtures, lang, section).optJSONArray(key);
        for (int i = 0; arr != null && i < arr.length(); i++) {
            out.add(arr.getString(i));
        }
        return out;
    }

    /** An array of string arrays — the rows of a grid; empty when absent. */
    public static List<List<String>> rows(String fixtures, String lang, String section, String key) {
        List<List<String>> out = new ArrayList<>();
        JSONArray arr = section(fixtures, lang, section).optJSONArray(key);
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

    /** Package-private on purpose: a JSONObject must never leave this module. */
    static JSONObject section(String fixtures, String lang, String section) {
        JSONObject root = new JSONObject(fixtures);
        JSONObject english = root.getJSONObject(FALLBACK);
        JSONObject mine = lang == null || lang.isBlank() || FALLBACK.equals(lang)
                ? english : root.optJSONObject(lang);
        JSONObject own = mine == null ? null : mine.optJSONObject(section);
        return own != null ? own : english.getJSONObject(section);
    }
}
