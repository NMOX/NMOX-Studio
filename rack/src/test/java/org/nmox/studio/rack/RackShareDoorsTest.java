package org.nmox.studio.rack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Share and Import doors go through {@code RackShare}, and the import mounts
 * nothing before the reader has said yes twice (v2.176.0). {@code RackShareTest}
 * proves the seam decides correctly; this is the other half of the v1.321.0 pair —
 * the call sites exist and are wired the safe way round.
 */
class RackShareDoorsTest {

    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/org/nmox/studio/rack/RackTopComponent.java"))
                .replace("\r\n", "\n");
    }

    @Test
    @DisplayName("Share writes what RackShare.export produced — the sender's home rewritten, the version named — never the raw patch")
    void shareGoesThroughExport() throws Exception {
        String src = source();
        int share = src.indexOf("private void shareRack()");
        assertThat(share).isPositive();
        String body = src.substring(share, src.indexOf("private void importRack()", share));
        assertThat(body).contains("RackShare.export(");
        assertThat(body).as("the home it hides is THIS user's").contains("user.home");
        assertThat(body).as("the write rides the save lane, off the EDT").contains("SAVE_RP.post(");
    }

    @Test
    @DisplayName("Import mounts RackShare.imported (~ expanded, self-starting flags off) after the manifest with Cancel as the default AND the replace confirm")
    void importIsInspectedThenConfirmedThenMountedAtRest() throws Exception {
        String src = source();
        int mount = src.indexOf("private void mountShared(");
        assertThat(mount).isPositive();
        String body = src.substring(mount, src.indexOf("static String manifestText(", mount));
        int inspect = body.indexOf("RackShare.inspect(");
        int dialog = body.indexOf("DialogDisplayer.getDefault().notify(ask)");
        int confirm = body.indexOf("confirmReplace(");
        int imported = body.indexOf("RackShare.imported(");
        int fromJson = body.indexOf("RackIO.fromJson(");
        assertThat(List.of(inspect, dialog, confirm, imported, fromJson)).allMatch(i -> i > 0);
        assertThat(inspect).as("the manifest is read first").isLessThan(dialog);
        assertThat(dialog).as("then shown, then the replace question").isLessThan(confirm);
        assertThat(confirm).as("only then is anything mounted").isLessThan(fromJson);
        assertThat(body).as("what mounts is the file made local — never the raw shared document")
                .contains("RackIO.fromJson(rack, org.nmox.studio.rack.model.RackShare.imported(");
        assertThat(body).as("a reflexive Enter must not mount a stranger's rack: Cancel is the default (v1.98.0)")
                .contains("org.openide.NotifyDescriptor.CANCEL_OPTION);");
        // the CALL statement, not the phrase — the method's own comment names it
        // (a gate that matches a comment is the v2.160.0 wiring-gate scar)
        assertThat(body).as("an imported rack is unsaved work, not the project's persisted patch")
                .doesNotContain("markPersisted();");
    }

    @Test
    @DisplayName("the import reads the file off the EDT and mounts on it — the Load Patch split")
    void importReadsOffTheEdt() throws Exception {
        String src = source();
        int imp = src.indexOf("private void importRack()");
        String body = src.substring(imp, src.indexOf("private void mountShared(", imp));
        assertThat(body).contains("SAVE_RP.post(");
        assertThat(body).contains("RackIO.readDocument(");
        assertThat(body).contains("invokeLater(() -> mountShared(doc))");
    }
}
