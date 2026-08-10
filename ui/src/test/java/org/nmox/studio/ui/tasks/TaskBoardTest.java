package org.nmox.studio.ui.tasks;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Task Board's pure rules (v1.323.0). Every kanban behavior a user
 * relies on — where a move lands, what a WIP limit means, what a
 * column deletion takes with it — is pinned here, UI-free.
 */
class TaskBoardTest {

    @Test
    @DisplayName("the starter board is To Do / Doing / Done, empty")
    void starterShape() {
        TaskBoard b = TaskBoard.starter();
        assertThat(b.columns()).extracting(TaskBoard.Column::name)
                .containsExactly("To Do", "Doing", "Done");
        assertThat(b.cardCount()).isZero();
    }

    @Test
    @DisplayName("cards add at the end, edit in place, and remove by id")
    void cardCrud() {
        TaskBoard b = TaskBoard.starter();
        TaskBoard.Card first = b.addCard(0, "write tests", "");
        TaskBoard.Card second = b.addCard(0, "ship", "the gated pipeline");
        assertThat(b.column(0).cards()).extracting(TaskBoard.Card::title)
                .containsExactly("write tests", "ship");
        assertThat(b.editCard(second.id(), "ship it", "v1.323.0")).isTrue();
        assertThat(b.find(second.id()).title()).isEqualTo("ship it");
        assertThat(b.removeCard(first.id())).isTrue();
        assertThat(b.cardCount()).isEqualTo(1);
        // blank titles are refused at add and at edit
        assertThat(b.addCard(0, "   ", "")).isNull();
        assertThat(b.editCard(second.id(), "", "x")).isFalse();
    }

    @Test
    @DisplayName("moves land where the gesture meant: clamped, order kept")
    void movesClampAndOrder() {
        TaskBoard b = TaskBoard.starter();
        TaskBoard.Card a = b.addCard(0, "a", "");
        TaskBoard.Card c = b.addCard(0, "c", "");
        b.addCard(1, "x", "");
        // to another column, index far past the end -> lands at the end
        assertThat(b.moveCard(a.id(), 1, Integer.MAX_VALUE)).isTrue();
        assertThat(b.column(1).cards()).extracting(TaskBoard.Card::title)
                .containsExactly("x", "a");
        // within a column to index 0
        assertThat(b.moveCard(a.id(), 1, 0)).isTrue();
        assertThat(b.column(1).cards()).extracting(TaskBoard.Card::title)
                .containsExactly("a", "x");
        // a NEGATIVE index clamps to the front — this input is the ONLY
        // one where the clamp's max(0,·) half diverges from a bare
        // min(·,size), and the first mutation proof SURVIVED without it
        // (fallbacks and clamps are the usual maskers)
        assertThat(b.moveCard(a.id(), 1, -5)).isTrue();
        assertThat(b.column(1).cards().get(0).title()).isEqualTo("a");
        // unknown card or column refuses without side effects
        assertThat(b.moveCard("nope", 0, 0)).isFalse();
        assertThat(b.moveCard(c.id(), 9, 0)).isFalse();
        assertThat(b.columnOf(c.id())).isZero();
    }

    @Test
    @DisplayName("WIP limits are advisory: overLimit reports, nothing blocks")
    void wipLimits() {
        TaskBoard b = TaskBoard.starter();
        b.setWipLimit(1, 1);
        b.addCard(1, "one", "");
        assertThat(b.column(1).overLimit()).isFalse();
        // the limit does NOT refuse the second card — a kanban limit is
        // a signal to the human, not a lock; the header turns red instead
        assertThat(b.addCard(1, "two", "")).isNotNull();
        assertThat(b.column(1).overLimit()).isTrue();
        b.setWipLimit(1, 0);
        assertThat(b.column(1).overLimit()).isFalse();
    }

    @Test
    @DisplayName("column ops: rename, reorder, and the last column survives")
    void columnOps() {
        TaskBoard b = TaskBoard.starter();
        assertThat(b.renameColumn(0, "Backlog")).isTrue();
        assertThat(b.moveColumn(0, 2)).isTrue();
        assertThat(b.columns()).extracting(TaskBoard.Column::name)
                .containsExactly("Doing", "Done", "Backlog");
        assertThat(b.removeColumn(0)).isTrue();
        assertThat(b.removeColumn(0)).isTrue();
        // a board with nowhere to put a card is not a board
        assertThat(b.removeColumn(0)).isFalse();
        assertThat(b.columnCount()).isEqualTo(1);
        assertThat(b.addColumn("  ", 0)).isFalse();
    }

    @Test
    @DisplayName("JSON round-trips byte-stable: order, notes, WIP, ids")
    void jsonRoundTrip() {
        TaskBoard b = TaskBoard.starter();
        b.setWipLimit(1, 2);
        b.addCard(0, "títle with ünïcode", "line one\nline two");
        b.addCard(1, "<html><img src=x>", "hostile title stays text");
        String json = b.toJson();
        TaskBoard back = TaskBoard.fromJson(json);
        assertThat(back.toJson())
                .as("generate(parse(x)) must be byte-identical — the round"
                        + " trip is the persistence contract")
                .isEqualTo(json);
        assertThat(back.column(1).wipLimit()).isEqualTo(2);
        assertThat(back.column(1).cards().get(0).title())
                .isEqualTo("<html><img src=x>");
    }

    @Test
    @DisplayName("malformed input throws; a card without an id gets a fresh one")
    void parseEdges() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> TaskBoard.fromJson("not json"))
                .isInstanceOf(RuntimeException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> TaskBoard.fromJson("{\"columns\":[]}"))
                .as("a board needs at least one column")
                .isInstanceOf(RuntimeException.class);
        TaskBoard b = TaskBoard.fromJson(
                "{\"columns\":[{\"name\":\"A\",\"cards\":[{\"title\":\"t\"}]}]}");
        assertThat(b.column(0).cards().get(0).id()).isNotBlank();
    }

    @Test
    @DisplayName("duplicate ids in the file become distinct cards (git-merge safety)")
    void duplicateIdsAreSeparated() {
        // The board file is checked in, so a merge resolved by KEEPING BOTH
        // sides — or a hand copy-paste of a card block, which the parser
        // deliberately tolerates — produces two cards carrying one id.
        // Ids are internal identity, not user data (see fromJson), so the
        // second occurrence must get a fresh one; otherwise deleting one
        // card deletes BOTH (removeCard removes every match in a column)
        // and editing one appears to do nothing to its twin.
        String json = """
            {"version":1,"columns":[{"name":"To Do","cards":[
              {"id":"same","title":"ours","created":1},
              {"id":"same","title":"theirs","created":2}]}]}
            """;
        TaskBoard b = TaskBoard.fromJson(json);
        assertThat(b.column(0).cards()).extracting(TaskBoard.Card::title)
                .as("both cards survive the merge")
                .containsExactly("ours", "theirs");
        String a = b.column(0).cards().get(0).id();
        String c = b.column(0).cards().get(1).id();
        assertThat(a).as("the ids are now distinct").isNotEqualTo(c);
        assertThat(b.removeCard(a)).isTrue();
        assertThat(b.column(0).cards()).extracting(TaskBoard.Card::title)
                .as("deleting one card takes exactly one card")
                .containsExactly("theirs");
    }

    @Test
    @DisplayName("an empty id string is treated as absent, not as a shared id")
    void blankIdsAreSeparated() {
        String json = """
            {"version":1,"columns":[{"name":"To Do","cards":[
              {"id":"","title":"one"},{"id":"","title":"two"}]}]}
            """;
        TaskBoard b = TaskBoard.fromJson(json);
        assertThat(b.column(0).cards().get(0).id())
                .isNotBlank()
                .isNotEqualTo(b.column(0).cards().get(1).id());
    }
}
