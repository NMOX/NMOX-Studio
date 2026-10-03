package org.nmox.studio.tools.vscode;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final List<String[]> told = new ArrayList<>();
    /** For each notice, the project whose extensions sheet its click opens; null for Quick Search. */
    private final List<File> clickOpens = new ArrayList<>();
    private final Memory memory = new Memory();

    @BeforeEach
    void seams() {
        VsCodeTasks.clearCache();
        VsCodeLaunch.clearCache();
        VsCodeFilesNotice.shownStore = () -> memory;
        VsCodeFilesNotice.sink = (title, detail, extensionsOf) -> {
            told.add(new String[] {title, detail});
            clickOpens.add(extensionsOf);
        };
    }

    @AfterEach
    void restore() {
        VsCodeFilesNotice.shownStore = realStore;
        VsCodeFilesNotice.resetSink();
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
    @DisplayName("a repository that recommends extensions is told how many, and the click opens their sheet")
    void extensionsAreNamedAndTheClickReachesThem() throws Exception {
        vscode("extensions.json", "{\"recommendations\": [\"dbaeumer.vscode-eslint\", \"esbenp.prettier-vscode\"]}");
        File dir = project.toFile();
        VsCodeFilesNotice.check(dir, () -> dir);
        assertThat(told).hasSize(1);
        assertThat(told.get(0)[1]).as("the file is named, like settings.json in its sentence")
                .contains(".vscode/extensions.json recommends 2 VS Code extensions").doesNotContain("Quick Search");
        assertThat(clickOpens.get(0)).as("the click opens the sheet for the project the notice is about").isEqualTo(dir);
    }

    @Test
    @DisplayName("one recommendation reads as one extension, beside the tasks sentence")
    void oneExtensionBesideTasks() throws Exception {
        vscode("tasks.json", "{\"version\":\"2.0.0\",\"tasks\":[{\"label\":\"build\",\"type\":\"shell\",\"command\":\"make\"}]}");
        vscode("extensions.json", "// what we use\n{\"recommendations\": [\"golang.go\",],}");
        File dir = project.toFile();
        VsCodeFilesNotice.check(dir, () -> dir);
        assertThat(told.get(0)[1]).contains("Quick Search").contains("1 VS Code extension:");
        assertThat(clickOpens.get(0)).isEqualTo(dir);
    }

    @Test
    @DisplayName("without recommendations the click still opens Quick Search")
    void noExtensionsKeepsQuickSearch() throws Exception {
        vscode("tasks.json", "{\"version\":\"2.0.0\",\"tasks\":[{\"label\":\"build\",\"type\":\"shell\",\"command\":\"make\"}]}");
        vscode("extensions.json", "{\"recommendations\": []}");
        File dir = project.toFile();
        VsCodeFilesNotice.check(dir, () -> dir);
        assertThat(told.get(0)[1]).doesNotContain("extension");
        assertThat(clickOpens.get(0)).isNull();
    }

    @Test
    @DisplayName("an extensions.json that recommends nothing, or does not parse, is not worth a notice")
    void emptyOrBrokenExtensionsSayNothing() throws Exception {
        vscode("extensions.json", "{\"unwantedRecommendations\": [\"ms-vscode.vscode-typescript-next\"]}");
        File dir = project.toFile();
        VsCodeFilesNotice.check(dir, () -> dir);
        vscode("extensions.json", "{\"recommendations\": [");
        VsCodeFilesNotice.check(dir, () -> dir);
        assertThat(told).isEmpty();
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
    @DisplayName("a settings.json that holds nothing this IDE reads is not announced as applying")
    void settingsWithNothingReadSayNothing() throws Exception {
        vscode("settings.json", "{\"workbench.colorTheme\": \"Monokai\",\n"
                + "  // \"editor.tabSize\": 2,\n"
                + "  \"[markdown]\": {\"editor.fontSize\": 14}}");
        File dir = project.toFile();
        VsCodeFilesNotice.check(dir, () -> dir);
        assertThat(told).as("a commented-out setting is not a setting").isEmpty();
    }

    @Test
    @DisplayName("every setting the product reads from settings.json is announced: excludes, rulers, wrap, not only indentation")
    void everySettingReadIsAnnounced() throws Exception {
        Path settings = project.resolve(".vscode/settings.json");
        Files.createDirectories(settings.getParent());
        for (String key : VsCodeFilesNotice.SETTINGS_KEYS) {
            Files.writeString(settings, "{\"" + key + "\": true}");
            assertThat(VsCodeFilesNotice.setsSomethingRead(settings.toFile())).as(key).isTrue();
            Files.writeString(settings, "{\"[typescript]\": {\"" + key + "\": true}}");
            assertThat(VsCodeFilesNotice.setsSomethingRead(settings.toFile())).as("[typescript] " + key).isTrue();
        }
        Files.writeString(settings, "{not json");
        assertThat(VsCodeFilesNotice.setsSomethingRead(settings.toFile())).isFalse();
    }

    /** The settings keys a source file reads, as the string literals it names them by. */
    private static java.util.Set<String> keysReadBy(String path) throws Exception {
        java.util.Set<String> keys = new java.util.TreeSet<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"((?:editor|files|search)\\.[A-Za-z]+)\"")
                .matcher(Files.readString(Path.of("..").resolve(path)));
        while (m.find()) {
            keys.add(m.group(1));
        }
        return keys;
    }

    @Test
    @DisplayName("the notice's list is every key the editor's and the trees' readers of settings.json name")
    void theListIsTheReadersKeys() throws Exception {
        java.util.Set<String> read = new java.util.TreeSet<>();
        read.addAll(keysReadBy("editor/src/main/java/org/nmox/studio/editor/standards/VsCodeSettings.java"));
        read.addAll(keysReadBy("core/src/main/java/org/nmox/studio/core/util/VsCodeExcludes.java"));
        assertThat(read).as("the readers were found").contains("editor.tabSize", "files.exclude");
        assertThat(VsCodeFilesNotice.SETTINGS_KEYS).containsExactlyInAnyOrderElementsOf(read);
    }

    @Test
    @DisplayName("a folder name with a control character reaches the title as one line of ordinary characters")
    void titleIsOneLine() {
        String title = VsCodeFilesNotice.title(new File("/work/odd\u0007name\nhere"));
        assertThat(title).isEqualTo("VS Code files in odd name here");
        assertThat(title.chars()).noneMatch(Character::isISOControl);
        String longName = "x".repeat(200);
        assertThat(VsCodeFilesNotice.title(new File(longName)).codePointCount(0, VsCodeFilesNotice.title(new File(longName)).length()))
                .isLessThan(100);
    }

    @Test
    @DisplayName("the sentence names the chord the reader's OS uses")
    void chordPerOs() {
        VsCodeFilesNotice.Found both = new VsCodeFilesNotice.Found(1, 2, true);
        assertThat(VsCodeFilesNotice.detail(both, true)).contains("⇧⌘P").contains("settings.json");
        assertThat(VsCodeFilesNotice.detail(both, false)).contains("Ctrl+Shift+P");
        assertThat(VsCodeFilesNotice.detail(both, true)).as("no extensions.json, no sentence about one")
                .doesNotContain("extension");
        assertThat(VsCodeFilesNotice.detail(new VsCodeFilesNotice.Found(1, 2, true, 12), true))
                .contains("12 VS Code extensions");
    }

    @Test
    @DisplayName("the record's key is short enough for Preferences and stable for a path")
    void keyFits() {
        String k = VsCodeFilesNotice.key(new File("/a/very/" + "long/".repeat(40) + "project"));
        assertThat(k.length()).isLessThanOrEqualTo(Preferences.MAX_KEY_LENGTH);
        assertThat(VsCodeFilesNotice.key(new File("/x"))).isEqualTo(VsCodeFilesNotice.key(new File("/x")));
    }
}
