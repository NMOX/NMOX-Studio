package org.nmox.studio.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An index page that omits a document beside it makes that document
 * invisible (v2.90.0: the docs index listed three of the eleven documents
 * in its own directory — the Kitchen Sink, the story, the tour, the
 * beginners' guide and both device docs were unlisted). The rule is the
 * outcome, derived from the directory: every Markdown file next to an
 * index is linked from it, by file name. Failing-first proven on eight.
 */
class DocsIndexGateTest {

    @ParameterizedTest
    @CsvSource({"docs, README.md", "docs/tutorials, README.md"})
    @DisplayName("every document beside an index is linked from it")
    void everyNeighbourIsListed(String dir, String index) throws IOException {
        Path root = Path.of("..", dir);
        String text = Files.readString(root.resolve(index)).replace("\r\n", "\n");
        List<String> unlisted = new ArrayList<>();
        try (Stream<Path> s = Files.list(root)) {
            s.filter(p -> p.getFileName().toString().endsWith(".md"))
                    .map(p -> p.getFileName().toString())
                    .filter(n -> !n.equals(index))
                    .sorted()
                    .forEach(n -> {
                        if (!text.contains("(" + n + ")") && !text.contains("(" + n + "#") && !text.contains("/" + n + ")")) {
                            unlisted.add(n);
                        }
                    });
        }
        assertThat(unlisted).as(dir + "/" + index + " omits documents beside it").isEmpty();
    }
}
