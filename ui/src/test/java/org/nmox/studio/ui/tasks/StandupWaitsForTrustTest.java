package org.nmox.studio.ui.tasks;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Standup's {@code git log} does not run in a folder nobody has trusted
 * (3.5.7): git reads the repository's own configuration, which can name a
 * program to run. The report goes without its Commits section, as it does
 * where there is no repository at all.
 */
class StandupWaitsForTrustTest {

    @Test
    @DisplayName("the git log is behind the trust question, in the same expression that would run it")
    void theLogIsGated() throws Exception {
        String src = Files.readString(Path.of("src/main/java/org/nmox/studio/ui/tasks/TasksTopComponent.java"),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
        int standup = src.indexOf("private void showStandup()");
        assertThat(standup).isPositive();
        String body = src.substring(standup, src.indexOf("\n    }\n", standup));
        // 3.5.13: the question is about the REPOSITORY's root (gitMayRun), not the
        // board's own folder: a trusted project inside an untrusted repository
        // had run git log under that repository's config
        int gate = body.indexOf("WorkspaceTrust.gitMayRun(dir) ? null");
        int spawn = body.indexOf("ProcessSupport.runBounded(");
        assertThat(gate).as("the Standup asks whether git may run in this folder's repository").isPositive();
        assertThat(body).as("and not whether the folder alone is trusted").doesNotContain("WorkspaceTrust.isTrusted(dir)");
        assertThat(spawn).as("before the one place it runs git").isGreaterThan(gate);
        assertThat(body.indexOf("ProcessSupport.runBounded(", spawn + 1)).as("the one place").isNegative();
        assertThat(body).as("and a refusal is an absent section, not an error").contains("if (r != null && r.exitCode() == 0)");
    }
}
