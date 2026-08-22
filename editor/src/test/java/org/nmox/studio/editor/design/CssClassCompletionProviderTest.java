package org.nmox.studio.editor.design;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The item assembly behind both class completions (markup and JS),
 * headless through the {@code itemsFor} seam (v2.30.0): local
 * {@code <style>} classes first with "this file" provenance, the
 * project's stylesheets behind them, prefix filtering exact.
 */
class CssClassCompletionProviderTest {

    @Test
    @DisplayName("local style-region classes lead, project classes follow, prefix filters")
    void assembly(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("styles.css"), ".hero { } .other { }");
        String html = "<style>.headline { }</style><p class=\"he\"></p>";

        List<CssClassCompletionItem> items = CssClassCompletionProvider
                .itemsFor("he", html, true, dir.toFile(), 40);
        assertThat(items).extracting(CssClassCompletionItem::getSortText)
                .containsExactly("headline", "hero");

        // the JS shape: no local regions, project only
        assertThat(CssClassCompletionProvider
                .itemsFor("he", "", false, dir.toFile(), 10))
                .extracting(CssClassCompletionItem::getSortText)
                .containsExactly("hero");

        // an unmatched prefix offers nothing
        assertThat(CssClassCompletionProvider
                .itemsFor("zz", html, true, dir.toFile(), 40)).isEmpty();
    }

    @Test
    @DisplayName("a mid-token accept folds the token's tail into the span (the .heroe walk find)")
    void midTokenTail() {
        assertThat(CssClassCompletionItem.tailLength("e')")).isEqualTo(1);
        assertThat(CssClassCompletionItem.tailLength("ro-badge' )")).isEqualTo(8);
        assertThat(CssClassCompletionItem.tailLength("')")).isZero();
        assertThat(CssClassCompletionItem.tailLength("")).isZero();
    }

    @Test
    @DisplayName("insert prefix and priority are the popup's contract")
    void itemContract(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("a.css"), ".btn-primary { }");
        List<CssClassCompletionItem> items = CssClassCompletionProvider
                .itemsFor("btn", "", false, dir.toFile(), 3);
        assertThat(items).hasSize(1);
        CssClassCompletionItem item = items.get(0);
        assertThat(item.getInsertPrefix().toString()).isEqualTo("btn-primary");
        assertThat(item.getSortPriority()).isEqualTo(90);
        assertThat(item.instantSubstitution(null)).isFalse();
        assertThat(item.createDocumentationTask()).isNull();
        assertThat(item.createToolTipTask()).isNull();
    }
}
