package org.nmox.studio.tools.vscode;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.tools.vscode.VsCodeExtensions.Entry;
import org.nmox.studio.tools.vscode.VsCodeExtensions.Recommendations;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A repository's {@code .vscode/extensions.json}, read as what it is:
 * a clone's text. Ids fold to lower case, repeats drop, the list has a
 * ceiling that is counted past, and an entry that is not an id is kept
 * as harmless text and never looked up.
 */
class VsCodeExtensionsTest {

    private static List<String> ids(Recommendations r) {
        return r.entries().stream().map(Entry::id).toList();
    }

    private static File project(Path dir, String json) throws Exception {
        Files.createDirectories(dir.resolve(".vscode"));
        Files.writeString(dir.resolve(".vscode/extensions.json"), json);
        return dir.toFile();
    }

    @Test
    @DisplayName("the recommendations array is read in file order")
    void readsRecommendations(@TempDir Path dir) throws Exception {
        Recommendations r = VsCodeExtensions.read(project(dir,
                "{\"recommendations\": [\"dbaeumer.vscode-eslint\", \"esbenp.prettier-vscode\"]}"));
        assertThat(ids(r)).containsExactly("dbaeumer.vscode-eslint", "esbenp.prettier-vscode");
        assertThat(r.entries()).allMatch(Entry::wellFormed);
        assertThat(r.notShown()).isZero();
        assertThat(r.unreadable()).isFalse();
        assertThat(r.total()).isEqualTo(2);
    }

    @Test
    @DisplayName("comments and trailing commas are JSONC, not errors")
    void jsonc() {
        Recommendations r = VsCodeExtensions.parse("""
                // See https://go.microsoft.com/fwlink/?LinkId=827846
                {
                  /* the linters */
                  "recommendations": [
                    "dbaeumer.vscode-eslint", // "not.an-entry"
                    "stylelint.vscode-stylelint",
                  ],
                }
                """);
        assertThat(ids(r)).containsExactly("dbaeumer.vscode-eslint", "stylelint.vscode-stylelint");
    }

    @Test
    @DisplayName("ids are case-insensitive: folded to lower case, and a repeat in another case is one recommendation")
    void caseFoldedAndDeduplicated() {
        Recommendations r = VsCodeExtensions.parse(
                "{\"recommendations\": [\"EditorConfig.EditorConfig\", \"editorconfig.editorconfig\","
                + " \"Vue.volar\", \"  vue.VOLAR  \", \"golang.Go\"]}");
        assertThat(ids(r)).containsExactly("editorconfig.editorconfig", "vue.volar", "golang.go");
        assertThat(r.total()).isEqualTo(3);
    }

    @Test
    @DisplayName("folding does not depend on the reader's language: a Turkish locale still finds the dotted i")
    void foldingIsLocaleFree() {
        java.util.Locale before = java.util.Locale.getDefault();
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
        try {
            assertThat(ids(VsCodeExtensions.parse("{\"recommendations\": [\"ESLINT.INTELLI\"]}")))
                    .containsExactly("eslint.intelli");
        } finally {
            java.util.Locale.setDefault(before);
        }
    }

    @Test
    @DisplayName("at most MAX_IDS are listed, and the rest are counted, not dropped in silence")
    void cappedAndCounted() {
        StringBuilder json = new StringBuilder("{\"recommendations\": [");
        int written = VsCodeExtensions.MAX_IDS + 7;
        for (int i = 0; i < written; i++) {
            json.append(i == 0 ? "" : ",").append("\"pub.ext-").append(i).append('"');
        }
        // repeats past the cap are still repeats, not more recommendations
        json.append(",\"PUB.EXT-0\",\"pub.ext-").append(written - 1).append("\"]}");
        Recommendations r = VsCodeExtensions.parse(json.toString());
        assertThat(r.entries()).hasSize(VsCodeExtensions.MAX_IDS);
        assertThat(r.entries().get(VsCodeExtensions.MAX_IDS - 1).id()).isEqualTo("pub.ext-" + (VsCodeExtensions.MAX_IDS - 1));
        assertThat(r.notShown()).isEqualTo(7);
        assertThat(r.total()).isEqualTo(written);
    }

    @Test
    @DisplayName("an entry that is not shaped like publisher.name is kept as text and marked: it is never looked up")
    void hostileEntriesAreMarked() {
        Recommendations r = VsCodeExtensions.parse("{\"recommendations\": ["
                + "\"<html><img src='http://evil.example/x'>\","
                + "\"no-dot\","
                + "\".leading.dot\","
                + "\"pub.name with spaces\","
                + "\"two\\nlines.ext\","
                + "\"\\u202Egnp.exe\","
                + "\"ok.fine\"]}");
        assertThat(r.entries()).filteredOn(Entry::wellFormed).extracting(Entry::id).containsExactly("ok.fine");
        assertThat(r.entries()).filteredOn(e -> !e.wellFormed()).extracting(Entry::id)
                .as("kept as written, with a line break or a direction override folded away")
                .containsExactly("<html><img src='http://evil.example/x'>", "no-dot", ".leading.dot",
                        "pub.name with spaces", "two lines.ext", "gnp.exe");
    }

    @Test
    @DisplayName("a malformed entry keeps its own spelling, since nothing will match it anyway")
    void malformedKeepsItsCase() {
        assertThat(ids(VsCodeExtensions.parse("{\"recommendations\": [\"Not An Id\"]}"))).containsExactly("Not An Id");
    }

    @Test
    @DisplayName("a very long entry is cut on a code-point boundary, with an ellipsis")
    void longEntriesAreCut() {
        String emoji = "\uD83D\uDE00"; // one code point, two chars
        String shown = VsCodeExtensions.shown(emoji.repeat(VsCodeExtensions.MAX_ID_LENGTH + 50));
        assertThat(shown.codePointCount(0, shown.length())).isEqualTo(VsCodeExtensions.MAX_ID_LENGTH + 1);
        assertThat(shown).endsWith("\u2026");
        assertThat(Character.isHighSurrogate(shown.charAt(shown.length() - 2))).as("no half of a pair before the ellipsis").isFalse();
        String exact = "a".repeat(VsCodeExtensions.MAX_ID_LENGTH);
        assertThat(VsCodeExtensions.shown(exact)).as("exactly at the limit is not cut").isEqualTo(exact);
    }

    @Test
    @DisplayName("entries that are not strings, and blank ones, are not recommendations")
    void nonStringsAreSkipped() {
        Recommendations r = VsCodeExtensions.parse(
                "{\"recommendations\": [42, null, {\"id\": \"a.b\"}, [\"c.d\"], \"\", \"   \", \"real.one\"]}");
        assertThat(ids(r)).containsExactly("real.one");
    }

    @Test
    @DisplayName("unwantedRecommendations are not recommendations")
    void unwantedAreNotRead() {
        Recommendations r = VsCodeExtensions.parse(
                "{\"recommendations\": [\"a.b\"], \"unwantedRecommendations\": [\"c.d\", \"e.f\"]}");
        assertThat(ids(r)).containsExactly("a.b");
        assertThat(r.total()).isEqualTo(1);
    }

    @Test
    @DisplayName("no file, no folder, or no recommendations key: nothing recommended, and nothing wrong")
    void absentIsNone(@TempDir Path dir) throws Exception {
        assertThat(VsCodeExtensions.read(dir.toFile())).isEqualTo(Recommendations.NONE);
        assertThat(VsCodeExtensions.read(null)).isEqualTo(Recommendations.NONE);
        assertThat(VsCodeExtensions.read(project(dir, "{}"))).isEqualTo(Recommendations.NONE);
        assertThat(VsCodeExtensions.parse("{\"recommendations\": \"dbaeumer.vscode-eslint\"}").entries())
                .as("a string where the array belongs is not a list").isEmpty();
    }

    @Test
    @DisplayName("a file that does not parse is unreadable, never an empty list that looks deliberate")
    void malformedIsUnreadable(@TempDir Path dir) throws Exception {
        assertThat(VsCodeExtensions.parse("{\"recommendations\": [").unreadable()).isTrue();
        assertThat(VsCodeExtensions.parse("[\"a.b\"]").unreadable()).as("an array at the top is not this file's shape").isTrue();
        assertThat(VsCodeExtensions.parse("").unreadable()).isTrue();
        assertThat(VsCodeExtensions.read(project(dir, "not json")).unreadable()).isTrue();
    }

    @Test
    @DisplayName("ten thousand nested brackets are a refusal, not a stack overflow")
    void deepNestingIsRefused() {
        String deep = "{\"recommendations\": " + "[".repeat(10_000) + "]".repeat(10_000) + "}";
        Recommendations r = VsCodeExtensions.parse(deep);
        assertThat(r.unreadable() || r.entries().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("a file over the ceiling is not read at all")
    void oversizeIsNotRead(@TempDir Path dir) throws Exception {
        StringBuilder big = new StringBuilder("{\"recommendations\": [\"a.b\"], \"pad\": \"");
        big.append("x".repeat((int) VsCodeExtensions.MAX_BYTES)).append("\"}");
        Recommendations r = VsCodeExtensions.read(project(dir, big.toString()));
        assertThat(r.unreadable()).isTrue();
        assertThat(r.entries()).isEmpty();
    }

    @Test
    @DisplayName("a directory named extensions.json is not a file to read")
    void aDirectoryIsNotTheFile(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve(".vscode/extensions.json"));
        assertThat(VsCodeExtensions.read(dir.toFile())).isEqualTo(Recommendations.NONE);
    }
}
