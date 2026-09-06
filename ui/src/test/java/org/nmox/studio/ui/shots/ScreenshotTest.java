package org.nmox.studio.ui.shots;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import javax.swing.JPanel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScreenshotTest {

    @Test
    @DisplayName("a sized component paints at 2x, its pixels the component's own colour")
    void paintsAtTwoX() {
        JPanel p = new JPanel();
        p.setBackground(Color.RED);
        p.setOpaque(true);
        p.setSize(40, 30);
        BufferedImage img = Screenshot.paint2x(p);
        assertThat(img).isNotNull();
        assertThat(img.getWidth()).isEqualTo(80);
        assertThat(img.getHeight()).isEqualTo(60);
        assertThat(new Color(img.getRGB(10, 10))).isEqualTo(Color.RED);
    }

    @Test
    @DisplayName("a component with no size is null, never an exception (a hidden window)")
    void unsizedIsNull() {
        assertThat(Screenshot.paint2x(new JPanel())).isNull();
    }

    @Test
    @DisplayName("the default file name sorts by time and says what it is")
    void fileName() {
        assertThat(Screenshot.defaultFileName(LocalDateTime.of(2026, 9, 6, 8, 15, 30)))
                .isEqualTo("nmox-studio-2026-09-06-081530.png");
    }

    @Test
    @DisplayName("an editor shot is named after its document, then the stamp")
    void editorFileName() {
        LocalDateTime at = LocalDateTime.of(2026, 9, 6, 8, 15, 30);
        assertThat(Screenshot.editorFileName("App.jsx", at)).isEqualTo("App.jsx-2026-09-06-081530.png");
    }

    @Test
    @DisplayName("a tab title is external text: separators and metacharacters become dashes, a blank name reads editor")
    void editorFileNameIsSafe() {
        LocalDateTime at = LocalDateTime.of(2026, 9, 6, 8, 15, 30);
        assertThat(Screenshot.editorFileName("../a/b:c d$(x).js", at)).isEqualTo("a-b-c-d--x-.js-2026-09-06-081530.png");
        assertThat(Screenshot.editorFileName("", at)).isEqualTo("editor-2026-09-06-081530.png");
        assertThat(Screenshot.editorFileName(null, at)).isEqualTo("editor-2026-09-06-081530.png");
        assertThat(Screenshot.editorFileName("Zusammenfassung ✓.md", at)).isEqualTo("Zusammenfassung--.md-2026-09-06-081530.png");
    }

    @Test
    @DisplayName("a long tab title is capped, never an unbounded file name")
    void editorFileNameIsBounded() {
        String name = Screenshot.editorFileName("x".repeat(500), LocalDateTime.of(2026, 9, 6, 8, 15, 30));
        assertThat(name).startsWith("x".repeat(Screenshot.NAME_CAP) + "-2026").hasSize(Screenshot.NAME_CAP + 22);
    }

    @Test
    @DisplayName("the one save path paints on the EDT and writes off it, and speaks either way; both gestures ride it")
    void saveWiring() throws Exception {
        String saver = Files.readString(Path.of("src/main/java/org/nmox/studio/ui/shots/ShotSaver.java"));
        assertThat(saver).contains("Screenshot.paint2x(target)")
                .contains("RP.post(")
                .contains("ImageIO.write(img, \"png\", file)")
                .contains("Not saved");
        // the write is inside the RP task, never before it
        assertThat(saver.indexOf("ImageIO.write")).isGreaterThan(saver.indexOf("RP.post("));
        for (String action : new String[] {"SaveScreenshotAction", "SaveEditorScreenshotAction"}) {
            String src = Files.readString(Path.of("src/main/java/org/nmox/studio/ui/shots/" + action + ".java"));
            assertThat(src).as(action).contains("path = \"Menu/Tools\"").contains("ShotSaver.save(");
            assertThat(src).as(action + " never paints or writes on its own").doesNotContain("ImageIO").doesNotContain("paint2x");
        }
    }
}
