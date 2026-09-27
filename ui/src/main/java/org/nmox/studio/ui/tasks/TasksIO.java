package org.nmox.studio.ui.tasks;

import java.io.File;
import java.io.IOException;
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
    record LoadOutcome(TaskBoard board, Refusal refusal, String rescuedAs) {

        /** The bytes exist and could not be read at all. */
        boolean unreadable() {
            return refusal == Refusal.UNREADABLE;
        }

        /** The bytes hold git's unresolved merge conflict (3.4). */
        boolean conflicted() {
            return refusal == Refusal.CONFLICTED;
        }

        /**
         * True when the board on screen is a stand-in for a file that
         * exists and is not ours to write: unread bytes, bytes holding
         * git's unresolved merge conflict, or malformed bytes no copy
         * could be kept of (3.4). Every save refuses while this holds, and
         * the file pulse brings the real board back when the file changes
         * on disk.
         */
        boolean readOnly() {
            return refusal != Refusal.NONE;
        }
    }

    /**
     * Why a board on screen is not the project's own (3.4). NONE is a
     * board we may write: the parsed file, a fresh starter, or a starter
     * after the malformed bytes were safely copied aside
     * ({@link LoadOutcome#rescuedAs()} names the copy).
     */
    enum Refusal {
        NONE,
        UNREADABLE,
        CONFLICTED,
        /** Malformed, and the copy aside failed: writing would lose the only copy. */
        UNRESCUED
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
        if (!f.exists()) {
            return new LoadOutcome(starterBoard(), Refusal.NONE, null);
        }
        if (!f.isFile()) {
            // something that is not a file wears the board's name (a
            // directory, a device): it is not ours to replace
            LOG.log(Level.WARNING, "{0} is not a file; the board is read-only", f);
            return new LoadOutcome(starterBoard(), Refusal.UNREADABLE, null);
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
            return new LoadOutcome(starterBoard(), Refusal.UNREADABLE, null);
        }
        if (org.nmox.studio.core.util.MergeConflicts.hasMarkers(text)) {
            // A merge git has not finished holds BOTH people's boards. It
            // is neither corrupt nor ours to repair: until 3.4 it failed
            // to parse, was copied to .bak, and the next card edit wrote a
            // starter board over it — so the commit that finished the
            // merge recorded the loss. It is left exactly as it is, and
            // nothing is written over it until git's markers are gone.
            LOG.log(Level.INFO,
                    "{0} has unresolved merge conflicts; the board is read-only until they are resolved",
                    f);
            return new LoadOutcome(starterBoard(), Refusal.CONFLICTED, null);
        }
        try {
            return new LoadOutcome(TaskBoard.fromJson(text), Refusal.NONE, null);
        } catch (RuntimeException broken) {
            // never over an earlier rescue: a second corrupt load used to
            // replace the first .bak, and the older bytes were gone; the
            // same broken bytes read again (a re-aim, a search) reuse the
            // copy they already have
            try {
                File bak = org.nmox.studio.core.util.Backups.keep(f.toPath(),
                        text.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toFile();
                LOG.log(Level.WARNING,
                        "Malformed {0}; kept a copy as {1} and started fresh ({2})",
                        new Object[] {f, bak.getName(), broken.toString()});
                return new LoadOutcome(starterBoard(), Refusal.NONE, bak.getName());
            } catch (IOException io) {
                // No copy exists anywhere but the file itself. Until 3.4
                // the starter still bound WRITABLE here, so the next card
                // edit replaced the only copy of the user's bytes.
                LOG.log(Level.WARNING, "Malformed {0} and its copy failed ({1}); the board is read-only",
                        new Object[] {f, io.getMessage()});
                return new LoadOutcome(starterBoard(), Refusal.UNRESCUED, null);
            }
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
