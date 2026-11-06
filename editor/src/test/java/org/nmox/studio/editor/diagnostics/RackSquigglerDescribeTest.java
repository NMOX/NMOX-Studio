package org.nmox.studio.editor.diagnostics;

import java.io.File;
import java.util.List;
import javax.swing.text.DefaultStyledDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.Severity;
import org.nmox.studio.rack.engine.DiagnosticsBus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The mapping from a rack tool's problems to platform hints is the
 * testable heart of the squiggler: severity by the problem's error
 * flag, the "[tool] " prefix that names the source in the hover, and
 * the 1-based line clamp that keeps a tool's line-0 report legal.
 * (The registry/HintsController halves need open editors — GUI-bound.)
 */
class RackSquigglerDescribeTest {

    private static DefaultStyledDocument doc() throws Exception {
        DefaultStyledDocument d = new DefaultStyledDocument();
        d.insertString(0, "const a = 1;\nconst b = 2;\nconst c = 3;\n", null);
        return d;
    }

    @Test
    @DisplayName("Errors become ERROR hints, warnings WARNING, each prefixed with its tool")
    void severityAndPrefix() throws Exception {
        File f = new File("src/app.js");
        List<ErrorDescription> out = RackSquiggler.describe(doc(), "[eslint] ", List.of(
                new DiagnosticsBus.Problem(f, 1, "no-unused-vars", true),
                new DiagnosticsBus.Problem(f, 2, "prefer-const", false)));

        assertThat(out).hasSize(2);
        assertThat(out.get(0).getSeverity()).isEqualTo(Severity.ERROR);
        assertThat(out.get(0).getDescription()).isEqualTo("[eslint] no-unused-vars");
        assertThat(out.get(1).getSeverity()).isEqualTo(Severity.WARNING);
        assertThat(out.get(1).getDescription()).isEqualTo("[eslint] prefer-const");
    }

    @Test
    @DisplayName("A tool reporting line 0 clamps to line 1 — hints are 1-based")
    void lineZeroClamps() throws Exception {
        List<ErrorDescription> out = RackSquiggler.describe(doc(), "", List.of(
                new DiagnosticsBus.Problem(new File("x.js"), 0, "file-level finding", true)));
        // without the Math.max(1, line) clamp this construction is illegal;
        // the batch arriving intact IS the behaviour under test
        assertThat(out).hasSize(1);
        assertThat(out.get(0).getDescription()).isEqualTo("file-level finding");
    }

    @Test
    @DisplayName("An empty batch maps to an empty hint list — the all-clear that erases marks")
    void emptyBatch() throws Exception {
        assertThat(RackSquiggler.describe(doc(), "[tsc] ", List.of())).isEmpty();
    }

    @Test
    @DisplayName("an editor that gains focus is painted with the rack tools' problems, never a second copy of a language server's")
    void focusDoesNotRepaintAServersProblems() throws Exception {
        File f = new File("squiggled-" + System.nanoTime() + "/src/main.ts").getAbsoluteFile();
        File other = new File(f.getParentFile(), "other.ts");
        String server = "lsp:typescript";
        try {
            DiagnosticsBus.publish(server, List.of(
                    new DiagnosticsBus.Problem(f, 5, "Type 'string' is not assignable to type 'number'. (2322)", true)));
            DiagnosticsBus.publish("squiggled-test-eslint", List.of(
                    new DiagnosticsBus.Problem(f, 2, "no-unused-vars", true),
                    new DiagnosticsBus.Problem(other, 1, "another file", true)));
            assertThat(DiagnosticsBus.problemsFor(f)).as("both are on the bus, for Action Items").hasSize(2);
            assertThat(RackSquiggler.squiggled(f))
                    .as("the server's is the platform client's to paint, on the expression it names; "
                            + "painted here too it becomes a whole-line mark nothing clears (walked, 3.10.0)")
                    .extracting(DiagnosticsBus.Problem::message).containsExactly("no-unused-vars");
        } finally {
            DiagnosticsBus.publish(server, List.of());
            DiagnosticsBus.publish("squiggled-test-eslint", List.of());
        }
    }

    @Test
    @DisplayName("wiring: the focus listener paints what squiggled() answers, and nothing reads the whole bus for a file")
    void theFocusListenerUsesIt() throws Exception {
        String src = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/org/nmox/studio/editor/diagnostics/RackSquiggler.java"));
        assertThat(src).contains("describe(comp.getDocument(), \"\", squiggled(file))");
        assertThat(src).as("problemsFor answers every tool's problems, a language server's among them")
                .doesNotContain("DiagnosticsBus.problemsFor(");
    }
}
