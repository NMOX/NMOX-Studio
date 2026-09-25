package org.nmox.studio.editor.blame;

import java.awt.Component;
import java.awt.EventQueue;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.prefs.PreferenceChangeListener;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.Timer;
import javax.swing.event.CaretListener;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorRegistry;
import org.nmox.studio.core.util.PlainText;
import org.nmox.studio.rack.service.GitStatusLine;
import org.openide.awt.StatusLineElementProvider;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;

/**
 * Who wrote the line the caret is on, as a quiet note on the status line
 * (3.2.0): {@code Ada Lovelace, 3 days ago · Fix the parser}. A click opens
 * the whole file's annotations — the git module's own Annotate, through the
 * git chip's door ({@link GitStatusLine#showAnnotations}).
 *
 * <p><b>When it says nothing.</b> No focused editor yet (so nothing runs at
 * boot until an editor has had focus), a document that is not a file on
 * disk, a file outside a repository, an untracked file, a file over
 * {@link LineBlame#MAX_FILE_BYTES}, or View ▸ Line Blame switched off.
 *
 * <p><b>Unsaved changes.</b> Blame reads the file as saved: once the buffer
 * differs, line 40 on screen need not be line 40 on disk, so a name next to
 * it could be the author of some other line. The note says {@code unsaved
 * changes} instead of attributing a line wrongly, and returns on save —
 * saying nothing would read as the feature having stopped.
 *
 * <p>The author and the summary are a repository's data, written by anyone:
 * they are painted as text ({@link PlainText#plain}), never as markup.
 */
@ServiceProvider(service = StatusLineElementProvider.class, position = 588)
@Messages({
    "# {0} - author",
    "# {1} - when, e.g. 3 days ago",
    "# {2} - the commit's summary line",
    "BlameStatusLine_line={0}, {1} · {2}",
    "BlameStatusLine_uncommitted=Not committed yet",
    "BlameStatusLine_unsaved=Line blame: unsaved changes",
    "# {0} - short commit id",
    "# {1} - author",
    "# {2} - date and time",
    "BlameStatusLine_tooltip=Commit {0} by {1}, {2}. Click to see who last changed every line of the file.",
    "BlameStatusLine_tooltipUncommitted=This line has changes that are not committed yet. Click to see who last changed every line of the file.",
    "BlameStatusLine_tooltipUnsaved=Line blame reads the file as it is saved on disk; save to see who last changed this line.",
    "BlameStatusLine_name=Line blame"
})
public final class BlameStatusLine implements StatusLineElementProvider {

    /** Longest author shown, in code points; the rest is an ellipsis. */
    static final int AUTHOR_MAX = 32;
    /** Longest summary shown, in code points. */
    static final int SUMMARY_MAX = 60;
    /** Caret moves settle this long before a lookup. */
    static final int DEBOUNCE_MS = 300;

    @Override
    public Component getStatusLineElement() {
        return new Strip(LineBlame.create());
    }

    /** The note's text for a line's commit, or null for nothing to say. Pure. */
    static String text(BlamePorcelain.Line entry, long nowMillis) {
        if (entry == null) {
            return null;
        }
        if (entry.uncommitted()) {
            return Bundle.BlameStatusLine_uncommitted();
        }
        return Bundle.BlameStatusLine_line(clip(entry.author(), AUTHOR_MAX),
                RelativeTime.ago(entry.authorTime() * 1000L, nowMillis),
                clip(entry.summary(), SUMMARY_MAX));
    }

    /** The note's tooltip for a line's commit. Pure but for the reader's zone and locale. */
    static String tooltip(BlamePorcelain.Line entry) {
        if (entry.uncommitted()) {
            return Bundle.BlameStatusLine_tooltipUncommitted();
        }
        // built per call so a live language switch reaches it (the Clocks rule)
        String when = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(Locale.getDefault())
                .format(Instant.ofEpochSecond(entry.authorTime()).atZone(ZoneId.systemDefault()));
        return Bundle.BlameStatusLine_tooltip(entry.shortSha(), clip(entry.author(), AUTHOR_MAX), when);
    }

    /** {@code s} cut to {@code max} code points with an ellipsis; never splits a surrogate pair. */
    static String clip(String s, int max) {
        String t = s == null ? "" : s.strip();
        if (t.codePointCount(0, t.length()) <= max) {
            return t;
        }
        return t.substring(0, t.offsetByCodePoints(0, max - 1)) + "…";
    }

    /** The label on the status line. Listens only while it is in the status bar. */
    /**
     * Whether the user has pressed a key or a mouse button in this session.
     * Zero processes at boot is a house law (v1.38.0), and the window system
     * restores and focuses the last editor at startup: without this gate that
     * restore alone would run {@code git blame}. The first real gesture arms
     * the note; a seam for tests.
     */
    static volatile boolean gestureSeen;
    static final java.util.concurrent.atomic.AtomicBoolean GESTURE_WATCH =
            new java.util.concurrent.atomic.AtomicBoolean();

    /** Listens once, app-wide, for the first key or mouse press; then runs {@code then} and stops listening. */
    static void armOnFirstGesture(Runnable then) {
        if (gestureSeen || !GESTURE_WATCH.compareAndSet(false, true)) {
            return;
        }
        java.awt.Toolkit tk = java.awt.Toolkit.getDefaultToolkit();
        java.awt.event.AWTEventListener[] self = new java.awt.event.AWTEventListener[1];
        self[0] = e -> {
            int id = e.getID();
            if (id == java.awt.event.KeyEvent.KEY_PRESSED || id == java.awt.event.MouseEvent.MOUSE_PRESSED) {
                gestureSeen = true;
                tk.removeAWTEventListener(self[0]);
                then.run();
            }
        };
        tk.addAWTEventListener(self[0], java.awt.AWTEvent.KEY_EVENT_MASK | java.awt.AWTEvent.MOUSE_EVENT_MASK);
    }

    static final class Strip extends JLabel {

        private final LineBlame blame;
        private final Timer debounce;
        private JTextComponent target;
        private DataObject targetDob;
        private File shownFile;

        private final CaretListener caretListener = e -> schedule();
        private final PropertyChangeListener dobListener = e -> {
            if (DataObject.PROP_MODIFIED.equals(e.getPropertyName())) {
                onEdt(this::schedule);
            }
        };
        private final PropertyChangeListener registryListener = e -> onEdt(this::retarget);
        private final PreferenceChangeListener prefsListener = e -> {
            if (BlamePrefs.KEY.equals(e.getKey())) {
                onEdt(this::schedule);
            }
        };

        Strip(LineBlame blame) {
            this.blame = blame;
            this.debounce = new Timer(DEBOUNCE_MS, e -> update());
            debounce.setRepeats(false);
            putClientProperty("html.disable", Boolean.TRUE);
            setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            getAccessibleContext().setAccessibleName(Bundle.BlameStatusLine_name());
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    if (shownFile != null) {
                        GitStatusLine.showAnnotations(shownFile, Strip.this);
                    }
                }
            });
            setVisible(false);
        }

        @Override
        public void addNotify() {
            super.addNotify();
            armOnFirstGesture(this::schedule);
            EditorRegistry.addPropertyChangeListener(registryListener);
            BlamePrefs.prefs().addPreferenceChangeListener(prefsListener);
            retarget();
        }

        @Override
        public void removeNotify() {
            EditorRegistry.removePropertyChangeListener(registryListener);
            BlamePrefs.prefs().removePreferenceChangeListener(prefsListener);
            attach(null);
            debounce.stop();
            blame.cancel();
            super.removeNotify();
        }

        private static void onEdt(Runnable r) {
            if (EventQueue.isDispatchThread()) {
                r.run();
            } else {
                EventQueue.invokeLater(r);
            }
        }

        /**
         * Follows the editor that has focus. Focus leaving for another window
         * (or for this very label) keeps the last editor's note; closing that
         * editor drops it. Only a component that really had focus is ever
         * targeted, so nothing runs before the user is in an editor.
         */
        void retarget() {
            JTextComponent focused = EditorRegistry.focusedComponent();
            if (focused != null && focused != target) {
                attach(focused);
                schedule();
            } else if (target != null && !EditorRegistry.componentList().contains(target)) {
                attach(null);
                schedule();
            }
        }

        /** Moves the caret listener to {@code c}; EDT only. */
        void attach(JTextComponent c) {
            // an answer still in flight belongs to the editor being left: it
            // must not paint under the next one, nor a click annotate it
            blame.cancel();
            if (target != null) {
                target.removeCaretListener(caretListener);
            }
            target = c;
            if (target != null) {
                target.addCaretListener(caretListener);
            }
            watch(null);
        }

        private void watch(DataObject dob) {
            if (dob == targetDob) {
                return;
            }
            if (targetDob != null) {
                targetDob.removePropertyChangeListener(dobListener);
            }
            targetDob = dob;
            if (targetDob != null) {
                targetDob.addPropertyChangeListener(dobListener);
            }
        }

        void schedule() {
            debounce.restart();
        }

        /** The debounced step, on the EDT: decide what can be asked, then ask off the EDT. */
        void update() {
            if (!BlamePrefs.enabled() || target == null || !gestureSeen) {
                blame.cancel();
                showNothing();
                return;
            }
            Document doc = target.getDocument();
            Object described = doc.getProperty(Document.StreamDescriptionProperty);
            DataObject dob = described instanceof DataObject d ? d : null;
            watch(dob);
            File file = dob == null ? null : FileUtil.toFile(dob.getPrimaryFile());
            if (file == null) {
                blame.cancel();
                showNothing();
                return;
            }
            if (dob.isModified()) {
                blame.cancel();
                showUnsaved();
                return;
            }
            int line = doc.getDefaultRootElement().getElementIndex(target.getCaretPosition()) + 1;
            blame.request(file, line, answer -> EventQueue.invokeLater(() -> {
                if (blame.isCurrent(answer.generation())) {
                    show(answer);
                }
            }));
        }

        /** Paints one answer; EDT only. */
        void show(LineBlame.Answer answer) {
            String text = text(answer.entry(), System.currentTimeMillis());
            if (text == null) {
                showNothing();
                return;
            }
            shownFile = answer.file();
            setText(PlainText.plain(text));
            setToolTipText(PlainText.plain(tooltip(answer.entry())));
            getAccessibleContext().setAccessibleDescription(getToolTipText());
            setVisible(true);
        }

        private void showUnsaved() {
            shownFile = null;
            setText(PlainText.plain(Bundle.BlameStatusLine_unsaved()));
            setToolTipText(PlainText.plain(Bundle.BlameStatusLine_tooltipUnsaved()));
            getAccessibleContext().setAccessibleDescription(getToolTipText());
            setVisible(true);
        }

        private void showNothing() {
            shownFile = null;
            setText("");
            setToolTipText(null);
            setVisible(false);
        }

        /** The file the note speaks about now, or null. */
        File shownFile() {
            return shownFile;
        }
    }
}
