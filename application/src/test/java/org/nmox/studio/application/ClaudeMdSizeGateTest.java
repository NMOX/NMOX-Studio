package org.nmox.studio.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code CLAUDE.md} stays small enough to be loaded.
 *
 * <p>The file is read into every coding-agent session as project
 * instructions, and the tool that reads it stops at 150,000 characters.
 * On 2026-11-19 it held 626,700: each ship had put its status paragraph
 * above the previous ones on a single line (315,000 characters) and added
 * a line to a version history (294,000 more), so for months the sections a
 * session needs, the working method and the build commands among them,
 * sat behind a warning that the file was over the limit. Nothing
 * failed; the file only stopped doing its job.
 *
 * <p>The history lives in {@code docs/engineering/status-history.md} now.
 * Two laws keep it there: a budget well under the limit, so the failure
 * arrives while the fix is one paragraph, and the mechanism itself, one
 * status paragraph that is replaced and never stacked.
 */
class ClaudeMdSizeGateTest {

    private static final Path REPO = Path.of("..").toAbsolutePath().normalize();
    private static final Path CLAUDE_MD = REPO.resolve("CLAUDE.md");
    private static final Path HISTORY = REPO.resolve(Path.of("docs", "engineering", "status-history.md"));

    /** The reader's limit is 150,000; the budget leaves room to notice. */
    static final int BUDGET = 60_000;

    @Test
    @DisplayName("CLAUDE.md is under its budget, far below the 150,000 characters at which it stops loading")
    void staysUnderBudget() throws IOException {
        String text = Files.readString(CLAUDE_MD, StandardCharsets.UTF_8);
        assertThat(text.length())
                .as("CLAUDE.md holds %d characters. Move what is history to %s (CHANGELOG.md is the full record) "
                        + "rather than raising the budget: the file is loaded whole into every session",
                        text.length(), REPO.relativize(HISTORY))
                .isLessThan(BUDGET);
    }

    @Test
    @DisplayName("there is one status paragraph: a ship replaces it, and the outgoing one goes to the history file")
    void oneStatusParagraph() throws IOException {
        String text = Files.readString(CLAUDE_MD, StandardCharsets.UTF_8);
        assertThat(count(text, "**Status**: shipping (v"))
                .as("status paragraphs in CLAUDE.md").isEqualTo(1);
        assertThat(count(text, "**Before that"))
                .as("an earlier release's status stacked under the current one: this is how the file grew to "
                        + "four times its limit. Put it at the top of %s instead", REPO.relativize(HISTORY))
                .isZero();
        assertThat(HISTORY).as("the file CLAUDE.md sends its history to").isRegularFile();
        assertThat(text).as("and CLAUDE.md says where it went").contains("docs/engineering/status-history.md");
    }

    private static int count(String text, String needle) {
        int n = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) {
            n++;
        }
        return n;
    }
}
