package org.nmox.studio.editor.i18n;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.editor.i18n.I18nCatalogs.Catalog;
import org.nmox.studio.editor.i18n.I18nCatalogs.Catalogs;
import org.nmox.studio.editor.i18n.I18nCatalogs.Entry;
import org.nmox.studio.editor.i18n.I18nCatalogs.Format;
import org.nmox.studio.editor.i18n.I18nCheck.Finding;
import org.nmox.studio.editor.i18n.I18nCheck.Kind;
import org.nmox.studio.editor.i18n.I18nCheck.Report;
import org.nmox.studio.editor.i18n.I18nUsage.Ref;
import org.nmox.studio.editor.i18n.I18nUsage.Usage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Each trap alone, over hand-built {@link Catalogs} records so the
 * check is judged apart from the parsers: plural siblings are not
 * missing (M1), {@code $schema} is ignored at the parser, an ICU value
 * is never "identical" (M2), pipe values and letterless values are
 * never identical, a placeholder mismatch IS an error, an Angular
 * {@code state="new"} is missing on its own line, a dynamic lookup
 * downgrades unused to "possibly", a partial census suppresses unused,
 * namespaces are per file, and the source locale is skipped.
 */
class I18nCheckTest {

    private static final Path ROOT = Path.of("/p");

    private static Map<String, Entry> entries(String... kv) {
        Map<String, Entry> out = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            out.put(kv[i], new Entry(kv[i + 1], i / 2 + 1, null));
        }
        return out;
    }

    private static Catalog cat(String locale, String ns, Map<String, Entry> entries) {
        return new Catalog(locale, ROOT.resolve(locale + "/" + (ns.isEmpty() ? "x" : ns) + ".json"), ns, entries);
    }

    private static Catalogs catalogs(Format format, Catalog... cats) {
        return new Catalogs(format, ROOT, "locales/", "en", "en present", List.of(cats), true, null, 0);
    }

    private static final Usage USES_ALL = new Usage(Set.of(), List.of(), false);

    @Test
    @DisplayName("M1: plural siblings compare on the base key — a Polish _few/_many beside an English _one/_other is not missing")
    void pluralSiblingsAreNotMissing() {
        Catalogs c = catalogs(Format.I18NEXT_NESTED,
                cat("en", "", entries("items_one", "{{count}} item", "items_other", "{{count}} items", "cart.total", "Total")),
                cat("pl", "", entries("items_few", "{{count}} rzeczy", "items_many", "{{count}} rzeczy", "cart.total", "Suma")));
        Report r = I18nCheck.run(c, USES_ALL);
        assertThat(r.of(Kind.MISSING)).isEmpty();
    }

    @Test
    @DisplayName("a context suffix is stripped only when the bare key exists — first_name is its own key")
    void contextSuffixNeedsItsBase() {
        Catalogs c = catalogs(Format.I18NEXT_NESTED,
                cat("en", "", entries("friend", "Friend", "friend_male", "Boyfriend", "first_name", "First name")),
                cat("de", "", entries("friend", "Freund")));
        Report r = I18nCheck.run(c, USES_ALL);
        assertThat(r.of(Kind.MISSING)).extracting(Finding::key).containsExactly("first_name");
    }

    @Test
    @DisplayName("missing lands on the SOURCE file at the key's line — the key exists there")
    void missingPointsAtTheSourceLine() {
        Catalogs c = catalogs(Format.I18NEXT_NESTED,
                cat("en", "", entries("a", "A", "b", "B")),
                cat("de", "", entries("a", "Ä")));
        Finding f = I18nCheck.run(c, USES_ALL).of(Kind.MISSING).get(0);
        assertThat(f.key()).isEqualTo("b");
        assertThat(f.locale()).isEqualTo("de");
        assertThat(f.file()).isEqualTo(ROOT.resolve("en/x.json"));
        assertThat(f.line()).isEqualTo(2);
        assertThat(f.error()).isFalse();
    }

    @Test
    @DisplayName("Angular: a target in state new is missing on ITS line, a missing target too; a translated one is not")
    void angularStateNewIsMissing() {
        Map<String, Entry> de = new LinkedHashMap<>();
        de.put("greeting", new Entry("Hallo", 5, null));
        de.put("7a1b", new Entry("Sign out", 9, "new"));
        de.put("farewell", new Entry("", 13, "missing target"));
        Catalogs c = catalogs(Format.ANGULAR_XLIFF,
                cat("en", "", entries("greeting", "Hello", "7a1b", "Sign out", "farewell", "Goodbye")),
                new Catalog("de", ROOT.resolve("messages.de.xlf"), "", de));
        Report r = I18nCheck.run(c, null);
        assertThat(r.of(Kind.MISSING)).extracting(Finding::key, Finding::line, Finding::detail)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("7a1b", 9, "new"),
                        org.assertj.core.groups.Tuple.tuple("farewell", 13, "missing target"));
        assertThat(r.of(Kind.MISSING)).allMatch(f -> f.file().equals(ROOT.resolve("messages.de.xlf")));
        assertThat(r.of(Kind.IDENTICAL)).as("a unit in state new is not judged identical").isEmpty();
    }

    @Test
    @DisplayName("M2: an ICU-bearing value is never identical — the categories are the language's own")
    void icuValuesAreNeverIdentical() {
        String icu = "{count, plural, one {# item} other {# items}}";
        Catalogs c = catalogs(Format.NESTED_ICU,
                cat("en", "", entries("items", icu, "hello", "Hello")),
                cat("de", "", entries("items", icu, "hello", "Hello")));
        Report r = I18nCheck.run(c, USES_ALL);
        assertThat(r.of(Kind.IDENTICAL)).extracting(Finding::key).containsExactly("hello");
        assertThat(r.of(Kind.IDENTICAL).get(0).error()).isFalse();
    }

    @Test
    @DisplayName("vue-i18n pipe plurals and letterless values are never identical; in another format the pipe is prose")
    void pipeAndLetterlessAreNotIdentical() {
        Map<String, Entry> same = entries("cars", "car | cars", "ok", "OK", "arrow", "→", "pct", "100%", "word", "Word");
        Report vue = I18nCheck.run(catalogs(Format.VUE_NESTED, cat("en", "", same), cat("fr", "", same)), USES_ALL);
        assertThat(vue.of(Kind.IDENTICAL)).extracting(Finding::key).containsExactly("ok", "word");
        Report next = I18nCheck.run(catalogs(Format.I18NEXT_NESTED, cat("en", "", same), cat("fr", "", same)), USES_ALL);
        assertThat(next.of(Kind.IDENTICAL)).extracting(Finding::key).containsExactly("cars", "ok", "word");
    }

    @Test
    @DisplayName("identical: capped at IDENTICAL_CAP per locale, the total kept for the sentence")
    void identicalIsCapped() {
        Map<String, Entry> many = new LinkedHashMap<>();
        for (int i = 0; i < I18nCheck.IDENTICAL_CAP + 7; i++) {
            many.put("k" + i, new Entry("Same text", i + 1, null));
        }
        Report r = I18nCheck.run(catalogs(Format.I18NEXT_NESTED, cat("en", "", many), cat("de", "", many)), USES_ALL);
        assertThat(r.of(Kind.IDENTICAL)).hasSize(I18nCheck.IDENTICAL_CAP);
        assertThat(r.identicalTotals()).containsEntry("de", I18nCheck.IDENTICAL_CAP + 7);
    }

    @Test
    @DisplayName("a placeholder mismatch IS an error, on the target's line, naming both argument sets")
    void placeholderMismatchIsAnError() {
        Catalogs c = catalogs(Format.I18NEXT_NESTED,
                cat("en", "", entries("hi", "Hi {{name}}", "n", "{{count}} of {{total}}")),
                cat("de", "", entries("hi", "Hallo {{nombre}}", "n", "{{count}} von {{total}}")));
        Report r = I18nCheck.run(c, USES_ALL);
        Finding f = r.of(Kind.MISMATCH).get(0);
        assertThat(r.of(Kind.MISMATCH)).hasSize(1);
        assertThat(f.key()).isEqualTo("hi");
        assertThat(f.error()).isTrue();
        assertThat(f.file()).isEqualTo(ROOT.resolve("de/x.json"));
        assertThat(f.detail()).contains("name").contains("nombre");
        assertThat(r.of(Kind.IDENTICAL)).as("a mismatched value is not also counted identical").isEmpty();
    }

    @Test
    @DisplayName("the source locale is never compared with itself, and a locale with no catalog for a namespace misses every key")
    void sourceSkippedAndNamespacesPerFile() {
        Catalogs c = catalogs(Format.I18NEXT_NESTED,
                cat("en", "common", entries("title", "Title")),
                cat("en", "auth", entries("title", "Sign in", "login", "Log in")),
                cat("de", "common", entries("title", "Title")));
        Report r = I18nCheck.run(c, USES_ALL);
        assertThat(r.of(Kind.MISSING)).extracting(Finding::key).containsExactly("login", "title");
        assertThat(r.of(Kind.MISSING)).allMatch(f -> f.file().equals(ROOT.resolve("en/auth.json")));
        assertThat(r.of(Kind.IDENTICAL)).extracting(Finding::key, Finding::locale)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("title", "de"));
    }

    @Test
    @DisplayName("unused: a referenced key, a key referenced through its plural base and a namespaced reference are used; the rest unused")
    void unusedOverACompleteCensus() {
        Catalogs c = catalogs(Format.I18NEXT_NESTED,
                cat("en", "common", entries("title", "T", "items_one", "1", "items_other", "n", "dead", "D")),
                cat("en", "auth", entries("login", "L", "logout", "O")));
        Usage usage = new Usage(Set.of(new Ref(null, "title"), new Ref(null, "items"), new Ref("auth", "login")),
                List.of(), true);
        Report r = I18nCheck.run(c, usage);
        assertThat(r.unusedSkipped()).isNull();
        assertThat(r.of(Kind.UNUSED)).extracting(Finding::key).containsExactly("dead", "logout");
        assertThat(r.of(Kind.UNUSED).get(0).file()).isEqualTo(ROOT.resolve("en/common.json"));
        assertThat(r.of(Kind.UNUSED).get(0).line()).isEqualTo(4);
        assertThat(r.of(Kind.POSSIBLY_UNUSED)).isEmpty();
    }

    @Test
    @DisplayName("a namespaced reference does not vouch for another namespace's key")
    void namespacedReferenceIsScoped() {
        Catalogs c = catalogs(Format.I18NEXT_NESTED,
                cat("en", "common", entries("title", "T")),
                cat("en", "auth", entries("title", "S")));
        Usage usage = new Usage(Set.of(new Ref("auth", "title")), List.of(), true);
        Report r = I18nCheck.run(c, usage);
        assertThat(r.of(Kind.UNUSED)).extracting(Finding::file)
                .containsExactly(ROOT.resolve("en/common.json"));
    }

    @Test
    @DisplayName("any dynamic lookup downgrades unused to possibly unused and names the prefixes seen")
    void dynamicLookupDowngrades() {
        Catalogs c = catalogs(Format.I18NEXT_NESTED,
                cat("en", "", entries("title", "T", "errors.404", "Not found")));
        Usage usage = new Usage(Set.of(new Ref(null, "title")), List.of("errors.${…}"), true);
        Report r = I18nCheck.run(c, usage);
        assertThat(r.of(Kind.UNUSED)).isEmpty();
        assertThat(r.of(Kind.POSSIBLY_UNUSED)).extracting(Finding::key, Finding::detail)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("errors.404", "errors.${…}"));
        assertThat(r.dynamic()).containsExactly("errors.${…}");
    }

    @Test
    @DisplayName("a partial census never accuses a key: the unused check is skipped and says why")
    void partialCensusSuppressesUnused() {
        Catalogs complete = catalogs(Format.I18NEXT_NESTED, cat("en", "", entries("dead", "D")));
        Report partialWalk = I18nCheck.run(complete, new Usage(Set.of(), List.of(), false));
        assertThat(partialWalk.of(Kind.UNUSED)).isEmpty();
        assertThat(partialWalk.of(Kind.POSSIBLY_UNUSED)).isEmpty();
        assertThat(partialWalk.unusedSkipped()).isEqualTo(I18nCheck.SKIPPED_PARTIAL);
        Catalogs partialCatalogs = new Catalogs(Format.I18NEXT_NESTED, ROOT, "locales/", "en", "en present",
                List.of(cat("en", "", entries("dead", "D"))), false, null, 0);
        Report partialCensus = I18nCheck.run(partialCatalogs, new Usage(Set.of(), List.of(), true));
        assertThat(partialCensus.of(Kind.UNUSED)).isEmpty();
        assertThat(partialCensus.unusedSkipped()).isEqualTo(I18nCheck.SKIPPED_PARTIAL);
    }

    @Test
    @DisplayName("Lingui: the unused check is skipped and says so; keys are source text")
    void linguiSkipsUnused() {
        Catalogs c = catalogs(Format.LINGUI_PO, cat("en", "", entries("Hello", "Hello")));
        Report r = I18nCheck.run(c, null);
        assertThat(r.unusedSkipped()).isEqualTo(I18nCheck.SKIPPED_LINGUI);
        assertThat(r.findings()).isEmpty();
    }

    @Test
    @DisplayName("Angular: unused is the extract-against-target diff — an id a translation carries that messages.xlf lost")
    void angularUnusedIsTheExtractDiff() {
        Catalogs c = catalogs(Format.ANGULAR_XLIFF,
                cat("en", "", entries("greeting", "Hello")),
                new Catalog("de", ROOT.resolve("messages.de.xlf"), "",
                        entries("greeting", "Hallo", "gone", "Alt")));
        Report r = I18nCheck.run(c, null);
        assertThat(r.of(Kind.UNUSED)).extracting(Finding::key, Finding::detail, Finding::line)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("gone", "messages.xlf", 2));
        assertThat(r.unusedSkipped()).isNull();
    }

    @Test
    @DisplayName("the YAML refusal is the whole report: one finding on the config line, nothing else guessed")
    void yamlRefusalIsTheWholeReport() {
        Catalogs c = new Catalogs(Format.VUE_NESTED, ROOT, "src/locales/", "en", "refused: YAML catalogs",
                List.of(), true, ROOT.resolve("package.json"), 4);
        Report r = I18nCheck.run(c, USES_ALL);
        assertThat(r.findings()).hasSize(1);
        Finding f = r.findings().get(0);
        assertThat(f.kind()).isEqualTo(Kind.YAML_REFUSED);
        assertThat(f.file()).isEqualTo(ROOT.resolve("package.json"));
        assertThat(f.line()).isEqualTo(4);
        assertThat(f.error()).isFalse();
    }

    @Test
    @DisplayName("baseKey: CLDR suffixes strip, legacy _plural strips, a nested parent segment never does")
    void baseKeyRules() {
        Set<String> keys = Set.of("a_b.c", "friend");
        assertThat(I18nCheck.baseKey("items_one", keys)).isEqualTo("items");
        assertThat(I18nCheck.baseKey("items_plural", keys)).isEqualTo("items");
        assertThat(I18nCheck.baseKey("cart.items_many", keys)).isEqualTo("cart.items");
        assertThat(I18nCheck.baseKey("friend_male", keys)).isEqualTo("friend");
        assertThat(I18nCheck.baseKey("first_name", keys)).isEqualTo("first_name");
        assertThat(I18nCheck.baseKey("a_b.c", keys)).isEqualTo("a_b.c");
        assertThat(I18nCheck.baseKey("_", keys)).isEqualTo("_");
        assertThat(I18nCheck.baseKey("trailing_", keys)).isEqualTo("trailing_");
    }
}
