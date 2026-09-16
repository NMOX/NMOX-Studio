package org.nmox.studio.ui.tasks;

import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.util.DocsFixtures;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The board every language's Task Board, Overview and Standup pictures show.
 * Read back through the product's own loader, so a fixture the product could
 * not open fails here rather than as a blank picture in fourteen languages.
 */
class DocsTaskBoardTest {

    private static final String FIXTURES = """
            {"en": {"board": {
               "todo": ["Ship the offline page", "Name the empty states"],
               "doing": ["Cut the login round-trip", "Tune the cache"],
               "done": ["Translate the error catalogue"],
               "sprint": "Sprint 8", "epic": "Payment",
               "blockOwner": "Dana", "blockAction": "Ask design for the copy"}}}
            """;

    private TaskBoard staged(Path home) throws Exception {
        File dir = new DocsTaskBoard().stage(home.toFile(), FIXTURES, "en");
        assertThat(dir).isEqualTo(DocsFixtures.projectDir(home.toFile()));
        return TasksIO.load(dir);
    }

    @Test
    @DisplayName("the product's own starter columns hold the fixture's cards, every one under the epic")
    void cardsLandInTheStarterColumns(@TempDir Path home) throws Exception {
        TaskBoard board = staged(home);
        assertThat(board.columnCount()).isEqualTo(TasksIO.starterBoard().columnCount());
        assertThat(board.column(0).cards()).extracting(TaskBoard.Card::title)
                .containsExactly("Ship the offline page", "Name the empty states");
        assertThat(board.column(1).cards()).hasSize(2);
        assertThat(board.column(board.columnCount() - 1).cards()).extracting(TaskBoard.Card::title)
                .containsExactly("Translate the error catalogue");
        for (int c = 0; c < board.columnCount(); c++) {
            assertThat(board.column(c).cards()).allSatisfy(card -> assertThat(card.label()).isEqualTo("Payment"));
        }
    }

    @Test
    @DisplayName("the LAST to-do card is blocked, with the owner and the action that would unblock it")
    void lastTodoIsBlocked(@TempDir Path home) throws Exception {
        TaskBoard board = staged(home);
        TaskBoard.Card first = board.column(0).cards().get(0);
        TaskBoard.Card last = board.column(0).cards().get(1);
        assertThat(first.blocked()).isFalse();
        assertThat(last.blocked()).isTrue();
        assertThat(last.blockOwner()).isEqualTo("Dana");
        assertThat(last.blockAction()).isEqualTo("Ask design for the copy");
    }

    @Test
    @DisplayName("the one running clock is on the FIRST in-progress card")
    void clockRunsOnTheFirstDoingCard(@TempDir Path home) throws Exception {
        TaskBoard board = staged(home);
        TaskBoard.Card running = board.runningCard();
        assertThat(running).isNotNull();
        assertThat(running.title()).isEqualTo("Cut the login round-trip");
        assertThat(board.column(1).cards().get(1).clockedIn()).isFalse();
    }

    @Test
    @DisplayName("the done card carries a stamp and today sits inside the sprint — the burndown and Standup read both")
    void doneStampAndSprintWindow(@TempDir Path home) throws Exception {
        TaskBoard board = staged(home);
        long now = System.currentTimeMillis();
        assertThat(board.column(board.columnCount() - 1).cards().get(0).done()).isPositive();
        assertThat(board.hasSprint()).isTrue();
        assertThat(board.sprintName()).isEqualTo("Sprint 8");
        assertThat(board.sprintStart()).isLessThan(now);
        assertThat(board.sprintEnd()).isGreaterThan(now);
    }
}
