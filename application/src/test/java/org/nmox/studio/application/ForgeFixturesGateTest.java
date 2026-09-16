package org.nmox.studio.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every language the guides are written in has its own scene content
 * (v2.163.0).
 *
 * <p>The forge stages windows with data in them — a board mid-sprint, a
 * grid with customers, a design with named nodes — from
 * {@code docs/i18n/forge-fixtures.json}. A language missing from that file
 * silently falls back to English, which is exactly the defect this release
 * exists to end: a translated guide showing an English working context.
 * Silent fallback is right at RUNTIME (a half-translated language should
 * still paint) and wrong at BUILD time, so the gate is what makes it loud.
 *
 * <p>Two families of value are IDENTICAL in every language by design and
 * are exempt from the copies-English law: the SQL, because keywords and
 * identifiers are code a developer retypes (v2.104.0), and the order counts
 * in the grid, because only the names and cities are the reader's world.
 * A gate that failed on those would be failing on correct content — the
 * v2.129.0 lesson about a gate measuring its own assumptions.
 */
class ForgeFixturesGateTest {

    private static final Pattern GUIDE = Pattern.compile("user-guide\\.([a-z]{2})\\.md");
    private static final String EN = "en";
    private static final String DNS_SUFFIX = ".example.com";

    /** Values that carry the reader's world and so must not equal English. */
    private static List<String> translatable(JSONObject lang) {
        List<String> out = new ArrayList<>();
        JSONObject board = lang.getJSONObject("board");
        for (String key : List.of("todo", "doing", "done")) {
            JSONArray arr = board.getJSONArray(key);
            for (int i = 0; i < arr.length(); i++) {
                out.add(arr.getString(i));
            }
        }
        for (String key : List.of("sprint", "epic", "blockOwner", "blockAction")) {
            out.add(board.getString(key));
        }
        JSONObject infra = lang.getJSONObject("infra");
        for (String key : List.of("loadBalancer", "droplet", "volume")) {
            out.add(infra.getString(key));
        }
        JSONArray rows = lang.getJSONObject("db").getJSONArray("rows");
        for (int i = 0; i < rows.length(); i++) {
            JSONArray row = rows.getJSONArray(i);
            out.add(row.getString(0)); // the customer
            out.add(row.getString(1)); // their city
        }
        return out;
    }

    private static JSONObject fixtures() throws IOException {
        Path root = Path.of("..").toRealPath();
        return new JSONObject(Files.readString(
                root.resolve("docs/i18n/forge-fixtures.json"), StandardCharsets.UTF_8));
    }

    /** The languages a user guide is written in — the readers who need pictures. */
    private static Set<String> guideLanguages() throws IOException {
        Set<String> langs = new LinkedHashSet<>();
        Path docs = Path.of("..").toRealPath().resolve("docs");
        try (Stream<Path> s = Files.list(docs)) {
            for (Path p : s.toList()) {
                Matcher m = GUIDE.matcher(p.getFileName().toString());
                if (m.matches()) {
                    langs.add(m.group(1));
                }
            }
        }
        return langs;
    }

    @Test
    @DisplayName("every translated guide's language has its own scene content")
    void everyGuideLanguageHasFixtures() throws Exception {
        JSONObject root = fixtures();
        Set<String> langs = guideLanguages();
        assertThat(langs).as("languages with a translated user guide").hasSizeGreaterThan(10);
        List<String> missing = new ArrayList<>();
        for (String lang : langs) {
            if (!root.has(lang)) {
                missing.add(lang);
            }
        }
        assertThat(missing).as("languages whose guide would show English fixture content").isEmpty();
    }

    @Test
    @DisplayName("every language carries every section and key English carries")
    void everyLanguageIsComplete() throws Exception {
        JSONObject root = fixtures();
        JSONObject en = root.getJSONObject(EN);
        List<String> gaps = new ArrayList<>();
        for (String lang : guideLanguages()) {
            if (!root.has(lang)) {
                continue; // the test above names these
            }
            JSONObject mine = root.getJSONObject(lang);
            for (String section : en.keySet()) {
                if (!mine.has(section)) {
                    gaps.add(lang + "." + section);
                    continue;
                }
                JSONObject enSection = en.getJSONObject(section);
                JSONObject mySection = mine.getJSONObject(section);
                for (String key : enSection.keySet()) {
                    if (!mySection.has(key)) {
                        gaps.add(lang + "." + section + "." + key);
                    } else if (enSection.get(key) instanceof JSONArray enArr
                            && mySection.get(key) instanceof JSONArray myArr
                            && enArr.length() != myArr.length()) {
                        // the scenes stage one card, one row, per entry:
                        // a different count is a different picture
                        gaps.add(lang + "." + section + "." + key + " has "
                                + myArr.length() + " entries, English has " + enArr.length());
                    }
                }
            }
        }
        assertThat(gaps).as("fixture content a language is missing").isEmpty();
    }

    @Test
    @DisplayName("no language's scene content is a copy of English")
    void noLanguageCopiesEnglish() throws Exception {
        JSONObject root = fixtures();
        List<String> english = translatable(root.getJSONObject(EN));
        List<String> copies = new ArrayList<>();
        for (String lang : guideLanguages()) {
            if (root.has(lang) && translatable(root.getJSONObject(lang)).equals(english)) {
                copies.add(lang);
            }
        }
        assertThat(copies)
                .as("languages whose cards, people and places are English — the defect v2.163.0 closes")
                .isEmpty();
    }

    @Test
    @DisplayName("the SQL and the order counts are identical in every language — they are machine text")
    void machineTextNeverVaries() throws Exception {
        JSONObject root = fixtures();
        JSONObject en = root.getJSONObject(EN);
        String sql = en.getJSONObject("db").getString("query");
        JSONArray enRows = en.getJSONObject("db").getJSONArray("rows");
        List<String> wrong = new ArrayList<>();
        for (String lang : guideLanguages()) {
            if (!root.has(lang)) {
                continue;
            }
            JSONObject db = root.getJSONObject(lang).getJSONObject("db");
            if (!sql.equals(db.getString("query"))) {
                wrong.add(lang + ": the SQL differs — keywords and identifiers are code");
            }
            JSONArray rows = db.getJSONArray("rows");
            for (int i = 0; i < Math.min(rows.length(), enRows.length()); i++) {
                String mine = rows.getJSONArray(i).getString(2);
                String theirs = enRows.getJSONArray(i).getString(2);
                if (!mine.equals(theirs)) {
                    wrong.add(lang + ": row " + i + " counts " + mine + ", English counts " + theirs);
                }
            }
        }
        assertThat(wrong).as("machine text that drifted between languages").isEmpty();
    }

    @Test
    @DisplayName("every hostname keeps the reserved example.com suffix")
    void hostnamesStayReserved() throws Exception {
        JSONObject root = fixtures();
        List<String> wrong = new ArrayList<>();
        for (String lang : guideLanguages()) {
            if (!root.has(lang)) {
                continue;
            }
            String dns = root.getJSONObject(lang).getJSONObject("infra").getString("dns");
            // the first label may be the reader's own word for a shop; the
            // suffix may not move — RFC 2606 reserves it for documentation
            if (!dns.endsWith(DNS_SUFFIX)) {
                wrong.add(lang + ": " + dns);
            }
        }
        assertThat(wrong).as("hostnames that left the reserved documentation domain").isEmpty();
    }
}
