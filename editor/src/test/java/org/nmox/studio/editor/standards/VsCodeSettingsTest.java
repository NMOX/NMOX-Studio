package org.nmox.studio.editor.standards;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A repository's .vscode/settings.json says how its files are written;
 * 3.1.0 reads the four settings that mean something to the editor and
 * hands them on as EditorConfig properties, under the project's own
 * .editorconfig.
 */
class VsCodeSettingsTest {

    @TempDir
    Path tmp;

    private static Map<String, String> of(String json, String lang) {
        return VsCodeSettings.translate(new JSONObject(json), lang);
    }

    @Test
    @DisplayName("tabSize and insertSpaces become indentation")
    void tabSizeAndSpaces() {
        assertThat(of("{\"editor.tabSize\": 4, \"editor.insertSpaces\": true}", null))
                .containsExactlyInAnyOrderEntriesOf(Map.of("tab_width", "4", "indent_size", "4", "indent_style", "space"));
        assertThat(of("{\"editor.insertSpaces\": false}", null)).containsExactly(Map.entry("indent_style", "tab"));
    }

    @Test
    @DisplayName("indentSize names the indentation when it is a number; tabSize stays the tab width")
    void indentSize() {
        assertThat(of("{\"editor.tabSize\": 8, \"editor.indentSize\": 2}", null))
                .containsEntry("tab_width", "8").containsEntry("indent_size", "2");
        assertThat(of("{\"editor.tabSize\": 3, \"editor.indentSize\": \"tabSize\"}", null))
                .containsEntry("indent_size", "3");
    }

    @Test
    @DisplayName("true trims and ends with a newline; false says nothing - VS Code's false is 'leave it', EditorConfig's would strip")
    void onlyTrueIsTranslated() {
        assertThat(of("{\"files.trimTrailingWhitespace\": true, \"files.insertFinalNewline\": true}", null))
                .containsEntry("trim_trailing_whitespace", "true").containsEntry("insert_final_newline", "true");
        assertThat(of("{\"files.trimTrailingWhitespace\": false, \"files.insertFinalNewline\": false}", null)).isEmpty();
    }

    @Test
    @DisplayName("a language block overrides the top level for its language, and only for it")
    void languageBlocks() {
        String json = "{\"editor.tabSize\": 4, \"[javascript][typescript]\": {\"editor.tabSize\": 2},"
                + " \"[python]\": {\"editor.insertSpaces\": true}}";
        assertThat(of(json, "typescript")).containsEntry("indent_size", "2").doesNotContainKey("indent_style");
        assertThat(of(json, "javascript")).containsEntry("indent_size", "2");
        assertThat(of(json, "python")).containsEntry("indent_size", "4").containsEntry("indent_style", "space");
        assertThat(of(json, null)).containsEntry("indent_size", "4");
    }

    @Test
    @DisplayName("a width VS Code would not honour says nothing")
    void junkWidths() {
        for (String v : new String[] {"\"auto\"", "0", "100", "2.5", "-4", "true"}) {
            assertThat(of("{\"editor.tabSize\": " + v + "}", null)).as(v).isEmpty();
        }
    }

    @Test
    @DisplayName("the nearest settings.json above the file, and none above the repository's root")
    void findsTheNearestInsideTheRepository() throws Exception {
        Path repo = Files.createDirectories(tmp.resolve("repo"));
        Files.createDirectories(repo.resolve(".git"));
        Path file = Files.createDirectories(repo.resolve("src/deep")).resolve("app.js");
        Files.writeString(file, "x");
        assertThat(VsCodeSettings.propertiesFor(file.toFile())).isEmpty();

        Files.createDirectories(tmp.resolve(".vscode"));
        Files.writeString(tmp.resolve(".vscode/settings.json"), "{\"editor.tabSize\": 8}");
        assertThat(VsCodeSettings.propertiesFor(file.toFile())).as("above .git is somebody else's").isEmpty();

        Files.createDirectories(repo.resolve(".vscode"));
        Files.writeString(repo.resolve(".vscode/settings.json"),
                "{\n  // the house style\n  \"editor.tabSize\": 2,\n  \"editor.insertSpaces\": true,\n}\n");
        assertThat(VsCodeSettings.propertiesFor(file.toFile())).as("JSONC: comments and a trailing comma")
                .containsEntry("indent_size", "2").containsEntry("indent_style", "space");
    }

    @Test
    @DisplayName("outside any repository nothing is read: a folder above the file is not the project's")
    void outsideARepositoryNothingIsRead() throws Exception {
        Files.createDirectories(tmp.resolve(".vscode"));
        Files.writeString(tmp.resolve(".vscode/settings.json"), "{\"editor.tabSize\": 8}");
        Path file = Files.createDirectories(tmp.resolve("loose/dir")).resolve("a.js");
        Files.writeString(file, "x");
        assertThat(VsCodeSettings.propertiesFor(file.toFile())).isEmpty();
    }

    @Test
    @DisplayName("a settings.json that does not parse says nothing")
    void unparsableSaysNothing() throws Exception {
        Files.createDirectories(tmp.resolve(".git"));
        Files.createDirectories(tmp.resolve(".vscode"));
        Files.writeString(tmp.resolve(".vscode/settings.json"), "{ \"editor.tabSize\": ");
        Path file = Files.writeString(tmp.resolve("a.js"), "x");
        assertThat(VsCodeSettings.propertiesFor(file.toFile())).isEmpty();
    }

    @Test
    @DisplayName(".editorconfig wins wherever both speak; settings.json fills what it leaves outside indentation")
    void editorconfigWins() throws Exception {
        Files.createDirectories(tmp.resolve(".git"));
        Files.createDirectories(tmp.resolve(".vscode"));
        Files.writeString(tmp.resolve(".vscode/settings.json"),
                "{\"editor.tabSize\": 8, \"editor.insertSpaces\": false, \"files.insertFinalNewline\": true}");
        Files.writeString(tmp.resolve(".editorconfig"), "root = true\n[*]\nindent_size = 2\n");
        File file = Files.writeString(tmp.resolve("a.js"), "x").toFile();
        Map<String, String> props = ProjectFormatting.propertiesFor(file);
        assertThat(props).containsEntry("indent_size", "2")
                .as("the .editorconfig names the indentation, so it decides all of it")
                .doesNotContainKey("indent_style").doesNotContainKey("tab_width")
                .as("what it does not name still comes through").containsEntry("insert_final_newline", "true");
    }

    @Test
    @DisplayName("an .editorconfig that speaks about indentation takes all of it: no settings.json tab width under it")
    void indentationIsOneSourceNotAMix() throws Exception {
        Files.createDirectories(tmp.resolve(".git"));
        Files.createDirectories(tmp.resolve(".vscode"));
        Files.writeString(tmp.resolve(".vscode/settings.json"),
                "{\"editor.tabSize\": 2, \"files.trimTrailingWhitespace\": true}");
        Files.writeString(tmp.resolve(".editorconfig"), "root = true\n[*]\nindent_style = tab\nindent_size = 4\n");
        File file = Files.writeString(tmp.resolve("a.go"), "x").toFile();
        Map<String, String> props = ProjectFormatting.propertiesFor(file);
        assertThat(props).as("a tab width of 2 under an indent of 4 writes two tabs per level")
                .doesNotContainKey("tab_width").containsEntry("indent_size", "4");
        assertThat(props).as("what the .editorconfig does not speak to still comes through")
                .containsEntry("trim_trailing_whitespace", "true");
        assertThat(EditorConfigIndentation.overrides(props, () -> 8))
                .containsEntry(EditorConfigIndentation.TAB_SIZE, "4");
    }

    @Test
    @DisplayName("JSX and TSX are VS Code's javascriptreact and typescriptreact")
    void reactLanguageIds() {
        assertThat(VsCodeSettings.languageId(new File("App.jsx"))).isEqualTo("javascriptreact");
        assertThat(VsCodeSettings.languageId(new File("App.tsx"))).isEqualTo("typescriptreact");
    }
}
