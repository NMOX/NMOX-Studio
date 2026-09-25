package org.nmox.studio.editor.share;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.ResourceBundle;
import javax.swing.text.PlainDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Open on GitHub and Copy GitHub Link in the editor (3.2.0).
 */
class GitHubLinkActionTest {

    private static final Path SRC = Path.of("src/main/java/org/nmox/studio/editor/share");

    @Test
    @DisplayName("with nothing selected the link points at the caret's line; a selection links its line range")
    void linkLines() throws Exception {
        PlainDocument doc = new PlainDocument();
        doc.insertString(0, "a\nbb\nccc\ndddd\n", null);
        assertThat(CopyAsMarkdown.linkLines(doc, 3, 3, 3)).as("caret inside line 2").containsExactly(2, 2);
        assertThat(CopyAsMarkdown.linkLines(doc, 0, 0, 0)).as("caret at the very start").containsExactly(1, 1);
        assertThat(CopyAsMarkdown.linkLines(doc, 2, 6, 6)).as("a selection wins over the caret").containsExactly(2, 3);
        assertThat(CopyAsMarkdown.linkLines(doc, 2, 5, 5)).as("a selection ending on a newline").containsExactly(2, 2);
        assertThat(CopyAsMarkdown.linkLines(doc, 0, 0, 999)).as("a caret past the end is clamped")
                .containsExactly(5, 5);
    }

    @Test
    @DisplayName("both gestures are on the editor popup AND the Edit menu, beside the Copy as Markdown family")
    void registrations() throws Exception {
        String open = Files.readString(SRC.resolve("OpenOnGitHubAction.java"));
        String copy = Files.readString(SRC.resolve("CopyGitHubLinkAction.java"));
        assertThat(open).contains("path = \"Editors/Popup\", position = 1962").contains("path = \"Menu/Edit\", position = 1372")
                .contains("GitHubLinks.Gesture.OPEN");
        assertThat(copy).contains("path = \"Editors/Popup\", position = 1963").contains("path = \"Menu/Edit\", position = 1373")
                .contains("GitHubLinks.Gesture.COPY");
    }

    @Test
    @DisplayName("the editor refuses an unsaved buffer BEFORE handing off, and the ladder is the shared one, not a copy")
    void oneLadder() throws Exception {
        String base = Files.readString(SRC.resolve("GitHubLinkAction.java"));
        assertThat(base.indexOf("CopyAsMarkdownWithLinkAction.unsaved(sd)"))
                .isGreaterThan(0).isLessThan(base.indexOf("GitHubLinks.perform("));
        for (String f : new String[] {"GitHubLinkAction.java", "CopyAsMarkdownWithLinkAction.java"}) {
            String src = Files.readString(SRC.resolve(f));
            assertThat(src).as(f + " asks the shared resolver; it does not walk the repository itself")
                    .doesNotContain("GitFacts.").doesNotContain("parseRemote(");
        }
        assertThat(Files.readString(SRC.resolve("CopyAsMarkdownWithLinkAction.java"))).contains("GitHubLinks.resolve(file");
    }

    @Test
    @DisplayName("the editor's menu names equal the tree's row names in every language — one gesture, one name")
    void menuNamesMatchTheTreeRows() {
        for (String lang : new String[] {"", "es", "fr", "de", "ru", "hi", "uk", "pl", "pt", "id", "tl", "vi", "zh", "he", "ar"}) {
            Locale l = lang.isEmpty() ? Locale.ROOT : Locale.forLanguageTag(lang);
            ResourceBundle editor = ResourceBundle.getBundle("org.nmox.studio.editor.share.Bundle", l,
                    ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
            ResourceBundle rack = ResourceBundle.getBundle("org.nmox.studio.rack.service.Bundle", l,
                    ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
            assertThat(editor.getString("CTL_OpenOnGitHub")).as(lang + " Open on GitHub")
                    .isEqualTo(rack.getString("GitHubLinks_open"));
            assertThat(editor.getString("CTL_CopyGitHubLink")).as(lang + " Copy GitHub Link")
                    .isEqualTo(rack.getString("GitHubLinks_copy"));
        }
    }
}
