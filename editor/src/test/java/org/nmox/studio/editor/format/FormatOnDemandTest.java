package org.nmox.studio.editor.format;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import javax.swing.text.PlainDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.editor.format.PrettierFormatter.OnDemand;
import org.nmox.studio.editor.format.PrettierFormatter.OnDemandOutcome;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The explicit Format-with-Prettier gesture (v1.196.0). The load-bearing
 * difference from the on-save hook: the user asked BY NAME, so a project
 * with no Prettier config still formats (with defaults) and the outcome
 * carries {@code optedIn=false} so the UI can say so. Everything else —
 * the size cap, the trust-gated binary, syntax-error refusal — behaves
 * exactly like the save path.
 */
class FormatOnDemandTest {

    private static File project(Path root) throws Exception {
        Path bin = Files.createDirectories(root.resolve("node_modules/.bin"));
        Path prettier = bin.resolve("prettier");
        Files.writeString(prettier, "#!/bin/sh\ncat\n");
        Files.setPosixFilePermissions(prettier, EnumSet.allOf(PosixFilePermission.class));
        Files.createDirectory(root.resolve(".git"));
        org.nmox.studio.rack.service.WorkspaceTrust.trust(root.toFile());
        return root.toFile();
    }

    @Test
    @DisplayName("A project with NO Prettier config still formats — with optedIn=false")
    void noConfigStillFormats(@TempDir Path root) throws Exception {
        project(root);
        File file = new File(root.toFile(), "a.js");
        PrettierFormatter f = new PrettierFormatter((cmd, dir, stdin) ->
                new PrettierFormatter.Result(0, "formatted!\n"));
        OnDemand r = f.formatOnDemand("const x=1", file);
        assertThat(r.outcome()).isEqualTo(OnDemandOutcome.FORMATTED);
        assertThat(r.text()).isEqualTo("formatted!\n");
        assertThat(r.optedIn()).as("no config anywhere").isFalse();

        // and the SILENT on-save path still refuses the same project:
        // the opt-in stays the save hook's law
        assertThat(f.format("const x=1", file)).isNull();
    }

    @Test
    @DisplayName("A configured project reports optedIn=true")
    void configReportsOptedIn(@TempDir Path root) throws Exception {
        project(root);
        Files.writeString(root.resolve(".prettierrc"), "{}");
        PrettierFormatter f = new PrettierFormatter((cmd, dir, stdin) ->
                new PrettierFormatter.Result(0, "formatted!\n"));
        OnDemand r = f.formatOnDemand("const x=1", new File(root.toFile(), "a.js"));
        assertThat(r.outcome()).isEqualTo(OnDemandOutcome.FORMATTED);
        assertThat(r.optedIn()).isTrue();
    }

    @Test
    @DisplayName("Unchanged output is ALREADY_FORMATTED; nonzero exit is FAILED")
    void honestOutcomes(@TempDir Path root) throws Exception {
        project(root);
        File file = new File(root.toFile(), "a.js");
        OnDemand same = new PrettierFormatter((cmd, dir, stdin) ->
                new PrettierFormatter.Result(0, stdin)).formatOnDemand("done\n", file);
        assertThat(same.outcome()).isEqualTo(OnDemandOutcome.ALREADY_FORMATTED);

        OnDemand broken = new PrettierFormatter((cmd, dir, stdin) ->
                new PrettierFormatter.Result(2, "")).formatOnDemand("const {", file);
        assertThat(broken.outcome()).isEqualTo(OnDemandOutcome.FAILED);
    }

    @Test
    @DisplayName("Oversize text is refused before any process spawns")
    void oversizeRefused(@TempDir Path root) throws Exception {
        project(root);
        String huge = "x".repeat(PrettierFormatter.MAX_CHARS + 1);
        OnDemand r = new PrettierFormatter((cmd, dir, stdin) -> {
            throw new AssertionError("must not spawn for oversize text");
        }).formatOnDemand(huge, new File(root.toFile(), "a.js"));
        assertThat(r.outcome()).isEqualTo(OnDemandOutcome.TOO_LARGE);
    }

    @Test
    @DisplayName("The stale-document guard applies only when the buffer still matches the snapshot")
    void staleDocumentGuard() throws Exception {
        PlainDocument doc = new PlainDocument();
        doc.insertString(0, "const x=1", null);
        assertThat(FormatWithPrettierAction.applyIfUnchanged(doc, "const x=1", "const x = 1;\n"))
                .isTrue();
        assertThat(doc.getText(0, doc.getLength())).isEqualTo("const x = 1;\n");

        // the user typed while prettier ran: refuse, touch nothing
        doc.insertString(0, "// new\n", null);
        String now = doc.getText(0, doc.getLength());
        assertThat(FormatWithPrettierAction.applyIfUnchanged(doc, "const x = 1;\n", "clobber"))
                .isFalse();
        assertThat(doc.getText(0, doc.getLength())).isEqualTo(now);
    }
}
