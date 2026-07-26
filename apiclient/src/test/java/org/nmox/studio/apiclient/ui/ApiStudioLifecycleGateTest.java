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

    /**
     * v1.172.0 — the disclosure law: a response belongs to the project
     * it came from. Binding a new workspace must forget it, or Explain…
     * stays armed with the PREVIOUS project's response body while the
     * user works in a new one. Before v1.171.0 the same staleness was
     * only a cosmetic display bug; the Explain button made it a
     * disclosure path.
     */
    @Test
    @DisplayName("a workspace re-aim clears the response Explain could disclose")
    void reAimClearsTheArmedResponse() throws Exception {
        String source = source();

        String apply = method(source, "private void applyWorkspace(Workspace loaded, File dir)");
        assertThat(apply)
                .as("every re-aim must forget the previous project's response")
                .contains("clearResponse()");

        String clear = method(source, "void clearResponse()");
        assertThat(clear)
                .as("clearing means the disclosure fields AND the button")
                .contains("lastResponse = null")
                .contains("lastMethod = null")
                .contains("lastUrl = null")
                .contains("explainButton.setEnabled(false)");
    }
}
