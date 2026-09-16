package org.nmox.studio.ui.tasks;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.json.JSONObject;
import org.nmox.studio.core.spi.DocsScene;
import org.nmox.studio.core.util.DocsFixtures;
import org.nmox.studio.core.util.SelfWriteTracker;
import org.openide.util.lookup.ServiceProvider;

/**
 * Stages the Task Board the guides photograph (v2.163.0).
 *
 * <p>Three pictures come from this one fixture — the board itself, the
 * sprint Overview, and the Standup report — because all three are faces of
 * the same board, and a board with no work on it says nothing in any
 * language. Until now they were photographed once in English, so a reader
 * of any other language met an English board under a translated sentence.
 *
 * <p>It lives in this package because {@code TasksIO} is package-private:
 * the board is written by the product's own writer, through the product's
 * own model, so the fixture cannot drift from the format the product reads.
 * The COLUMN names are not set here at all — {@link TasksIO#starterBoard()}
 * takes them from this language's bundle (the v2.130.0 seed rule), so the
 * headings are the product's translation and only the cards are fixture
 * content.
 *
 * <p>Forge-only: reached through {@link DocsScene}, which nothing but the
 * forge looks up, and writing only under the throwaway home it is given.
 */
@ServiceProvider(service = DocsScene.class)
public final class DocsTaskBoard implements DocsScene {

    /** The scene's name, as its picture is named. */
    public static final String ID = "task-board";

    /** How long the running card has been clocked in — a plausible morning. */
    static final long CLOCKED_FOR_MS = (2 * 60 + 7) * 60_000L;

    private static final long DAY_MS = 24L * 60 * 60 * 1000L;
    /** The sprint began two days ago and has eight to run: today sits inside it. */
    static final long SPRINT_STARTED_AGO_MS = 2 * DAY_MS;
    static final long SPRINT_ENDS_IN_MS = 8 * DAY_MS;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public File stage(File home, String fixtures, String lang) throws IOException {
        JSONObject text = DocsFixtures.section(fixtures, lang, "board");
        File dir = DocsFixtures.projectDir(home);
        Files.createDirectories(dir.toPath());

        TaskBoard board = TasksIO.starterBoard();
        int last = board.columnCount() - 1;
        String epic = text.getString("epic");
        long now = System.currentTimeMillis();

        List<String> todo = DocsFixtures.strings(text, "todo");
        TaskBoard.Card blocked = null;
        for (String title : todo) {
            TaskBoard.Card card = board.addCard(0, title, "");
            if (card != null) {
                board.setLabel(card.id(), epic);
                blocked = card;
            }
        }
        // the LAST to-do card is the blocked one: its blocker names an owner
        // and the action that would unblock it, which is what the register
        // shows (v2.5.0 — a row with no action is not a register row)
        if (blocked != null) {
            board.block(blocked.id(), text.getString("blockOwner"), text.getString("blockAction"));
        }

        // exactly one clock runs on a board, and it is the FIRST in-progress
        // card. v2.163.0 dropped a `clocked` key naming that card by title:
        // it was the same fact in two places, kept byte-identical by hand in
        // fifteen files (the v2.131.0 rule — the defect is the second home).
        boolean clockStarted = false;
        for (String title : DocsFixtures.strings(text, "doing")) {
            TaskBoard.Card card = board.addCard(1, title, "");
            if (card == null) {
                continue;
            }
            board.setLabel(card.id(), epic);
            if (!clockStarted) {
                clockStarted = board.clockIn(card.id(), now - CLOCKED_FOR_MS);
            }
        }

        // addCard into the last column stamps `done` itself, which is what
        // the Overview's burndown and the Standup's Yesterday/Today read
        for (String title : DocsFixtures.strings(text, "done")) {
            TaskBoard.Card card = board.addCard(last, title, "");
            if (card != null) {
                board.setLabel(card.id(), epic);
            }
        }

        board.setSprint(text.getString("sprint"), now - SPRINT_STARTED_AGO_MS, now + SPRINT_ENDS_IN_MS);
        TasksIO.save(dir, board, new SelfWriteTracker());
        return dir;
    }
}
