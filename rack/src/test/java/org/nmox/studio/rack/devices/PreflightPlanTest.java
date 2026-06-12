package org.nmox.studio.rack.devices;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.devices.PreflightPlan.Check;
import org.nmox.studio.rack.devices.PreflightPlan.Pass;

import static org.assertj.core.api.Assertions.assertThat;

/** The checklist planner: what the machine checks before you ship. */
class PreflightPlanTest {

    @TempDir
    Path dir;

    @Test
    @DisplayName("A node project with everything gets the full list, in order")
    void fullNodeChecklist() throws Exception {
        Files.createDirectory(dir.resolve(".git"));
        Files.writeString(dir.resolve("package.json"), """
                {"scripts":{"test":"vitest run","build":"vite build"}}
                """);
        Files.writeString(dir.resolve("eslint.config.mjs"), "export default []");

        List<Check> checks = PreflightPlan.forProject(dir.toFile());
        assertThat(checks).extracting(Check::name)
                .containsExactly("GIT CLEAN", "TESTS", "BUILD", "LINT", "AUDIT");
        assertThat(checks.get(0).pass()).isEqualTo(Pass.EMPTY_OUTPUT);
        assertThat(checks.get(4).soft()).as("audit is advisory").isTrue();
    }

    @Test
    @DisplayName("Checks the project cannot satisfy are never planned")
    void plansOnlyWhatExists() throws Exception {
        Files.writeString(dir.resolve("package.json"), "{\"scripts\":{}}");
        List<Check> checks = PreflightPlan.forProject(dir.toFile());
        assertThat(checks).extracting(Check::name)
                .doesNotContain("GIT CLEAN", "TESTS", "BUILD", "LINT")
                .contains("AUDIT");
    }

    @Test
    @DisplayName("An empty directory has nothing to verify")
    void emptyDirEmptyList() {
        assertThat(PreflightPlan.forProject(dir.toFile())).isEmpty();
    }

    @Test
    @DisplayName("Pass criteria: exit code vs must-print-nothing")
    void passCriteria() {
        Check exit = new Check("TESTS", List.of("x"), Pass.EXIT_ZERO, false);
        assertThat(PreflightPlan.passed(exit, 0, "lots of output")).isTrue();
        assertThat(PreflightPlan.passed(exit, 1, "")).isFalse();

        Check empty = new Check("GIT CLEAN", List.of("x"), Pass.EMPTY_OUTPUT, false);
        assertThat(PreflightPlan.passed(empty, 0, "")).isTrue();
        assertThat(PreflightPlan.passed(empty, 0, " M file.js\n")).isFalse();
    }

    @Test
    @DisplayName("Failure hints surface the first useful output line, truncated")
    void failureHints() {
        assertThat(PreflightDevice.firstUsefulLine("\n> npm test\nFAIL src/a.test.js boom\n"))
                .isEqualTo("FAIL src/a.test.js boom");
        assertThat(PreflightDevice.firstUsefulLine("")).isEmpty();
        assertThat(PreflightDevice.firstUsefulLine("x".repeat(60))).hasSize(41);
    }

    @Test
    @DisplayName("ChangedSince finds fresh files, skips node_modules, caps the list")
    void changedSince(@TempDir Path root) throws Exception {
        long since = System.currentTimeMillis() - 60_000;
        Files.createDirectories(root.resolve("src"));
        Files.createDirectories(root.resolve("node_modules/dep"));
        File fresh = root.resolve("src/app.js").toFile();
        Files.writeString(fresh.toPath(), "x");
        File dep = root.resolve("node_modules/dep/i.js").toFile();
        Files.writeString(dep.toPath(), "x");
        File old = root.resolve("src/old.js").toFile();
        Files.writeString(old.toPath(), "x");
        old.setLastModified(since - 100_000);

        List<File> changed = org.nmox.studio.rack.engine.ChangedSince.scan(root.toFile(), since);
        assertThat(changed).contains(fresh).doesNotContain(dep, old);
    }
}
