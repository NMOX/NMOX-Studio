package org.nmox.studio.editor.standards;

import java.beans.PropertyChangeListener;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;
import javax.swing.text.Document;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.lib.editor.util.swing.DocumentUtilities;
import org.netbeans.modules.editor.NbEditorDocument;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A project's {@code editor.wordWrap} wraps (or does not wrap) that
 * project's files, per document: read through the platform's real editor
 * document, and never written to the user's own line-wrap preference -
 * which every other project's files, and View's own switch, keep.
 */
class ProjectWordWrapTest {

    private static final String WRAP = "text-line-wrap";

    @TempDir
    Path tmp;

    @BeforeEach
    @AfterEach
    void reset() {
        EditorConfigCodeStyle.resetForTest();
    }

    private static NbEditorDocument documentFor(Path file) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "x\n");
        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(file.toFile()));
        assertThat(fo).isNotNull();
        NbEditorDocument doc = new NbEditorDocument(fo.getMIMEType());
        doc.putProperty(Document.StreamDescriptionProperty, fo);
        return doc;
    }

    private Path repo(String name, String settings) throws Exception {
        Path repo = Files.createDirectories(tmp.resolve(name));
        Files.createDirectories(repo.resolve(".git"));
        if (settings != null) {
            Files.createDirectories(repo.resolve(".vscode"));
            Files.writeString(repo.resolve(".vscode/settings.json"), settings);
        }
        return repo;
    }

    private static String wrap(String json, String lang) {
        return VsCodeSettings.translate(new JSONObject(json), lang).get(VsCodeSettings.WORD_WRAP);
    }

    @Test
    @DisplayName("on and off are read; the two values that wrap at a column, and anything else, say nothing")
    void values() {
        assertThat(wrap("{\"editor.wordWrap\": \"on\"}", null)).isEqualTo("on");
        assertThat(wrap("{\"editor.wordWrap\": \"off\"}", null)).isEqualTo("off");
        for (String v : new String[] {"\"wordWrapColumn\"", "\"bounded\"", "true", "false", "1", "\"ON\"", "null", "[\"on\"]"}) {
            assertThat(wrap("{\"editor.wordWrap\": " + v + "}", null)).as(v).isNull();
        }
        assertThat(wrap("{\"editor.tabSize\": 2}", null)).isNull();
    }

    @Test
    @DisplayName("a language block decides for its language: Markdown wraps, the rest of the project does not")
    void languageBlock() {
        String json = "{\"editor.wordWrap\": \"off\", \"[markdown]\": {\"editor.wordWrap\": \"on\"}}";
        assertThat(wrap(json, "markdown")).isEqualTo("on");
        assertThat(wrap(json, "typescript")).isEqualTo("off");
        assertThat(wrap("{\"[markdown]\": {\"editor.wordWrap\": \"on\"}}", "typescript")).isNull();
    }

    @Test
    @DisplayName("on is the editor's wrap-at-a-word, off its no-wrap; nothing else is an override")
    void overrides() {
        assertThat(EditorConfigMargin.overrides(Map.of(VsCodeSettings.WORD_WRAP, "on")))
                .containsExactly(Map.entry(WRAP, "words"));
        assertThat(EditorConfigMargin.overrides(Map.of(VsCodeSettings.WORD_WRAP, "off")))
                .containsExactly(Map.entry(WRAP, "none"));
        assertThat(EditorConfigMargin.overrides(Map.of(VsCodeSettings.WORD_WRAP, "bounded"))).isEmpty();
        assertThat(EditorConfigMargin.overrides(Map.of("editor.wordwrap", "on")))
                .as("an .editorconfig's keys arrive in lower case: it cannot spell this one").isEmpty();
    }

    @Test
    @DisplayName("the project's files wrap; another project's keep the user's own setting, which is not written")
    void perDocumentNeverGlobal() throws Exception {
        Preferences user = MimeLookup.getLookup(MimePath.parse("text/plain")).lookup(Preferences.class);
        String before = user.get(WRAP, "none");
        String stored = user.get(WRAP, null);

        Path wrapping = repo("wrapping", "{ \"editor.wordWrap\": \"on\" }");
        Path plain = repo("plain", null);
        assertThat(documentFor(wrapping.resolve("notes.txt")).getProperty(WRAP))
                .as("what the editor's view reads for this document").isEqualTo("words");
        assertThat(documentFor(plain.resolve("notes.txt")).getProperty(WRAP)).isEqualTo(before);
        assertThat(new NbEditorDocument("text/plain").getProperty(WRAP)).isEqualTo(before);

        assertThat(user.get(WRAP, "none")).as("the user's preference, read back").isEqualTo(before);
        assertThat(user.get(WRAP, null)).as("and nothing was stored where nothing was").isEqualTo(stored);
    }

    @Test
    @DisplayName("off is stated too: a project that says its lines never wrap overrides a user who wraps - for its files only")
    void offIsAStatement() throws Exception {
        Path repo = repo("never", "{ \"editor.wordWrap\": \"off\" }");
        NbEditorDocument doc = documentFor(repo.resolve("a.txt"));
        Preferences prefs = new EditorConfigCodeStyle().forDocument(doc, "text/plain");
        assertThat(prefs).isNotNull();
        assertThat(prefs.get(WRAP, "words")).isEqualTo("none");
        assertThat(((OverlayPreferences) prefs).overrides()).containsOnlyKeys(WRAP);
    }

    @Test
    @DisplayName("an .editorconfig cannot switch wrapping: it has no word for it, and VS Code's key in it is not read")
    void editorconfigHasNoSay() throws Exception {
        Path repo = repo("ec", null);
        Files.writeString(repo.resolve(".editorconfig"), "root = true\n[*]\neditor.wordWrap = on\n");
        assertThat(new EditorConfigCodeStyle().forDocument(documentFor(repo.resolve("a.txt")), "text/plain")).isNull();
    }

    @Test
    @DisplayName("telling a document announces the wrap property too, so the view re-reads it")
    void retellAnnouncesWrap() throws Exception {
        Path repo = repo("fire", "{ \"editor.wordWrap\": \"on\", \"editor.rulers\": [90] }");
        NbEditorDocument doc = documentFor(repo.resolve("a.txt"));
        List<String> events = new ArrayList<>();
        PropertyChangeListener view = evt -> events.add(evt.getPropertyName() + "=" + doc.getProperty(evt.getPropertyName()));
        DocumentUtilities.addPropertyChangeListener(doc, view);
        EditorConfigCodeStyle.retell(doc);
        assertThat(events).containsExactly("text-limit-width=90", WRAP + "=words");
    }

    @Test
    @DisplayName("a change of wrapping alone is a change the view must be told of")
    void wrapIsAViewProperty() {
        assertThat(EditorConfigCodeStyle.VIEW_PROPERTIES).contains(WRAP, "text-limit-width");
        assertThat(EditorConfigCodeStyle.view(Map.of(VsCodeSettings.WORD_WRAP, "on")))
                .isNotEqualTo(EditorConfigCodeStyle.view(Map.of()));
    }
}
