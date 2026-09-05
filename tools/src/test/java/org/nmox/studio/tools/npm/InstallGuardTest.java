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
        // ids carry the platform's own absolute path (a drive-rooted one on
        // Windows — the first gate of v2.72.0 went red there on a "/tmp" literal)
        String shop = p.getAbsolutePath();
        String sibling = new File("/tmp/shop-two").getAbsolutePath();
        assertThat(InstallGuard.installing(p, List.of())).isFalse();
        assertThat(InstallGuard.installing(p, List.of(run("project-setup:" + shop + "#1")))).isTrue();
        assertThat(InstallGuard.installing(p, List.of(run("experiment-setup:" + shop + "#7")))).isTrue();
        assertThat(InstallGuard.installing(p, List.of(run("project-setup:" + sibling + "#1"))))
                .as("a sibling whose path merely starts the same").isFalse();
        assertThat(InstallGuard.installing(p, List.of(run("npm-run:" + shop + "#1"))))
                .as("a running script is not an install").isFalse();
        assertThat(InstallGuard.message(p)).contains("shop").contains("■");
    }

    @Test
    @DisplayName("needsInstall: declared dependencies with no node_modules; a bare project, an installed one, or a malformed manifest never (v2.73.0)")
    void needsInstall(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        File d = dir.toFile();
        assertThat(InstallGuard.needsInstall(d)).as("no package.json").isFalse();
        Files.writeString(dir.resolve("package.json"), "{\"name\":\"x\",\"scripts\":{\"start\":\"node a.js\"}}");
        assertThat(InstallGuard.needsInstall(d)).as("nothing declared").isFalse();
        Files.writeString(dir.resolve("package.json"), "{\"name\":\"x\",\"dependencies\":{}}");
        assertThat(InstallGuard.needsInstall(d)).as("an empty dependencies object").isFalse();
        Files.writeString(dir.resolve("package.json"), "{\"name\":\"x\",\"dependencies\":{\"express\":\"^5\"}}");
        assertThat(InstallGuard.needsInstall(d)).as("declared, uninstalled").isTrue();
        Files.writeString(dir.resolve("package.json"), "{\"name\":\"x\",\"devDependencies\":{\"vitest\":\"^3\"}}");
        assertThat(InstallGuard.needsInstall(d)).as("devDependencies count too").isTrue();
        Files.createDirectories(dir.resolve("node_modules"));
        assertThat(InstallGuard.needsInstall(d)).as("installed").isFalse();
        Files.writeString(dir.resolve("package.json"), "{not json");
        assertThat(InstallGuard.needsInstall(d)).as("malformed is not this wall").isFalse();
        assertThat(InstallGuard.needsInstallMessage(d)).contains(d.getName()).contains("NPM Explorer ▸ Install");
    }

    @Test
    @DisplayName("both lanes consult the guard before they spawn (source law)")
    void bothLanesConsultTheGuard() throws Exception {
        for (String f : List.of("NpmService.java", "WebProjectActionProvider.java")) {
            String src = Files.readString(Path.of("src/main/java/org/nmox/studio/tools/npm/" + f));
            int guard = src.indexOf("InstallGuard.installing(");
            int wall = src.indexOf("InstallGuard.needsInstall(");
            int spawn = src.indexOf("CommandExecutor.run(");
            assertThat(guard).as(f + " consults the guard").isPositive();
            assertThat(guard).as(f + ": before the spawn").isLessThan(spawn);
            assertThat(wall).as(f + " consults the third wall, before the spawn (v2.73.0)").isPositive().isLessThan(spawn);
        }
    }
}
