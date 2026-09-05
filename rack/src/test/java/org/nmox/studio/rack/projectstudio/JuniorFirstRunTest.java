package org.nmox.studio.rack.projectstudio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The first-fifteen-minutes laws (v1.210.0), from walking the shipped app
 * as a junior web developer would: create a project, press Run, see the
 * page. Each test pins one place where that walk used to dead-end in
 * silence.
 */
class JuniorFirstRunTest {

    private static String read(String relative) throws IOException {
        return Files.readString(Path.of(relative), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("a project the wizard scaffolds is trusted — no malware prompt for our own files")
    void scaffoldedProjectsAreTrusted() throws IOException {
        String src = read("src/main/java/org/nmox/studio/rack/projectstudio/NewProjectDialog.java");
        assertThat(src)
                .as("every byte here came from our own template seconds ago; asking "
                        + "'do you trust this folder?' — with Keep Safe as the default "
                        + "button — left the first Run doing nothing at all")
                .contains("WorkspaceTrust.trust(dir)");
    }

    @Test
    @DisplayName("the same pre-trust the other two generators have always done")
    void matchesExperimentAndLearningSpacePrecedent() throws IOException {
        assertThat(read("src/main/java/org/nmox/studio/rack/projectstudio/Experiments.java"))
                .contains("WorkspaceTrust.trust");
        assertThat(read("src/main/java/org/nmox/studio/rack/projectstudio/LearningSpace.java"))
                .contains("WorkspaceTrust.trust");
        // ...and now the third generator, which was the odd one out
        assertThat(read("src/main/java/org/nmox/studio/rack/projectstudio/NewProjectDialog.java"))
                .contains("WorkspaceTrust.trust");
    }

    @Test
    @DisplayName("a failed dependency install is reported, not swallowed by empty callbacks")
    void installFailureIsReported() throws IOException {
        String src = read("src/main/java/org/nmox/studio/rack/projectstudio/NewProjectDialog.java");
        // the exit callback must actually do something
        assertThat(src)
                .as("both callbacks used to be empty lambdas, so a failed install "
                        + "(no Node on PATH) was completely invisible")
                .contains("reportInstall(pm, code);");
        assertThat(src).as("a user's Stop reads stopped, not failed (v2.73.0)")
                .contains("LiveRuns.wasStoppedByUser(runId)");
        // v2.71.0: the exit callback ALSO withdraws the run from the ■ —
        // the report call is what this gate exists for, so it is what it reads
        // and the failure message must name where to look
        assertThat(src)
                .as("a beginner needs the two places that hold the answer")
                .contains("Output ▸ Project Setup")
                .contains("Tools ▸ Environment Doctor");
    }

    @Test
    @DisplayName("a device with no command for the toolchain SAYS so instead of ignoring the click")
    void noCommandRefusalSpeaks() throws IOException {
        String src = read("src/main/java/org/nmox/studio/rack/devices/CommandDevice.java");
        int idx = src.indexOf("if (command == null || command.isEmpty())");
        assertThat(idx).as("the null-command choke point exists").isGreaterThan(0);
        String branch = src.substring(idx, Math.min(src.length(), idx + 1400));
        assertThat(branch)
                .as("two dozen call sites comment this as 'CHECK greys' / 'IGNITION "
                        + "greys', but nothing greyed and nothing spoke — the button "
                        + "stayed lit and the click did nothing")
                .contains("statusLcd.setText(noCommandReason())");
        assertThat(src)
                .as("the reason names the device and the toolchain")
                .contains("\"NO \" + getTitle() + \" VERB FOR \"");
    }
}
