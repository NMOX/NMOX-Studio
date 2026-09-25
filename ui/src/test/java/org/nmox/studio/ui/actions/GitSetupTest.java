package org.nmox.studio.ui.actions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Team ▸ Use NMOX Studio with Git… (3.2.0): the settings it writes are
 * exactly the ones `nmox --help` and the guide print, and the dialog shows
 * what each replaces.
 */
class GitSetupTest {

    @Test
    @DisplayName("the six settings, in order, the tools' variables left for git to fill")
    void settings() {
        assertThat(GitSetup.settings()).containsExactly(
                Map.entry("core.editor", "nmox -w"),
                Map.entry("diff.tool", "nmox"),
                Map.entry("difftool.nmox.cmd", "nmox -w -d \"$LOCAL\" \"$REMOTE\""),
                Map.entry("merge.tool", "nmox"),
                Map.entry("mergetool.nmox.cmd", "nmox -w \"$MERGED\""),
                // nmox -w exits 0 either way: git must judge by whether the file was saved
                Map.entry("mergetool.nmox.trustExitCode", "false"));
    }

    @Test
    @DisplayName("each setting is one argv of fixed words and the value as one argument, never a shell line")
    void argv() {
        assertThat(GitSetup.setCommand("difftool.nmox.cmd", "nmox -w -d \"$LOCAL\" \"$REMOTE\""))
                .containsExactly("git", "config", "--global", "difftool.nmox.cmd", "nmox -w -d \"$LOCAL\" \"$REMOTE\"");
        assertThat(GitSetup.getCommand("core.editor")).containsExactly("git", "config", "--global", "--get", "core.editor");
    }

    @Test
    @DisplayName("the copied lines are single-quoted so the shell that pastes them leaves $LOCAL for git")
    void shellLines() {
        assertThat(GitSetup.shellLines()).containsExactly(
                "git config --global core.editor 'nmox -w'",
                "git config --global diff.tool 'nmox'",
                "git config --global difftool.nmox.cmd 'nmox -w -d \"$LOCAL\" \"$REMOTE\"'",
                "git config --global merge.tool 'nmox'",
                "git config --global mergetool.nmox.cmd 'nmox -w \"$MERGED\"'",
                "git config --global mergetool.nmox.trustExitCode 'false'");
    }

    @Test
    @DisplayName("the dialog names what each setting is now, or that it is not set")
    void describe() {
        Map<String, String> now = new HashMap<>();
        now.put("core.editor", "vim");
        String text = GitSetupAction.describe(now);
        assertThat(text).contains("core.editor = nmox -w\n    now: vim\n")
                .contains("diff.tool = nmox\n    now: not set\n");
        assertThat(List.of(text.split("\n"))).hasSize(2 * GitSetup.settings().size());
    }

    @Test
    @DisplayName("the help text all three launchers print carries every setting the dialog applies")
    void helpAgrees() throws Exception {
        String linux = java.nio.file.Files.readString(java.nio.file.Path.of("..", "packaging", "linux", "nmox"));
        assertThat(linux).contains("git config --global core.editor \"nmox -w\"")
                .contains("git config --global diff.tool nmox")
                .contains("git config --global difftool.nmox.cmd 'nmox -w -d \"$LOCAL\" \"$REMOTE\"'");
        // derived, so a setting added to one home and not the other fails here
        // (TerminalCommandGateTest holds the three launchers to one text)
        for (Map.Entry<String, String> s : GitSetup.settings().entrySet()) {
            String line = "  git config --global " + s.getKey() + " ";
            String v = s.getValue();
            assertThat(linux).as(s.getKey()).containsAnyOf(line + v + "\n",
                    line + "\"" + v + "\"\n", line + "'" + v + "'\n");
        }
    }

    @Test
    @org.junit.jupiter.api.DisplayName("a git that already uses NMOX Studio is told so, and Apply is not offered")
    void alreadySet() {
        java.util.Map<String, String> now = new java.util.HashMap<>(GitSetup.settings());
        org.assertj.core.api.Assertions.assertThat(GitSetupAction.alreadySet(now)).isTrue();
        now.put("diff.tool", "vimdiff");
        org.assertj.core.api.Assertions.assertThat(GitSetupAction.alreadySet(now)).isFalse();
        org.assertj.core.api.Assertions.assertThat(GitSetupAction.alreadySet(java.util.Map.of())).isFalse();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("Apply sets every setting in order, says so, and stops at git's first refusal in git's own words")
    void applyRunsAndStops() {
        var savedRunner = GitSetupAction.runner;
        var savedStatus = GitSetupAction.status;
        java.util.List<java.util.List<String>> ran = new java.util.ArrayList<>();
        java.util.List<String> said = new java.util.ArrayList<>();
        try {
            GitSetupAction.status = said::add;
            GitSetupAction.runner = argv -> {
                ran.add(argv);
                return new org.nmox.studio.core.process.ProcessSupport.BoundedResult(0, "", "", false, false);
            };
            GitSetupAction.apply();
            org.assertj.core.api.Assertions.assertThat(ran).hasSize(GitSetup.settings().size());
            org.assertj.core.api.Assertions.assertThat(ran.get(0))
                    .isEqualTo(GitSetup.setCommand("core.editor", "nmox -w"));
            org.assertj.core.api.Assertions.assertThat(said).containsExactly(Bundle.GitSetupAction_applied());
            ran.clear();
            said.clear();
            GitSetupAction.runner = argv -> {
                ran.add(argv);
                return new org.nmox.studio.core.process.ProcessSupport.BoundedResult(
                        ran.size() == 2 ? 5 : 0, "", "error: could not lock config file\n", false, false);
            };
            GitSetupAction.apply();
            org.assertj.core.api.Assertions.assertThat(ran).as("stops at the refusal").hasSize(2);
            org.assertj.core.api.Assertions.assertThat(said).containsExactly(
                    Bundle.GitSetupAction_failed("diff.tool", "error: could not lock config file"));
            said.clear();
            GitSetupAction.runner = argv -> null;
            GitSetupAction.apply();
            org.assertj.core.api.Assertions.assertThat(said).containsExactly(
                    Bundle.GitSetupAction_failed("core.editor", Bundle.GitSetupAction_noGit()));
        } finally {
            GitSetupAction.runner = savedRunner;
            GitSetupAction.status = savedStatus;
        }
    }
}
