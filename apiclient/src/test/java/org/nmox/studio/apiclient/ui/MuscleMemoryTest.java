package org.nmox.studio.apiclient.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The v1.194.0 muscle-memory grants, source-gated: ⌘Enter sends from
 * anywhere in the tab, the tree offers Duplicate (⌘D) and Rename, and
 * a duplicated request's auth secret is written to the keychain under
 * the COPY's fresh id, off the EDT. These are the requests a senior
 * web dev files after a week — the gate keeps them granted.
 */
class MuscleMemoryTest {

    private static String source() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/org/nmox/studio/apiclient/ui/ApiClientTopComponent.java"));
    }

    @Test
    @DisplayName("Cmd/Ctrl+Enter is bound to send at the component level")
    void sendHasAKeyChord() throws Exception {
        String s = source();
        assertThat(s).contains("\"nmox-send\"");
        assertThat(s).contains("VK_ENTER");
        assertThat(s).contains("getMenuShortcutKeyMaskEx()");
        // component-level InputMap, never a Shortcuts/ registration —
        // the v1.38.1 Keymaps-profile theft class cannot reach here
        assertThat(s).contains("WHEN_ANCESTOR_OF_FOCUSED_COMPONENT");
    }

    @Test
    @DisplayName("The tree offers Duplicate (with Cmd/Ctrl+D) and Rename")
    void treeOffersDuplicateAndRename() throws Exception {
        String s = source();
        assertThat(s).contains("duplicateSelected());");
        assertThat(s).contains("renameSelected());");
        assertThat(s).contains("\"nmox-duplicate\"");
        assertThat(s).contains("VK_D");
    }

    @Test
    @DisplayName("Duplicate carries the auth secret to the copy's OWN keychain id, off the EDT")
    void duplicateCarriesTheSecret() throws Exception {
        String s = source();
        int start = s.indexOf("private void duplicateSelected()");
        assertThat(start).isGreaterThan(-1);
        String body = s.substring(start, s.indexOf("\n    }", start));
        assertThat(body).contains("Request.duplicate(");
        assertThat(body).contains("ApiSecrets.save(id, token)");
        assertThat(body).as("keyring writes ride the RP, never the EDT")
                .contains("RP.post(");
    }
}
