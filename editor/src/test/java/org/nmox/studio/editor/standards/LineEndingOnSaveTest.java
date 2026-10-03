package org.nmox.studio.editor.standards;

import java.io.File;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.netbeans.editor.BaseDocument;
import org.netbeans.editor.BaseKit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A project that states its line ending gets it on every file it saves
 * (3.5.13). {@code end_of_line} was the one common {@code .editorconfig}
 * property the save path ignored: a file kept whatever ending it arrived
 * with and a new one took the operating system's, so a Windows
 * contributor to an {@code lf} repository committed CRLF files.
 */
class LineEndingOnSaveTest {

    @TempDir
    Path tmp;

    /** What the platform's own kit writes for a document: the bytes a save puts on disk. */
    private static String written(BaseDocument doc) throws Exception {
        StringWriter out = new StringWriter();
        new BaseKit() {
            @Override
            public String getContentType() {
                return "text/plain";
            }
        }.write(out, doc, 0, doc.getLength());
        return out.toString();
    }

    private static BaseDocument doc(String text, String arrivedWith) throws Exception {
        BaseDocument d = new BaseDocument(false, "text/plain");
        d.insertString(0, text, null);
        if (arrivedWith != null) {
            d.putProperty(EditorConfigOnSave.END_OF_LINE, arrivedWith);
        }
        return d;
    }

    @Test
    @DisplayName("end_of_line names the separator: lf, crlf, cr, in any case; anything else says nothing")
    void theSeparatorNamed() {
        assertThat(EditorConfig.lineSeparator(Map.of("end_of_line", "lf"))).isEqualTo("\n");
        assertThat(EditorConfig.lineSeparator(Map.of("end_of_line", "CRLF"))).isEqualTo("\r\n");
        assertThat(EditorConfig.lineSeparator(Map.of("end_of_line", " cr "))).isEqualTo("\r");
        assertThat(EditorConfig.lineSeparator(Map.of("end_of_line", "unset"))).isNull();
        assertThat(EditorConfig.lineSeparator(Map.of("end_of_line", "native"))).isNull();
        assertThat(EditorConfig.lineSeparator(Map.of())).isNull();
    }

    @Test
    @DisplayName("a file that arrived with CRLF is written with LF once the project says lf, through the platform's own writer")
    void aCrlfFileIsWrittenLf() throws Exception {
        BaseDocument d = doc("one\ntwo\n", "\r\n");
        assertThat(written(d)).as("the control: as it arrived").isEqualTo("one\r\ntwo\r\n");

        EditorConfigOnSave.writeWith(d, "\n");

        assertThat(written(d)).isEqualTo("one\ntwo\n");
        assertThat(d.getText(0, d.getLength())).as("the text itself is not touched").isEqualTo("one\ntwo\n");
    }

    @Test
    @DisplayName("and the other way: an lf file in a crlf project")
    void anLfFileIsWrittenCrlf() throws Exception {
        BaseDocument d = doc("one\ntwo\n", "\n");
        EditorConfigOnSave.writeWith(d, "\r\n");
        assertThat(written(d)).isEqualTo("one\r\ntwo\r\n");
    }

    @Test
    @DisplayName("a separator somebody set for writing alone does not outvote the project")
    void theWriteOnlyPropertyFollows() throws Exception {
        BaseDocument d = doc("one\ntwo\n", "\n");
        d.putProperty(EditorConfigOnSave.WRITE_END_OF_LINE, "\n"); // the platform reads this one first
        EditorConfigOnSave.writeWith(d, "\r\n");
        assertThat(written(d)).isEqualTo("one\r\ntwo\r\n");
    }

    @Test
    @DisplayName("a project that says nothing changes nothing")
    void silenceChangesNothing() throws Exception {
        BaseDocument d = doc("one\ntwo\n", "\r\n");
        EditorConfigOnSave.writeWith(d, null);
        assertThat(written(d)).isEqualTo("one\r\ntwo\r\n");
    }

    @Test
    @DisplayName("files.eol in settings.json is the same statement; \"auto\" is none")
    void filesEol() {
        assertThat(VsCodeSettings.translate(new JSONObject("{\"files.eol\": \"\\n\"}"), null))
                .containsEntry("end_of_line", "lf");
        assertThat(VsCodeSettings.translate(new JSONObject("{\"files.eol\": \"\\r\\n\"}"), null))
                .containsEntry("end_of_line", "crlf");
        assertThat(VsCodeSettings.translate(new JSONObject("{\"files.eol\": \"auto\"}"), null))
                .doesNotContainKey("end_of_line");
        assertThat(VsCodeSettings.translate(new JSONObject("{\"files.eol\": 1}"), null))
                .doesNotContainKey("end_of_line");
    }

    @Test
    @DisplayName("the .editorconfig's end_of_line wins over settings.json's files.eol, as everywhere both speak")
    void editorconfigWins() throws Exception {
        Files.createDirectories(tmp.resolve(".git"));
        Files.createDirectories(tmp.resolve(".vscode"));
        Files.writeString(tmp.resolve(".vscode/settings.json"), "{ \"files.eol\": \"\\r\\n\" }");
        File file = tmp.resolve("a.txt").toFile();
        Files.writeString(file.toPath(), "x\n");
        assertThat(EditorConfig.lineSeparator(ProjectFormatting.propertiesFor(file))).isEqualTo("\r\n");

        Files.writeString(tmp.resolve(".editorconfig"), "root = true\n[*]\nend_of_line = lf\n");
        assertThat(EditorConfig.lineSeparator(ProjectFormatting.propertiesFor(file))).isEqualTo("\n");
    }

    @Test
    @DisplayName("the save task hands the project's ending to the document: wired where the save happens")
    void theSaveTaskIsWired() throws Exception {
        String src = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/editor/standards/EditorConfigOnSave.java"));
        String task = src.substring(src.indexOf("public void performTask()"), src.indexOf("static final String END_OF_LINE"));
        assertThat(task).contains("writeWith(doc, EditorConfig.lineSeparator(props))");
    }
}
