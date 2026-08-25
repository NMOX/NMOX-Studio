package org.nmox.studio.editor;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ledger-82 seam's wiring, source-gated (the v1.321.0 two-proof
 * law: NgSuffixlessTest proves the predicate diverges; this proves the
 * call site exists and carries BOTH halves the walk showed are
 * required — registerEditor alone rerouted only the multiview
 * registry, and the popup/breadcrumb stayed html until the PUBLIC
 * CloneableEditorSupport.setMIMEType pinned the document mime).
 */
class NgSuffixlessWiringTest {

    @Test
    @DisplayName("WebFileSupport routes suffixless templates via BOTH halves of the seam")
    void bothHalvesWired() throws Exception {
        String src = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/editor/WebFileSupport.java"));
        assertThat(src)
                .as("the conditional claim consults the two-signal predicate")
                .contains("NgSuffixless.isSuffixlessTemplate(onDisk)");
        assertThat(src)
                .as("half 1: registerEditor with the template mime, plain editor")
                .contains("registerEditor(ngTemplate ? \"text/x-ng-template\" : \"text/html\", !ngTemplate)");
        assertThat(src)
                .as("half 2: the document mime pinned — without it the popup stays html")
                .contains("ces.setMIMEType(\"text/x-ng-template\")");
    }
}
