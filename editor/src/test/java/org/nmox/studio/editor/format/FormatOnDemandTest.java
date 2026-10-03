package org.nmox.studio.editor.format;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
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
        // portable executability (setPosixFilePermissions throws on
        // Windows — the 1.196.0 windows-lane catch); the Runner seam
        // means the stub never actually runs, it only needs to resolve
        assertThat(prettier.toFile().setExecutable(true)).isTrue();
        Files.writeString(bin.resolve("prettier.cmd"), "@echo off\r\n");
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
    /** A project that carries its own Prettier and has NOT been trusted (and a machine with none on its PATH). */
    private static File untrustedProject(Path root) throws Exception {
        Path bin = Files.createDirectories(root.resolve("node_modules/.bin"));
        Path prettier = bin.resolve("prettier");
        Files.writeString(prettier, "#!/bin/sh\ncat\n");
        assertThat(prettier.toFile().setExecutable(true)).isTrue();
        Files.writeString(bin.resolve("prettier.cmd"), "@echo off\r\n");
        Files.createDirectory(root.resolve(".git"));
        org.nmox.studio.rack.service.WorkspaceTrust.clearForTest();
        return root.toFile();
    }

    @Test
    @DisplayName("The gesture asks for trust when the project's own Prettier is the one that would run; Keep Safe runs nothing and says which it was")
    void theGestureAsksForTrust(@TempDir Path root) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                !org.nmox.studio.core.process.ToolLocator.resolve("prettier").contains(File.separator),
                "a Prettier on this machine's PATH answers before the question is needed");
        untrustedProject(root);
        java.util.function.Predicate<File> real = PrettierFormatter.trustAsk;
        java.util.List<File> asked = new java.util.ArrayList<>();
        try {
            PrettierFormatter.trustAsk = dir -> {
                asked.add(dir);
                return false;
            };
            OnDemand kept = new PrettierFormatter((cmd, dir, stdin) -> {
                throw new AssertionError("Keep Safe must spawn nothing");
            }).formatOnDemand("const x=1", new File(root.toFile(), "src/a.js"));
            assertThat(kept.outcome()).as("not NO_PRETTIER: the project has one").isEqualTo(OnDemandOutcome.UNTRUSTED);
            assertThat(asked).as("asked about the folder whose node_modules holds the binary")
                    .extracting(File::getCanonicalFile).containsExactly(root.toFile().getCanonicalFile());

            // the save hook never asks: silence, and nothing run
            assertThat(new PrettierFormatter((cmd, dir, stdin) -> {
                throw new AssertionError("an untrusted project is not formatted on save");
            }).format("const x=1", new File(root.toFile(), "src/a.js"))).isNull();
            assertThat(asked).hasSize(1);

            PrettierFormatter.trustAsk = dir -> {
                org.nmox.studio.rack.service.WorkspaceTrust.trust(dir);
                return true;
            };
            java.util.List<String> ran = new java.util.ArrayList<>();
            OnDemand trusted = new PrettierFormatter((cmd, dir, stdin) -> {
                ran.add(cmd.get(0));
                return new PrettierFormatter.Result(0, "const x = 1;\n");
            }).formatOnDemand("const x=1", new File(root.toFile(), "src/a.js"));
            assertThat(trusted.outcome()).isEqualTo(OnDemandOutcome.FORMATTED);
            assertThat(ran.get(0)).as("the project's own binary, once trusted").contains("node_modules");
        } finally {
            PrettierFormatter.trustAsk = real;
            org.nmox.studio.rack.service.WorkspaceTrust.clearForTest();
        }
    }

    @Test
    @DisplayName("No Prettier in the project and none on the PATH is NO_PRETTIER, and nobody is asked anything")
    void nothingToAskAbout(@TempDir Path root) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                !org.nmox.studio.core.process.ToolLocator.resolve("prettier").contains(File.separator),
                "a Prettier on this machine's PATH would be found");
        Files.createDirectory(root.resolve(".git"));
        java.util.function.Predicate<File> real = PrettierFormatter.trustAsk;
        try {
            PrettierFormatter.trustAsk = dir -> {
                throw new AssertionError("there is no project binary to ask about");
            };
            OnDemand r = new PrettierFormatter((cmd, dir, stdin) -> {
                throw new AssertionError("nothing to run");
            }).formatOnDemand("const x=1", new File(root.toFile(), "a.js"));
            assertThat(r.outcome()).isEqualTo(OnDemandOutcome.NO_PRETTIER);
        } finally {
            PrettierFormatter.trustAsk = real;
        }
    }
}
