package org.nmox.studio.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The newcomer's three pages speak every language the product does (3.1.0):
 * the quickstart, the glossary and Coming from VS Code. A reader who is
 * sent to the user guide in their own language and meets the quickstart in
 * English has met the product's first wall in the one place it should not
 * be.
 *
 * <p>The population is {@code UiLocale.SUPPORTED}, read from source, so a
 * new language fails here until its pages exist. Each translation keeps the
 * English page's shape: a language bar naming all fifteen, the same number
 * of {@code ##} sections in the same order each carrying the English
 * heading's anchor (so an English link lands in every language), and every
 * fenced code block byte for byte the English one - a translated command is
 * a command that fails, only for readers the author cannot read.
 */
class TranslatedNewcomerDocsTest {

    private static final List<String> PAGES = List.of("quickstart", "glossary", "coming-from-vscode");
    private static final Pattern CHOICE = Pattern.compile("new Choice\\(\"([a-z]{2})\"");
    private static final Pattern H2 = Pattern.compile("(?m)^## (.+)$");
    private static final Pattern FENCE = Pattern.compile("```[a-z]*\\n.*?```", Pattern.DOTALL);
    private static final Pattern ANCHORED_H2 = Pattern.compile("(?m)^<a id=\"([^\"]+)\"></a>\\s*\\n## ");

    private static String read(Path p) throws IOException {
        return Files.readString(p).replace("\r\n", "\n");
    }

    static List<String> languages() throws IOException {
        String body = read(Path.of("..", "core", "src", "main", "java", "org", "nmox", "studio",
                "core", "util", "UiLocale.java"));
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

    private static List<String> all(Pattern p, String text, int group) {
        List<String> out = new ArrayList<>();
        Matcher m = p.matcher(text);
        while (m.find()) {
            out.add(m.group(group));
        }
        return out;
    }

    @Test
    @DisplayName("every newcomer page exists in every language, with the English page's shape")
    void everyLanguageEveryPage() throws IOException {
        List<String> langs = languages();
        assertThat(langs).as("the languages the chrome speaks").hasSizeGreaterThanOrEqualTo(14);
        List<String> problems = new ArrayList<>();
        for (String page : PAGES) {
            String en = read(Path.of("..", "docs", page + ".md"));
            List<String> enHeadings = all(H2, en, 1);
            List<String> enFences = all(FENCE, en, 0);
            assertThat(en).as("%s.md carries the language bar", page).contains("<!-- languages -->");
            for (String lang : langs) {
                Path p = Path.of("..", "docs", page + "." + lang + ".md");
                if (!Files.isRegularFile(p)) {
                    problems.add(p.getFileName() + ": missing");
                    continue;
                }
                String t = read(p);
                if (!t.contains("<!-- languages -->") || !t.contains("<!-- /languages -->")) {
                    problems.add(p.getFileName() + ": no language bar");
                } else {
                    String bar = t.substring(t.indexOf("<!-- languages -->"), t.indexOf("<!-- /languages -->"));
                    if (!bar.contains("(" + page + ".md)")) {
                        problems.add(p.getFileName() + ": the bar does not link the English page");
                    }
                    for (String other : langs) {
                        if (!other.equals(lang) && !bar.contains("(" + page + "." + other + ".md)")) {
                            problems.add(p.getFileName() + ": the bar misses " + other);
                        }
                    }
                }
                int sections = all(H2, t, 1).size();
                if (sections != enHeadings.size()) {
                    problems.add(p.getFileName() + ": " + sections + " sections, English has " + enHeadings.size());
                }
                List<String> anchors = all(ANCHORED_H2, t, 1);
                List<String> expected = new ArrayList<>();
                for (String h : enHeadings) {
                    expected.add(DocsLinksResolveTest.slug(h));
                }
                if (!anchors.equals(expected)) {
                    problems.add(p.getFileName() + ": section anchors " + anchors + " are not the English " + expected);
                }
                if (!all(FENCE, t, 0).equals(enFences)) {
                    problems.add(p.getFileName() + ": code blocks differ from the English page's");
                }
            }
        }
        assertThat(problems).as("newcomer pages that are missing or out of shape").isEmpty();
    }
}
