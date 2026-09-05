package org.nmox.studio.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ⌥⌘. stops every running command (v2.72.0): the chord rides all five
 * keymap profiles (KeymapProfileParityTest owns parity; this pins the
 * TARGET), and the target is the tools module's real action instance —
 * a shadow at a path no module registers is a dead chord (the v1.38.1
 * class).
 */
class StopChordTest {

    @Test
    @DisplayName("DA-PERIOD points at the ■ in every profile, and the ■ is registered where it points")
    void chordReachesTheStopAction() throws Exception {
        String layer = Files.readString(Path.of("src/main/resources/org/nmox/studio/ui/layer.xml"));
        String target = "Actions/Run/org-nmox-studio-tools-npm-StopRunAction.instance";
        for (String prof : List.of("NetBeans", "Eclipse", "Emacs", "Idea", "NetBeans55")) {
            int folder = layer.indexOf("<folder name=\"" + prof + "\">");
            int end = layer.indexOf("</folder>", folder);
            int chord = layer.indexOf("<file name=\"DA-PERIOD.shadow\">", folder);
            // bounded to THIS profile's folder: an unbounded search found the
            // next profile's chord and passed with Emacs empty (the first
            // mutant survived it; the parity gate caught what this did not)
            assertThat(chord).as(prof + " carries the chord inside its own folder")
                    .isGreaterThan(folder).isLessThan(end);
            assertThat(layer.substring(chord, chord + 400)).as(prof + ": the chord's target").contains(target);
        }
        // the target is pinned against the tools module's SOURCE annotation:
        // in the reactor ui builds BEFORE tools, so its generated layer does
        // not exist on a clean build (the first gate of v2.72.0 went red on
        // all three lanes reading it — a stale local build had supplied it)
        String action = Files.readString(Path.of("../tools/src/main/java/org/nmox/studio/tools/npm/StopRunAction.java"));
        assertThat(action).as("the ■ is registered where the chord points (category + id → the instance path)")
                .contains("@ActionID(category = \"Run\", id = \"org.nmox.studio.tools.npm.StopRunAction\")");
    }
}
