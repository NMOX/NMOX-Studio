package org.nmox.studio.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The user guide exists in every language the product speaks (v2.104.0).
 *
 * <p>Translating the chrome and leaving the manual in English gets a user
 * exactly as far as the first thing they cannot do from the buttons alone.
 * So the guide is part of the language, not a separate project — and this
 * gate derives its population from {@code UiLocale.SUPPORTED} rather than a
 * hand-kept list, which means a fourteenth language fails the build until
 * someone writes for it. Enumeration beats recollection.
 *
 * <p>Three properties beyond existence, each paid for by a way translated
 * documentation usually goes wrong:
 *
 * <ul>
 * <li><b>Commands are never translated.</b> The install block is compared
 * byte for byte against the English guide. A helpfully localized flag or a
 * smart-quoted path is a command that fails, and it fails only for readers
 * the author cannot read.</li>
 * <li><b>The words match the app's own words.</b> A guide that calls the
 * window something the window does not call itself is worse than English,
 * because now the reader is hunting for a thing that is not there. Every
 * guide is checked against {@code docs/i18n/glossary.json}, which was
 * extracted from the shipped bundles.</li>
 * <li><b>The apostrophe law (v2.98.0) reaches the docs.</b> Not for
 * MessageFormat here — a bare {@code '} beside a code span is simply the
 * wrong character in every language that uses one.</li>
 * </ul>
 */
class TranslatedGuideGateTest {

    /** A {@code new Choice("xx", "…")} row in UiLocale's supported list. */
    private static final Pattern CHOICE = Pattern.compile("new Choice\\(\"([a-z]{2})\"");

    private static final Pattern CHAPTER = Pattern.compile("(?m)^## (\\d+)\\. ");

    private static final Pattern FENCE = Pattern.compile("```bash\\n(.*?)```", Pattern.DOTALL);

    private static Path docs() {
        return Path.of("..", "docs");
    }

    /** The languages the product ships chrome for, English aside. */
    private static List<String> translatedLanguages() throws IOException {
        Path src = Path.of("..", "core", "src", "main", "java", "org", "nmox", "studio",
                "core", "util", "UiLocale.java");
        String body = Files.readString(src, StandardCharsets.UTF_8);
        String list = body.substring(body.indexOf("SUPPORTED = List.of("));
        list = list.substring(0, list.indexOf(";"));
        List<String> out = new ArrayList<>();
        Matcher m = CHOICE.matcher(list);
        while (m.find()) {
            if (!"en".equals(m.group(1))) {
                out.add(m.group(1));
            }
        }
        return out;
    }

    @Test
    @DisplayName("every language the chrome speaks has a user guide — and no guide is a ghost")
    void everyLanguageHasAGuide() throws IOException {
        List<String> languages = translatedLanguages();
        assertThat(languages).as("the locale list should not be empty").hasSizeGreaterThan(5);

        List<String> missing = new ArrayList<>();
        for (String lang : languages) {
            if (!Files.isRegularFile(docs().resolve("user-guide." + lang + ".md"))) {
                missing.add(lang);
            }
        }
        assertThat(missing)
                .as("a language whose users are handed an English manual — write the guide or drop the language")
                .isEmpty();

        List<String> ghosts = new ArrayList<>();
        try (Stream<Path> s = Files.list(docs())) {
            for (Path p : s.toList()) {
                String name = p.getFileName().toString();
                if (name.matches("user-guide\\.[a-z]{2}\\.md")
                        && !languages.contains(name.substring(11, 13))) {
                    ghosts.add(name);
                }
            }
        }
        assertThat(ghosts).as("a guide for a language the product no longer offers").isEmpty();
    }

    @Test
    @DisplayName("chapters are numbered like the English guide's, contiguously from 1")
    void chapterStructureMatches() throws IOException {
        List<String> english = chapters(docs().resolve("user-guide.md"));
        assertThat(english).as("the English guide's own chapters").startsWith("1", "2", "3");

        Map<String, String> wrong = new LinkedHashMap<>();
        for (String lang : translatedLanguages()) {
            List<String> mine = chapters(docs().resolve("user-guide." + lang + ".md"));
            if (mine.isEmpty() || !english.subList(0, mine.size()).equals(mine)) {
                wrong.put(lang, mine.toString());
            }
        }
        assertThat(wrong)
                .as("a translated guide must cover a PREFIX of the English chapters, numbered the same, "
                        + "so a reader who follows a cross-reference lands where they expect")
                .isEmpty();
    }

    @Test
    @DisplayName("a partial translation says so and links back to the English guide")
    void partialTranslationsAreHonest() throws IOException {
        List<String> english = chapters(docs().resolve("user-guide.md"));
        List<String> silent = new ArrayList<>();
        for (String lang : translatedLanguages()) {
            Path p = docs().resolve("user-guide." + lang + ".md");
            String body = Files.readString(p, StandardCharsets.UTF_8);
            if (chapters(p).size() < english.size() && !body.contains("](user-guide.md)")) {
                silent.add(lang);
            }
        }
        assertThat(silent)
                .as("a guide that stops early without naming where the rest is strands its reader")
                .isEmpty();
    }

    @Test
    @DisplayName("commands are never translated — the install block is byte-identical to English")
    void commandsAreVerbatim() throws IOException {
        String english = firstFence(docs().resolve("user-guide.md"));
        assertThat(english).as("the English install block").contains("brew install");

        Map<String, String> drifted = new LinkedHashMap<>();
        for (String lang : translatedLanguages()) {
            String mine = firstFence(docs().resolve("user-guide." + lang + ".md"));
            if (!english.equals(mine)) {
                drifted.put(lang, mine);
            }
        }
        assertThat(drifted)
                .as("a localized command is a command that fails, for readers the author cannot read")
                .isEmpty();
    }

    @Test
    @DisplayName("the guide calls each window what the window calls itself")
    void wordsMatchTheApp() throws IOException {
        JSONObject glossary = new JSONObject(
                Files.readString(docs().resolve("i18n/glossary.json"), StandardCharsets.UTF_8));
        // the terms tranche 1 actually names; the list grows with the tranches
        List<String> named = List.of("Welcome", "Task Rack", "DB Studio", "Contract Studio",
                "Infra Designer", "API Studio", "Project Studio", "Workbench", "NPM Explorer");

        List<String> mismatched = new ArrayList<>();
        int checked = 0;
        for (String lang : translatedLanguages()) {
            JSONObject mine = glossary.optJSONObject(lang);
            assertThat(mine).as("glossary entry for %s", lang).isNotNull();
            String body = Files.readString(docs().resolve("user-guide." + lang + ".md"),
                    StandardCharsets.UTF_8);
            for (String term : named) {
                String word = mine.getString(term);
                checked++;
                if (!body.contains(word)) {
                    mismatched.add(lang + ": the app says “" + word + "” for " + term
                            + ", the guide never does");
                }
            }
        }
        assertThat(checked).as("the census should cover every language's terms").isGreaterThan(100);
        assertThat(mismatched)
                .as("a guide naming a window something the window does not answer to sends the reader hunting")
                .isEmpty();
    }

    @Test
    @DisplayName("no translated guide introduces a bare ASCII apostrophe")
    void apostropheLawHolds() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String lang : translatedLanguages()) {
            Path p = docs().resolve("user-guide." + lang + ".md");
            List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).indexOf('\'') >= 0) {
                    offenders.add(p.getFileName() + ":" + (i + 1));
                }
            }
        }
        assertThat(offenders).as("use ’ (U+2019) — the v2.98.0 rule, in the docs too").isEmpty();
    }

    @Test
    @DisplayName("every guide's language bar reaches every other guide, and never links to itself")
    void theLanguageBarIsComplete() throws IOException {
        Map<String, String> names = nativeNames();
        assertThat(names).as("UiLocale's own names for its languages").containsKey("en");

        List<String> broken = new ArrayList<>();
        for (String lang : names.keySet()) {
            Path p = "en".equals(lang) ? docs().resolve("user-guide.md")
                    : docs().resolve("user-guide." + lang + ".md");
            String bar = between(Files.readString(p, StandardCharsets.UTF_8),
                    "<!-- languages -->", "<!-- /languages -->");
            if (bar.isEmpty()) {
                broken.add(lang + ": no language bar at all");
                continue;
            }
            for (Map.Entry<String, String> e : names.entrySet()) {
                String target = "en".equals(e.getKey()) ? "user-guide.md"
                        : "user-guide." + e.getKey() + ".md";
                boolean self = e.getKey().equals(lang);
                String wanted = self ? "**" + e.getValue() + "**"
                        : "[" + e.getValue() + "](" + target + ")";
                if (!bar.contains(wanted)) {
                    broken.add(lang + ": bar is missing " + (self ? "its own name, unlinked" : wanted));
                }
                if (self && bar.contains("](" + target + ")")) {
                    broken.add(lang + ": the bar links to the page the reader is already on");
                }
                if (!self && !Files.isRegularFile(docs().resolve(target))) {
                    broken.add(lang + ": bar points at " + target + ", which does not exist");
                }
            }
        }
        assertThat(broken)
                .as("the switcher is the only way most readers will find their language — it cannot rot")
                .isEmpty();
    }

    /** UiLocale's own name for each language, in its declared order. */
    private static Map<String, String> nativeNames() throws IOException {
        Path src = Path.of("..", "core", "src", "main", "java", "org", "nmox", "studio",
                "core", "util", "UiLocale.java");
        String body = Files.readString(src, StandardCharsets.UTF_8);
        String list = body.substring(body.indexOf("SUPPORTED = List.of("));
        list = list.substring(0, list.indexOf(";"));
        Map<String, String> out = new LinkedHashMap<>();
        Matcher m = Pattern.compile("new Choice\\(\"([a-z]{2})\", \"([^\"]+)\"\\)").matcher(list);
        while (m.find()) {
            out.put(m.group(1), m.group(2));
        }
        return out;
    }

    private static String between(String body, String open, String close) {
        int a = body.indexOf(open);
        int b = body.indexOf(close);
        return a < 0 || b < a ? "" : body.substring(a + open.length(), b);
    }

    @Test
    @DisplayName("a cross-reference lands on the same chapter in every language")
    void anchorsSurviveTranslation() throws IOException {
        // a heading's generated anchor comes from its own words, so a
        // translated chapter would answer to a different address and every
        // link written against the English guide would land at the top of
        // the page instead. An explicit anchor above each chapter keeps ONE
        // address per chapter across all thirteen guides — which is what
        // lets the product link a reader straight to their own §1.
        Map<String, String> english = englishAnchors();
        assertThat(english).as("the English chapter anchors")
                .containsEntry("1", "1-install").containsEntry("2", "2-first-launch");

        List<String> adrift = new ArrayList<>();
        for (String lang : translatedLanguages()) {
            String body = Files.readString(docs().resolve("user-guide." + lang + ".md"),
                    StandardCharsets.UTF_8);
            for (String chapter : chapters(docs().resolve("user-guide." + lang + ".md"))) {
                String wanted = "<a id=\"" + english.get(chapter) + "\"></a>";
                if (!body.contains(wanted)) {
                    adrift.add(lang + " chapter " + chapter + ": no " + wanted);
                }
            }
        }
        assertThat(adrift)
                .as("a link into a chapter must reach that chapter in the reader's own guide")
                .isEmpty();
    }

    /** The English guide's chapter number to its anchor id. */
    private static Map<String, String> englishAnchors() throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        Matcher m = Pattern.compile("(?m)^## (\\d+)\\. (.+)$")
                .matcher(Files.readString(docs().resolve("user-guide.md"), StandardCharsets.UTF_8));
        while (m.find()) {
            String slug = (m.group(1) + ". " + m.group(2)).toLowerCase(java.util.Locale.ROOT)
                    .replaceAll("[^\\w\\s-]", "").replaceAll("\\s+", "-");
            out.put(m.group(1), slug);
        }
        return out;
    }

    private static List<String> chapters(Path guide) throws IOException {
        List<String> out = new ArrayList<>();
        Matcher m = CHAPTER.matcher(Files.readString(guide, StandardCharsets.UTF_8));
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    private static String firstFence(Path guide) throws IOException {
        Matcher m = FENCE.matcher(Files.readString(guide, StandardCharsets.UTF_8));
        return m.find() ? m.group(1) : "";
    }
}
