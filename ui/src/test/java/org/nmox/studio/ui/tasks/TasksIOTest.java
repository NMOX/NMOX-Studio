package org.nmox.studio.ui.tasks;

import java.io.File;
import java.nio.file.Files;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.util.SelfWriteTracker;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code .nmoxtasks.json} persistence laws (v1.323.0): absent file →
 * starter board, corrupt file → .bak then starter (never clobbered),
 * saves round-trip, and the self-write tracker tells our writes from
 * foreign ones so an external edit wins over a stale gesture.
 */
class TasksIOTest {

    @Test
    @DisplayName("no file yet: the starter board, and a save round-trips")
    void absentThenSave(@TempDir File dir) throws Exception {
        SelfWriteTracker tracker = new SelfWriteTracker();
        TaskBoard b = TasksIO.load(dir);
        assertThat(b.columnCount()).isEqualTo(3);
        b.addCard(0, "first", "");
        TasksIO.save(dir, b, tracker);
        assertThat(TasksIO.fileFor(dir)).exists();
        TaskBoard back = TasksIO.load(dir);
        assertThat(back.column(0).cards()).extracting(TaskBoard.Card::title)
                .containsExactly("first");
    }

    @Test
    @DisplayName("a corrupt file is kept as .bak, never clobbered silently")
    void corruptKeepsBak(@TempDir File dir) throws Exception {
        File f = TasksIO.fileFor(dir);
        Files.writeString(f.toPath(), "{ definitely not a board");
        TaskBoard b = TasksIO.load(dir);
        assertThat(b.columnCount())
                .as("fallback is the starter board")
                .isEqualTo(3);
        assertThat(new File(dir, TasksIO.FILENAME + ".bak"))
                .as("the user's bytes survive as .bak — the v1.39.0 law")
                .exists();
        assertThat(Files.readString(new File(dir,
                TasksIO.FILENAME + ".bak").toPath()))
                .isEqualTo("{ definitely not a board");
    }

    @Test
    @DisplayName("our own save is not a foreign edit; an outside write is")
    void foreignEditDiscrimination(@TempDir File dir) throws Exception {
        SelfWriteTracker tracker = new SelfWriteTracker();
        TaskBoard b = TaskBoard.starter();
        b.addCard(0, "mine", "");
        TasksIO.save(dir, b, tracker);
        assertThat(TasksIO.foreignEdit(dir, tracker))
                .as("a save we just stamped must not read as foreign")
                .isFalse();
        // an outside writer (git pull, editor) replaces the file
        Files.writeString(TasksIO.fileFor(dir).toPath(),
                TaskBoard.starter().toJson() + "\n");
        assertThat(TasksIO.foreignEdit(dir, tracker))
                .as("a write we did not stamp is foreign — the board must"
                        + " reload rather than clobber it")
                .isTrue();
    }
}
