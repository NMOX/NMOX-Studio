package org.nmox.studio.editor.emmet;

import java.io.File;
import java.nio.file.Files;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two-proof seam law (v1.321.0) applied to Emmet: {@link EmmetTest}
 * proves the grammar diverges correctly, and THIS gate proves the call
 * sites exist — the action registered for both markup mimes, the chord
 * bound to the action's exact name, and the layer carrying the binding
 * under both mimes. A green grammar with an unwired chord is a payload
 * without a gate.
 */
class EmmetWiringGateTest {

    @Test
    @DisplayName("the action is mime-registered for HTML AND Angular templates")
    void actionRegisteredForBothMimes() throws Exception {
        String src = Files.readString(new File(
                "src/main/java/org/nmox/studio/editor/emmet/ExpandAbbreviationAction.java")
                .toPath());
        assertThat(src)
                .contains("mimeType = \"text/html\"")
                .contains("mimeType = \"text/x-ng-template\"")
                .contains("name = \"nmox-expand-abbreviation\"");
    }

    @Test
    @DisplayName("⌥⌘E dispatches the action by its exact name, in both mimes' layers")
    void chordPinned() throws Exception {
        String xml = Files.readString(new File(
                "src/main/resources/org/nmox/studio/editor/emmet-keybindings.xml")
                .toPath());
        assertThat(xml)
                .as("the chord must name the action EXACTLY — a rename on"
                        + " either side silently kills the binding")
                .contains("<bind actionName=\"nmox-expand-abbreviation\" key=\"DA-E\"/>");
        String layer = Files.readString(new File(
                "src/main/resources/org/nmox/studio/editor/layer.xml").toPath());
        assertThat(layer.split("emmet-keybindings\\.xml", -1).length - 1)
                .as("the binding file is registered under BOTH markup mimes"
                        + " (html + x-ng-template), each as name + url")
                .isGreaterThanOrEqualTo(4);
    }
}
