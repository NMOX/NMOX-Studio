package org.nmox.studio.editor.present;

import java.awt.Font;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutputFontTest {

    @Test
    @DisplayName("the presenting font is the user's own, ten points larger, family and style kept")
    void bumpKeepsTheFace() {
        Font base = new Font("Monospaced", Font.BOLD, 12);
        Font big = OutputFont.bumped(base, OutputFont.DELTA_POINTS);
        assertThat(big.getSize2D()).isEqualTo(22f);
        assertThat(big.getFamily()).isEqualTo(base.getFamily());
        assertThat(big.getStyle()).isEqualTo(Font.BOLD);
        assertThat(OutputFont.DELTA_POINTS).isEqualTo(PresentationMode.DELTA_POINTS);
    }

    @Test
    @DisplayName("the follow is live and never persisted: setFont on the singleton, saveTo never named; leaving restores the remembered font")
    void liveNeverPersisted() throws Exception {
        String src = Files.readString(Path.of("src/main/java/org/nmox/studio/editor/present/OutputFont.java"));
        assertThat(src).contains("lookup(ClassLoader.class)")
                .contains("\"org.netbeans.core.output2.options.OutputOptions\"")
                .contains("getMethod(\"setFont\", Font.class)")
                .contains("before = current;")
                .contains("next = before != null ? before : current;");
        String code = src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", ""); // the javadoc names the verb to forbid it
        assertThat(code).as("the Options panel's persistence verb is never called").doesNotContain("saveTo");
        assertThat(code).doesNotContain("Preferences");
        String mode = Files.readString(Path.of("src/main/java/org/nmox/studio/editor/present/PresentationMode.java"));
        assertThat(mode).contains("OutputFont.follow(enable)");
    }
}
