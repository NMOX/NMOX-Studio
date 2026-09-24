package org.nmox.studio.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every tutorial exists in every language the product speaks (v2.153.0).
 *
 * <p>The user guide has been translated since v2.104.0; the tutorials, the
 * short do-it-now walkthroughs a new user actually follows, stayed English.
 * The population is derived twice: the tutorials are the English files in
 * {@code docs/tutorials}, the languages are {@code UiLocale.SUPPORTED}. A new
 * tutorial or a sixteenth language fails the build until someone writes it.
 *
 * <p>Beyond existence, the properties a translated walkthrough usually loses:
 * the commands (every fenced block byte for byte), the shape (the same
 * headings and screenshots — since v2.161.0 a forge shot may come from the
 * language's own directory, the same picture painted in the reader's
 * words), and the way around (the language bar, and links
 * that stay in the reader's language instead of dropping them back into English).
 */
class TranslatedTutorialsGateTest {

    private static final Path DIR = Path.of("..", "docs", "tutorials");
    private static final List<String> LANGUAGES = ShippedLocales.TRANSLATED;
    private static final Pattern FENCE = Pattern.compile("(?ms)^```.*?^```");
    private static final Pattern IMAGE = Pattern.compile("!\\[[^\\]]*\\]\\(([^)\\s]+)");
    private static final Pattern HEADING = Pattern.compile("(?m)^(#{2,3}) ");
    private static final Pattern BAR = Pattern.compile("(?s)<!-- languages -->\\n(.*?)\\n<!-- /languages -->");

    private static List<String> englishStems() throws IOException {
        try (Stream<Path> s = Files.list(DIR)) {
            return s.map(p -> p.getFileName().toString())
                    .filter(n -> n.matches("[A-Za-z0-9-]+\\.md"))
                    .map(n -> n.substring(0, n.length() - 3)).sorted().toList();
        }
    }

    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
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
    @DisplayName("every tutorial has a translation in every language, and no translation is a ghost")
    void everyTutorialInEveryLanguage() throws IOException {
        List<String> stems = englishStems();
        assertThat(stems).as("English tutorials").contains("README", "the-task-rack");
        List<String> missing = new ArrayList<>();
        for (String stem : stems) {
            for (String lang : LANGUAGES) {
                if (!Files.isRegularFile(DIR.resolve(stem + "." + lang + ".md"))) {
                    missing.add(stem + "." + lang + ".md");
                }
            }
        }
        List<String> ghosts = new ArrayList<>();
        try (Stream<Path> s = Files.list(DIR)) {
            for (String n : s.map(p -> p.getFileName().toString()).toList()) {
                Matcher m = Pattern.compile("([A-Za-z0-9-]+)\\.([a-z]{2})\\.md").matcher(n);
                if (m.matches() && (!stems.contains(m.group(1)) || !LANGUAGES.contains(m.group(2)))) {
                    ghosts.add(n);
                }
            }
        }
        assertThat(missing).as("a tutorial a reader of that language cannot follow").isEmpty();
        assertThat(ghosts).as("a translation with no English tutorial or no shipped language behind it").isEmpty();
    }

    @Test
    @DisplayName("commands, headings and screenshots are the English tutorial's")
    void theShapeIsTheEnglishTutorials() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (String stem : englishStems()) {
            String en = read(DIR.resolve(stem + ".md"));
            for (String lang : LANGUAGES) {
                Path p = DIR.resolve(stem + "." + lang + ".md");
                if (!Files.isRegularFile(p)) {
                    continue; // the existence law names it
                }
                String tr = read(p);
                if (!all(FENCE, en, 0).equals(all(FENCE, tr, 0))) {
                    wrong.add(p.getFileName() + ": a fenced block differs from English (commands are never translated)");
                }
                if (!all(HEADING, en, 1).equals(all(HEADING, tr, 1))) {
                    wrong.add(p.getFileName() + ": headings " + all(HEADING, tr, 1) + " but English has " + all(HEADING, en, 1));
                }
                // a translated tutorial shows the forge's shot in ITS language
                // (docs/images/<lang>/tabs/, v2.161.0); folded back to the
                // English path, the set must still be the English tutorial's
                List<String> shots = all(IMAGE, tr, 1).stream()
                        .map(ref -> ref.replace("images/" + lang + "/", "images/")).toList();
                if (!all(IMAGE, en, 1).equals(shots)) {
                    wrong.add(p.getFileName() + ": screenshots " + shots + " but English has " + all(IMAGE, en, 1));
                }
            }
        }
        assertThat(wrong).as("a translated tutorial that is not the English walkthrough").isEmpty();
    }

    @Test
    @DisplayName("the language bar reaches every other language and marks its own")
    void theLanguageBar() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (String stem : englishStems()) {
            Matcher enBar = BAR.matcher(read(DIR.resolve(stem + ".md")));
            if (!enBar.find()) {
                wrong.add(stem + ".md: no language bar");
                continue;
            }
            for (String lang : LANGUAGES) {
                Path p = DIR.resolve(stem + "." + lang + ".md");
                if (!Files.isRegularFile(p)) {
                    continue;
                }
                Matcher own = Pattern.compile("\\[([^\\]]+)\\]\\(" + Pattern.quote(stem + "." + lang + ".md") + "\\)")
                        .matcher(enBar.group(1));
                if (!own.find()) {
                    wrong.add(stem + ".md: its bar has no link for " + lang);
                    continue;
                }
                String expected = enBar.group(1)
                        .replace("**English**", "[English](" + stem + ".md)")
                        .replace(own.group(), "**" + own.group(1) + "**");
                Matcher trBar = BAR.matcher(read(p));
                if (!trBar.find() || !trBar.group(1).equals(expected)) {
                    wrong.add(p.getFileName() + ": the language bar is not the English bar with its own language marked");
                }
            }
        }
        assertThat(wrong).as("a tutorial whose reader cannot switch language").isEmpty();
    }

    @Test
    @DisplayName("links stay in the reader's language")
    void linksStayInTheReadersLanguage() throws IOException {
        List<String> stems = englishStems();
        List<String> wrong = new ArrayList<>();
        for (String stem : stems) {
            for (String lang : LANGUAGES) {
                Path p = DIR.resolve(stem + "." + lang + ".md");
                if (!Files.isRegularFile(p)) {
                    continue;
                }
                String body = BAR.matcher(read(p)).replaceFirst("");
                for (String target : all(Pattern.compile("\\]\\(([^)#\\s]+)"), body, 1)) {
                    String name = target.replaceFirst("^\\./", "");
                    boolean englishTutorial = name.matches("[A-Za-z0-9-]+\\.md")
                            && stems.contains(name.substring(0, name.length() - 3));
                    if (englishTutorial || name.equals("../user-guide.md")) {
                        wrong.add(p.getFileName() + ": links " + target + " (use its ." + lang + ".md sibling)");
                    }
                }
            }
        }
        assertThat(wrong).as("a translated tutorial that sends its reader back to English").isEmpty();
    }

    /**
     * French sets a no-break space before {@code : ; ! ? »} and after {@code «}
     * (conventions.md), and {@code NativeTypographyGateTest} holds the bundles to
     * it. The documents were never held: the French user guide had 133 ordinary
     * spaces there and none of the no-break kind, so a narrow window could wrap a
     * colon onto a line of its own. Code, link targets and table rules are not prose.
     */
    @Test
    @DisplayName("French documents keep their punctuation on the word's line")
    void frenchDocumentsUseNoBreakSpaces() throws IOException {
        List<Path> docs = new ArrayList<>(List.of(Path.of("..", "docs", "user-guide.fr.md")));
        for (String stem : englishStems()) {
            Path p = DIR.resolve(stem + ".fr.md");
            if (Files.isRegularFile(p)) {
                docs.add(p);
            }
        }
        // `![` opens an image: its ! is Markdown, not the French exclamation
        // mark (3.1.0 - the guide's first indented image under a bullet)
        Pattern notProse = Pattern.compile("`[^`]*`|!\\[|\\]\\([^)]*\\)|<[^>]+>|https?://\\S+");
        List<String> wrong = new ArrayList<>();
        for (Path p : docs) {
            boolean fence = false;
            int n = 0;
            for (String line : read(p).split("\n")) {
                n++;
                if (line.stripLeading().startsWith("```")) {
                    fence = !fence;
                    continue;
                }
                if (fence || line.startsWith("    ") || line.matches("\\s*\\|?[\\s:|\\-]+\\|?\\s*")) {
                    continue;
                }
                Matcher m = Pattern.compile(" [:;!?»]|« ").matcher(notProse.matcher(line).replaceAll(" "));
                if (m.find()) {
                    wrong.add(p.getFileName() + ":" + n + ": \"" + line.substring(Math.max(0, m.start() - 20),
                            Math.min(line.length(), m.end() + 10)) + "\"");
                }
            }
        }
        assertThat(wrong).as("an ordinary space where French sets a no-break one (U+00A0)").isEmpty();
    }
}
