package org.nmox.studio.tools.npm;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A run that never started says so where the eye rests, and names the two places to look (v2.73.0). */
class LaunchFailureTest {

    @Test
    @DisplayName("the sentence names the run, its Output tab, and the Doctor")
    void sentence() {
        assertThat(LaunchFailure.status("Run — shop"))
                .isEqualTo("Run — shop didn't start — see Output ▸ Rack: Run — shop, or Tools ▸ Environment Doctor");
    }

    @Test
    @DisplayName("the ▶'s exit handler speaks it on exit -1, after the run leaves the ■")
    void wiredAtTheExitHandler() throws Exception {
        String src = Files.readString(Path.of("src/main/java/org/nmox/studio/tools/npm/WebProjectActionProvider.java"));
        int remove = src.indexOf("LiveRuns.remove(servingId);");
        int speak = src.indexOf("LaunchFailure.status(label)");
        assertThat(speak).isGreaterThan(remove);
        assertThat(src.substring(remove, speak)).contains("if (exit == -1)");
    }
}
