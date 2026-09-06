package org.nmox.studio.ui.shots;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageTransferableTest {

    @Test
    @DisplayName("the clipboard entry is an image and only an image — a text paste target gets an honest refusal")
    void imageOnly() throws Exception {
        BufferedImage img = new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB);
        ImageTransferable t = new ImageTransferable(img);
        assertThat(t.getTransferDataFlavors()).containsExactly(DataFlavor.imageFlavor);
        assertThat(t.isDataFlavorSupported(DataFlavor.imageFlavor)).isTrue();
        assertThat(t.isDataFlavorSupported(DataFlavor.stringFlavor)).isFalse();
        assertThat(t.getTransferData(DataFlavor.imageFlavor)).isSameAs(img);
        assertThatThrownBy(() -> t.getTransferData(DataFlavor.stringFlavor)).isInstanceOf(UnsupportedFlavorException.class);
    }

    @Test
    @DisplayName("copy paints at 2x onto the clipboard with no chooser and no disk, and the action rides the shared tab rule")
    void copyWiring() throws Exception {
        String saver = Files.readString(Path.of("src/main/java/org/nmox/studio/ui/shots/ShotSaver.java"));
        int copy = saver.indexOf("static void copy(");
        String body = saver.substring(copy, saver.indexOf("static File defaultDir()"));
        assertThat(body).contains("Screenshot.paint2x(target)").contains("new ImageTransferable(img)").contains("Not copied");
        assertThat(body).doesNotContain("JFileChooser").doesNotContain("ImageIO");
        String action = Files.readString(Path.of("src/main/java/org/nmox/studio/ui/shots/CopyEditorScreenshotAction.java"));
        assertThat(action).contains("path = \"Menu/Tools\", position = 102")
                .contains("SaveEditorScreenshotAction.selectedEditor(")
                .contains("ShotSaver.copy(");
    }
}
