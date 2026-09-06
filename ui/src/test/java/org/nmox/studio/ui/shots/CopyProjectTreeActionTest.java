package org.nmox.studio.ui.shots;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CopyProjectTreeActionTest {

    @Test
    @DisplayName("the walk rides the RP, the clipboard follows on the EDT, the aim comes from the core seam, and no project is a spoken refusal")
    void wiring() throws Exception {
        String src = Files.readString(Path.of("src/main/java/org/nmox/studio/ui/shots/CopyProjectTreeAction.java"));
        assertThat(src).contains("path = \"Menu/Tools\", position = 103")
                .contains("ProjectAim.find()")
                .contains("no project is aimed")
                .contains("RP.post(")
                .contains("Markdown.fenceFor(tree.text())");
        assertThat(src.indexOf("TreeText.render(")).isGreaterThan(src.indexOf("RP.post("));
        assertThat(src.indexOf("setContents(")).isGreaterThan(src.indexOf("invokeLater("));
        assertThat(CopyProjectTreeAction.MAX_DEPTH).isEqualTo(4);
        assertThat(CopyProjectTreeAction.MAX_ENTRIES).isEqualTo(200);
    }
}
