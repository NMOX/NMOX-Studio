package org.nmox.studio.tools.vscode;

import java.awt.EventQueue;
import java.io.File;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.nmox.studio.core.spi.ProjectAim;
import org.nmox.studio.tools.vscode.VsCodeTasks.EditorContext;
import org.openide.util.RequestProcessor;

/**
 * What <i>Run ▸ Run Task…</i> and <i>Debug ▸ Start Debugging…</i> have in
 * common (3.6.0): a menu row that lists what the aimed project's {@code
 * .vscode} file offers and starts the row that is chosen — the same
 * start Enter in Quick Search makes, through the same code, so a task
 * run from the menu is refused, trust-gated and stopped exactly as one
 * run from Quick Search.
 *
 * <p><b>Why a menu at all.</b> Quick Search finds a task by name, which
 * serves someone who knows the name. A switcher looks in the menus first
 * (VS Code's own doors are <i>Terminal ▸ Run Task…</i> and <i>Run ▸ Start
 * Debugging</i>), and a list answers "what can this repository run?"
 * without a name to type.
 *
 * <p><b>The order of a press.</b> The editor is read first, on the event
 * thread, before any dialog takes the focus: {@code ${file}} means the
 * file the user was looking at when they opened the menu. The file is
 * then read on a lane (never the event thread), the list is shown on the
 * event thread only if the project it was read for is still the one
 * aimed (a result belongs to the workspace that produced it), and the
 * chosen row is handed to the provider's own lane.
 *
 * <p>Always enabled, like the kit actions: with no project, or no file,
 * or a file with nothing in it, the status line says which rather than a
 * row sitting grey without a reason.
 *
 * @param <T> what one row starts: a task, or a launch configuration
 */
final class VsCodeMenuDoor<T> {

    /** What the door needs from its file. */
    interface Source<T> {

        /** Everything the project's file offers, in the file's order; empty without a file. */
        List<T> read(File project);

        /** One row of the list for {@code item}: one line, plain text. */
        String row(T item);

        /** Starts {@code item}, as Enter on it in Quick Search would. */
        void start(File project, T item, EditorContext editor);
    }

    /** The sentences and labels of one door, already in the reader's language. */
    record Words(String title, String start, String noProject, String nothing) {
    }

    /** Shows the list and answers with the chosen index (a seam: a test needs no dialog). */
    interface Picker {
        OptionalInt pick(String title, String start, List<String> rows);
    }

    private static final RequestProcessor RP = new RequestProcessor("VS Code menu doors", 1);

    private final Source<T> source;
    private final Supplier<Words> words;

    /** Seams, so every rule of a press is a test with no window, no project service and no editor. */
    volatile Supplier<File> aimed = VsCodeMenuDoor::aimedProject;
    volatile Supplier<EditorContext> editorProbe = VsCodeTaskEditor::snapshot;
    volatile Picker picker = VsCodePicker::pick;
    volatile Consumer<String> statusSink;
    volatile Consumer<Runnable> onEventThread = EventQueue::invokeLater;

    VsCodeMenuDoor(Source<T> source, Supplier<Words> words, Consumer<String> statusSink) {
        this.source = source;
        this.words = words;
        this.statusSink = statusSink;
    }

    private static File aimedProject() {
        ProjectAim aim = ProjectAim.find();
        return aim == null ? null : aim.projectDir();
    }

    /** The menu row was pressed (event thread). Returns the lane's task, for a test to wait on. */
    org.openide.util.Task press() {
        EditorContext editor = editorProbe.get();
        File project = aimed.get();
        Words w = words.get();
        if (project == null) {
            statusSink.accept(w.noProject());
            return org.openide.util.Task.EMPTY;
        }
        return RP.post(() -> {
            List<T> items = source.read(project);
            if (items.isEmpty()) {
                statusSink.accept(w.nothing());
                return;
            }
            List<String> rows = items.stream().map(source::row).toList();
            onEventThread.accept(() -> {
                if (!project.equals(aimed.get())) {
                    return; // the aim moved while the file was read: this list is another project's
                }
                OptionalInt chosen = picker.pick(w.title(), w.start(), rows);
                if (chosen.isPresent()) {
                    source.start(project, items.get(chosen.getAsInt()), editor);
                }
            });
        });
    }
}
