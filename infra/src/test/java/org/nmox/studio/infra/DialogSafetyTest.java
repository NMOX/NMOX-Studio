package org.nmox.studio.infra;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source gate for ledger 53 (v1.98.0): the dialogs that delete or
 * create real paid cloud resources must default their Enter/Space
 * button to the SAFE option, so a reflexive keypress never destroys or
 * deploys. The default button is set only by a {@code NotifyDescriptor}
 * / {@code DialogDescriptor} constructor's {@code initialValue} — a
 * {@code setValue(...)} call writes only the current value and leaves
 * the default at OK/YES. A future edit that reverts to the
 * {@code Confirmation(...)} shortcut (initialValue = OK_OPTION) or to
 * {@code dd.setValue("Cancel")} would silently re-arm the destructive
 * default, and fails here by name.
 */
class DialogSafetyTest {

    private static String source() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/org/nmox/studio/infra/InfraDesignerTopComponent.java"),
                StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("The destructive confirm() defaults to No, not the OK_OPTION Confirmation shortcut")
    void confirmDefaultsToSafe() throws Exception {
        String src = source();
        int m = src.indexOf("private boolean confirm(");
        assertThat(m).as("confirm() exists").isPositive();
        String body = src.substring(m, src.indexOf("\n    }", m));
        assertThat(body)
                .as("No is the default button — the full ctor with an initialValue arg")
                .contains("NotifyDescriptor.YES_OPTION, NotifyDescriptor.NO_OPTION},")
                .contains("NotifyDescriptor.NO_OPTION);");
        assertThat(body)
                .as("the Confirmation shortcut hard-codes initialValue = OK_OPTION — must not return")
                .doesNotContain("new NotifyDescriptor.Confirmation(");
    }

    @Test
    @DisplayName("The live Deploy dialog defaults to Cancel via the initialValue ctor, not setValue")
    void deployDefaultsToCancel() throws Exception {
        String src = source();
        int m = src.indexOf("String title = live ? \"Deploy?\"");
        assertThat(m).as("deploy dialog block exists").isPositive();
        String block = src.substring(m, src.indexOf("runExclusive(deployButton", m));
        assertThat(block)
                .as("Cancel is the initialValue (default button), not a no-op setValue")
                .contains("new Object[]{deploy, cancel}, cancel,");
        assertThat(block)
                .as("dd.setValue(\"Cancel\") never moved the default button — must be gone")
                .doesNotContain("dd.setValue(\"Cancel\")");
    }
}
