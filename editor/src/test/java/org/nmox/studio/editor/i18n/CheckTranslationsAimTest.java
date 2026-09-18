package org.nmox.studio.editor.i18n;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.engine.DiagnosticsBus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A result belongs to the workspace that produced it (the v1.172.0 law):
 * the check reads the project aimed at click time, and a walk of hundreds
 * of files is long enough for the aim to move. If it did, the findings
 * must not become the bus's current {@code i18n} batch — that would hang
 * the OLD project's rows in Action Items under the NEW one (the 2026-09-17
 * arc review, re-aim lens).
 */
class CheckTranslationsAimTest {

    private static Path i18nextProject(Path tmp, String name) throws Exception {
        Path root = Files.createDirectories(tmp.resolve(name));
        Files.writeString(root.resolve("package.json"), "{\"dependencies\":{\"i18next\":\"1\"}}");
        Files.createDirectories(root.resolve("public/locales/en"));
        Files.createDirectories(root.resolve("public/locales/de"));
        Files.writeString(root.resolve("public/locales/en/common.json"), "{\"hello\":\"Hello {{name}}\"}");
        Files.writeString(root.resolve("public/locales/de/common.json"), "{\"hello\":\"Hallo {{nom}}\"}");
        return root;
    }

    @Test
    @DisplayName("a run whose project is no longer aimed when it finishes publishes NOTHING; the same run with the aim held publishes its findings")
    void movedAimPublishesNothing(@TempDir Path tmp) throws Exception {
        Path project = i18nextProject(tmp, "checked");
        Path other = Files.createDirectories(tmp.resolve("other"));
        DiagnosticsBus.Problem sentinel = new DiagnosticsBus.Problem(
                tmp.resolve("sentinel.json").toFile(), 1, "the batch before the run", false);
        DiagnosticsBus.publish(CheckTranslationsAction.TOOL, List.of(sentinel));

        CheckTranslationsAction.run(project, other::toFile);
        assertThat(DiagnosticsBus.all().get(CheckTranslationsAction.TOOL))
                .as("the aim moved: the last good batch stays, the stale run's rows never land")
                .containsExactly(sentinel);

        CheckTranslationsAction.run(project, () -> null);
        assertThat(DiagnosticsBus.all().get(CheckTranslationsAction.TOOL))
                .as("no project aimed at all: nothing lands either")
                .containsExactly(sentinel);

        CheckTranslationsAction.run(project, project::toFile);
        List<DiagnosticsBus.Problem> published = DiagnosticsBus.all().get(CheckTranslationsAction.TOOL);
        assertThat(published).as("the aim held: the placeholder mismatch is published").isNotEmpty();
        assertThat(published).noneMatch(p -> p == sentinel);
        assertThat(published).anyMatch(p -> p.error() && p.file().getName().equals("common.json"));

        // the predicate itself: spelling differences are not a move
        File spelledDifferently = new File(project.toFile(), "." + File.separator + ".");
        assertThat(CheckTranslationsAction.stillAimed(project, spelledDifferently)).isTrue();
        assertThat(CheckTranslationsAction.stillAimed(project, other.toFile())).isFalse();
        assertThat(CheckTranslationsAction.stillAimed(project, null)).isFalse();
    }
}
