package org.nmox.studio.tools.vscode;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The first time a project with VS Code files is aimed, one balloon says
 * where its tasks and configurations are - once per project, and only
 * while that project is still the one aimed.
 */
class VsCodeFilesNoticeTest {

    /** Preferences that live in memory, so the test never writes the user's own. */
    private static final class Memory extends AbstractPreferences {
        private final Map<String, String> values = new HashMap<>();

        Memory() {
            super(null, "");
        }

        @Override protected void putSpi(String key, String value) { values.put(key, value); }
        @Override protected String getSpi(String key) { return values.get(key); }
        @Override protected void removeSpi(String key) { values.remove(key); }
        @Override protected void removeNodeSpi() { }
        @Override protected String[] keysSpi() { return values.keySet().toArray(String[]::new); }
        @Override protected String[] childrenNamesSpi() { return new String[0]; }
        @Override protected AbstractPreferences childSpi(String name) { return new Memory(); }
        @Override protected void syncSpi() { }
        @Override protected void flushSpi() { }
    }

    @TempDir
    Path project;

    private final Supplier<Preferences> realStore = VsCodeFilesNotice.shownStore;
    private final BiConsumer<String, String> realSink = VsCodeFilesNotice.sink;
    private final List<String[]> told = new ArrayList<>();
    private final Memory memory = new Memory();

    @BeforeEach
    void seams() {
        VsCodeTasks.clearCache();
        VsCodeLaunch.clearCache();
        VsCodeFilesNotice.shownStore = () -> memory;
        VsCodeFilesNotice.sink = (title, detail) -> told.add(new String[] {title, detail});
    }

    @AfterEach
    void restore() {
        VsCodeFilesNotice.shownStore = realStore;
        VsCodeFilesNotice.sink = realSink;
    }

    private void vscode(String file, String json) throws Exception {
        Files.createDirectories(project.resolve(".vscode"));
        Files.writeString(project.resolve(".vscode").resolve(file), json);
    }

    @Test
    @DisplayName("a project with tasks is told once where they are, and never again")
    void toldOnce() throws Exception {
        vscode("tasks.json", "{\"version\":\"2.0.0\",\"tasks\":[{\"label\":\"build\",\"type\":\"shell\",\"command\":\"make\"}]}");
        File dir = project.toFile();
        VsCodeFilesNotice.check(dir, () -> dir);
        VsCodeFilesNotice.check(dir, () -> dir);
        assertThat(told).hasSize(1);
        assertThat(told.get(0)[0]).contains(dir.getName());
        assertThat(told.get(0)[1]).contains("Quick Search").doesNotContain("settings.json");
    }

    @Test
    @DisplayName("a project with nothing VS Code wrote is told nothing")
    void nothingToTell() {
        File dir = project.toFile();
        VsCodeFilesNotice.check(dir, () -> dir);
        assertThat(told).isEmpty();
    }

    @Test
    @DisplayName("aimed away before the files were read: nothing is said, and it is not marked told")
    void aimedAway() throws Exception {
        vscode("settings.json", "{\"editor.tabSize\": 2}");
        File dir = project.toFile();
        VsCodeFilesNotice.check(dir, () -> new File("/elsewhere"));
        assertThat(told).isEmpty();
        VsCodeFilesNotice.check(dir, () -> dir);
        assertThat(told).as("told when it is aimed again").hasSize(1);
        assertThat(told.get(0)[1]).contains("settings.json").doesNotContain("Quick Search");
    }

    @Test
    @DisplayName("the sentence names the chord the reader's OS uses")
    void chordPerOs() {
        VsCodeFilesNotice.Found both = new VsCodeFilesNotice.Found(1, 2, true);
        assertThat(VsCodeFilesNotice.detail(both, true)).contains("⇧⌘P").contains("settings.json");
        assertThat(VsCodeFilesNotice.detail(both, false)).contains("Ctrl+Shift+P");
    }

    @Test
    @DisplayName("the record's key is short enough for Preferences and stable for a path")
    void keyFits() {
        String k = VsCodeFilesNotice.key(new File("/a/very/" + "long/".repeat(40) + "project"));
        assertThat(k.length()).isLessThanOrEqualTo(Preferences.MAX_KEY_LENGTH);
        assertThat(VsCodeFilesNotice.key(new File("/x"))).isEqualTo(VsCodeFilesNotice.key(new File("/x")));
    }
}
