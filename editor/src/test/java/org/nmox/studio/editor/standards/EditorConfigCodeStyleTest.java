package org.nmox.studio.editor.standards;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;
import javax.swing.SwingUtilities;
import javax.swing.text.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.netbeans.editor.BaseDocument;
import org.netbeans.modules.editor.indent.api.IndentUtils;
import org.netbeans.modules.editor.indent.spi.CodeStylePreferences;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The provider that carries {@code .editorconfig} indentation into the
 * editor: registered ahead of the platform's own, silent for files the
 * config says nothing about, and - through the platform's real
 * {@code CodeStylePreferences} and {@code IndentUtils} - changing what
 * a document in a tab project indents with.
 */
class EditorConfigCodeStyleTest {

    private static final String SERVICES =
            "META-INF/services/org.netbeans.modules.editor.indent.spi.CodeStylePreferences$Provider";

    @BeforeEach
    @AfterEach
    void reset() {
        EditorConfigCodeStyle.resetForTest();
    }

    private static BaseDocument documentFor(Path file) throws Exception {
        Files.writeString(file, "x\n");
        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(file.toFile()));
        assertThat(fo).as("the test needs a real FileObject for %s", file).isNotNull();
        BaseDocument doc = new BaseDocument(false, "text/plain");
        doc.putProperty(Document.StreamDescriptionProperty, fo);
        return doc;
    }

    @Test
    @DisplayName("Registered as a CodeStylePreferences.Provider, positioned ahead of the platform's")
    void registered() throws Exception {
        List<String> lines = new ArrayList<>();
        for (URL url : Collections.list(getClass().getClassLoader().getResources(SERVICES))) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
                r.lines().forEach(lines::add);
            }
        }
        int at = lines.indexOf(EditorConfigCodeStyle.class.getName());
        assertThat(at).as("the generated services file names the provider").isGreaterThanOrEqualTo(0);
        assertThat(lines.get(at + 1))
                .as("a position, so it is asked before the unpositioned project-aware provider")
                .isEqualTo("#position=100");
        // the platform's project-aware provider is an unpositioned services line;
        // a stand-in registered the same way proves the lookup puts ours first
        assertThat(Lookup.getDefault().lookupAll(CodeStylePreferences.Provider.class))
                .hasAtLeastOneElementOfType(UnpositionedCodeStyleProvider.class)
                .first().isInstanceOf(EditorConfigCodeStyle.class);
    }

    @Test
    @DisplayName("A file with no .editorconfig gets null: the platform's answer stands")
    void silentWithoutConfig(@TempDir Path tmp) throws Exception {
        BaseDocument doc = documentFor(tmp.resolve("app.js"));
        assertThat(new EditorConfigCodeStyle().forDocument(doc, "text/plain")).isNull();
        assertThat(new EditorConfigCodeStyle().forDocument(new BaseDocument(false, "text/plain"), "text/plain"))
                .as("an in-memory document has no file to ask about").isNull();
    }

    @Test
    @DisplayName("A config that says nothing about indentation also gets null")
    void silentForUnrelatedConfig(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve(".editorconfig"), "root = true\n[*]\ninsert_final_newline = true\n");
        BaseDocument doc = documentFor(tmp.resolve("app.js"));
        assertThat(new EditorConfigCodeStyle().forDocument(doc, "text/plain")).isNull();
    }

    @Test
    @DisplayName("A tab project: the platform's own CodeStylePreferences and IndentUtils indent with tabs")
    void tabProjectThroughThePlatform(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve(".editorconfig"), """
                root = true
                [*]
                indent_style = tab
                indent_size = tab
                tab_width = 4
                """);
        BaseDocument doc = documentFor(tmp.resolve("main.go"));

        Preferences prefs = CodeStylePreferences.get(doc).getPreferences();
        assertThat(prefs.getBoolean("expand-tabs", true)).isFalse();
        assertThat(prefs.getInt("tab-size", -1)).isEqualTo(4);
        assertThat(IndentUtils.isExpandTabs(doc)).isFalse();
        assertThat(IndentUtils.indentLevelSize(doc)).isEqualTo(4);
        assertThat(IndentUtils.createIndentString(doc, 8)).isEqualTo("\t\t");
    }

    @Test
    @DisplayName("A four-space project: spaces, four wide, through the same platform calls")
    void spaceProjectThroughThePlatform(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve(".editorconfig"), """
                root = true
                [*]
                indent_style = space
                indent_size = 4
                """);
        BaseDocument doc = documentFor(tmp.resolve("app.py"));

        assertThat(IndentUtils.isExpandTabs(doc)).isTrue();
        assertThat(IndentUtils.indentLevelSize(doc)).isEqualTo(4);
        assertThat(IndentUtils.createIndentString(doc, 8)).isEqualTo("        ");
    }

    @Test
    @DisplayName("On the EDT the first ask answers from memory, and the resolution lands off it")
    void edtNeverTouchesTheDisk(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve(".editorconfig"), "root = true\n[*]\nindent_style = tab\n");
        BaseDocument doc = documentFor(tmp.resolve("a.c"));
        EditorConfigCodeStyle provider = new EditorConfigCodeStyle();

        AtomicReference<Preferences> first = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> first.set(provider.forDocument(doc, "text/plain")));
        assertThat(first.get()).as("unresolved on the EDT: the editor's own settings, for now").isNull();

        EditorConfigCodeStyle.awaitIdle();
        AtomicReference<Preferences> second = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> second.set(provider.forDocument(doc, "text/plain")));
        assertThat(second.get()).isNotNull();
        assertThat(second.get().getBoolean("expand-tabs", true)).isFalse();
    }

    @Test
    @DisplayName("An edited .editorconfig reaches the open document once the answer ages")
    void editsArrive(@TempDir Path tmp) throws Exception {
        Path cfg = tmp.resolve(".editorconfig");
        Files.writeString(cfg, "root = true\n[*]\nindent_style = tab\n");
        BaseDocument doc = documentFor(tmp.resolve("a.c"));
        long[] now = {1_000_000};
        EditorConfigCodeStyle.clock = () -> now[0];
        EditorConfigCodeStyle provider = new EditorConfigCodeStyle();
        assertThat(provider.forDocument(doc, "text/plain").getBoolean("expand-tabs", true)).isFalse();

        Files.writeString(cfg, "root = true\n[*]\nindent_style = space\nindent_size = 2\n");
        assertThat(provider.forDocument(doc, "text/plain").getBoolean("expand-tabs", true))
                .as("still fresh: served from memory").isFalse();
        now[0] += EditorConfigCodeStyle.FRESH_MS;
        Preferences after = provider.forDocument(doc, "text/plain");
        assertThat(after.getBoolean("expand-tabs", false)).isTrue();
        assertThat(after.getInt("indent-shift-width", -1)).isEqualTo(2);
    }

    @Test
    @DisplayName("The overlay answers only its own keys and hands every write to the base")
    void overlayDelegates() throws Exception {
        MemoryPreferences store = new MemoryPreferences();
        store.put("tab-size", "8");
        store.put("text-limit-width", "100");
        OverlayPreferences over = new OverlayPreferences(store, java.util.Map.of("tab-size", "4"));
        assertThat(over.getInt("tab-size", -1)).isEqualTo(4);
        assertThat(over.getInt("text-limit-width", -1)).as("unnamed keys come from the base").isEqualTo(100);
        over.put("text-limit-width", "120");
        assertThat(store.get("text-limit-width", null)).as("writes land in the base").isEqualTo("120");
        over.put("tab-size", "2");
        assertThat(store.get("tab-size", null)).isEqualTo("2");
        assertThat(over.getInt("tab-size", -1)).as("the file still rules the key it names").isEqualTo(4);
        assertThat(over.keys()).contains("tab-size", "text-limit-width");
        assertThat(new OverlayPreferences(null, java.util.Map.of()).get("anything", "default"))
                .as("no base at all: the caller's default").isEqualTo("default");
    }

    /** A root preferences node that lives in memory only - never the user's real store. */
    static final class MemoryPreferences extends java.util.prefs.AbstractPreferences {
        private final java.util.Map<String, String> values = new java.util.HashMap<>();

        MemoryPreferences() {
            super(null, "");
        }

        @Override
        protected void putSpi(String key, String value) {
            values.put(key, value);
        }

        @Override
        protected String getSpi(String key) {
            return values.get(key);
        }

        @Override
        protected void removeSpi(String key) {
            values.remove(key);
        }

        @Override
        protected void removeNodeSpi() {
        }

        @Override
        protected String[] keysSpi() {
            return values.keySet().toArray(String[]::new);
        }

        @Override
        protected String[] childrenNamesSpi() {
            return new String[0];
        }

        @Override
        protected java.util.prefs.AbstractPreferences childSpi(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void syncSpi() {
        }

        @Override
        protected void flushSpi() {
        }
    }
}
