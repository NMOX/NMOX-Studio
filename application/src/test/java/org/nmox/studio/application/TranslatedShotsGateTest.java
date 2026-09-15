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
 *
 * <p>v2.162.0 widens it to the STAGED shots the forge now paints per
 * language ({@code scripts/docs-shots.sh} with {@code NMOX_SHOTS_STAGED=1}):
 * whenever {@code docs/images/<lang>/<name>.png} exists, a document of that
 * language must reference it rather than the English {@code docs/images/<name>.png}
 * — and the six the user guide shows must exist for every language that has
 * a tab directory. The staged shots no forge paints yet (the tutorials'
 * live-external scenes: a hit breakpoint, a real database grid, Docker,
 * Anvil) stay English and are the recorded ceiling in
 * {@code docs/engineering/l10n-completion.md}.
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
        int stagedChecked = 0;
        for (Path doc : docs) {
            Matcher name = TRANSLATED.matcher(doc.getFileName().toString());
            name.matches();
            String lang = name.group(1);
            Matcher m = IMG.matcher(Files.readString(doc));
            while (m.find()) {
                String target = m.group(1);
                if (target.contains("/tabs/")) {
                    checked++;
                    if (!target.contains("/" + lang + "/tabs/")) {
                        wrong.add(root.relativize(doc) + " -> " + target);
                    }
                    continue;
                }
                // a staged shot: English unless the forge has painted this language's own
                String shot = target.substring(target.lastIndexOf('/') + 1);
                if (Files.isRegularFile(root.resolve("docs/images").resolve(lang).resolve(shot))) {
                    stagedChecked++;
                    if (!target.contains("/" + lang + "/")) {
                        wrong.add(root.relativize(doc) + " -> " + target + " (a " + lang + " copy exists)");
                    }
                }
            }
        }
        assertThat(checked).as("tab-shot references in translated documents").isGreaterThan(50);
        assertThat(stagedChecked).as("staged-shot references in translated documents").isGreaterThan(50);
        assertThat(wrong).as("a translated document illustrated with another language's shot").isEmpty();
    }

    /** The six staged states the user guide shows, painted per language since v2.162.0. */
    static final List<String> GUIDE_STAGED = List.of("task-rack.png", "rack-rear.png", "editor.png",
            "experiment-walkthrough.png", "kvasir-explain.png", "spaces-shelf.png");

    @Test
    @DisplayName("every language with forge tab shots also has the guide's six staged shots")
    void everyLanguageHasTheGuidesStagedShots() throws Exception {
        Path images = Path.of("..").toRealPath().resolve("docs/images");
        List<String> missing = new ArrayList<>();
        int languages = 0;
        try (Stream<Path> s = Files.list(images)) {
            for (Path dir : s.filter(p -> Files.isDirectory(p.resolve("tabs"))).toList()) {
                languages++;
                for (String shot : GUIDE_STAGED) {
                    if (!Files.isRegularFile(dir.resolve(shot))) {
                        missing.add(dir.getFileName() + "/" + shot);
                    }
                }
            }
        }
        assertThat(languages).as("translated languages with a tabs directory").isGreaterThan(10);
        assertThat(missing).as("staged shots the forge has not painted for a language").isEmpty();
    }
}
