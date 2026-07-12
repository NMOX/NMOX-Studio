package org.nmox.studio.rack.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.io.File;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The git chip's verbs (v1.45.0, ledger 29): with the studios now
 * publishing a real selection, the chip ships the context verbs it had
 * to withhold in v1.40. Source-gated because the menu itself is
 * pure-Swing (JaCoCo-excluded): the four verbs must exist, each must be
 * invoked context-aware against the registered .instance actions, and a
 * refused context must fall back to an honest message naming the Team
 * menu — never a dead no-op.
 */
class GitChipVerbsTest {

    private static String source() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/org/nmox/studio/rack/service/GitStatusLine.java"),
                StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("the chip menu carries all four verbs plus Refresh")
    void menuCarriesAllVerbs() throws Exception {
        String source = source();
        for (String item : new String[]{
            "new JMenuItem(\"Show Changes\")",
            "new JMenuItem(\"Diff Project\")",
            "new JMenuItem(\"Annotate\")",
            "new JMenuItem(\"History\")",
            "new JMenuItem(\"Refresh\")"}) {
            assertThat(source).as(item + " is on the chip menu").contains(item);
        }
    }

    @Test
    @DisplayName("verbs resolve the git module's registered actions, context-aware")
    void verbsAreContextAwareInstanceInvocations() throws Exception {
        String source = source();
        // the exact .instance registrations pinned from git-layer.xml — a
        // git module rename breaks these strings, and this test names them
        for (String instance : new String[]{
            "Actions/Git/org-netbeans-modules-git-ui-status-StatusAction.instance",
            "Actions/Git/org-netbeans-modules-git-ui-diff-DiffAction.instance",
            "Actions/Git/org-netbeans-modules-git-ui-blame-AnnotateAction.instance"}) {
            assertThat(source).contains(instance);
        }
        assertThat(source)
                .as("actions bind to an explicit context, not the global selection")
                .contains("createContextAwareInstance");
        assertThat(source)
                .as("the context carries node + DataObject + FileObject")
                .contains("Lookups.fixed(dob.getNodeDelegate(), dob, fo)");
    }

    @Test
    @DisplayName("a refused or missing action falls back to a message naming the Team menu")
    void disabledActionsFallBackHonestly() throws Exception {
        String source = source();
        assertThat(source)
                .as("enablement is checked before performing")
                .contains("!action.isEnabled()");
        assertThat(source)
                .as("the refusal names where the verb still works")
                .contains("use the Team menu");
    }

    @Test
    @DisplayName("with no editor open, Annotate's file resolution answers null, not a throw")
    void currentEditorFileNullWithoutEditors() throws Exception {
        AtomicReference<File> result = new AtomicReference<>(new File("sentinel"));
        SwingUtilities.invokeAndWait(() ->
                result.set(GitStatusLine.currentEditorFile()));
        assertThat(result.get())
                .as("no registry entries: Annotate must refuse, not guess")
                .isNull();
        assertThat(GitStatusLine.fileOf(null)).isNull();
    }
}
