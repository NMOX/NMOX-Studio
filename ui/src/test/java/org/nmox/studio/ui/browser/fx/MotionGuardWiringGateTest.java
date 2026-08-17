package org.nmox.studio.ui.browser.fx;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The v1.321.0 two-proof law for the Motion target guard: the seam's
 * rules are unit-proven in {@code MotionTargetGuardTest}; this gate
 * proves the CALL SITES exist — a guard with green tests that nothing
 * consults is a payload without a gate. Play and Scrub must consult
 * {@code retarget(...)}, Stop must consult {@code stopTarget(...)}.
 * Reads the committed source (folded to LF — the v2.14.0 windows
 * lesson) so deleting a wiring line fails the build by name.
 */
class MotionGuardWiringGateTest {

    @Test
    @DisplayName("Play, Scrub, and Stop all consult the target guard")
    void wiringExists() throws Exception {
        Path panel = Path.of("src/main/java/org/nmox/studio/ui/browser/fx/DevToolsPanel.java");
        String src = Files.readString(panel).replace("\r\n", "\n");

        String play = methodBody(src, "private void motionPlay()");
        String scrub = methodBody(src, "private void motionScrub(int percent)");
        String stop = methodBody(src, "private void motionStop()");

        assertThat(play).as("motionPlay clears the stale target before applying")
                .contains("motionGuard.retarget(");
        assertThat(scrub).as("motionScrub clears the stale target before applying")
                .contains("motionGuard.retarget(");
        assertThat(stop).as("motionStop stops what is PLAYING, not what is selected")
                .contains("motionGuard.stopTarget(");
    }

    /** The text from the signature to the next method-level brace close. */
    private static String methodBody(String src, String signature) {
        int at = src.indexOf(signature);
        assertThat(at).as("method exists: " + signature).isGreaterThanOrEqualTo(0);
        int depth = 0;
        for (int i = src.indexOf('{', at); i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return src.substring(at, i + 1);
                }
            }
        }
        return src.substring(at);
    }
}
