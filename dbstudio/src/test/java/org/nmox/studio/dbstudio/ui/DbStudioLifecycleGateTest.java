package org.nmox.studio.dbstudio.ui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The constructor runs during window-system deserialization (it fires
 * for hidden default-open tabs at boot) and must not read the
 * workspace; componentOpened owns the initial load, exactly once —
 * re-aims arrive via the rack listener, foreign edits via the watcher.
 * Pinned at the source, the NpmExplorerBootGateTest shape (the class is
 * pure Swing and excluded from instrumented coverage).
 */
class DbStudioLifecycleGateTest {

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertThat(start).as(signature + " exists").isGreaterThan(0);
        int end = source.indexOf("\n    }", start);
        return source.substring(start, end);
    }

    @Test
    @DisplayName("the constructor never loads the workspace; componentOpened loads exactly once")
    void constructorDefersInitialLoadToOpen() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/dbstudio/ui/DbStudioTopComponent.java"),
                StandardCharsets.UTF_8);

        assertThat(method(source, "public DbStudioTopComponent()"))
                .as("winsys deserialization constructs the tab — no workspace read there")
                .doesNotContain("reloadWorkspace()");

        String opened = method(source, "public void componentOpened()");
        assertThat(opened)
                .as("the first open owns the initial load, guarded to once")
                .contains("loadedOnce")
                .contains("reloadWorkspace()");
        assertThat(opened.indexOf("loadedOnce = true"))
                .as("the guard flips before the load — a reentrant open never doubles it")
                .isLessThan(opened.indexOf("reloadWorkspace()"));
    }
}
