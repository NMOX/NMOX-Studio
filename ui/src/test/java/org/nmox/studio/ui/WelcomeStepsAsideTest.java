package org.nmox.studio.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.lookup.Lookups;
import org.openide.windows.TopComponent;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The first launch ever brings the Welcome to the front, unless that launch
 * was {@code nmox app.js:42}: the file handed over is what the person asked
 * for (walked in 3.1.0 - the Welcome sat over app.js with its caret on line
 * 42). The rule is {@link MainWindow#documentOpen}: a FILE's editor in the
 * editor area keeps the front; a folder does not count, because AimFollower
 * gives the Welcome itself the aimed project's folder node.
 */
class WelcomeStepsAsideTest {

    private static org.openide.filesystems.FileObject root() {
        return FileUtil.createMemoryFileSystem().getRoot();
    }

    private static TopComponent holding(DataObject d) {
        return new TopComponent(Lookups.singleton(d));
    }

    @Test
    @DisplayName("a file open in the editor area keeps the front")
    void aFileKeepsTheFront() throws Exception {
        DataObject d = DataObject.find(root().createData("app.js"));
        assertThat(MainWindow.documentOpen(List.of(new TopComponent(), holding(d)), tc -> true)).isTrue();
    }

    @Test
    @DisplayName("the aimed folder's node does not count - it is the Welcome's own selection")
    void aFolderDoesNot() throws Exception {
        DataObject folder = DataObject.find(root().createFolder("project"));
        assertThat(MainWindow.documentOpen(List.of(holding(folder)), tc -> true)).isFalse();
    }

    @Test
    @DisplayName("a file shown outside the editor area does not count")
    void outsideTheEditorAreaDoesNot() throws Exception {
        DataObject d = DataObject.find(root().createData("app.js"));
        assertThat(MainWindow.documentOpen(List.of(holding(d)), tc -> false)).isFalse();
    }

    @Test
    @DisplayName("the first-launch activation waits for the window system and asks the rule")
    void theActivationAsksTheRule() throws Exception {
        String src = Files.readString(Path.of("src/main/java/org/nmox/studio/ui/MainWindow.java"));
        int shown = src.indexOf("prefs.putBoolean(\"welcomeShown\", true);");
        int ready = src.indexOf("invokeWhenUIReady", shown);
        int rule = src.indexOf("!documentOpen(", ready);
        int active = src.indexOf("requestActive();", rule);
        assertThat(shown).isNotEqualTo(-1);
        assertThat(ready).as("waits for the window system").isGreaterThan(shown);
        assertThat(rule).as("then asks whether a document is open").isGreaterThan(ready);
        assertThat(active).as("and only then takes the front").isGreaterThan(rule);
        assertThat(src.substring(shown, rule)).as("no activation before the rule is asked")
                .doesNotContain("requestActive");
    }
}
