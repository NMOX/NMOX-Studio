package org.nmox.studio.ui.tasks;

import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The overview's editorial half (v2.5.0): the blocker triple's
 * lifecycle, epic labels and their derived legend, and the board-level
 * retro notes — the model and stats rules behind the Sprint-Studio
 * panels, pinned pure.
 */
class BoardEditorialTest {

    private static final long NOW = 1_786_795_200_000L;

    private static long daysAgo(int d) {
        return NOW - TimeUnit.DAYS.toMillis(d);
    }

    // ---- blocker lifecycle ----------------------------------------------

    @Test
    @DisplayName("Blocking stamps since ONCE; re-blocking updates the triple but keeps it")
    void blockStampsOnce() {
        TaskBoard b = TaskBoard.starter();
        TaskBoard.Card c = b.addCard(0, "stuck", "");
        assertThat(b.block(c.id(), "alice", "needs the cert")).isTrue();
        long since = c.blockedSince();
        assertThat(since).isPositive();
        assertThat(b.block(c.id(), "bob", "cert ordered")).isTrue();
        assertThat(c.blockOwner()).isEqualTo("bob");
        assertThat(c.blockAction()).isEqualTo("cert ordered");
        assertThat(c.blockedSince()).isEqualTo(since); // stuck since it first stuck
    }

    @Test
    @DisplayName("A blank unblock action is refused — the register must stay actionable")
    void blankActionRefused() {
        TaskBoard b = TaskBoard.starter();
        TaskBoard.Card c = b.addCard(0, "x", "");
        assertThat(b.block(c.id(), "alice", "   ")).isFalse();
        assertThat(c.blocked()).isFalse();
    }

    @Test
    @DisplayName("Unblock clears the WHOLE triple — owner, action, and since")
    void unblockClearsAll() {
        TaskBoard b = TaskBoard.starter();
        TaskBoard.Card c = b.addCard(0, "x", "");
        b.block(c.id(), "alice", "waiting on review");
        assertThat(b.unblock(c.id())).isTrue();
        assertThat(c.blocked()).isFalse();
        assertThat(c.blockOwner()).isEmpty();
        assertThat(c.blockedSince()).isZero();
    }

    // ---- persistence -----------------------------------------------------

    @Test
    @DisplayName("Label, blocker triple, and retro all round-trip through JSON")
    void editorialFieldsRoundTrip() {
        TaskBoard b = TaskBoard.starter();
        TaskBoard.Card c = b.addCard(0, "x", "");
        b.setLabel(c.id(), "auth");
        b.block(c.id(), "alice", "needs the cert");
        b.setRetro("Went well: shipping.\nBit us: flakes.");
        TaskBoard back = TaskBoard.fromJson(b.toJson());
        TaskBoard.Card rc = back.find(c.id());
        assertThat(rc.label()).isEqualTo("auth");
        assertThat(rc.blockOwner()).isEqualTo("alice");
        assertThat(rc.blockAction()).isEqualTo("needs the cert");
        assertThat(rc.blockedSince()).isEqualTo(c.blockedSince());
        assertThat(back.retro()).isEqualTo("Went well: shipping.\nBit us: flakes.");
    }

    @Test
    @DisplayName("A v2.4.0 file without the new keys loads clean — nothing blocked, no retro")
    void oldFilesLoadClean() {
        TaskBoard b = TaskBoard.starter();
        b.addCard(0, "plain", "");
        TaskBoard back = TaskBoard.fromJson(b.toJson()
                .replace("\"label\"", "\"ignored\"")); // no-op safety
        TaskBoard.Card rc = back.columns().get(0).cards().get(0);
        assertThat(rc.blocked()).isFalse();
        assertThat(rc.label()).isEmpty();
        assertThat(back.retro()).isEmpty();
    }

    // ---- stats -----------------------------------------------------------

    private static TaskBoard richBoard() {
        TaskBoard b = TaskBoard.starter();
        TaskBoard.Card oldBlock = b.addCard(0, "waiting on infra", "");
        TaskBoard.Card newBlock = b.addCard(1, "waiting on design", "");
        TaskBoard.Card doneBlock = b.addCard(0, "was stuck, now done", "");
        b.block(oldBlock.id(), "alice", "provision the droplet");
        b.block(newBlock.id(), "", "mockups due");
        b.block(doneBlock.id(), "carol", "irrelevant now");
        b.moveCard(doneBlock.id(), 2, 0); // finished — leaves the register
        b.setLabel(oldBlock.id(), "infra");
        b.setLabel(newBlock.id(), "design");
        TaskBoard.Card extra = b.addCard(0, "more infra", "");
        b.setLabel(extra.id(), "infra");
        return b;
    }

    @Test
    @DisplayName("The blocker register lists active blocks only — a finished card left it")
    void registerExcludesDone() {
        BoardStats s = BoardStats.of(richBoard(), NOW, ZoneOffset.UTC, 14, 5);
        assertThat(s.blockedCount()).isEqualTo(2);
        assertThat(s.blockers()).extracting(BoardStats.Blocker::title)
                .containsExactlyInAnyOrder("waiting on infra", "waiting on design");
        // a blank owner reads as such, never invented
        assertThat(s.blockers()).extracting(BoardStats.Blocker::owner)
                .contains("alice", "");
    }

    @Test
    @DisplayName("The epics legend counts every label, busiest first, ties by name")
    void legendCountsAndOrders() {
        BoardStats s = BoardStats.of(richBoard(), NOW, ZoneOffset.UTC, 14, 5);
        assertThat(s.labels()).extracting(BoardStats.LabelCount::label)
                .containsExactly("infra", "design");
        assertThat(s.labels().get(0).count()).isEqualTo(2);
        assertThat(s.labels().get(1).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Blocker rows order longest-stuck first")
    void registerOrdersByAge() throws Exception {
        // stamps set via JSON so the ages are deterministic
        TaskBoard b = TaskBoard.fromJson(new org.json.JSONObject()
                .put("version", 1)
                .put("columns", new org.json.JSONArray()
                        .put(new org.json.JSONObject().put("name", "A")
                                .put("cards", new org.json.JSONArray()
                                        .put(new org.json.JSONObject()
                                                .put("title", "fresh block")
                                                .put("blockAction", "a")
                                                .put("blockedSince", daysAgo(1)))
                                        .put(new org.json.JSONObject()
                                                .put("title", "ancient block")
                                                .put("blockAction", "b")
                                                .put("blockedSince", daysAgo(9)))))
                        .put(new org.json.JSONObject().put("name", "Done")
                                .put("cards", new org.json.JSONArray())))
                .toString());
        BoardStats s = BoardStats.of(b, NOW, ZoneOffset.UTC, 14, 5);
        assertThat(s.blockers().get(0).title()).isEqualTo("ancient block");
        assertThat(s.blockers().get(0).sinceDays()).isEqualTo(9);
        assertThat(s.blockers().get(1).sinceDays()).isEqualTo(1);
    }
}
