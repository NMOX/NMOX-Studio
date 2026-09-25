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
 * The surfaces that name the file being edited — copied paths, GitHub
 * links, Markdown blocks, line blame, an editor's accessible name — ask
 * {@link EditedFile}, never a DataObject's primary file, which names the
 * wrong file for a grouped DataObject such as a locale's
 * {@code Bundle_de.properties} (3.2 fourth review).
 */
class EditedFileGateTest {

    private static final Path MAIN = Path.of("src/main/java/org/nmox/studio/editor");

    @Test
    @DisplayName("no surface that names the edited file reads a primary file")
    void noPrimaryFileWhereTheEditedFileIsMeant() throws Exception {
        List<Path> roots = List.of(MAIN.resolve("share"), MAIN.resolve("blame"),
                MAIN.resolve("a11y/EditorAccessibleNames.java"));
        List<String> offenders = new ArrayList<>();
        int read = 0;
        for (Path root : roots) {
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path p : walk.filter(f -> f.toString().endsWith(".java")).toList()) {
                    read++;
                    if (p.getFileName().toString().equals("EditedFile.java")) {
                        continue;
                    }
                    if (Files.readString(p).contains("getPrimaryFile(")) {
                        offenders.add(p.getFileName().toString());
                    }
                }
            }
        }
        assertThat(read).as("the census read the surfaces").isGreaterThanOrEqualTo(8);
        assertThat(offenders).as("read EditedFile.of(document) instead").isEmpty();
    }
}
