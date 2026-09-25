package org.nmox.studio.ui.actions;

import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;

import org.nmox.studio.core.util.PlainStatus;
import org.openide.awt.StatusDisplayer;
import org.openide.cookies.EditCookie;
import org.openide.cookies.LineCookie;
import org.openide.cookies.OpenCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.text.Line;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;

/**
 * Opens what an {@link EditRequest} names and, for a waiting request, tells
 * the terminal when every one of them has been closed (3.2.0,
 * {@code nmox --wait} as git's editor).
 *
 * <p>A request's targets are the DataObject of each opened file and the
 * window of each comparison. A target is ARMED once it has been seen open
 * and CLOSED once, armed, it no longer is; the request is done when every
 * target is closed. The arming matters: an editor opens a moment after it
 * is asked to, and a window closing elsewhere in that moment must not read
 * as this file being closed.
 *
 * <p>A file already open when the request arrives is armed at once and
 * waits for its tab to close, as {@code code --wait} does. Quitting the IDE
 * answers {@code done} for every request still waiting (a shutdown hook), so
 * git goes on with what was saved; a crash leaves no answer, and the command
 * notices that the IDE's process id is gone.
 *
 * <p>EDT-confined: every entry point runs on the event thread.
 */
@Messages({
    "# {0} - the file names, or the two names a comparison shows",
    "EditRequestWatcher_waiting=A terminal is waiting for {0}: save, then close the tab to hand it back",
    "EditRequestWatcher_handedBack=Handed {0} back to the terminal",
    "# {0} - the file, {1} - why it could not be opened",
    "EditRequestWatcher_couldNotOpen=Could not open {0}: {1}"
})
final class EditRequestWatcher {

    private static final Logger LOG = Logger.getLogger(EditRequestWatcher.class.getName());

    /** One waiting request: its folder, its targets in order, and which are armed. */
    static final class Session {
        final File folder;
        final String names;
        final Map<Object, Boolean> armed = new LinkedHashMap<>();

        Session(File folder, String names, List<Object> targets) {
            this.folder = folder;
            this.names = names;
            for (Object t : targets) {
                armed.put(t, Boolean.FALSE);
            }
        }

        /**
         * Brings the session up to date with what is showing, and answers
         * whether every target has now been closed.
         */
        boolean update(Predicate<Object> showing) {
            boolean allClosed = true;
            for (Map.Entry<Object, Boolean> e : armed.entrySet()) {
                boolean open = showing.test(e.getKey());
                if (open) {
                    e.setValue(Boolean.TRUE);
                    allClosed = false;
                } else if (!e.getValue()) {
                    allClosed = false;   // not seen open yet: still on its way
                }
            }
            return allClosed;
        }
    }

    private static final List<Session> WAITING = new ArrayList<>();
    private static PropertyChangeListener registryListener;

    /** Where the status line is, as a seam: tests capture it instead of sharing the platform's one line. */
    static java.util.function.Consumer<String> status =
            text -> StatusDisplayer.getDefault().setStatusText(PlainStatus.text(text));

    /** The seam tests replace: whether a target is still showing. */
    static Predicate<Object> showing = EditRequestWatcher::isShowing;

    static {
        // quitting the IDE hands every waiting file back: git goes on with
        // what was saved rather than waiting for an IDE that is gone
        Runtime.getRuntime().addShutdownHook(new Thread(EditRequestWatcher::answerAll, "nmox-edit-request-exit"));
    }

    private EditRequestWatcher() {
    }

    /** Opens everything {@code request} names; tracks it when it waits. On the EDT. */
    static void show(File folder, EditRequest request) {
        List<Object> targets = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (EditRequest.Item item : request.items()) {
            try {
                if (item instanceof EditRequest.Open o) {
                    targets.add(open(o.file(), o.line()));
                    names.add(o.file().getName());
                } else if (item instanceof EditRequest.Diff d) {
                    DiffWindow w = DiffWindow.open(d.left(), d.right());
                    targets.add(w);
                    names.add(w.getDisplayName());
                }
            } catch (IOException ex) {
                String file = item instanceof EditRequest.Open o ? o.file().getName()
                        : ((EditRequest.Diff) item).left() != null ? ((EditRequest.Diff) item).left().getName()
                        : ((EditRequest.Diff) item).right().getName();
                String why = Bundle.EditRequestWatcher_couldNotOpen(file, ex.getLocalizedMessage());
                status.accept(PlainStatus.text(why));
                EditRequestOption.answer(folder, "refused", why);
                return;
            }
        }
        if (!request.waits()) {
            return;
        }
        Session s = new Session(folder, String.join(", ", names), targets);
        // a plain status text, gone in seconds: a raised one would hide every
        // other status line for as long as the message is being written
        status.accept(PlainStatus.text(Bundle.EditRequestWatcher_waiting(s.names)));
        track(s);
        // the editor may have opened synchronously; arm what already shows
        recheck();
    }

    /** Starts waiting on {@code s}. On the EDT. */
    static void track(Session s) {
        synchronized (WAITING) {
            WAITING.add(s);
        }
        listen();
    }

    /** Opens {@code file}, at {@code line} when it is positive, and answers the DataObject that tracks it. */
    private static DataObject open(File file, int line) throws IOException {
        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(file));
        if (fo == null) {
            throw new IOException("no such file");
        }
        DataObject dob = DataObject.find(fo);
        LineCookie lc = dob.getLookup().lookup(LineCookie.class);
        if (line > 0 && lc != null) {
            Line.Set set = lc.getLineSet();
            int index = Math.min(line - 1, Math.max(0, set.getLines().size() - 1));
            set.getOriginal(index).show(Line.ShowOpenType.OPEN, Line.ShowVisibilityType.FOCUS);
            return dob;
        }
        EditCookie edit = dob.getLookup().lookup(EditCookie.class);
        if (edit != null) {
            edit.edit();
            return dob;
        }
        OpenCookie open = dob.getLookup().lookup(OpenCookie.class);
        if (open == null) {
            throw new IOException("it is not a file the IDE can open");
        }
        open.open();
        return dob;
    }

    private static void listen() {
        if (registryListener == null) {
            registryListener = e -> {
                String p = e.getPropertyName();
                if (TopComponent.Registry.PROP_TC_CLOSED.equals(p)
                        || TopComponent.Registry.PROP_TC_OPENED.equals(p)
                        || TopComponent.Registry.PROP_OPENED.equals(p)) {
                    recheck();
                }
            };
            TopComponent.getRegistry().addPropertyChangeListener(registryListener);
        }
    }

    /** Brings every waiting session up to date; answers {@code done} for each one that finished. */
    static void recheck() {
        List<Session> finished = new ArrayList<>();
        synchronized (WAITING) {
            for (Session s : WAITING) {
                if (s.update(showing)) {
                    finished.add(s);
                }
            }
            WAITING.removeAll(finished);
            if (WAITING.isEmpty() && registryListener != null) {
                TopComponent.getRegistry().removePropertyChangeListener(registryListener);
                registryListener = null;
            }
        }
        for (Session s : finished) {
            EditRequestOption.answer(s.folder, "done", "");
            status.accept(PlainStatus.text(Bundle.EditRequestWatcher_handedBack(s.names)));
        }
    }

    /** The shutdown hook's answer: every request still waiting is done. */
    static void answerAll() {
        List<Session> all;
        synchronized (WAITING) {
            all = new ArrayList<>(WAITING);
            WAITING.clear();
        }
        for (Session s : all) {
            EditRequestOption.answer(s.folder, "done", "");
        }
    }

    /** The requests still waiting, for tests. */
    static int waiting() {
        synchronized (WAITING) {
            return WAITING.size();
        }
    }

    /** Whether {@code target} - a DataObject or a window - is open in any window. */
    private static boolean isShowing(Object target) {
        if (!SwingUtilities.isEventDispatchThread()) {
            LOG.log(Level.FINE, "isShowing off the EDT");
        }
        if (target instanceof TopComponent tc) {
            return tc.isOpened();
        }
        // the platform's own answer first: an editor's panes. A window whose
        // lookup merely HOLDS the file is not the file being open - Project
        // Studio publishes the selected file's node, so with the file selected
        // in the tree a closed tab would otherwise never count as closed
        if (target instanceof DataObject d) {
            org.openide.cookies.EditorCookie ec = d.getLookup().lookup(org.openide.cookies.EditorCookie.class);
            if (ec != null) {
                javax.swing.JEditorPane[] panes = ec.getOpenedPanes();
                return panes != null && panes.length > 0;
            }
        }
        // a file with no editor (an image): only a window that is a document
        // of its own - a cloneable one, as every file window is - counts
        for (TopComponent tc : TopComponent.getRegistry().getOpened()) {
            if (!(tc instanceof org.openide.windows.CloneableTopComponent)) {
                continue;
            }
            DataObject d = tc.getLookup().lookup(DataObject.class);
            if (d != null && d.equals(target)) {
                return true;
            }
        }
        return false;
    }
}
