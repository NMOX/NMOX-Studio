package org.nmox.studio.editor.i18n;

import java.nio.file.Path;
import java.util.List;

import javax.swing.text.Document;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.netbeans.spi.editor.completion.CompletionItem;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ⌃Space translation-key completion (v2.177.0) proven where the user
 * presses it — driven through the platform's OWN query path against a
 * real project on disk, a real document backed by a real file, and a
 * real {@code CompletionResultSet}, rather than through the pure core
 * the provider happens to call. Ledger 103 had recorded this surface as
 * needing "a real completion popup… a screen walk with full control, or
 * a person"; it does not. A popup is a RENDERING of an answer the
 * provider has already given, and the answer is the thing under test
 * ({@link I18nCompletionQuery} carries the harness and its evidence).
 *
 * <p>The query type matters as much as the items. A second ⌃Space while
 * the popup shows re-queries every provider as {@code COMPLETION_ALL}
 * (9), and the v2.58.1 walk found all eleven NMOX providers dropping
 * every item on that press. This provider was written after that fix
 * and carries the bit mask, so 9 is asserted here to answer EXACTLY
 * like 1 — the same items, the same provenance, the same spans — while
 * DOCUMENTATION (2) and TOOLTIP (4) stay refused.
 *
 * <p><b>The ceiling, stated:</b> this proves what the provider ANSWERS.
 * It does not prove the popup PAINTS those answers, and nothing in this
 * repo can — the v1.324.0 rule, where ⌘I's reach was pinned at unit
 * level rather than fabricated. The paint is one call,
 * {@code CompletionUtilities.renderHtml(null, name, provenance, …)},
 * taking exactly the two fields asserted below.
 */
class I18nKeyCompletionLiveTest {

    /** An i18next project whose source catalog holds two keys sharing a prefix. */
    private static void i18next(Path root) throws Exception {
        I18nCompletionQuery.write(root, "package.json", "{\"dependencies\":{\"i18next\":\"^23\"}}");
        I18nCompletionQuery.write(root, "public/locales/en/common.json",
                "{\n  \"title\": \"Home\",\n  \"tagline\": \"Shop well\",\n  \"other\": \"x\"\n}");
        I18nCompletionQuery.write(root, "public/locales/de/common.json",
                "{\n  \"title\": \"Start\"\n}");
    }

    /** An inlang/Paraglide project — the format behind the {@code m.} shape. */
    private static void paraglide(Path root) throws Exception {
        I18nCompletionQuery.write(root, "package.json", "{\"dependencies\":{}}");
        I18nCompletionQuery.write(root, "project.inlang/settings.json",
                "{\"sourceLanguageTag\":\"en\",\"languageTags\":[\"en\",\"fr\"],\"modules\":[],"
                + "\"plugin.inlang.messageFormat\":{\"pathPattern\":\"./messages/{languageTag}.json\"}}");
        I18nCompletionQuery.write(root, "messages/en.json",
                "{\n  \"$schema\": \"https://inlang.com/schema/inlang-message-format\",\n"
                + "  \"title\": \"Home\",\n  \"tagline\": \"Shop well\"\n}");
        I18nCompletionQuery.write(root, "messages/fr.json",
                "{\"$schema\":\"https://inlang.com/schema/inlang-message-format\",\"title\":\"Accueil\"}");
    }

    /** The document for {@code source}, written to {@code rel} under {@code root}. */
    private static Document docOn(Path root, String rel, String source) throws Exception {
        return I18nCompletionQuery.documentOn(
                I18nCompletionQuery.write(root, rel, source), source);
    }

    /** What a real ⌃Space at the caret after {@code partial} offers, as shown text. */
    private static List<String> offered(Document doc, int caret) throws Exception {
        List<? extends CompletionItem> items = I18nCompletionQuery.items(1, doc, caret);
        assertThat(items).as("COMPLETION (1) must produce a task").isNotNull();
        return items.stream().map(i -> {
            try {
                return I18nCompletionQuery.shown(i);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).toList();
    }

    @Test
    @DisplayName("a real ⌃Space inside t(' offers the project's own keys with catalog·value provenance")
    void realQueryOffersKeysWithProvenance(@TempDir Path root) throws Exception {
        i18next(root);
        String js = "const s = t('t');";
        Document doc = docOn(root, "src/app.js", js);
        int caret = js.indexOf("'t'") + 2;

        // the catalog's keys are a sorted map, so the popup's order is the catalog's
        assertThat(offered(doc, caret)).containsExactly(
                "tagline · en/common.json · &quot;Shop well&quot;",
                "title · en/common.json · &quot;Home&quot;");
        List<? extends CompletionItem> items = I18nCompletionQuery.items(1, doc, caret);
        // the accept span is the typed prefix alone — the popup rewrites "t", never the quote
        assertThat(I18nCompletionQuery.span(items.get(1))).isEqualTo(caret - 1 + "+1");
        assertThat(items.get(1).getInsertPrefix()).hasToString("title");
        // the SOURCE catalog answers; the German sibling never appears
        assertThat(offered(doc, caret)).noneMatch(s -> s.contains("de/common.json"));
    }

    @Test
    @DisplayName("COMPLETION_ALL (9) answers exactly like COMPLETION (1); DOCUMENTATION and TOOLTIP are refused")
    void completionAllIsNotDropped(@TempDir Path root) throws Exception {
        i18next(root);
        String js = "const s = t('ti');";
        Document doc = docOn(root, "src/app.js", js);
        int caret = js.indexOf("ti") + 2;

        List<? extends CompletionItem> one = I18nCompletionQuery.items(1, doc, caret);
        List<? extends CompletionItem> all = I18nCompletionQuery.items(9, doc, caret);
        assertThat(one).as("the first ⌃Space offers the key").hasSize(1);
        assertThat(all).as("a second ⌃Space must still produce a task, not be refused").isNotNull();
        assertThat(all).as("a second ⌃Space must still offer the key").hasSize(1);
        assertThat(I18nCompletionQuery.shown(all.get(0)))
                .as("a second ⌃Space (COMPLETION_ALL) must offer the same item, not wipe it")
                .isEqualTo(I18nCompletionQuery.shown(one.get(0)));
        assertThat(I18nCompletionQuery.span(all.get(0)))
                .isEqualTo(I18nCompletionQuery.span(one.get(0)));
        assertThat(I18nCompletionQuery.items(2, doc, caret)).as("DOCUMENTATION (2)").isNull();
        assertThat(I18nCompletionQuery.items(4, doc, caret)).as("TOOLTIP (4)").isNull();
    }

    @Test
    @DisplayName("all six lookup shapes complete: t(', $t(', data-i18n=, i18nKey=, <FormattedMessage id=, and m.")
    void sixShapes(@TempDir Path root, @TempDir Path inlangRoot) throws Exception {
        i18next(root);
        record Shape(String rel, String source, String partial) {
        }
        List<Shape> shapes = List.of(
                new Shape("src/app.js", "const s = t('ti');", "ti"),
                new Shape("src/Page.vue", "<p>{{ $t('ti') }}</p>", "ti"),
                new Shape("src/index.html", "<span data-i18n=\"ti\"></span>", "ti"),
                new Shape("src/Trans.jsx", "<Trans i18nKey=\"ti\" />", "ti"),
                new Shape("src/Msg.jsx", "<FormattedMessage id=\"ti\" />", "ti"));
        for (Shape shape : shapes) {
            Document doc = docOn(root, shape.rel(), shape.source());
            int caret = shape.source().indexOf(shape.partial()) + shape.partial().length();
            assertThat(offered(doc, caret))
                    .as("%s must complete the key", shape.rel())
                    .containsExactly("title · en/common.json · &quot;Home&quot;");
        }

        // Paraglide's m.<identifier> — no quote at all, and its own catalog shape
        paraglide(inlangRoot);
        String svelte = "<h1>{m.ti}</h1>";
        Document doc = docOn(inlangRoot, "src/routes/Page.svelte", svelte);
        assertThat(offered(doc, svelte.indexOf("m.ti") + 4))
                .containsExactly("title · en.json · &quot;Home&quot;");
    }

    @Test
    @DisplayName("a caret that is not in a key position offers nothing at all")
    void notAKeyPosition(@TempDir Path root) throws Exception {
        i18next(root);
        String js = "fetch('/api/title'); log('title'); const title = 1;";
        Document doc = docOn(root, "src/app.js", js);
        assertThat(offered(doc, js.indexOf("/api/title") + 7)).as("a URL is not a key").isEmpty();
        assertThat(offered(doc, js.indexOf("log('title") + 8)).as("log() is not a lookup").isEmpty();
        assertThat(offered(doc, js.indexOf("const title") + 9)).as("an identifier is not a key").isEmpty();
    }

    @Test
    @DisplayName("a project with no catalogs, and a key nothing matches, both offer nothing rather than guessing")
    void nothingToOffer(@TempDir Path bare, @TempDir Path root) throws Exception {
        I18nCompletionQuery.write(bare, "package.json", "{\"dependencies\":{}}");
        String js = "const s = t('ti');";
        assertThat(offered(docOn(bare, "src/app.js", js), js.indexOf("ti") + 2)).isEmpty();

        i18next(root);
        String miss = "const s = t('zz');";
        assertThat(offered(docOn(root, "src/app.js", miss), miss.indexOf("zz") + 2)).isEmpty();
    }
}
