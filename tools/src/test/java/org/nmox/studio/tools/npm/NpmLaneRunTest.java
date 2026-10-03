package org.nmox.studio.tools.npm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A script on the NPM lane, answered with its exit: what a chain of VS
 * Code tasks needs to know before it starts the next one.
 */
class NpmLaneRunTest {

    @Test
    @DisplayName("a failure is its exit code, read from the lane's own message; a clean end is zero")
    void exitCodes() {
        assertThat(NpmLaneRun.exitOf(false, null)).isZero();
        assertThat(NpmLaneRun.exitOf(false, new RuntimeException("Command failed with exit code: 2\nOutput: x")))
                .isEqualTo(2);
        assertThat(NpmLaneRun.exitOf(false, new RuntimeException("Command failed with exit code: -1\nOutput: ")))
                .as("the tool was not found").isEqualTo(-1);
        assertThat(NpmLaneRun.exitOf(false, new RuntimeException("Command failed with exit code: 143\nOutput: ")))
                .isEqualTo(143);
        assertThat(NpmLaneRun.exitOf(false, new IllegalStateException("something else")))
                .as("a failure with no code is still a failure").isEqualTo(1);
        assertThat(NpmLaneRun.exitOf(false, new RuntimeException((String) null))).isEqualTo(1);
        assertThat(NpmLaneRun.exitOf(false, new RuntimeException("Command failed with exit code: 0\nOutput: ")))
                .as("a failure never reads as success").isEqualTo(1);
        assertThat(NpmLaneRun.exitOf(false,
                new RuntimeException("Command failed with exit code: 99999999999999999999\nOutput: "))).isEqualTo(1);
    }

    @Test
    @DisplayName("a run the lane refused behind one of its walls did not run: never exit 0")
    void wallIsNotSuccess() {
        assertThat(NpmLaneRun.exitOf(true, null)).isEqualTo(NpmLaneRun.NOT_RUN);
        assertThat(NpmLaneRun.NOT_RUN).isNotZero().isNotEqualTo(-1);
        assertThat(NpmLaneRun.exitOf(true, new RuntimeException("Command failed with exit code: 3\nOutput: ")))
                .as("it ran after all, and failed: the code").isEqualTo(3);
    }

    @Test
    @DisplayName("the message this class reads the code from is the one the lane writes")
    void theLaneStillWritesThatMessage() throws Exception {
        String lane = Files.readString(Path.of("src/main/java/org/nmox/studio/tools/npm/NpmService.java"),
                StandardCharsets.UTF_8);
        assertThat(lane).contains("\"Command failed with exit code: \" + exit");
    }
}
