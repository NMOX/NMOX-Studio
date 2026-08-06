package org.nmox.studio.rack;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Replacing the whole rack asks first (v1.280.0, the Task Rack persona
 * walk). Both patch verbs — the Presets menu and Load Patch — route
 * through {@code RackIO.fromJson}, which CLEARS UNDO HISTORY by design
 * (v1.50.0, so undo can never peel a just-loaded patch apart), and
 * neither asked: clicking a preset on a pipeline you had not saved
 * destroyed it with no confirmation and no way back. Every other
 * irreversible gesture in the product carries the v1.98.0 safe-default
 * confirm; these two never did.
 */
class PatchReplaceConfirmTest {

    @Test
    @DisplayName("unsaved work is a difference from the last persisted patch")
    void dirtyIsADifferenceFromBaseline() {
        assertThat(RackTopComponent.unsavedWork("{a}", "{a}"))
                .as("nothing changed since the load or save — nothing to lose")
                .isFalse();
        assertThat(RackTopComponent.unsavedWork("{a,b}", "{a}"))
                .as("a device added since the baseline is work worth keeping")
                .isTrue();
        assertThat(RackTopComponent.unsavedWork("{a}", null))
                .as("no baseline yet (the window never opened) must not"
                        + " nag — the check is opt-in, not fail-closed")
                .isFalse();
    }

    @Test
    @DisplayName("both patch verbs confirm before replacing, with the safe default")
    void bothVerbsConfirm() throws Exception {
        String src = Files.readString(Path.of("src", "main", "java", "org",
                "nmox", "studio", "rack", "RackTopComponent.java"),
                StandardCharsets.UTF_8);

        int preset = src.indexOf("Could not wire the preset");
        assertThat(preset).isPositive();
        assertThat(src.substring(Math.max(0, preset - 500), preset))
                .as("a preset replaces the rack — it must ask first")
                .contains("confirmReplace(");

        int load = src.indexOf("loadPatch(source);");
        assertThat(load).isPositive();
        assertThat(src.substring(Math.max(0, load - 300), load))
                .as("Load Patch replaces the rack — it must ask first")
                .contains("confirmReplace(");

        int confirm = src.indexOf("private boolean confirmReplace(");
        assertThat(confirm).isPositive();
        String body = src.substring(confirm, src.indexOf("\n    }", confirm));
        assertThat(body)
                .as("the v1.98.0 idiom: full NotifyDescriptor ctor with"
                        + " NO_OPTION as initialValue — Confirmation"
                        + " hard-codes OK, so a reflexive Enter would destroy")
                .contains("NotifyDescriptor.NO_OPTION)")
                .doesNotContain("new NotifyDescriptor.Confirmation(");
        assertThat(body)
                .as("the message must say undo cannot bring it back —"
                        + " fromJson clears the history")
                .contains("cannot be undone");
    }

    @Test
    @DisplayName("saving and loading re-baseline, so the next replace is quiet")
    void persistingReBaselines() throws Exception {
        String src = Files.readString(Path.of("src", "main", "java", "org",
                "nmox", "studio", "rack", "RackTopComponent.java"),
                StandardCharsets.UTF_8);
        // save success, load apply, preset apply, and window open all
        // re-baseline — otherwise the confirm would nag forever after
        // the first edit, and users learn to click through warnings
        assertThat(src.split("markPersisted\\(\\)", -1).length - 1)
                .as("four re-baseline sites plus the method itself")
                .isGreaterThanOrEqualTo(5);
    }
}
