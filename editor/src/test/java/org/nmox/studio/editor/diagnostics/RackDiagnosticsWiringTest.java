package org.nmox.studio.editor.diagnostics;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source-gates for the rack-diagnostics rendering (tech-debt #32). The
 * v1.49.0 recon found the squiggle half was ALREADY platform plumbing —
 * RackSquiggler has rendered via HintsController since it shipped, and no
 * hand-painted annotation path ever existed — so these gates pin both
 * halves to the platform APIs so a bespoke renderer can't sneak in later,
 * and pin the layer wiring the Task List half depends on.
 */
class RackDiagnosticsWiringTest {

    private static final Path SQUIGGLER = Path.of(
            "src/main/java/org/nmox/studio/editor/diagnostics/RackSquiggler.java");
    private static final Path SCANNER = Path.of(
            "src/main/java/org/nmox/studio/editor/diagnostics/RackFindingsTaskScanner.java");
    private static final Path LAYER = Path.of(
            "src/main/resources/org/nmox/studio/editor/layer.xml");

    @Test
    @DisplayName("squiggles render via HintsController — never a hand-painted annotation layer")
    void squigglerRidesHintsController() throws Exception {
        String source = Files.readString(SQUIGGLER, StandardCharsets.UTF_8);

        assertThat(source)
                .as("the platform hint API is the renderer")
                .contains("HintsController.setErrors");
        assertThat(source)
                .as("no bespoke annotation painting — that is the debt #32 shape")
                .doesNotContain("org.openide.text.Annotation")
                .doesNotContain("AnnotationProvider")
                .doesNotContain("addAnnotation");
    }

    @Test
    @DisplayName("the Task List scanner is layer-registered under TaskList/Scanners")
    void scannerIsRegisteredInTheLayer() throws Exception {
        String layer = Files.readString(LAYER, StandardCharsets.UTF_8);

        // the framework only finds scanners in this folder; a typo here
        // fails silently in the product (no error, just no rows)
        assertThat(layer).contains("<folder name=\"TaskList\">");
        assertThat(layer).contains("<folder name=\"Scanners\">");
        assertThat(layer).contains(
                "methodvalue=\"org.nmox.studio.editor.diagnostics.RackFindingsTaskScanner.create\"");
        assertThat(layer).contains(
                "stringvalue=\"org.netbeans.spi.tasklist.PushTaskScanner\"");
    }

    @Test
    @DisplayName("the scanner subscribes to the bus, normalizes files, and touches no EDT")
    void scannerWiringIsSound() throws Exception {
        String source = Files.readString(SCANNER, StandardCharsets.UTF_8);

        assertThat(source)
                .as("DiagnosticsBus stays the transport; the scanner is a renderer")
                .contains("DiagnosticsBus.addListener");
        assertThat(source)
                .as("File→FileObject rides the house normalizeFile one-liner")
                .contains("FileUtil.toFileObject(FileUtil.normalizeFile(");
        assertThat(source)
                .as("publishes arrive on device worker threads and must stay there")
                .doesNotContain("SwingUtilities")
                .doesNotContain("invokeLater")
                .doesNotContain("EventQueue");
        assertThat(source)
                .as("severity maps through the shared core, not a second copy")
                .doesNotContain("nb-tasklist-");
    }

    @Test
    @DisplayName("the layer factory builds a scanner headlessly (lazy — no boot cost)")
    void factoryConstructsHeadless() {
        RackFindingsTaskScanner scanner = RackFindingsTaskScanner.create();

        assertThat(scanner).isNotNull();
        // deactivation contract: the framework passes nulls to say stop —
        // must be a no-op, not an NPE
        scanner.setScope(null, null);
    }
}
