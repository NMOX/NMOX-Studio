package org.nmox.studio.editor.i18n;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.text.PlainDocument;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.netbeans.lib.editor.hyperlink.spi.HyperlinkType;
import org.nmox.studio.editor.i18n.I18nCatalogs.Catalogs;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ⌘-click plumbing on a plain document (the {@code ProjectJumpHyperlinkTest}
 * shape): a span only inside a lookup, the tooltip honest, the click
 * path running to its refusal with no project; and the resolution
 * itself pure over a real catalog record — the namespace's file for a
 * namespaced key, the first declaring source catalog for a bare one,
 * null for a stranger. The provider's provenance line is checked here
 * too: file beside value, the value truncated and markup-safe.
 */
class I18nKeyHyperlinkTest {

    private static PlainDocument doc(String text) throws Exception {
        PlainDocument d = new PlainDocument();
        d.insertString(0, text, null);
        return d;
    }

    @Test
    @DisplayName("span in a lookup, prose refused, tooltip names the catalog, click runs to its refusal without a project")
    void plumbing() throws Exception {
        String js = "const s = t('app.title'); log('app.title');";
        PlainDocument d = doc(js);
        I18nKeyHyperlink link = new I18nKeyHyperlink();
        int in = js.indexOf("app.title") + 2;
        assertThat(link.getSupportedHyperlinkTypes()).containsExactly(HyperlinkType.GO_TO_DECLARATION);
        assertThat(link.isHyperlinkPoint(d, in, HyperlinkType.GO_TO_DECLARATION)).isTrue();
        assertThat(link.getHyperlinkSpan(d, in, HyperlinkType.GO_TO_DECLARATION))
                .containsExactly(js.indexOf("app.title"), js.indexOf("app.title") + 9);
        assertThat(link.getTooltipText(d, in, HyperlinkType.GO_TO_DECLARATION)).contains("catalog");
        int prose = js.lastIndexOf("app.title") + 2;
        assertThat(link.isHyperlinkPoint(d, prose, HyperlinkType.GO_TO_DECLARATION)).isFalse();
        // the click path with no project: runs to its refusal, no throw
        link.performClickAction(d, in, HyperlinkType.GO_TO_DECLARATION);
    }

    private static void write(Path root, String rel, String text) throws Exception {
        Path f = root.resolve(rel);
        Files.createDirectories(f.getParent());
        Files.writeString(f, text);
    }

    @Test
    @DisplayName("find: a namespaced key resolves in its namespace's file, a bare key in the first source catalog, a stranger to null")
    void resolution(@TempDir Path root) throws Exception {
        write(root, "package.json", "{\"dependencies\":{\"i18next\":\"^23\"}}");
        write(root, "public/locales/en/auth.json", "{\n  \"title\": \"Sign in\"\n}");
        write(root, "public/locales/en/common.json", "{\n  \"title\": \"Home\",\n  \"app\": {\n    \"name\": \"Shop\"\n  }\n}");
        write(root, "public/locales/de/common.json", "{ \"title\": \"Start\" }");
        Catalogs c = I18nCatalogs.detect(root);
        I18nKeyHyperlink.Target ns = I18nKeyHyperlink.find(c, "common:title");
        assertThat(ns.catalog().file()).isEqualTo(root.resolve("public/locales/en/common.json"));
        assertThat(ns.entry().line()).isEqualTo(2);
        I18nKeyHyperlink.Target bare = I18nKeyHyperlink.find(c, "app.name");
        assertThat(bare.catalog().namespace()).isEqualTo("common");
        assertThat(bare.entry().line()).isEqualTo(4);
        assertThat(I18nKeyHyperlink.find(c, "auth:title").catalog().namespace()).isEqualTo("auth");
        assertThat(I18nKeyHyperlink.find(c, "nope")).isNull();
        assertThat(I18nKeyHyperlink.find(c, "other:title")).as("a namespace with no file").isNull();
        // the source catalog is the target, never a translation
        assertThat(I18nKeyHyperlink.find(c, "title").catalog().locale()).isEqualTo("en");
    }

    @Test
    @DisplayName("provenance: file (with its locale dir for i18next) beside the value, truncated by code points, markup escaped")
    void provenance(@TempDir Path root) throws Exception {
        write(root, "package.json", "{\"dependencies\":{\"i18next\":\"^23\"}}");
        write(root, "public/locales/en/common.json",
                "{\"short\":\"Hello!\",\"long\":\"This value is far longer than the popup column\",\"markup\":\"<b>bold</b> & co\"}");
        Catalogs c = I18nCatalogs.detect(root);
        I18nCatalogs.Catalog en = c.source().get(0);
        assertThat(I18nKeyCompletionProvider.provenance(en, en.entries().get("short"), true))
                .isEqualTo("en/common.json · &quot;Hello!&quot;");
        String longOne = I18nKeyCompletionProvider.provenance(en, en.entries().get("long"), false);
        assertThat(longOne).startsWith("common.json · &quot;This value is far lon").endsWith("…&quot;");
        assertThat(I18nKeyCompletionProvider.provenance(en, en.entries().get("markup"), false))
                .doesNotContain("<b>").contains("&lt;b&gt;bold&lt;/b&gt; &amp; co");
    }
}
