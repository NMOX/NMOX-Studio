package org.nmox.studio.ui.tasks;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.util.SelfWriteTracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A board git has not finished merging is not ours to write (3.4).
 *
 * <p>Measured before 3.4: a {@code .nmoxtasks.json} holding git's conflict
 * markers failed to parse, was copied to {@code .bak}, and the board bound a
 * three-column starter WRITABLE — so the next card edit wrote the starter
 * over both people's boards, and the commit that finished the merge recorded
 * the loss. These tests run the window's own sequence (load → stamp only a
 * read that happened → foreign-edit check), as {@link UnreadableBoardTest}
 * does for the read failure.
 */
class MergedBoardTest {

    private Locale before;

    @BeforeEach
    void english() {
        before = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
    }

    @AfterEach
    void restore() {
        Locale.setDefault(before);
    }

    /** A board as git leaves it mid-merge. Markers built, never typed. */
    static String conflicted() {
        return "{\n  \"version\": 1,\n  \"columns\": [\n    {\n      \"name\": \"To Do\",\n"
                + "<".repeat(7) + " HEAD\n      \"cards\": [ { \"id\": \"a\", \"title\": \"Alice's\" } ]\n"
                + "=".repeat(7) + "\n      \"cards\": [ { \"id\": \"b\", \"title\": \"Bob's\" } ]\n"
                + ">".repeat(7) + " theirs\n    }\n  ]\n}\n";
    }

    @Test
    @DisplayName("a conflicted board is left byte-for-byte, bound read-only, and never stamped as ours")
    void conflictedBoardIsNeverWritten(@TempDir File dir) throws Exception {
        File f = TasksIO.fileFor(dir);
        String bytes = conflicted();
        Files.writeString(f.toPath(), bytes);

        TasksIO.LoadOutcome outcome = TasksIO.load(dir);
        assertThat(outcome.conflicted()).isTrue();
        assertThat(outcome.readOnly()).as("every save path refuses").isTrue();
        assertThat(new File(dir, TasksIO.FILENAME + ".bak")).as("nothing copied, nothing moved").doesNotExist();

        // the window's reload: ownership only for a read that happened
        SelfWriteTracker tracker = new SelfWriteTracker();
        if (!outcome.readOnly()) {
            tracker.noteSync(f);
        }
        assertThat(TasksIO.foreignEdit(dir, tracker))
                .as("the never-clobber guard refuses the save")
                .isTrue();
        assertThat(Files.readString(f.toPath())).isEqualTo(bytes);

        assertThat(TasksTopComponent.readOnlyReason(outcome))
                .as("said where the user looks, with git and the way out")
                .contains(TasksIO.FILENAME).contains("merge conflicts").contains("git");
    }

    @Test
    @DisplayName("once the merge is resolved on disk, the board loads normally")
    void resolvedBoardLoads(@TempDir File dir) throws Exception {
        Files.writeString(TasksIO.fileFor(dir).toPath(), conflicted());
        assertThat(TasksIO.load(dir).conflicted()).isTrue();
        TaskBoard resolved = TaskBoard.starter("To Do", "Doing", "Done");
        resolved.addCard(0, "Alice's", "");
        resolved.addCard(0, "Bob's", "");
        Files.writeString(TasksIO.fileFor(dir).toPath(), resolved.toJson());
        TasksIO.LoadOutcome outcome = TasksIO.load(dir);
        assertThat(outcome.readOnly()).isFalse();
        assertThat(outcome.board().cardCount()).isEqualTo(2);
        assertThat(TasksTopComponent.readOnlyReason(outcome)).isNull();
    }

    @Test
    @DisplayName("a malformed board is copied aside and SAID; a second one never overwrites the first rescue")
    void rescuesSpeakAndNeverOverwrite(@TempDir File dir) throws Exception {
        Path f = TasksIO.fileFor(dir).toPath();
        Files.writeString(f, "{ first");
        TasksIO.LoadOutcome one = TasksIO.load(dir);
        assertThat(one.readOnly()).isFalse();
        assertThat(one.rescuedAs()).isEqualTo(TasksIO.FILENAME + ".bak");
        TasksIO.load(dir); // a re-aim or a search reads the same bytes again
        Files.writeString(f, "{ second");
        TasksIO.LoadOutcome two = TasksIO.load(dir);
        assertThat(two.rescuedAs()).isEqualTo(TasksIO.FILENAME + ".bak.1");
        assertThat(new File(dir, TasksIO.FILENAME + ".bak")).hasContent("{ first");
        assertThat(new File(dir, TasksIO.FILENAME + ".bak.1")).hasContent("{ second");
        assertThat(new File(dir, TasksIO.FILENAME + ".bak.2")).doesNotExist();
    }

    @Test
    @DisplayName("a malformed board no copy can be kept of is read-only: the file is the only copy")
    void unrescuedBoardIsReadOnly(@TempDir File dir) throws Exception {
        Path f = TasksIO.fileFor(dir).toPath();
        Files.writeString(f, "{ broken");
        try {
            Files.setPosixFilePermissions(dir.toPath(), java.util.Set.of(
                    java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                    java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE));
        } catch (UnsupportedOperationException noPosix) {
            assumeTrue(false, "POSIX permissions needed");
        }
        try {
            assumeFalse(Files.isWritable(dir.toPath()), "running as root");
            TasksIO.LoadOutcome outcome = TasksIO.load(dir);
            assertThat(outcome.readOnly()).as("writing would lose the only copy").isTrue();
            assertThat(TasksTopComponent.readOnlyReason(outcome)).contains("no copy");
        } finally {
            Files.setPosixFilePermissions(dir.toPath(), java.util.EnumSet.allOf(
                    java.nio.file.attribute.PosixFilePermission.class));
        }
    }

    @Test
    @DisplayName("a directory wearing the board's name is not a board we may replace")
    void directoryIsReadOnly(@TempDir File dir) throws Exception {
        Files.createDirectory(TasksIO.fileFor(dir).toPath());
        TasksIO.LoadOutcome outcome = TasksIO.load(dir);
        assertThat(outcome.readOnly()).isTrue();
        assertThat(outcome.unreadable()).isTrue();
    }
}
