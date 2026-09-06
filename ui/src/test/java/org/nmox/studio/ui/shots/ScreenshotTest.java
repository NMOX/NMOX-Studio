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
    @DisplayName("the action paints on the EDT and writes off it, and speaks either way")
    void actionWiring() throws Exception {
        String src = Files.readString(Path.of("src/main/java/org/nmox/studio/ui/shots/SaveScreenshotAction.java"));
        assertThat(src).contains("path = \"Menu/Tools\"")
                .contains("Screenshot.paint2x(main)")
                .contains("RP.post(")
                .contains("ImageIO.write(img, \"png\", target)")
                .contains("Screenshot not saved");
        // the write is inside the RP task, never before it
        assertThat(src.indexOf("ImageIO.write")).isGreaterThan(src.indexOf("RP.post("));
    }
}
