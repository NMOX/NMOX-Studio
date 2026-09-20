package org.nmox.studio.editor.i18n;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.text.Document;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.netbeans.lib.editor.hyperlink.spi.HyperlinkType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ⌘-click a translation key and land on its line in the source catalog
 * (v2.177.0), proven through the REAL
 * {@link org.netbeans.lib.editor.hyperlink.spi.HyperlinkProviderExt} —
 * {@code getHyperlinkSpan} over a real document backed by a real file,
 * then {@code performClickAction} run to the file and line it resolves.
 * Ledger 103 had recorded this surface as needing "a real
 * modifier-click"; it needs the modifier only to REACH the provider,
 * and the provider is what decides where the click lands. This is the
 * v2.27.0 recipe one surface over, where the CSS class jump's hover
 * consultation was pinned live while the modifier-click leg itself
 * stayed out of reach.
 *
 * <p>The one seam is {@link I18nKeyHyperlink#open}: the platform's own
 * open needs a live editor and a {@code LineCookie}, and "something was
 * opened" is not the claim a jump makes — the claim is WHICH file and
 * WHICH line, so the sink is captured and both are asserted.
 *
 * <p><b>The ceiling, stated:</b> this proves the provider claims the
 * right span and resolves the right target. It does not prove that a
 * held ⌘ paints the span as a link, nor that the editor scrolls — the
 * platform's {@code HyperlinkOperation} owns both, and no instrument
 * here can deliver a modifier-click (the v1.291.0 measurement: three
 * MOUSE_MOVED events for a whole session).
 */
class I18nKeyJumpLiveTest {

    /** The real provider with its jump sink captured instead of opened. */
    private static final class Captured {

        final I18nKeyHyperlink link = new I18nKeyHyperlink();
        final AtomicReference<String> landed = new AtomicReference<>();
        final CountDownLatch opened = new CountDownLatch(1);

        Captured() {
            link.open = (File file, int line) -> {
                landed.set(file.getName() + ":" + line);
                opened.countDown();
            };
        }
    }

    private static void i18next(Path root) throws Exception {
        I18nCompletionQuery.write(root, "package.json", "{\"dependencies\":{\"i18next\":\"^23\"}}");
        I18nCompletionQuery.write(root, "public/locales/en/common.json",
                "{\n  \"title\": \"Home\",\n  \"tagline\": \"Shop well\"\n}");
        I18nCompletionQuery.write(root, "public/locales/en/auth.json",
                "{\n  \"title\": \"Sign in\"\n}");
        I18nCompletionQuery.write(root, "public/locales/de/common.json", "{\"title\":\"Start\"}");
    }

    private static void paraglide(Path root) throws Exception {
        I18nCompletionQuery.write(root, "package.json", "{\"dependencies\":{}}");
        I18nCompletionQuery.write(root, "project.inlang/settings.json",
                "{\"sourceLanguageTag\":\"en\",\"languageTags\":[\"en\"],\"modules\":[],"
                + "\"plugin.inlang.messageFormat\":{\"pathPattern\":\"./messages/{languageTag}.json\"}}");
        I18nCompletionQuery.write(root, "messages/en.json",
                "{\n  \"$schema\": \"https://inlang.com/schema/inlang-message-format\",\n"
                + "  \"title\": \"Home\"\n}");
    }

    /**
     * A real ⌘-click on {@code key} inside {@code source}: the span must
     * be exactly the key, and the resolved target is returned as
     * {@code file:line}.
     */
    private static String clicked(Path root, String rel, String source, String key) throws Exception {
        Document doc = I18nCompletionQuery.documentOn(
                I18nCompletionQuery.write(root, rel, source), source);
        int at = source.indexOf(key);
        int caret = at + 1;
        Captured c = new Captured();
        assertThat(c.link.isHyperlinkPoint(doc, caret, HyperlinkType.GO_TO_DECLARATION))
                .as("%s: the key must be a hyperlink point", rel).isTrue();
        assertThat(c.link.getHyperlinkSpan(doc, caret, HyperlinkType.GO_TO_DECLARATION))
                .as("%s: the span is exactly the key", rel)
                .containsExactly(at, at + key.length());
        c.link.performClickAction(doc, caret, HyperlinkType.GO_TO_DECLARATION);
        assertThat(c.opened.await(10, TimeUnit.SECONDS))
                .as("%s: the click must resolve a target", rel).isTrue();
        return c.landed.get();
    }

    @Test
    @DisplayName("all six lookup shapes jump to the key's own line in the source catalog")
    void sixShapes(@TempDir Path root, @TempDir Path inlangRoot) throws Exception {
        i18next(root);
        record Shape(String rel, String source) {
        }
        List<Shape> shapes = List.of(
                new Shape("src/app.js", "const s = t('tagline');"),
                new Shape("src/Page.vue", "<p>{{ $t('tagline') }}</p>"),
                new Shape("src/index.html", "<span data-i18n=\"tagline\"></span>"),
                new Shape("src/Trans.jsx", "<Trans i18nKey=\"tagline\" />"),
                new Shape("src/Msg.jsx", "<FormattedMessage id=\"tagline\" />"));
        for (Shape shape : shapes) {
            assertThat(clicked(root, shape.rel(), shape.source(), "tagline"))
                    .as("%s lands on the key's own line, not the file's first", shape.rel())
                    .isEqualTo("common.json:3");
        }

        // Paraglide's m.<identifier>: the key carries a sigil the span excludes
        paraglide(inlangRoot);
        String svelte = "<h1>{m.title}</h1>";
        Document doc = I18nCompletionQuery.documentOn(
                I18nCompletionQuery.write(inlangRoot, "src/Page.svelte", svelte), svelte);
        int at = svelte.indexOf("title");
        Captured c = new Captured();
        assertThat(c.link.getHyperlinkSpan(doc, at + 1, HyperlinkType.GO_TO_DECLARATION))
                .as("the span starts after m., never over the sigil")
                .containsExactly(at, at + "title".length());
        c.link.performClickAction(doc, at + 1, HyperlinkType.GO_TO_DECLARATION);
        assertThat(c.opened.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(c.landed.get()).isEqualTo("en.json:3");
    }

    @Test
    @DisplayName("a namespaced key lands in its own namespace's file, not the first one declaring the name")
    void namespaceDecidesTheFile(@TempDir Path root) throws Exception {
        i18next(root);
        assertThat(clicked(root, "src/a.js", "t('auth:title')", "auth:title")).isEqualTo("auth.json:2");
        assertThat(clicked(root, "src/b.js", "t('common:title')", "common:title")).isEqualTo("common.json:2");
        // a BARE key that two namespaces both declare lands in the first one —
        // the rule the javadoc states, and the reason the namespace matters
        assertThat(clicked(root, "src/c.js", "t('title')", "title")).isEqualTo("auth.json:2");
        // the SOURCE catalog is the target — never the German sibling that also declares it
        assertThat(clicked(root, "src/d.js", "t('tagline')", "tagline")).isEqualTo("common.json:3");
    }

    @Test
    @DisplayName("prose and a URL are not hyperlink points; an undeclared key opens nothing")
    void refusals(@TempDir Path root) throws Exception {
        i18next(root);
        String js = "fetch('/api/title'); const title = 1; t('nope');";
        Document doc = I18nCompletionQuery.documentOn(
                I18nCompletionQuery.write(root, "src/app.js", js), js);
        Captured c = new Captured();
        assertThat(c.link.isHyperlinkPoint(doc, js.indexOf("/api/title") + 7, HyperlinkType.GO_TO_DECLARATION))
                .as("a URL is not a key").isFalse();
        assertThat(c.link.isHyperlinkPoint(doc, js.indexOf("const title") + 9, HyperlinkType.GO_TO_DECLARATION))
                .as("an identifier is not a key").isFalse();
        // an undeclared key IS a hyperlink point (the reader is asking a fair
        // question) and the answer is a refusal on the status line, not a jump
        int nope = js.indexOf("nope") + 1;
        assertThat(c.link.isHyperlinkPoint(doc, nope, HyperlinkType.GO_TO_DECLARATION)).isTrue();
        c.link.click(js, c.link.getHyperlinkSpan(doc, nope, HyperlinkType.GO_TO_DECLARATION), root.toFile());
        assertThat(c.landed.get()).as("nothing is opened for a key nothing declares").isNull();
    }
}
