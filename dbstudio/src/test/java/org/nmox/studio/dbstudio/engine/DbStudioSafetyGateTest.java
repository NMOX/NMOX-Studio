package org.nmox.studio.dbstudio.engine;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source gates for the v1.101.0 dbstudio safety fixes that live in the
 * pure-Swing surfaces plain tests can't drive: the two destructive
 * dialogs default to the SAFE button (the v1.98.0 idiom — a reflexive
 * Enter must not run UPDATEs or delete a connection + its keychain
 * password), and Apply treats a 0-row UPDATE as a failure rather than
 * a silent success.
 */
class DbStudioSafetyGateTest {

    private static String read(String rel) throws Exception {
        return Files.readString(Path.of(rel), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("The Apply-edits dialog defaults to Cancel, not Apply")
    void applyPreviewDefaultsToCancel() throws Exception {
        String src = read("src/main/java/org/nmox/studio/dbstudio/ui/ApplyPreviewDialog.java");
        int m = src.indexOf("new Object[]{applyOption, NotifyDescriptor.CANCEL_OPTION}");
        assertThat(m).as("the two-option dialog exists").isPositive();
        // the initialValue (arg after the options array) must be CANCEL
        assertThat(src.substring(m))
                .as("Cancel is the initialValue — Enter must not run the UPDATEs")
                .containsPattern("CANCEL_OPTION\\},\\s*\\n?\\s*NotifyDescriptor.CANCEL_OPTION,");
    }

    @Test
    @DisplayName("Remove-connection defaults to Cancel via the full ctor, not the OK-defaulting shortcut")
    void removeConnectionDefaultsToCancel() throws Exception {
        String src = read("src/main/java/org/nmox/studio/dbstudio/ui/DbStudioTopComponent.java");
        // v2.97.0 (the l10n arc): the question is a bundle value; the anchor
        // is its CALL SITE, which is where the safe default has to be
        int m = src.indexOf("Bundle.DbStudioTopComponent_removeConnectionConfirm(");
        assertThat(m).as("the remove confirm exists").isPositive();
        String around = src.substring(m, m + 500);
        assertThat(around)
                .as("full NotifyDescriptor ctor with CANCEL_OPTION as initialValue")
                .contains("new Object[]{NotifyDescriptor.OK_OPTION, NotifyDescriptor.CANCEL_OPTION}")
                .contains("NotifyDescriptor.CANCEL_OPTION);");
        assertThat(around)
                .as("the OK-defaulting Confirmation shortcut must be gone")
                .doesNotContain("new NotifyDescriptor.Confirmation(");
    }

    @Test
    @DisplayName("Apply treats a 0-row UPDATE as a failure, not a silent success")
    void applyGuardsZeroRowCount() throws Exception {
        String src = read("src/main/java/org/nmox/studio/dbstudio/ui/DbStudioTopComponent.java");
        int m = src.indexOf("for (String statement : statements) {");
        assertThat(m).as("the apply loop exists").isPositive();
        String loop = src.substring(m, src.indexOf("applied++;", m) + 20);
        assertThat(loop)
                .as("a 0-row match aborts with an honest message before counting applied")
                .contains("first.updateCount() == 0")
                .contains("Bundle.DbStudioTopComponent_zeroRowsMatched()");
        java.util.Properties english = new java.util.Properties();
        try (java.io.InputStream in = java.nio.file.Files.newInputStream(
                Path.of("target/classes/org/nmox/studio/dbstudio/ui/Bundle.properties"))) {
            english.load(in);
        }
        assertThat(english.getProperty("DbStudioTopComponent_zeroRowsMatched", ""))
                .as("and the message is honest about what happened")
                .contains("0 rows matched");
    }
}
