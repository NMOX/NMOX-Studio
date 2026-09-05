package org.nmox.studio.tools.npm;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.core.spi.LiveRuns;

import static org.assertj.core.api.Assertions.assertThat;

/** A run while the project's own install is live is refused, out loud (v2.72.0). */
class InstallGuardTest {

    private static LiveRuns.Run run(String id) {
        return new LiveRuns.Run(id, id, () -> { });
    }

    @Test
    @DisplayName("only THIS project's setup installs count: wizard or experiment, not another dir, not a plain run")
    void onlyOwnSetupInstalls() {
        File p = new File("/tmp/shop");
        assertThat(InstallGuard.installing(p, List.of())).isFalse();
        assertThat(InstallGuard.installing(p, List.of(run("project-setup:/tmp/shop#1")))).isTrue();
        assertThat(InstallGuard.installing(p, List.of(run("experiment-setup:/tmp/shop#7")))).isTrue();
        assertThat(InstallGuard.installing(p, List.of(run("project-setup:/tmp/shop-two#1"))))
                .as("a sibling whose path merely starts the same").isFalse();
        assertThat(InstallGuard.installing(p, List.of(run("npm-run:/tmp/shop#1"))))
                .as("a running script is not an install").isFalse();
        assertThat(InstallGuard.message(p)).contains("shop").contains("■");
    }

    @Test
    @DisplayName("both lanes consult the guard before they spawn (source law)")
    void bothLanesConsultTheGuard() throws Exception {
        for (String f : List.of("NpmService.java", "WebProjectActionProvider.java")) {
            String src = Files.readString(Path.of("src/main/java/org/nmox/studio/tools/npm/" + f));
            int guard = src.indexOf("InstallGuard.installing(");
            int spawn = src.indexOf("CommandExecutor.run(");
            assertThat(guard).as(f + " consults the guard").isPositive();
            assertThat(guard).as(f + ": before the spawn").isLessThan(spawn);
        }
    }
}
