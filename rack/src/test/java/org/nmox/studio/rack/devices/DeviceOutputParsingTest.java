package org.nmox.studio.rack.devices;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.engine.DiagnosticsBus;
import org.nmox.studio.rack.model.Rack;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The other half of a command device: reading its output. Fed the real
 * stdout of npm audit, eslint, and tsc, these devices parse counts and
 * problems - the LCD tallies and the IDE diagnostics that make a rack
 * unit useful after the tool exits.
 */
class DeviceOutputParsingTest {

    @TempDir
    Path projectDir;

    private Rack aimedRack() throws IOException {
        Files.writeString(projectDir.resolve("package.json"), "{}");
        Rack rack = new Rack();
        rack.setProjectDir(projectDir.toFile());
        return rack;
    }

    // Drain both async paths before asserting: the EDT and the rack's
    // single-threaded signal router (which delivers to receivers off-thread).
    // A bare EDT flush leaves the router race that a loaded CI runner loses.
    private static void settle(Rack rack) {
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception ignored) {
            // not relevant to the assertion
        }
        rack.awaitRouterIdle();
    }

    // ---------------- SENTRY / AuditDevice ----------------

    @Test
    @DisplayName("SENTRY parses npm-audit JSON into the severity tally, SECURE on a clean tree")
    void auditParsesSeverities() throws IOException {
        Rack rack = aimedRack();
        try {
            AuditDevice sentry = new AuditDevice();
            rack.addDevice(sentry);
            String json = "{\"metadata\":{\"vulnerabilities\":"
                    + "{\"critical\":2,\"high\":1,\"moderate\":3,\"low\":0}}}";
            sentry.onLine(json);
            sentry.onFinished(1);
            settle(rack);
            // statusLcd is the CommandDevice status line (protected, same package)
            assertThat(sentry.statusLcd.getText()).isEqualTo("C:2 H:1 M:3 L:0");

            AuditDevice clean = new AuditDevice();
            rack.addDevice(clean);
            clean.onLine("{\"metadata\":{\"vulnerabilities\":"
                    + "{\"critical\":0,\"high\":0,\"moderate\":0,\"low\":0}}}");
            clean.onFinished(0);
            settle(rack);
            assertThat(clean.statusLcd.getText()).isEqualTo("C:0 H:0 M:0 L:0");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("SENTRY leaves the tally untouched when the output is not audit JSON")
    void auditIgnoresGarbage() throws IOException {
        Rack rack = aimedRack();
        try {
            AuditDevice sentry = new AuditDevice();
            rack.addDevice(sentry);
            String before = sentry.statusLcd.getText();
            sentry.onLine("npm ERR! code ENOLOCK");
            sentry.onFinished(1);
            settle(rack);
            assertThat(sentry.statusLcd.getText()).isEqualTo(before);
        } finally {
            rack.shutdown();
        }
    }

    // ---------------- PURITY / LintDevice ----------------

    @Test
    @DisplayName("PURITY turns eslint's per-file lines into IDE diagnostics")
    void lintPublishesDiagnostics() throws IOException {
        Rack rack = aimedRack();
        try {
            Files.writeString(projectDir.resolve("app.js"), "// code\n");
            LintDevice purity = new LintDevice();
            rack.addDevice(purity);

            // eslint output: a file header, then indented location lines
            purity.onLine(projectDir.resolve("app.js").toString());
            purity.onLine("  12:5  error  'x' is not defined  no-undef");
            purity.onLine("  20:1  warning  Unexpected console  no-console");
            purity.onLine("2 problems (1 error, 1 warning)");
            purity.onFinished(1);
            settle(rack);

            var problems = DiagnosticsBus.problemsFor(projectDir.resolve("app.js").toFile());
            assertThat(problems).hasSize(2);
            assertThat(problems).anyMatch(p -> p.line() == 12 && p.error());
            assertThat(problems).anyMatch(p -> p.line() == 20 && !p.error());
        } finally {
            DiagnosticsBus.publish("eslint", java.util.List.of()); // clear for other tests
            rack.shutdown();
        }
    }

    // ---------------- TYPEGUARD / TypecheckDevice ----------------

    @Test
    @DisplayName("TYPEGUARD parses tsc error lines into diagnostics")
    void typecheckPublishesDiagnostics() throws IOException {
        Rack rack = aimedRack();
        try {
            Files.writeString(projectDir.resolve("index.ts"), "const x: number = 's';\n");
            TypecheckDevice guard = new TypecheckDevice();
            rack.addDevice(guard);

            // tsc --pretty false line: file(line,col): error TSxxxx: message
            String path = projectDir.resolve("index.ts").toString();
            guard.onLine(path + "(1,7): error TS2322: Type 'string' is not assignable to type 'number'.");
            guard.onLine("Found 1 error.");
            guard.onFinished(2);
            settle(rack);

            var problems = DiagnosticsBus.problemsFor(projectDir.resolve("index.ts").toFile());
            assertThat(problems).anyMatch(p -> p.line() == 1 && p.error());
        } finally {
            DiagnosticsBus.publish("tsc", java.util.List.of());
            rack.shutdown();
        }
    }

    // ---------------- TIMELINE / GitDevice ----------------

    @Test
    @DisplayName("TIMELINE's default command is a short git status")
    void gitStatusCommand() throws IOException {
        Rack rack = aimedRack();
        try {
            GitDevice git = new GitDevice();
            rack.addDevice(git);
            assertThat(git.buildCommand()).containsExactly("git", "status", "--short");
        } finally {
            rack.shutdown();
        }
    }
}
