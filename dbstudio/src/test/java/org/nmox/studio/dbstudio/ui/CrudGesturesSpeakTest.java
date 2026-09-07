package org.nmox.studio.dbstudio.ui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The v2.18.0 law applied to the connection CRUD (the 2026-08-20 DBA
 * walk's find): Remove always announced itself, but Add and Edit were
 * silent — the header still showed the load-time message after a new
 * connection appeared in the tree. Every CRUD handler must post a
 * status; the walk is how a silent gesture gets noticed, this gate is
 * how it stays noticed.
 */
class CrudGesturesSpeakTest {

    @Test
    @DisplayName("Add, Edit, and Remove each post a status")
    void crudHandlersSpeak() throws Exception {
        String src = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/dbstudio/ui/DbStudioTopComponent.java"),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
        // v2.97.0 (the l10n arc): the sentences are bundle values, so the
        // gesture is proven twice — it still posts a status built from its
        // key, and that key still SAYS what happened, in the shipped English.
        java.util.Properties english = new java.util.Properties();
        try (java.io.InputStream in = java.nio.file.Files.newInputStream(
                Path.of("target/classes/org/nmox/studio/dbstudio/ui/Bundle.properties"))) {
            english.load(in);
        }
        for (String[] handler : new String[][]{
            {"private void addConnection()", "DbStudioTopComponent_addedConnection", "Added"},
            {"private void editSelected()", "DbStudioTopComponent_updatedConnection", "Updated"},
            {"private void removeSelected()", "DbStudioTopComponent_removedConnection", "Removed"}}) {
            int at = src.indexOf(handler[0]);
            assertThat(at).as(handler[0] + " exists").isPositive();
            String body = src.substring(at, src.indexOf("\n    private ", at + 10));
            assertThat(body)
                    .as(handler[0] + " announces its outcome")
                    .contains("status(Bundle." + handler[1] + "(");
            assertThat(english.getProperty(handler[1], ""))
                    .as(handler[1] + " says what happened")
                    .startsWith(handler[2]);
        }
    }
}
