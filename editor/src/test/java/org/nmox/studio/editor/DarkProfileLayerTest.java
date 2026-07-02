package org.nmox.studio.editor;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The IDE forces the FlatLaf Dark look and feel, so the editor color
 * profile must ship flipped to match — and the value has to be the
 * profile's DISPLAY name: the settings storage validates the attribute
 * against display names, and the folder-name form ("FlatLafDark")
 * silently falls back to the light profile. These assertions read the
 * module layer the platform actually merges.
 */
class DarkProfileLayerTest {

    private static String layer() throws Exception {
        try (InputStream in = DarkProfileLayerTest.class
                .getResourceAsStream("/org/nmox/studio/editor/layer.xml")) {
            assertThat(in).as("editor module layer must exist").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("The dark profile ships as the default, by display name")
    void darkProfileIsDefault() throws Exception {
        assertThat(layer()).contains(
                "<attr name=\"currentFontColorProfile\" stringvalue=\"FlatLaf Dark\"/>");
    }

    @Test
    @DisplayName("The Phosphor palette never maps into the light NetBeans profile")
    void darkPaletteStaysOutOfLightProfile() throws Exception {
        // a dark palette under the light profile is what painted
        // pale-on-white JS/TS whenever "NetBeans" was active
        String layer = layer();
        for (int at = layer.indexOf("syntax-colors.xml"); at >= 0;
                at = layer.indexOf("syntax-colors.xml", at + 1)) {
            String before = layer.substring(Math.max(0, at - 400), at);
            int dark = before.lastIndexOf("\"FlatLafDark\"");
            int light = before.lastIndexOf("\"NetBeans\"");
            assertThat(dark)
                    .as("every syntax-colors.xml mapping sits under FlatLafDark, "
                            + "and no light-profile folder opens after it")
                    .isGreaterThan(light);
        }
    }
}
