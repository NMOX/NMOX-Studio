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
        for (String[] handler : new String[][]{
            {"private void addConnection()", "Added \\\""},
            {"private void editSelected()", "Updated \\\""},
            {"private void removeSelected()", "Removed "}}) {
            int at = src.indexOf(handler[0]);
            assertThat(at).as(handler[0] + " exists").isPositive();
            String body = src.substring(at, src.indexOf("\n    private ", at + 10));
            assertThat(body)
                    .as(handler[0] + " announces its outcome")
                    .contains("status(\"" + handler[1]);
        }
    }
}
