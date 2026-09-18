package org.nmox.studio.editor.i18n;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.editor.i18n.I18nCatalogs.Catalog;
import org.nmox.studio.editor.i18n.I18nCatalogs.Catalogs;
import org.nmox.studio.editor.i18n.I18nCatalogs.Entry;
import org.nmox.studio.editor.i18n.I18nCatalogs.Format;
import org.nmox.studio.editor.i18n.I18nCheck.Finding;
import org.nmox.studio.editor.i18n.I18nCheck.Kind;
import org.nmox.studio.editor.i18n.I18nCheck.Report;
import org.nmox.studio.rack.engine.DiagnosticsBus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rendering half of the door: findings become bus problems with the
 * error bit only on a mismatch, and the status sentence reads the
 * counts from the report (the capped identical count with its
 * remainder, the unused verdict or the reason it was skipped, the clean
 * case) — pure over the report, so every branch is a unit test.
 */
class CheckTranslationsActionTest {

    private static final Path ROOT = Path.of("/p");

    private static Catalogs catalogs(int n) {
        Map<String, Entry> e = new LinkedHashMap<>();
        e.put("k", new Entry("v", 1, null));
        List<Catalog> cats = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            cats.add(new Catalog("l" + i, ROOT.resolve("l" + i + ".json"), "", e));
        }
        return new Catalogs(Format.I18NEXT_NESTED, ROOT, "locales/", "l0", "en present", cats, true, null, 0);
    }

    private static Finding f(Kind kind, String locale, String key, int line, String detail) {
        return new Finding(kind, locale, key, ROOT.resolve("de.json"), line, detail);
    }

    @Test
    @DisplayName("problems: file, line and the error bit — only a mismatch is an error; messages name locale and key")
    void problemsCarryTheErrorBit() {
        Report r = new Report(List.of(
                f(Kind.MISSING, "de", "app.title", 3, ""),
                f(Kind.MISSING, "de", "7a1b", 9, "new"),
                f(Kind.IDENTICAL, "fr", "ok", 4, ""),
                f(Kind.MISMATCH, "de", "hi", 5, "[name] ≠ [nombre]"),
                f(Kind.UNUSED, null, "dead", 6, ""),
                f(Kind.UNUSED, null, "gone", 7, "messages.xlf"),
                f(Kind.POSSIBLY_UNUSED, null, "errors.404", 8, "errors.${…}"),
                f(Kind.YAML_REFUSED, null, "", 1, "")),
                Map.of(), null, List.of());
        List<DiagnosticsBus.Problem> problems = CheckTranslationsAction.problems(r);
        assertThat(problems).hasSize(8);
        assertThat(problems).extracting(DiagnosticsBus.Problem::error)
                .containsExactly(false, false, false, true, false, false, false, false);
        assertThat(problems.get(0).file()).isEqualTo(ROOT.resolve("de.json").toFile());
        assertThat(problems.get(0).line()).isEqualTo(3);
        assertThat(problems.get(0).message()).isEqualTo("de: missing app.title");
        assertThat(problems.get(1).message()).contains("7a1b").contains("new");
        assertThat(problems.get(2).message()).contains("fr").contains("ok").contains("identical");
        assertThat(problems.get(3).message()).contains("hi").contains("[name] ≠ [nombre]");
        assertThat(problems.get(4).message()).contains("dead").contains("not referenced");
        assertThat(problems.get(5).message()).contains("gone").contains("messages.xlf");
        assertThat(problems.get(6).message()).contains("errors.404").contains("errors.${…}");
        assertThat(problems.get(7).message()).contains("YAML");
    }

    @Test
    @DisplayName("the sentence: catalogs, per-locale missing/identical/mismatch, unused — counted from the report")
    void summaryCountsFromTheReport() {
        Report r = new Report(List.of(
                f(Kind.MISSING, "de", "a", 1, ""), f(Kind.MISSING, "de", "b", 2, ""),
                f(Kind.MISSING, "de", "c", 3, ""), f(Kind.MISSING, "de", "d", 4, ""),
                f(Kind.IDENTICAL, "fr", "e", 5, ""), f(Kind.IDENTICAL, "fr", "f", 6, ""),
                f(Kind.MISMATCH, "de", "g", 7, ""),
                f(Kind.POSSIBLY_UNUSED, null, "h", 8, "x.${…}")),
                Map.of("fr", 2), null, List.of("x.${…}"));
        List<String> parts = CheckTranslationsAction.summary(catalogs(3), r);
        assertThat(String.join(", ", parts)).isEqualTo(
                "3 catalogs, de missing 4, de: 1 placeholder mismatch, fr identical 2, 1 key possibly unused");
    }

    @Test
    @DisplayName("identical beyond the cap says how many more; several unused keys pluralize; a skipped unused check says why")
    void summaryRemaindersAndSkips() {
        Report capped = new Report(List.of(f(Kind.IDENTICAL, "fr", "a", 1, "")),
                Map.of("fr", 51), I18nCheck.SKIPPED_PARTIAL, List.of());
        assertThat(String.join(", ", CheckTranslationsAction.summary(catalogs(1), capped)))
                .isEqualTo("1 catalog, fr identical 1 (…and 50 more), unused keys not judged (the source walk stopped at its cap)");
        Report unused = new Report(List.of(f(Kind.UNUSED, null, "a", 1, ""), f(Kind.UNUSED, null, "b", 2, "")),
                Map.of(), null, List.of());
        assertThat(String.join(", ", CheckTranslationsAction.summary(catalogs(2), unused)))
                .isEqualTo("2 catalogs, 2 keys unused");
        Report lingui = new Report(List.of(), Map.of(), I18nCheck.SKIPPED_LINGUI, List.of());
        assertThat(String.join(", ", CheckTranslationsAction.summary(catalogs(2), lingui)))
                .isEqualTo("2 catalogs, unused keys not judged (Lingui keys are source text)");
    }

    @Test
    @DisplayName("a clean run says so; a YAML refusal is the whole sentence")
    void summaryCleanAndRefused() {
        Report clean = new Report(List.of(), Map.of(), null, List.of());
        assertThat(String.join(", ", CheckTranslationsAction.summary(catalogs(2), clean)))
                .isEqualTo("2 catalogs, nothing to report");
        Catalogs refused = new Catalogs(Format.VUE_NESTED, ROOT, "src/locales/", "en", "refused: YAML catalogs",
                List.of(), true, ROOT.resolve("package.json"), 4);
        Report r = I18nCheck.run(refused, null);
        assertThat(String.join(", ", CheckTranslationsAction.summary(refused, r)))
                .isEqualTo("0 catalogs, YAML catalogs are not read yet — JSON catalogs are");
    }
}
