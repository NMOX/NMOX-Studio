package org.nmox.studio.rack.service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A repository nobody has trusted says so once when it is opened, and the
 * notice's click is the trust question for THAT repository (3.5.10).
 *
 * <p>3.5.7 left the question in the git chip's menu. A person opening their
 * own repository saw a branch with no count and nothing asking them
 * anything.
 */
class GitTrustNoticeTest {

    @TempDir
    Path dir;

    private final List<File> shown = new ArrayList<>();
    private final List<Runnable> clicks = new ArrayList<>();
    private final List<File> asked = new ArrayList<>();

    @BeforeEach
    void freshSession() {
        TrustNotices.forgetForTest();
        GitChip.trusted = folder -> false;
    }

    @AfterEach
    void theRealQuestionAgain() {
        GitChip.trusted = WorkspaceTrust::gitMayRun;
        System.clearProperty("nmox.shots.dir");
    }

    private File repo(String name) throws Exception {
        Path gitDir = dir.resolve(name).resolve(".git");
        Files.createDirectories(gitDir);
        Files.writeString(gitDir.resolve("HEAD"), "ref: refs/heads/main\n", StandardCharsets.UTF_8);
        return dir.resolve(name).toFile();
    }

    private boolean announce(GitChip chip) {
        return GitStatusLine.announceWaiting(chip, (root, click) -> {
            shown.add(root);
            clicks.add(click);
        }, asked::add);
    }

    @Test
    @DisplayName("an untrusted repository is announced once, by its root, and the click asks about that root")
    void announcedOnce() throws Exception {
        File repo = repo("mine");
        File inside = new File(repo, "src");
        Files.createDirectories(inside.toPath());
        GitChip chip = new GitChip();
        chip.aim(inside); // aimed below the root: the config that matters is the repository's

        assertThat(announce(chip)).isTrue();
        assertThat(shown).containsExactly(repo);
        assertThat(asked).as("nothing is asked until the notice is clicked").isEmpty();

        clicks.get(0).run();
        assertThat(asked).containsExactly(repo);

        assertThat(announce(chip)).as("the same repository again, this session").isFalse();
        assertThat(shown).hasSize(1);
    }

    @Test
    @DisplayName("the click asks about the repository the notice named, not the one aimed by then")
    void theClickKeepsItsRepository() throws Exception {
        File first = repo("first");
        File second = repo("second");
        GitChip chip = new GitChip();
        chip.aim(first);
        announce(chip);
        chip.aim(second);
        announce(chip);

        clicks.get(0).run();
        assertThat(asked).containsExactly(first);
    }

    @Test
    @DisplayName("nothing is announced for a trusted repository, for a folder that is not one, or in a documentation run")
    void quietWhereNothingWaits() throws Exception {
        File trusted = repo("trusted");
        GitChip.trusted = folder -> true;
        GitChip chip = new GitChip();
        chip.aim(trusted);
        assertThat(announce(chip)).isFalse();

        GitChip.trusted = folder -> false;
        File plain = dir.resolve("plain").toFile();
        Files.createDirectories(plain.toPath());
        chip.aim(plain);
        assertThat(announce(chip)).isFalse();

        System.setProperty("nmox.shots.dir", "a picture shows the product, not this machine's trust store");
        chip.aim(repo("pictured"));
        assertThat(announce(chip)).isFalse();
        assertThat(shown).isEmpty();
        System.clearProperty("nmox.shots.dir");
        assertThat(announce(chip)).as("the documentation run did not use the folder's one notice up").isTrue();
    }

    @Test
    @DisplayName("wiring: an aim that changed announces before the count, and the chip hears a grant only while it is in the status bar")
    void wiring() throws Exception {
        // comments stripped (3.5.13): each line below could be commented out and
        // still be found in the file, and a review named four mutants that lived so
        String src = org.nmox.studio.rack.GateSources.stripComments(Files.readString(
                Path.of("src/main/java/org/nmox/studio/rack/service/GitStatusLine.java"),
                StandardCharsets.UTF_8).replace("\r\n", "\n"));
        int onAim = src.indexOf("private void onAim() {");
        String body = src.substring(onAim, src.indexOf("\n        }\n", onAim));
        int announce = body.indexOf("announceWaiting(chip, NOTICE, GitStrip::askTrust);");
        assertThat(announce).as("the aim announces").isPositive();
        assertThat(body.indexOf("if (changed) {")).as("only when the aim changed").isBetween(0, announce);

        int add = src.indexOf("public void addNotify() {");
        assertThat(src.substring(add, src.indexOf("\n        }\n", add)))
                .contains("WorkspaceTrust.addGrantListener(onGrant);");
        int remove = src.indexOf("public void removeNotify() {");
        assertThat(src.substring(remove, src.indexOf("\n        }\n", remove)))
                .as("added and removed with the component: a status bar rebuilt does not leave a listener behind")
                .contains("WorkspaceTrust.removeGrantListener(onGrant);");

        assertThat(src).as("the folder's name reaches the notice as one line of ordinary characters")
                .contains("org.nmox.studio.core.util.PlainText.oneLine(root.getName(), 80)");
        int grant = src.indexOf("onGrant = dir -> RP.post(() -> {");
        assertThat(grant).as("a grant is acted on").isPositive();
        assertThat(src.substring(grant, src.indexOf("});", grant)))
                .as("it takes the count the chip was waiting for").contains("refreshCount();");
        int ask = src.indexOf("private void askTrust() {");
        assertThat(src.substring(ask, src.indexOf("\n        }\n", ask)))
                .as("the menu's row only asks: the count is the grant listener's, once (it ran git status twice)")
                .contains("WorkspaceTrust.requestTrust(root);").doesNotContain("refreshCount");
    }
}
