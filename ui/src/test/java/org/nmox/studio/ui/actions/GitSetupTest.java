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
    @DisplayName("the three settings, in order, the difftool's variables left for git to fill")
    void settings() {
        assertThat(GitSetup.settings()).containsExactly(
                Map.entry("core.editor", "nmox -w"),
                Map.entry("diff.tool", "nmox"),
                Map.entry("difftool.nmox.cmd", "nmox -w -d \"$LOCAL\" \"$REMOTE\""));
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
                "git config --global difftool.nmox.cmd 'nmox -w -d \"$LOCAL\" \"$REMOTE\"'");
    }

    @Test
    @DisplayName("the dialog names what each setting is now, or that it is not set")
    void describe() {
        Map<String, String> now = new HashMap<>();
        now.put("core.editor", "vim");
        String text = GitSetupAction.describe(now);
        assertThat(text).contains("core.editor = nmox -w\n    now: vim\n")
                .contains("diff.tool = nmox\n    now: not set\n");
        assertThat(List.of(text.split("\n"))).hasSize(6);
    }

    @Test
    @DisplayName("the help text all three launchers print carries the same three settings")
    void helpAgrees() throws Exception {
        String linux = java.nio.file.Files.readString(java.nio.file.Path.of("..", "packaging", "linux", "nmox"));
        assertThat(linux).contains("git config --global core.editor \"nmox -w\"")
                .contains("git config --global diff.tool nmox")
                .contains("git config --global difftool.nmox.cmd 'nmox -w -d \"$LOCAL\" \"$REMOTE\"'");
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
}
