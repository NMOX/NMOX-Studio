package org.nmox.studio.tools.npm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every lane the ■ knows also shows in the platform's progress bar with a
 * Cancel that IS the run's stop (v2.73.0): the NPM lane, the wizard's
 * install, the experiment's install — the ▶ had one since v1.2. Source law
 * across three modules (the application module would see all; this test
 * lives beside the lane and reads its siblings by relative path).
 */
class ProgressWiringTest {

    @Test
    @DisplayName("NPM lane, wizard install, experiment install: a progress handle started before the spawn, finished in the exit handler, Cancel stops through LiveRuns")
    void progressHandlesAtEverySetupSpawn() throws Exception {
        for (String f : List.of("src/main/java/org/nmox/studio/tools/npm/NpmService.java",
                "../rack/src/main/java/org/nmox/studio/rack/projectstudio/NewProjectDialog.java",
                "../ui/src/main/java/org/nmox/studio/ui/actions/NewExperimentAction.java")) {
            String src = Files.readString(Path.of(f));
            int create = src.indexOf("ProgressHandle.createHandle(");
            int spawn = src.indexOf("CommandExecutor.run(", create);
            int finish = src.indexOf(".finish();", spawn);
            assertThat(create).as(f + ": a handle is created").isPositive();
            assertThat(spawn).as(f + ": … before the spawn").isGreaterThan(create);
            assertThat(finish).as(f + ": … and finished after it (in the exit handler)").isGreaterThan(spawn);
            assertThat(src.substring(create, spawn)).as(f + ": Cancel is the run's stop").contains("LiveRuns.stop(runId)");
            // a handle created but never started paints nothing — the first
            // mutant (start dropped) survived the create/spawn/finish order alone
            assertThat(src.substring(create, spawn)).as(f + ": … and started before the spawn").contains(".start();");
        }
    }
}
