package org.nmox.studio.editor.standards;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.nmox.studio.editor.standards.EditorConfigIndentation.EXPAND_TABS;
import static org.nmox.studio.editor.standards.EditorConfigIndentation.INDENT_SHIFT_WIDTH;
import static org.nmox.studio.editor.standards.EditorConfigIndentation.SPACES_PER_TAB;
import static org.nmox.studio.editor.standards.EditorConfigIndentation.TAB_SIZE;

/**
 * The EditorConfig indentation properties, mapped onto the editor's own
 * preference keys the way the specification reads them.
 */
class EditorConfigIndentationTest {

    private static Map<String, String> map(Map<String, String> props) {
        return EditorConfigIndentation.overrides(props, () -> 8);
    }

    @Test
    @DisplayName("indent_style = tab writes tabs; indent_style = space writes spaces")
    void style() {
        assertThat(map(Map.of("indent_style", "tab"))).containsExactlyEntriesOf(Map.of(EXPAND_TABS, "false"));
        assertThat(map(Map.of("indent_style", "space"))).containsExactlyEntriesOf(Map.of(EXPAND_TABS, "true"));
    }

    @Test
    @DisplayName("indent_size = N sets the level width, and tab_width defaults to it")
    void numericSize() {
        assertThat(map(Map.of("indent_style", "space", "indent_size", "2")))
                .containsEntry(EXPAND_TABS, "true")
                .containsEntry(INDENT_SHIFT_WIDTH, "2")
                .containsEntry(SPACES_PER_TAB, "2")
                .containsEntry(TAB_SIZE, "2");
    }

    @Test
    @DisplayName("An explicit tab_width wins over the indent_size default")
    void explicitTabWidth() {
        assertThat(map(Map.of("indent_size", "4", "tab_width", "8")))
                .containsEntry(INDENT_SHIFT_WIDTH, "4")
                .containsEntry(TAB_SIZE, "8");
    }

    @Test
    @DisplayName("indent_size = tab takes tab_width when the file names one")
    void sizeIsTabWithWidth() {
        assertThat(map(Map.of("indent_style", "tab", "indent_size", "tab", "tab_width", "3")))
                .containsEntry(EXPAND_TABS, "false")
                .containsEntry(INDENT_SHIFT_WIDTH, "3")
                .containsEntry(TAB_SIZE, "3");
    }

    @Test
    @DisplayName("indent_size = tab with no tab_width defers to the editor's own tab size")
    void sizeIsTabWithoutWidth() {
        Map<String, String> over = EditorConfigIndentation.overrides(
                Map.of("indent_size", "tab"), () -> 6);
        assertThat(over).containsEntry(INDENT_SHIFT_WIDTH, "6")
                .as("the tab size itself is the editor's, so it is not overridden")
                .doesNotContainKey(TAB_SIZE);
    }

    @Test
    @DisplayName("unset, words, zero and absurd widths leave the editor's setting alone")
    void garbageIsIgnored() {
        assertThat(map(Map.of("indent_style", "unset", "indent_size", "unset"))).isEmpty();
        assertThat(map(Map.of("indent_size", "0"))).isEmpty();
        assertThat(map(Map.of("indent_size", "-2"))).isEmpty();
        assertThat(map(Map.of("indent_size", "4000"))).isEmpty();
        assertThat(map(Map.of("indent_size", "four"))).isEmpty();
        assertThat(map(Map.of("tab_width", "99999999999999999999"))).isEmpty();
        assertThat(map(Map.of("indent_style", "tabs"))).as("only the spec's two words").isEmpty();
    }

    @Test
    @DisplayName("Properties that say nothing about indentation produce no overrides")
    void unrelated() {
        assertThat(map(Map.of("trim_trailing_whitespace", "true", "charset", "utf-8"))).isEmpty();
    }

    @Test
    @DisplayName("From the file: last matching section wins, closer file wins, root stops the walk")
    void fromTheFile(@TempDir Path tmp) throws Exception {
        // an outer config the root=true below must hide
        Files.writeString(tmp.resolve(".editorconfig"), """
                [*]
                indent_style = space
                indent_size = 7
                """);
        Path project = Files.createDirectories(tmp.resolve("project"));
        Files.writeString(project.resolve(".editorconfig"), """
                root = true
                [*]
                indent_style = space
                indent_size = 4
                [*.go]
                indent_style = tab
                [Makefile]
                indent_style = tab
                indent_size = tab
                tab_width = 8
                """);
        Path web = Files.createDirectories(project.resolve("web"));
        Files.writeString(web.resolve(".editorconfig"), """
                [*.{js,ts}]
                indent_size = 2
                """);

        assertThat(map(EditorConfig.propertiesFor(project.resolve("main.go").toFile())))
                .as("a later section overrides an earlier one in the same file")
                .containsEntry(EXPAND_TABS, "false")
                .containsEntry(INDENT_SHIFT_WIDTH, "4");
        assertThat(map(EditorConfig.propertiesFor(project.resolve("Makefile").toFile())))
                .containsEntry(EXPAND_TABS, "false")
                .containsEntry(INDENT_SHIFT_WIDTH, "8")
                .containsEntry(TAB_SIZE, "8");
        assertThat(map(EditorConfig.propertiesFor(web.resolve("app.ts").toFile())))
                .as("the closer file overrides the size, the style comes from above")
                .containsEntry(EXPAND_TABS, "true")
                .containsEntry(INDENT_SHIFT_WIDTH, "2");
        assertThat(map(EditorConfig.propertiesFor(project.resolve("README.md").toFile())))
                .as("root = true: the outer indent_size = 7 never applies")
                .containsEntry(INDENT_SHIFT_WIDTH, "4");
    }
}
