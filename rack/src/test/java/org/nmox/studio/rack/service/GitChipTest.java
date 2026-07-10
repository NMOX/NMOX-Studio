package org.nmox.studio.rack.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The git chip's decision half, headless: visibility, label wording,
 * the aim equality guard, and — the part the v1.38.0 law leans on —
 * the boot-guard state machine that keeps `git status` from ever
 * spawning before an aim lands on a real repository. Fixtures are
 * hand-built .git directories (GitFacts reads files, not processes),
 * so no git binary is needed anywhere here.
 */
class GitChipTest {

    @TempDir
    Path dir;

    private Path repo(String name, String headContent) throws Exception {
        Path gitDir = dir.resolve(name).resolve(".git");
        Files.createDirectories(gitDir);
        Files.writeString(gitDir.resolve("HEAD"), headContent, StandardCharsets.UTF_8);
        return dir.resolve(name);
    }

    @Test
    @DisplayName("hidden outside a repo: no label, and no process may run")
    void hiddenForNonRepo() throws Exception {
        Path plain = dir.resolve("plain");
        Files.createDirectories(plain);
        GitChip chip = new GitChip();
        assertThat(chip.aim(plain.toFile())).isTrue();
        assertThat(chip.visible()).isFalse();
        assertThat(chip.label()).isNull();
        assertThat(chip.mayRunProcess()).isFalse();
    }

    @Test
    @DisplayName("a repo aim shows the branch — and ONLY the branch until a count is known")
    void branchLabelForRepo() throws Exception {
        Path repo = repo("proj", "ref: refs/heads/feature/chip\n");
        GitChip chip = new GitChip();
        chip.aim(repo.toFile());
        assertThat(chip.visible()).isTrue();
        assertThat(chip.label()).isEqualTo("⎇ feature/chip"); // no fake ±0
    }

    @Test
    @DisplayName("porcelain output appends ±N; a clean tree earns an honest ±0")
    void countFormatting() throws Exception {
        GitChip chip = new GitChip();
        chip.aim(repo("proj", "ref: refs/heads/main\n").toFile());
        chip.porcelain(" M pom.xml\n?? scratch.txt\n");
        assertThat(chip.label()).isEqualTo("⎇ main ±2");
        chip.porcelain("");
        assertThat(chip.label()).isEqualTo("⎇ main ±0");
    }

    @Test
    @DisplayName("re-aiming the same directory is a no-op — listener storms cost a compare")
    void aimEqualityGuard() throws Exception {
        Path repo = repo("proj", "ref: refs/heads/main\n");
        GitChip chip = new GitChip();
        assertThat(chip.aim(repo.toFile())).isTrue();
        chip.porcelain(" M a\n");
        assertThat(chip.aim(repo.toFile())).isFalse();
        assertThat(chip.label()).as("no-op aim keeps the known count").isEqualTo("⎇ main ±1");
    }

    @Test
    @DisplayName("a real re-aim forgets the old repo's count — never another project's ±N")
    void reAimResetsCount() throws Exception {
        GitChip chip = new GitChip();
        chip.aim(repo("a", "ref: refs/heads/main\n").toFile());
        chip.porcelain(" M x\n M y\n");
        chip.aim(repo("b", "ref: refs/heads/dev\n").toFile());
        assertThat(chip.label()).isEqualTo("⎇ dev");
    }

    @Test
    @DisplayName("boot-guard state machine: fresh → false, non-repo aim → false, repo aim → true")
    void bootGuardStateMachine() throws Exception {
        GitChip chip = new GitChip();
        assertThat(chip.mayRunProcess()).as("fresh chip, nothing aimed").isFalse();

        Path workspace = dir.resolve("NMOX"); // the fresh-launch default aim
        Files.createDirectories(workspace);
        chip.aim(workspace.toFile());
        assertThat(chip.mayRunProcess()).as("~/NMOX is not a repo — boot stays processless")
                .isFalse();

        chip.aim(repo("real", "ref: refs/heads/main\n").toFile());
        assertThat(chip.mayRunProcess()).as("only a repo aim arms the process path").isTrue();
    }

    @Test
    @DisplayName("refreshBranch follows a checkout made behind the IDE's back")
    void refreshBranchRereads() throws Exception {
        Path repo = repo("proj", "ref: refs/heads/main\n");
        GitChip chip = new GitChip();
        chip.aim(repo.toFile());
        Files.writeString(repo.resolve(".git/HEAD"),
                "ref: refs/heads/other\n", StandardCharsets.UTF_8);
        chip.refreshBranch();
        assertThat(chip.label()).isEqualTo("⎇ other");
    }

    // ---- the v1.38.0 source gate (the BootGateTest idiom) ----

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertThat(start).as(signature + " exists").isGreaterThan(0);
        int end = source.indexOf("\n        }", start);
        return source.substring(start, end);
    }

    @Test
    @DisplayName("source gate: the one runBounded site sits behind mayRunProcess, and nothing else forks")
    void processPathCannotFireBeforeAnAim() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/rack/service/GitStatusLine.java"),
                StandardCharsets.UTF_8);

        // exactly one spawn site in the whole chip
        assertThat(source.split("runBounded", -1).length - 1)
                .as("one and only one process launch site")
                .isEqualTo(1);

        // and that site opens with the boot guard, before any process code
        String refreshCount = method(source, "private void refreshCount()");
        assertThat(refreshCount).contains("if (!chip.mayRunProcess())");
        assertThat(refreshCount.indexOf("mayRunProcess"))
                .as("guard precedes the spawn")
                .isLessThan(refreshCount.indexOf("runBounded"));

        // the timer only pokes refreshCount (through RP) — no second path
        assertThat(method(source, "private void tick()"))
                .contains("refreshCount")
                .doesNotContain("runBounded");

        // wiring is symmetric and never spawns directly: addNotify only
        // subscribes + aims (the aim path funnels into the guarded method)
        assertThat(method(source, "public void addNotify()"))
                .contains("addListener(rackListener)")
                .doesNotContain("runBounded")
                .doesNotContain("poll.start()");
        assertThat(method(source, "public void removeNotify()"))
                .contains("removeListener(rackListener)")
                .contains("poll.stop()");
    }
}
