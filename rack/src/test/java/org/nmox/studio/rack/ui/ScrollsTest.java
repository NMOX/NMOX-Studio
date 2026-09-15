package org.nmox.studio.rack.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A mirrored view opens where its reader starts (v2.162.0): the forge's
 * Hebrew and Arabic Task Rack pictures showed every device face cut off,
 * because a right-to-left rack wider than its viewport opened at the far
 * end. Mutation-proven: a rule that always answers the minimum fails
 * {@code mirroredViewStartsAtTheFarSide} by name.
 */
class ScrollsTest {

    @Test
    @DisplayName("a mirrored view starts at the far side; a left-to-right one at the minimum")
    void mirroredViewStartsAtTheFarSide() {
        // a 2000px view in a 500px viewport
        assertThat(Scrolls.logicalStart(false, 0, 2_000, 500))
                .as("right-to-left starts at the rightmost column")
                .isEqualTo(1_500);
        assertThat(Scrolls.logicalStart(true, 0, 2_000, 500))
                .as("left-to-right starts at the left edge")
                .isZero();
    }

    @Test
    @DisplayName("a view no wider than its viewport has nowhere to go")
    void narrowViewStaysAtTheMinimum() {
        assertThat(Scrolls.logicalStart(false, 0, 400, 500)).isZero();
        assertThat(Scrolls.logicalStart(false, 0, 500, 500)).isZero();
        assertThat(Scrolls.logicalStart(true, 0, 400, 500)).isZero();
    }

    @Test
    @DisplayName("a non-zero minimum is still the floor")
    void honoursTheModelsMinimum() {
        assertThat(Scrolls.logicalStart(false, 100, 300, 500)).isEqualTo(100);
        assertThat(Scrolls.logicalStart(true, 100, 2_000, 500)).isEqualTo(100);
    }

    @Test
    @DisplayName("the rack window asks for it on every show")
    void theRackWindowAsksForIt() throws Exception {
        // a rule with green tests and no call site is a payload without a
        // gate (the v1.321.0 law): the window must really ask
        Path window = Path.of("src/main/java/org/nmox/studio/rack/RackTopComponent.java");
        assertThat(window).isRegularFile();
        String source = Files.readString(window);
        assertThat(Pattern.compile("(?m)^\\s*(\\(\\) ->\\s*)?(\\w+\\.)*Scrolls\\.toLogicalStart\\(").matcher(source).find())
                .as("RackTopComponent calls Scrolls.toLogicalStart")
                .isTrue();
        assertThat(source).contains("protected void componentShowing()");
    }
}
