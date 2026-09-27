package org.nmox.studio.ui.tasks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A {@code sessionOwners} array whose length no longer matches
 * {@code sessions} — a merge grew one and not the other (3.4, question 1;
 * the hostile review).
 *
 * <p>Measured before the fix: every session on the card was read as the
 * READER's (the legacy rule for an unlabelled board), so Carol, who never
 * clocked the card, owned Alice's and Bob's running clocks; the parse-time
 * heal then closed one of them at zero credit as a stray of Carol's; and the
 * next save wrote no owners at all, so every later reader did the same.
 */
class SessionOwnersMismatchTest {

    private static final String BOARD = """
            {"version":1,"columns":[{"name":"Doing","cards":[{
              "id":"c1","title":"Payments","created":1,
              "sessions":[[1000,0],[2000,0]],
              "sessionOwners":["alice","bob","bob"]
            }]}]}
            """;

    private static TaskBoard.Card card(TaskBoard b) {
        return b.columns().get(0).cards().get(0);
    }

    @Test
    @DisplayName("mismatched owners are nobody's: not Carol's, not Alice's — and both running clocks stay running")
    void mismatchedOwnersNeverCloseATeammatesRunningClock() {
        TaskBoard read = TaskBoard.fromJson(BOARD, "carol");
        TaskBoard.Card card = card(read);
        assertThat(card.sessions("carol")).as("Carol never clocked this card").isEmpty();
        assertThat(card.sessions("alice")).as("which session is Alice's cannot be told").isEmpty();
        assertThat(card.clockedIn("carol")).isFalse();
        assertThat(card.allSessions()).as("two people's running clocks stay running")
                .hasSize(2).allSatisfy(s -> assertThat(s[1]).isZero());
        assertThat(TaskBoard.fromJson(BOARD, "alice").columns().get(0).cards().get(0).sessions("alice"))
                .as("never the reader's, whoever reads").isEmpty();
    }

    @Test
    @DisplayName("the owners that were read are written back as read, so a person can still repair them")
    void theOwnersReadAreWrittenBack() {
        TaskBoard read = TaskBoard.fromJson(BOARD, "carol");
        JSONObject saved = new JSONObject(read.toJson()).getJSONArray("columns").getJSONObject(0)
                .getJSONArray("cards").getJSONObject(0);
        assertThat(saved.getJSONArray(TaskBoard.SESSION_OWNERS).toList())
                .containsExactly("alice", "bob", "bob");
        assertThat(saved.getJSONArray("sessions").length()).isEqualTo(2);
    }

    @Test
    @DisplayName("a session clocked since is kept too, its owner written after the ones read")
    void aNewSessionIsAppended() {
        TaskBoard read = TaskBoard.fromJson(BOARD, "carol");
        assertThat(read.clockIn("c1", 10_000L, "carol")).isTrue();
        JSONObject saved = new JSONObject(read.toJson()).getJSONArray("columns").getJSONObject(0)
                .getJSONArray("cards").getJSONObject(0);
        assertThat(saved.getJSONArray(TaskBoard.SESSION_OWNERS).toList())
                .containsExactly("alice", "bob", "bob", "carol");
        assertThat(saved.getJSONArray("sessions").length()).isEqualTo(3);
    }

    @Test
    @DisplayName("a board from a newer NMOX Studio is shown as read, read-only, and says why")
    void newerBoardIsReadOnly(@TempDir Path dir) throws Exception {
        Locale before = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
        try {
            JSONObject newer = new JSONObject().put("version", TaskBoard.FORMAT + 1)
                    .put("swimlanes", new JSONArray().put("later"))
                    .put("columns", new JSONArray().put(new JSONObject().put("name", "Doing")
                            .put("cards", new JSONArray().put(new JSONObject().put("id", "c1")
                                    .put("title", "Kept").put("created", 1)))));
            Files.writeString(dir.resolve(TasksIO.FILENAME), newer.toString(2));
            TasksIO.LoadOutcome outcome = TasksIO.load(dir.toFile());
            assertThat(outcome.readOnly()).as("a save here would drop what the newer build added").isTrue();
            assertThat(outcome.newer()).isTrue();
            assertThat(card(outcome.board()).title()).as("the real board, not a stand-in").isEqualTo("Kept");
            assertThat(TasksTopComponent.readOnlyReason(outcome)).contains(TasksIO.FILENAME)
                    .contains("newer NMOX Studio").contains(String.valueOf(TaskBoard.FORMAT + 1));
        } finally {
            Locale.setDefault(before);
        }
    }
}
