package org.nmox.studio.editor.present;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PresentationModeTest {

    @Test
    @DisplayName("the mode is off until toggled, and the zoom delta is a real presentation bump")
    void zoomArithmetic() {
        assertThat(PresentationMode.isOn()).isFalse();
        assertThat(PresentationMode.DELTA_POINTS).isGreaterThanOrEqualTo(6);
        assertThat(PresentationMode.zoomFor(true)).isEqualTo(PresentationMode.DELTA_POINTS);
        assertThat(PresentationMode.zoomFor(false)).isZero();
    }

    @Test
    @DisplayName("the key is the platform editor's decompiled zoom property, not a guess")
    void ridesThePlatformZoomProperty() {
        assertThat(PresentationMode.TEXT_ZOOM).isEqualTo("text-zoom");
    }

    @Test
    @DisplayName("the View action flips the mode and reads its live state into the checkbox")
    void actionWiring() throws Exception {
        String src = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/editor/present/PresentationModeAction.java"));
        assertThat(src).contains("PresentationMode.setOn(!PresentationMode.isOn())");
        assertThat(src).contains("setSelected(PresentationMode.isOn())");
        assertThat(src).contains("path = \"Menu/View\"");
        // the mode registers and REMOVES its focus hook (no leaked listener when off)
        String mode = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/editor/present/PresentationMode.java"));
        assertThat(mode).contains("EditorRegistry.addPropertyChangeListener")
                .contains("EditorRegistry.removePropertyChangeListener");
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("flipping the mode publishes the product-wide state before the editors are bumped (the Browser follows it)")
    void publishesProductWideState() throws Exception {
        String src = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/org/nmox/studio/editor/present/PresentationMode.java"));
        int publish = src.indexOf("Presentation.setOn(enable)");
        org.assertj.core.api.Assertions.assertThat(publish).isPositive();
        org.assertj.core.api.Assertions.assertThat(publish).isLessThan(src.indexOf("int delta = zoomFor(enable);"));
    }
}
