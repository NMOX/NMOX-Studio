package org.nmox.studio.editor.ghost;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The completion action copies only the window that travels. The v2.61.1
 * review found the first cut reading the WHOLE document
 * ({@code doc.getText(0, caret)} and the tail to the end) and clipping
 * afterwards — an unbounded read for a 7,500-character send. The window
 * is bounded by KvasirComplete's caps at the read itself; this gate keeps
 * the whole-document shapes out of the action by name.
 */
class BoundedCompletionReadGateTest {

    @Test
    @DisplayName("The action reads a capped window around the caret, never the whole document")
    void readsOnlyTheWindow() throws IOException {
        String src = Files.readString(Path.of("src", "main", "java", "org", "nmox", "studio",
                "editor", "ghost", "CompleteWithKvasirAction.java"));
        assertThat(src).doesNotContain("doc.getText(0, caret)");
        assertThat(src).doesNotContain("doc.getText(caret, doc.getLength() - caret)");
        assertThat(src).contains("caret - KvasirComplete.MAX_BEFORE_CHARS");
        assertThat(src).contains("caret + KvasirComplete.MAX_AFTER_CHARS");
    }
}
