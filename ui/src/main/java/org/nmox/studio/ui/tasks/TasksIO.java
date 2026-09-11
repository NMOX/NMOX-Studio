package org.nmox.studio.ui.tasks;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.nmox.studio.core.util.AtomicFiles;
import org.nmox.studio.core.util.SelfWriteTracker;

/**
 * Load/save for {@code .nmoxtasks.json} (v1.323.0) — the Task Board's
 * per-project file, persisted with the same laws as the five studio
 * files before it: atomic writes via {@link AtomicFiles} (temp sibling
 * + ATOMIC_MOVE, no torn reads), every save noted on the caller's
 * {@link SelfWriteTracker} so an external-edit check can tell our own
 * write from a foreign one, and a corrupt file kept as {@code .bak}
 * before falling back to the starter board — user data is never
 * clobbered by a parse failure (the v1.39.0 law).
 */
@org.openide.util.NbBundle.Messages({
    // the fresh board a project gets on its first open; these are
    // the product speaking, and become the user's the moment they
    // are edited or the file is written
    "TasksIO_starterTodo=To Do",
    "TasksIO_starterDoing=Doing",
    "TasksIO_starterDone=Done"
})
final class TasksIO {

    static final String FILENAME = ".nmoxtasks.json";

    private static final Logger LOG = Logger.getLogger(TasksIO.class.getName());

    private TasksIO() {
    }

    static File fileFor(File projectDir) {
        return new File(projectDir, FILENAME);
    }

    /**
     * The project's board: the parsed file when present and well-formed,
     * the starter board when absent, and — on a malformed file — the
     * starter board AFTER copying the bytes to {@code .nmoxtasks.json.bak}
     * so the next save cannot destroy what the user (or their merge)
     * wrote.
     */
    /**
     * The starter board in the reader's own language.
     *
     * <p>This is the consumer half of the v2.101.0 rule: {@code TaskBoard}
     * returns data and knows nothing about words, and the one place a board
     * is CREATED reads the bundle. A German walk found a fresh board's
     * headers reading To Do / Doing / Done in a fully translated build,
     * which no l10n gate could see — the English never passed through a
     * bundle at all.
     *
     * <p>Only a NEW board is named this way. A board already on disk keeps
     * the names it has, in whatever language it was made and however the
     * user has since renamed its columns, because by then they are the
     * user's words and not the product's.
     */
    static TaskBoard starterBoard() {
        return TaskBoard.starter(Bundle.TasksIO_starterTodo(),
                Bundle.TasksIO_starterDoing(), Bundle.TasksIO_starterDone());
    }

    static TaskBoard load(File projectDir) {
        File f = fileFor(projectDir);
        if (!f.isFile()) {
            return starterBoard();
        }
        String text;
        try {
            text = Files.readString(f.toPath());
        } catch (IOException ex) {
            LOG.log(Level.INFO, "Unreadable {0}; starting empty", f);
            return starterBoard();
        }
        try {
            return TaskBoard.fromJson(text);
        } catch (RuntimeException broken) {
            File bak = new File(projectDir, FILENAME + ".bak");
            try {
                Files.writeString(bak.toPath(), text);
                LOG.log(Level.WARNING,
                        "Malformed {0}; kept a .bak and started fresh ({1})",
                        new Object[] {f, broken.toString()});
            } catch (IOException io) {
                LOG.log(Level.WARNING, "Malformed {0} and .bak failed", f);
            }
            return starterBoard();
        }
    }

    /** Atomic save, noted on {@code tracker} as our own write. */
    static void save(File projectDir, TaskBoard board, SelfWriteTracker tracker)
            throws IOException {
        File f = fileFor(projectDir);
        AtomicFiles.writeString(f.toPath(), board.toJson());
        tracker.noteSync(f);
    }

    /**
     * True when the file on disk is not the one {@code tracker} last
     * noted — i.e. an EXTERNAL edit (git pull, editor save, teammate's
     * merge) changed it under us, and the board should reload.
     */
    static boolean foreignEdit(File projectDir, SelfWriteTracker tracker) {
        File f = fileFor(projectDir);
        return f.isFile() && tracker.isForeign(f.lastModified(), f.length());
    }
}
