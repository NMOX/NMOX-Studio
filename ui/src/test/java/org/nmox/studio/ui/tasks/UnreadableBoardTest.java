package org.nmox.studio.ui.tasks;

import java.io.File;
import java.nio.file.Files;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.util.BoundedReads;
import org.nmox.studio.core.util.SelfWriteTracker;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A board we could not READ is not a board we may overwrite.
 *
 * <p>The corrupt-file law ({@link TasksIOTest#corruptKeepsBak}) has held
 * since v1.323.0, and it covers the PARSE failure: the bytes were read,
 * they are not a board, so they are kept as {@code .bak} and the starter
 * takes over. The READ failure had no law at all — {@code TasksIO.load}
 * logged at INFO and handed back the same starter board, indistinguishable
 * from a clean empty load, and {@code TasksTopComponent.reload} then
 * stamped the file as OURS. From there {@code mutate}'s never-clobber
 * guard saw no foreign edit and the first card edit wrote a three-column
 * starter over the user's real board, with no backup.
 *
 * <p>{@link BoundedReads.TooLarge} is an {@link java.io.IOException}, so
 * an over-cap {@code .nmoxtasks.json} took that path — the file the 8 MiB
 * ceiling exists to refuse was the file the refusal destroyed. So did a
 * permissions error and any transient I/O failure.
 *
 * <p>These tests drive the real {@code TasksIO} and the real
 * {@link SelfWriteTracker} through the exact sequence the window runs
 * (load → stamp → foreign-edit check → save), because that sequence is
 * where the loss happens; the window itself only presses the buttons.
 */
class UnreadableBoardTest {

    /** Writes a file the 8 MiB cap refuses to read. */
    private static void writeOverCap(File f) throws Exception {
        byte[] chunk = new byte[1024 * 1024];
        java.util.Arrays.fill(chunk, (byte) 'x');
        try (java.io.OutputStream out = Files.newOutputStream(f.toPath())) {
            for (int i = 0; i < 9; i++) {
                out.write(chunk);
            }
        }
        assertThat(f.length()).isGreaterThan(BoundedReads.DEFAULT_MAX_BYTES);
    }

    @Test
    @DisplayName("an over-cap board is refused as unreadable, not loaded as a starter")
    void overCapIsUnreadable(@TempDir File dir) throws Exception {
        writeOverCap(TasksIO.fileFor(dir));
        TasksIO.LoadOutcome outcome = TasksIO.load(dir);
        assertThat(outcome.unreadable())
                .as("the bytes are unknown — the caller must not own this file")
                .isTrue();
    }

    @Test
    @DisplayName("an absent or well-formed board is never reported unreadable")
    void readableIsNotUnreadable(@TempDir File dir) throws Exception {
        assertThat(TasksIO.load(dir).unreadable())
                .as("no file at all is a fresh project, not a refusal")
                .isFalse();
        TaskBoard b = TaskBoard.starter("To Do", "Doing", "Done");
        b.addCard(0, "mine", "");
        TasksIO.save(dir, b, new SelfWriteTracker());
        assertThat(TasksIO.load(dir).unreadable()).isFalse();
    }

    @Test
    @DisplayName("a corrupt board is readable-but-malformed: .bak, starter, and we own it")
    void corruptIsNotUnreadable(@TempDir File dir) throws Exception {
        Files.writeString(TasksIO.fileFor(dir).toPath(), "{ definitely not a board");
        TasksIO.LoadOutcome outcome = TasksIO.load(dir);
        assertThat(outcome.unreadable())
                .as("we READ these bytes and kept them as .bak — the starter may"
                        + " replace them, which is the v1.323.0 law")
                .isFalse();
        assertThat(new File(dir, TasksIO.FILENAME + ".bak")).exists();
    }

    /**
     * The clobber itself, as the window runs it. On the code this test was
     * written against, the last assertion failed: the file held a starter
     * board and the user's cards were gone.
     */
    @Test
    @DisplayName("the window's load → stamp → save sequence cannot overwrite an unread board")
    void unreadBoardSurvivesTheNextEdit(@TempDir File dir) throws Exception {
        File f = TasksIO.fileFor(dir);
        writeOverCap(f);
        long sizeBefore = f.length();
        byte[] head = Files.readAllBytes(f.toPath());

        // --- TasksTopComponent.reload(), exactly ---
        SelfWriteTracker tracker = new SelfWriteTracker();
        TasksIO.LoadOutcome outcome = TasksIO.load(dir);
        if (!outcome.unreadable()) {
            // the window only takes ownership of a file it actually read
            tracker.noteSync(f);
        }

        // --- TasksTopComponent.mutate(), exactly ---
        assertThat(TasksIO.foreignEdit(dir, tracker))
                .as("a file we never read is never ours, so the never-clobber"
                        + " guard must refuse the save")
                .isTrue();

        assertThat(f.length())
                .as("the user's board is untouched on disk")
                .isEqualTo(sizeBefore);
        assertThat(Files.readAllBytes(f.toPath())).isEqualTo(head);
        assertThat(new File(dir, TasksIO.FILENAME + ".bak"))
                .as("nothing was written, so nothing needed backing up")
                .doesNotExist();
    }
}
