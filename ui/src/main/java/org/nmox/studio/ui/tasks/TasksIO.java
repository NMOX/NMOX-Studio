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
 *
 * <p>A file that could not be READ is the other half of that law and had
 * no answer until now: {@link #load} reports it as
 * {@link LoadOutcome#unreadable()} so the caller can refuse rather than
 * pretend. A file we never read is never ours.
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
     * What a load found.
     *
     * <p>{@code board} is never null: the parsed file, or the starter
     * board on any failure. {@code unreadable} is the one fact the caller
     * cannot work out for itself — the file EXISTS and its bytes could
     * not be read at all, so the board handed back is a stand-in and NOT
     * this project's board.
     *
     * <p>It is a returned fact rather than a thrown exception because the
     * window has something to show either way; it is a fact at all
     * because for two years it was not. The read-failure branch used to
     * hand back the same starter board a fresh project gets, so
     * {@code TasksTopComponent.reload} stamped the file as ours, the
     * never-clobber guard saw nothing foreign, and the first card edit
     * wrote a three-column starter over a board we had never read — with
     * no {@code .bak}, because nothing had parsed. An over-cap file
     * ({@link org.nmox.studio.core.util.BoundedReads.TooLarge} is an
     * {@link IOException}) was destroyed by the very cap that refused to
     * read it.
     */
    record LoadOutcome(TaskBoard board, boolean unreadable) {
    }

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

    /**
     * The project's board: the parsed file when present and well-formed,
     * the starter board when absent, and — on a malformed file — the
     * starter board AFTER copying the bytes to {@code .nmoxtasks.json.bak}
     * so the next save cannot destroy what the user (or their merge)
     * wrote. A file that cannot be READ comes back marked
     * {@link LoadOutcome#unreadable()} instead, because the caller must
     * not treat a stand-in board as this project's.
     */
    static LoadOutcome load(File projectDir) {
        File f = fileFor(projectDir);
        if (!f.isFile()) {
            return new LoadOutcome(starterBoard(), false);
        }
        String text;
        try {
            // .nmoxtasks.json sits beside the project and travels with a clone
            text = org.nmox.studio.core.util.BoundedReads.read(f.toPath());
        } catch (IOException ex) {
            // Unknown bytes, not absent ones: the file is still there and
            // still the user's board. No .bak is taken here and that is the
            // point — the parse failure copies the bytes aside because they
            // are about to be replaced, while nothing at all may be written
            // over a file we could not read, so there is nothing to rescue
            // it from. (Copying would also duplicate the very file the cap
            // refused, and would fail outright on the permission errors that
            // land here beside it.)
            LOG.log(Level.WARNING,
                    "Unreadable {0}; the board is read-only until it can be read ({1})",
                    new Object[]{f, ex.getMessage()});
            return new LoadOutcome(starterBoard(), true);
        }
        try {
            return new LoadOutcome(TaskBoard.fromJson(text), false);
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
            return new LoadOutcome(starterBoard(), false);
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
