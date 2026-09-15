package org.nmox.studio.application;

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
 * A translated document is illustrated in its own language (v2.161.0).
 * The forge's tab shots ({@code docs/images/tabs/*.png}) are the English
 * build; every {@code docs/*.<lang>.md} and {@code docs/tutorials/*.<lang>.md}
 * must reference the {@code docs/images/<lang>/tabs/} copy the forge paints
 * with {@code --locale <lang>} instead — a Hebrew guide illustrated in
 * English undercuts the translation, and for a right-to-left language the
 * mirrored window IS the illustration. Dead references and orphan images
 * stay {@link ImageRefsTest}'s law; this gate holds only the language of
 * the shot to the language of the document. Failing-first: before the
 * rewrite every translated document named the English shots.
 */
class TranslatedShotsGateTest {

    private static final Pattern IMG = Pattern.compile("!\\[[^\\]]*\\]\\(([^)\\s]+)\\)");
    private static final Pattern TRANSLATED = Pattern.compile(".*\\.([a-z]{2})\\.md");

    @Test
    @DisplayName("every translated document's forge shots come from its own language directory")
    void translatedDocsUseTheirLanguagesShots() throws Exception {
        Path root = Path.of("..").toRealPath();
        List<Path> docs = new ArrayList<>();
        try (Stream<Path> s = Files.walk(root.resolve("docs"))) {
            s.filter(p -> TRANSLATED.matcher(p.getFileName().toString()).matches()).forEach(docs::add);
        }
        assertThat(docs).as("translated documents exist to check").hasSizeGreaterThan(20);
        List<String> wrong = new ArrayList<>();
        int checked = 0;
        for (Path doc : docs) {
            Matcher name = TRANSLATED.matcher(doc.getFileName().toString());
            name.matches();
            String lang = name.group(1);
            Matcher m = IMG.matcher(Files.readString(doc));
            while (m.find()) {
                String target = m.group(1);
                if (!target.contains("/tabs/")) {
                    continue;   // staged shots are the recorded ceiling, not this gate's
                }
                checked++;
                if (!target.contains("/" + lang + "/tabs/")) {
                    wrong.add(root.relativize(doc) + " -> " + target);
                }
            }
        }
        assertThat(checked).as("tab-shot references in translated documents").isGreaterThan(50);
        assertThat(wrong).as("a translated document illustrated with another language's shot").isEmpty();
    }
}
