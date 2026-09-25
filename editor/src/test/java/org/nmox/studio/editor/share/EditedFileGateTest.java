package org.nmox.studio.editor.share;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Code that turns an editor's DOCUMENT into a file asks
 * {@code core.util.EditedFile}, never the DataObject's primary file, which
 * names the wrong file for a grouped DataObject such as a locale's
 * {@code Bundle_de.properties} (3.2 fourth review). The population is
 * derived (fifth review: the first cut listed three folders by hand and
 * missed .editorconfig's section lookup and the diagnostics' document
 * match): every editor source file that reads
 * {@code Document.StreamDescriptionProperty}, less those whose documents
 * belong to web mimes this product loads one file per DataObject for,
 * each named with that reason.
 */
class EditedFileGateTest {

    private static final Path MAIN = Path.of("src/main/java/org/nmox/studio/editor");

    /**
     * Files that read a document's stream and then its primary file for a
     * web mime only: this product's loaders give those one file per
     * DataObject, so the primary IS the file edited.
     */
    private static final java.util.Map<String, String> WEB_ONLY = java.util.Map.of(
            "RenameClassAction.java", "stylesheets and markup",
            "NgTemplateCompletionProvider.java", "Angular templates",
            "NgSwitchActions.java", "Angular component files",
            "RunFocusedTestAction.java", "test sources",
            "DapDebugAction.java", "JS/TS/Python/Go sources");

    @Test
    @DisplayName("code that turns an editor's document into a file never reads a primary file")
    void noPrimaryFileWhereTheEditedFileIsMeant() throws Exception {
        List<String> offenders = new ArrayList<>();
        int read = 0;
        try (Stream<Path> walk = Files.walk(MAIN)) {
            for (Path p : walk.filter(f -> f.toString().endsWith(".java")).toList()) {
                String src = Files.readString(p);
                if (!src.contains("StreamDescriptionProperty")) {
                    continue;
                }
                read++;
                if (src.contains("getPrimaryFile(") && !WEB_ONLY.containsKey(p.getFileName().toString())) {
                    offenders.add(p.getFileName().toString());
                }
            }
        }
        assertThat(read).as("the census found the document readers").isGreaterThanOrEqualTo(10);
        assertThat(offenders).as("read EditedFile.of(document) instead").isEmpty();
    }
}
