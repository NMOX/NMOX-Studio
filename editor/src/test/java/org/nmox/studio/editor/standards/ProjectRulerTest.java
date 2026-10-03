package org.nmox.studio.editor.standards;

import java.beans.PropertyChangeListener;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;
import javax.swing.SwingUtilities;
import javax.swing.text.Document;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.editor.BaseDocument;
import org.netbeans.lib.editor.util.swing.DocumentUtilities;
import org.netbeans.modules.editor.NbEditorDocument;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A project's ruler - the first of .vscode/settings.json's editor.rulers,
 * or .editorconfig's max_line_length - is where the editor draws its one
 * right-margin line, for that project's files and no other: read through
 * the platform's real editor document, and never written to the user's
 * own preference.
 */
class ProjectRulerTest {

    private static final String WIDTH = "text-limit-width";

    @TempDir
    Path tmp;

    private final List<File> told = new CopyOnWriteArrayList<>();

    @BeforeEach
    void start() {
        EditorConfigCodeStyle.resetForTest();
        EditorConfigCodeStyle.tellOpenDocuments = told::add;
    }

    @AfterEach
    void reset() {
        EditorConfigCodeStyle.resetForTest();
    }

    private static NbEditorDocument documentFor(Path file) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "x\n");
        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(file.toFile()));
        assertThat(fo).as("the test needs a real FileObject for %s", file).isNotNull();
        NbEditorDocument doc = new NbEditorDocument("text/plain");
        doc.putProperty(Document.StreamDescriptionProperty, fo);
        return doc;
    }

    /** A document with no lane of its own: the platform's editor document asks once, in the background, when it is named. */
    private static BaseDocument plainDocumentFor(Path file) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "x\n");
        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(file.toFile()));
        assertThat(fo).isNotNull();
        BaseDocument doc = new BaseDocument(false, "text/plain");
        doc.putProperty(Document.StreamDescriptionProperty, fo);
        return doc;
    }

    /**
     * How often the open documents of {@code file} were told. Counted per
     * file: an editor document from an earlier test may still be asking in
     * the background, about its own file.
     */
    private long toldAbout(File file) {
        return told.stream().filter(file::equals).count();
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

    /** The user's own right margin for plain text, as the Options dialog stores it. */
    private static Preferences userPreferences() {
        return MimeLookup.getLookup(MimePath.parse("text/plain")).lookup(Preferences.class);
    }

    // ---- reading the setting -------------------------------------------------

    private static String ruler(String json, String lang) {
        return VsCodeSettings.translate(new JSONObject(json), lang).get("max_line_length");
    }

    @Test
    @DisplayName("the first ruler is the column; later ones are not read")
    void firstRuler() {
        assertThat(ruler("{\"editor.rulers\": [100]}", null)).isEqualTo("100");
        assertThat(ruler("{\"editor.rulers\": [80, 120]}", null)).isEqualTo("80");
        assertThat(ruler("{\"editor.rulers\": [120, 80]}", null)).as("the first, not the smallest").isEqualTo("120");
        assertThat(ruler("{\"editor.tabSize\": 2}", null)).isNull();
    }

    @Test
    @DisplayName("a ruler written as {column, color} is its column; the colour is not read")
    void rulerWithColour() {
        assertThat(ruler("{\"editor.rulers\": [{\"column\": 88, \"color\": \"#ff0000\"}, 120]}", null)).isEqualTo("88");
        assertThat(ruler("{\"editor.rulers\": [{\"color\": \"#ff0000\"}]}", null)).as("no column: says nothing").isNull();
    }

    @Test
    @DisplayName("an empty list is the project saying no ruler: off")
    void emptyListIsOff() {
        assertThat(ruler("{\"editor.rulers\": []}", null)).isEqualTo("off");
    }

    @Test
    @DisplayName("a first ruler that is not a column this can draw says nothing - the second is not promoted")
    void junkSaysNothing() {
        for (String v : new String[] {"[0, 100]", "[-5]", "[80.5, 100]", "[\"80\"]", "[null, 100]", "[1001]",
            "[[80]]", "80", "\"80\"", "true", "{\"column\": 80}", "[{\"column\": \"80\"}]"}) {
            assertThat(ruler("{\"editor.rulers\": " + v + "}", null)).as(v).isNull();
        }
        assertThat(ruler("{\"editor.rulers\": [1000]}", null)).isEqualTo("1000");
        assertThat(ruler("{\"editor.rulers\": [1]}", null)).isEqualTo("1");
    }

    @Test
    @DisplayName("a language block's rulers override the top level for its language, an empty one included")
    void languageBlock() {
        String json = "{\"editor.rulers\": [100], \"[python]\": {\"editor.rulers\": [79]}, \"[markdown]\": {\"editor.rulers\": []}}";
        assertThat(ruler(json, "python")).isEqualTo("79");
        assertThat(ruler(json, "markdown")).isEqualTo("off");
        assertThat(ruler(json, "typescript")).isEqualTo("100");
        assertThat(ruler(json, null)).isEqualTo("100");
    }

    // ---- the property the editor reads ---------------------------------------

    @Test
    @DisplayName("max_line_length = N is the column, off is no line, anything else leaves the editor's own")
    void marginOverrides() {
        assertThat(EditorConfigMargin.overrides(Map.of("max_line_length", "100"))).containsExactly(Map.entry(WIDTH, "100"));
        assertThat(EditorConfigMargin.overrides(Map.of("max_line_length", "off"))).containsExactly(Map.entry(WIDTH, "0"));
        for (String v : new String[] {"unset", "0", "-1", "80.5", "1001", "12345", "", "eighty"}) {
            assertThat(EditorConfigMargin.overrides(Map.of("max_line_length", v))).as(v).isEmpty();
        }
        assertThat(EditorConfigMargin.overrides(Map.of("indent_size", "2"))).isEmpty();
        assertThat(EditorConfigMargin.overrides(Map.of("max_line_length", "1000"))).containsEntry(WIDTH, "1000");
    }

    // ---- through the platform's own editor document ---------------------------

    @Test
    @DisplayName("the project's ruler is the document's right margin; the user's own preference is not written")
    void perDocumentNeverGlobal() throws Exception {
        Preferences user = userPreferences();
        int before = user.getInt(WIDTH, 80);
        String stored = user.get(WIDTH, null);

        Path repo = repo("ruled", "{ \"editor.rulers\": [100, 120] }");
        NbEditorDocument ruled = documentFor(repo.resolve("src/app.txt"));
        Path other = repo("plain", null);
        NbEditorDocument plain = documentFor(other.resolve("src/app.txt"));

        assertThat(ruled.getProperty(WIDTH)).as("what the editor's view reads for this document").isEqualTo(100);
        assertThat(plain.getProperty(WIDTH)).as("another project's file keeps the user's own margin").isEqualTo(before);
        assertThat(new NbEditorDocument("text/plain").getProperty(WIDTH)).as("so does a document of no file").isEqualTo(before);

        assertThat(user.getInt(WIDTH, 80)).as("the user's preference, read back").isEqualTo(before);
        assertThat(user.get(WIDTH, null)).as("and nothing was stored where nothing was").isEqualTo(stored);
    }

    @Test
    @DisplayName(".editorconfig's max_line_length draws the same line, and wins over the settings.json ruler")
    void editorconfigWins() throws Exception {
        Path repo = repo("both", "{ \"editor.rulers\": [100], \"editor.tabSize\": 2 }");
        Files.writeString(repo.resolve(".editorconfig"), "root = true\n[*.txt]\nmax_line_length = 72\n");
        assertThat(documentFor(repo.resolve("a.txt")).getProperty(WIDTH)).isEqualTo(72);
        assertThat(documentFor(repo.resolve("a.md")).getProperty(WIDTH))
                .as("where the .editorconfig says nothing, the settings.json ruler stands").isEqualTo(100);

        Path only = repo("only", null);
        Files.writeString(only.resolve(".editorconfig"), "root = true\n[*]\nmax_line_length = 120\n");
        assertThat(documentFor(only.resolve("a.txt")).getProperty(WIDTH)).isEqualTo(120);
    }

    @Test
    @DisplayName("no ruler stated (an empty list, or max_line_length = off): the width is zero, which the view does not draw")
    void noRuler() throws Exception {
        Path repo = repo("none", "{ \"editor.rulers\": [] }");
        assertThat(documentFor(repo.resolve("a.txt")).getProperty(WIDTH)).isEqualTo(0);
        Path off = repo("off", null);
        Files.writeString(off.resolve(".editorconfig"), "root = true\n[*]\nmax_line_length = off\n");
        assertThat(documentFor(off.resolve("a.txt")).getProperty(WIDTH)).isEqualTo(0);
    }

    @Test
    @DisplayName("a project that states a ruler and no indentation is still answered, and its indentation left alone")
    void rulerAlone() throws Exception {
        Path repo = repo("alone", "{ \"editor.rulers\": [90] }");
        NbEditorDocument doc = documentFor(repo.resolve("a.txt"));
        Preferences prefs = new EditorConfigCodeStyle().forDocument(doc, "text/plain");
        assertThat(prefs).isNotNull();
        assertThat(prefs.getInt(WIDTH, -1)).isEqualTo(90);
        assertThat(((OverlayPreferences) prefs).overrides()).containsOnlyKeys(WIDTH);
    }

    // ---- telling the view -----------------------------------------------------

    @Test
    @DisplayName("the view is told when the margin first becomes known, and when an edit moves it - not otherwise")
    void toldWhenTheMarginMoves() throws Exception {
        Path repo = repo("told", "{ \"editor.rulers\": [100] }");
        BaseDocument doc = plainDocumentFor(repo.resolve("a.txt"));
        File file = FileUtil.toFile(FileUtil.toFileObject(FileUtil.normalizeFile(repo.resolve("a.txt").toFile())));
        EditorConfigCodeStyle provider = new EditorConfigCodeStyle();
        long[] now = {1_000_000};
        EditorConfigCodeStyle.clock = () -> now[0];

        AtomicReference<Preferences> first = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> first.set(provider.forDocument(doc, "text/plain")));
        assertThat(first.get()).as("on the EDT the first ask is answered from memory: the user's own margin").isNull();
        EditorConfigCodeStyle.awaitIdle();
        assertThat(toldAbout(file)).as("the read landed and the margin is the project's: the open documents are told")
                .isEqualTo(1);
        assertThat(provider.forDocument(doc, "text/plain").getInt(WIDTH, -1)).isEqualTo(100);

        now[0] += EditorConfigCodeStyle.FRESH_MS;
        assertThat(provider.forDocument(doc, "text/plain").getInt(WIDTH, -1)).isEqualTo(100);
        assertThat(toldAbout(file)).as("re-read, unchanged: nobody is told again").isEqualTo(1);

        Files.writeString(repo.resolve(".vscode/settings.json"), "{ \"editor.rulers\": [100], \"editor.tabSize\": 2 }");
        now[0] += EditorConfigCodeStyle.FRESH_MS;
        Preferences indented = provider.forDocument(doc, "text/plain");
        assertThat(indented.getInt(WIDTH, -1)).isEqualTo(100);
        assertThat(indented.getInt("tab-size", -1)).as("the edit was read").isEqualTo(2);
        assertThat(toldAbout(file)).as("only the indentation changed, which every Tab press asks for anyway").isEqualTo(1);

        Files.writeString(repo.resolve(".vscode/settings.json"), "{ \"editor.rulers\": [72], \"editor.tabSize\": 2, \"x\": 1 }");
        now[0] += EditorConfigCodeStyle.FRESH_MS;
        assertThat(provider.forDocument(doc, "text/plain").getInt(WIDTH, -1)).isEqualTo(72);
        assertThat(toldAbout(file)).as("the ruler moved").isEqualTo(2);

        Files.delete(repo.resolve(".vscode/settings.json"));
        now[0] += EditorConfigCodeStyle.FRESH_MS;
        assertThat(provider.forDocument(doc, "text/plain")).as("nothing is stated any more").isNull();
        assertThat(toldAbout(file)).as("the ruler is gone: told once more, to go back to the user's own").isEqualTo(3);
    }

    @Test
    @DisplayName("a file whose project states no margin tells nobody")
    void nothingToTell() throws Exception {
        Path repo = repo("quiet", "{ \"editor.tabSize\": 2 }");
        BaseDocument doc = plainDocumentFor(repo.resolve("a.txt"));
        EditorConfigCodeStyle provider = new EditorConfigCodeStyle();
        SwingUtilities.invokeAndWait(() -> provider.forDocument(doc, "text/plain"));
        EditorConfigCodeStyle.awaitIdle();
        assertThat(provider.forDocument(doc, "text/plain").getInt("tab-size", -1)).isEqualTo(2);
        assertThat(toldAbout(FileUtil.toFile(FileUtil.toFileObject(FileUtil.normalizeFile(repo.resolve("a.txt").toFile())))))
                .isZero();
    }

    @Test
    @DisplayName("telling a document makes it announce the margin again, with the answer now in memory")
    void retellFiresThePropertyTheViewListensTo() throws Exception {
        Path repo = repo("fire", "{ \"editor.rulers\": [100] }");
        NbEditorDocument doc = documentFor(repo.resolve("a.txt"));
        List<String> events = new ArrayList<>();
        PropertyChangeListener view = evt -> {
            if (WIDTH.equals(evt.getPropertyName())) { // the margin's own announcement; wrapping has its test
                events.add(evt.getPropertyName() + "=" + doc.getProperty(evt.getPropertyName()));
            }
        };
        DocumentUtilities.addPropertyChangeListener(doc, view);

        EditorConfigCodeStyle.retell(doc);
        assertThat(events).as("the listener the editor's view registers hears it, and reads the project's column")
                .containsExactly(WIDTH + "=100");
        assertThat(doc.getProperty(WIDTH)).as("nothing was stored over the lazy value").isEqualTo(100);

        Files.writeString(repo.resolve(".vscode/settings.json"), "{ \"editor.rulers\": [72], \"longer\": true }");
        EditorConfigCodeStyle.resetForTest();
        EditorConfigCodeStyle.retell(doc);
        assertThat(events).containsExactly(WIDTH + "=100", WIDTH + "=72");
    }

    @Test
    @DisplayName("an editor gaining focus asks again once the answer has aged - how an edit reaches a file nobody is typing in")
    void focusAsksAgain() throws Exception {
        Path repo = repo("focus", "{ \"editor.rulers\": [100] }");
        BaseDocument doc = plainDocumentFor(repo.resolve("a.txt"));
        EditorConfigCodeStyle provider = new EditorConfigCodeStyle();
        long[] now = {1_000_000};
        EditorConfigCodeStyle.clock = () -> now[0];
        File file = FileUtil.toFile(FileUtil.toFileObject(FileUtil.normalizeFile(repo.resolve("a.txt").toFile())));
        assertThat(provider.forDocument(doc, "text/plain").getInt(WIDTH, -1)).isEqualTo(100);
        assertThat(toldAbout(file)).isEqualTo(1);

        Files.writeString(repo.resolve(".vscode/settings.json"), "{ \"editor.rulers\": [60], \"longer\": true }");
        SwingUtilities.invokeAndWait(() -> EditorConfigCodeStyle.onFocus(doc));
        EditorConfigCodeStyle.awaitIdle();
        assertThat(toldAbout(file)).as("still fresh: focus costs nothing").isEqualTo(1);

        now[0] += EditorConfigCodeStyle.FRESH_MS;
        SwingUtilities.invokeAndWait(() -> EditorConfigCodeStyle.onFocus(doc));
        EditorConfigCodeStyle.awaitIdle();
        assertThat(toldAbout(file)).as("aged: re-read off the EDT, and the moved ruler is told").isEqualTo(2);
        assertThat(provider.forDocument(doc, "text/plain").getInt(WIDTH, -1)).isEqualTo(60);

        SwingUtilities.invokeAndWait(() -> EditorConfigCodeStyle.onFocus(new BaseDocument(false, "text/plain")));
        SwingUtilities.invokeAndWait(() -> EditorConfigCodeStyle.onFocus(null));
        EditorConfigCodeStyle.awaitIdle();
        assertThat(toldAbout(file)).as("a document of no file, and no document, ask about nothing").isEqualTo(2);
    }
}
