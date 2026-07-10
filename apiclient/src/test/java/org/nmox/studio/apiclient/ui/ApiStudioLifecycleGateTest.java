package org.nmox.studio.apiclient.ui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two RCP-idiom rules for this tab, pinned at the source (the
 * NpmExplorerBootGateTest shape — the class is pure Swing and excluded
 * from instrumented coverage):
 *
 * <p>1. The constructor runs during window-system deserialization and
 * must not read the workspace; componentOpened owns the initial load,
 * exactly once — a second open goes through onProjectReaimed, whose
 * equality guard makes it free.
 *
 * <p>2. UI-triggered background work (sends, rebind reads, registry
 * pokes) goes through the module RequestProcessor, never a raw
 * {@code new Thread} per click.
 */
class ApiStudioLifecycleGateTest {

    private static String source() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/org/nmox/studio/apiclient/ui/ApiClientTopComponent.java"),
                StandardCharsets.UTF_8);
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertThat(start).as(signature + " exists").isGreaterThan(0);
        int end = source.indexOf("\n    }", start);
        return source.substring(start, end);
    }

    @Test
    @DisplayName("the constructor never loads the workspace; componentOpened loads exactly once")
    void constructorDefersInitialLoadToOpen() throws Exception {
        String source = source();

        assertThat(method(source, "public ApiClientTopComponent()"))
                .as("winsys deserialization constructs the tab — no workspace read there")
                .doesNotContain("loadWorkspace()");

        String opened = method(source, "public void componentOpened()");
        assertThat(opened)
                .as("the first open owns the initial load, guarded to once")
                .contains("loadedOnce")
                .contains("loadWorkspace()");
        assertThat(opened.indexOf("loadedOnce = true"))
                .as("the guard flips before the load — a reentrant open never doubles it")
                .isLessThan(opened.indexOf("loadWorkspace()"));
    }

    @Test
    @DisplayName("background work rides the module RequestProcessor — no raw threads")
    void backgroundWorkUsesRequestProcessor() throws Exception {
        String source = source();

        assertThat(source)
                .as("sends, rebind reads and serving pokes must share the RP pool")
                .doesNotContain("new Thread(");
        assertThat(source).contains("new RequestProcessor(\"API Studio\"");
    }
}
