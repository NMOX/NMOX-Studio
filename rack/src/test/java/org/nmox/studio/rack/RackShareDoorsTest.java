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

    /** The CODE of the window class — comments blanked, so a literal in a comment cannot satisfy a gate (the 2026-09-17 arc review). */
    private static String source() throws Exception {
        return GateSources.stripComments(Files.readString(Path.of("src/main/java/org/nmox/studio/rack/RackTopComponent.java"))
                .replace("\r\n", "\n"));
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
        // a stranger's file can hold anything: the manifest read sits inside a
        // try whose catch speaks, not bare on the EDT (the 2026-09-17 arc review)
        int tryAt = body.lastIndexOf("try {", inspect);
        int catchAt = body.indexOf("catch (RuntimeException", inspect);
        assertThat(tryAt).as("inspect is guarded").isPositive();
        assertThat(catchAt).as("the guard's catch comes before the dialog").isPositive().isLessThan(dialog);
        assertThat(body.substring(catchAt, dialog)).as("the catch refuses out loud")
                .contains("RackTopComponent_importFailed(");
        // what this install cannot give the file is found BEFORE the question
        // (v2.179.0): the dry run sits between the manifest read and the dialog,
        // and a format this install does not read is refused with no dialog at all
        int compat = body.indexOf("RackCompat.check(");
        assertThat(compat).as("the dry run precedes the question").isGreaterThan(inspect).isLessThan(dialog);
        int tooNew = body.indexOf("if (compat.formatTooNew())");
        assertThat(tooNew).as("a newer format is refused before any dialog").isGreaterThan(compat).isLessThan(dialog);
        assertThat(body.substring(tooNew, dialog)).contains("RackTopComponent_importTooNew(").contains("return;");
        assertThat(body).as("the reader is shown the card and the losses, not the bare device list")
                .contains("manifestText(manifest, org.nmox.studio.rack.model.RackCard.of(doc), compat)");
        assertThat(dialog).as("then shown, then the replace question").isLessThan(confirm);
        assertThat(confirm).as("only then is anything mounted").isLessThan(fromJson);
        // the manifest is modal and the aim can move under it: the mount lands
        // only in the rack the import was asked FOR (the v1.172.0 law, the
        // 2026-09-17 arc review) — captured before the read, checked after the
        // dialog, before the replace question and the mount
        String importBody = src.substring(src.indexOf("private void importRack()"), mount);
        int captured = importBody.indexOf("aimedAt = rack.getProjectDir();");
        assertThat(captured).as("the aim is captured at pick time").isPositive()
                .isLessThan(importBody.indexOf("SAVE_RP.post("));
        assertThat(importBody).contains("mountShared(doc, aimedAt)");
        int guard = body.indexOf("if (!aimedAt.equals(rack.getProjectDir()))");
        assertThat(guard).as("the moved-aim guard sits between the manifest dialog and the replace question")
                .isGreaterThan(dialog).isLessThan(confirm);
        assertThat(body.substring(guard, confirm)).as("a moved aim refuses out loud and returns")
                .contains("RackTopComponent_importAimMoved()").contains("return;");
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
    @DisplayName("the clipboard door has no mount of its own: pasted text is parsed by RackText and handed to mountShared — same manifest, same two questions, same arrival at rest")
    void clipboardTakesTheSameDoor() throws Exception {
        String src = source();
        int door = src.indexOf("void importFromClipboard()");
        assertThat(door).isPositive();
        String body = src.substring(door, src.indexOf("private void mountShared(", door));
        assertThat(body).contains("RackText.parse(");
        assertThat(body).as("the aim is the one at the gesture").contains("mountShared(doc, rack.getProjectDir());");
        assertThat(body).as("never a second, unguarded mount").doesNotContain("RackIO.fromJson(");
        assertThat(body).as("every refusal reason speaks").contains("case EMPTY").contains("case TOO_LARGE")
                .contains("case NOT_JSON").contains("case NO_DEVICES");
    }

    @Test
    @DisplayName("the import reads the file off the EDT and mounts on it — the Load Patch split")
    void importReadsOffTheEdt() throws Exception {
        String src = source();
        int imp = src.indexOf("private void importRack()");
        String body = src.substring(imp, src.indexOf("private void mountShared(", imp));
        assertThat(body).contains("SAVE_RP.post(");
        assertThat(body).contains("RackIO.readDocument(");
        assertThat(body).contains("invokeLater(() -> mountShared(doc, aimedAt))");
    }
}
