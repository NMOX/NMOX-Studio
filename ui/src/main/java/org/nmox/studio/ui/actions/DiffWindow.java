package org.nmox.studio.ui.actions;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.netbeans.api.diff.DiffController;
import org.netbeans.api.diff.StreamSource;
import org.nmox.studio.core.util.PlainText;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Two files side by side in the platform's own diff view (3.2.0,
 * {@code nmox --diff a b}, and {@code git difftool} through it): the
 * graphical and textual panes and the colours the Team menu's diffs use,
 * under a bar of this window's own: Previous and Next Difference and where
 * the reader is ("Difference 2 of 5"), because the platform's controller
 * paints no toolbar and the stripe alone is a mouse-only way through a
 * long diff. Read-only: git hands a difftool temporary copies, and an edit
 * to one would be lost when the tool returns.
 *
 * <p>A file whose first bytes hold a NUL is handed over as
 * {@code application/octet-stream}, the one type the view shows as its
 * binary placeholder; any other type would paint the bytes as text.
 *
 * <p>Never persisted: a comparison of two temporary files is meaningless
 * after a restart.
 */
@Messages({
    "# {0} - the left file's name, {1} - the right file's name",
    "DiffWindow_name={0} ↔ {1}",
    "# {0} - the left file's path, {1} - the right file's path",
    "DiffWindow_tooltip={0} compared with {1}",
    "DiffWindow_previous=Previous Difference",
    "DiffWindow_next=Next Difference",
    "# {0} - the difference shown, {1} - how many there are",
    "DiffWindow_position=Difference {0} of {1}",
    "DiffWindow_none=The files are the same",
    "DiffWindow_bar=Differences",
    "# git names the missing side of an added or a deleted file /dev/null",
    "DiffWindow_nothing=no file",
    "DiffWindow_binaryDiffer=Binary files that differ"
})
final class DiffWindow extends TopComponent {

    /** The void constructor a TopComponent's Externalizable contract asks for; the window is never restored. */
    public DiffWindow() {
        setLayout(new BorderLayout());
    }

    /** The one type the diff view shows as its binary placeholder. */
    static final String BINARY = "application/octet-stream";

    private static final org.openide.util.RequestProcessor BYTES =
            new org.openide.util.RequestProcessor("nmox-diff-bytes", 1);

    /** How many bytes are read to decide whether a file is binary: git's own sniff is 8000. */
    static final int SNIFF = 8000;

    private DiffController diff;
    /** Binary sides only: whether their bytes differ, which the controller does not count. */
    private Boolean binaryDiffer;
    /** True for a binary pair: until the bytes are compared the bar says nothing rather than "the same". */
    private boolean binary;
    private final JLabel where = new JLabel();
    private final JButton previous = new JButton("\u2191");
    private final JButton next = new JButton("\u2193");
    private final PropertyChangeListener differences = e -> SwingUtilities.invokeLater(this::showWhere);

    private DiffWindow(DiffController diff, File left, File right) {
        this();
        this.diff = diff;
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 2));
        bar.getAccessibleContext().setAccessibleName(Bundle.DiffWindow_bar());
        for (JButton b : new JButton[] {previous, next}) {
            String label = b == previous ? Bundle.DiffWindow_previous() : Bundle.DiffWindow_next();
            b.setToolTipText(PlainText.plain(label));
            b.getAccessibleContext().setAccessibleName(label);
            b.setFocusable(true);
            bar.add(b);
        }
        previous.addActionListener(e -> go(-1));
        next.addActionListener(e -> go(1));
        bar.add(where);
        add(bar, BorderLayout.NORTH);
        add(diff.getJComponent(), BorderLayout.CENTER);
        setName(Bundle.DiffWindow_name(name(left), name(right)));
        setDisplayName(getName());
        setToolTipText(PlainText.plain(Bundle.DiffWindow_tooltip(
                left == null ? Bundle.DiffWindow_nothing() : left.getPath(),
                right == null ? Bundle.DiffWindow_nothing() : right.getPath())));
        getAccessibleContext().setAccessibleName(getName());
    }

    private static String name(File f) {
        return f == null ? Bundle.DiffWindow_nothing() : f.getName();
    }

    /**
     * Opens {@code left} and {@code right} side by side in the editor area;
     * a null side (git's {@code /dev/null}) is an empty one, typed like the
     * other so both panes colour alike. On the EDT.
     */
    static DiffWindow open(File left, File right) throws IOException {
        StreamSource l = left == null ? null : source(left);
        StreamSource r = right == null ? null : source(right);
        if (l == null) {
            l = StreamSource.createSource("", Bundle.DiffWindow_nothing(), r.getMIMEType(), new java.io.StringReader(""));
        }
        if (r == null) {
            r = StreamSource.createSource("", Bundle.DiffWindow_nothing(), l.getMIMEType(), new java.io.StringReader(""));
        }
        DiffController diff = DiffController.createEnhanced(l, r);
        DiffWindow w = new DiffWindow(diff, left, right);
        if (BINARY.equals(l.getMIMEType()) || BINARY.equals(r.getMIMEType())) {
            w.binary = true;
            // the view paints "<Binary File>" twice and counts no difference
            // at all, so the bar would say the files are the same (walked in
            // 3.2.0 on two PNGs one byte apart): compare the bytes here
            if (left == null || right == null) {
                w.binaryDiffer = Boolean.TRUE;
            } else {
                // off the EDT: two large binaries are read to the first byte that differs
                BYTES.post(() -> {
                    boolean differ;
                    try {
                        differ = Files.mismatch(left.toPath(), right.toPath()) >= 0;
                    } catch (IOException unreadable) {
                        differ = true;
                    }
                    boolean d = differ;
                    SwingUtilities.invokeLater(() -> {
                        w.binaryDiffer = d;
                        w.showWhere();
                    });
                });
            }
        }
        Mode editor = WindowManager.getDefault().findMode("editor");
        if (editor != null) {
            editor.dockInto(w);
        }
        w.open();
        w.requestActive();
        return w;
    }

    private static StreamSource source(File f) throws IOException {
        if (!f.isFile()) {
            throw new IOException(f.getPath() + ": no such file");
        }
        // the FileObject only names the type, for the panes' colouring
        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(f));
        byte[] head;
        try (InputStream in = Files.newInputStream(f.toPath())) {
            head = in.readNBytes(SNIFF);
        }
        String mime = looksBinary(head) ? BINARY : fo == null ? "text/plain" : fo.getMIMEType();
        return StreamSource.createSource(f.getName(), f.getPath(), mime, f);
    }

    /** Git's rule: a NUL in the first bytes makes a file binary. */
    static boolean looksBinary(byte[] head) {
        for (byte b : head) {
            if (b == 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * The difference a step lands on: {@code delta} from {@code index}, kept
     * inside the list; -1 when there is none. The controller reports -1
     * before anything was chosen while its own divider already presents the
     * first difference ("1/2"), so -1 counts as the first: the first press
     * of Next goes to the second, as the reader expects (walked in 3.2.0,
     * where it spent a press landing on the difference already shown).
     */
    static int step(int index, int count, int delta) {
        if (count <= 0) {
            return -1;
        }
        int at = Math.min(Math.max(index, 0), count - 1);
        return Math.max(0, Math.min(count - 1, at + delta));
    }

    /** The bar's sentence: which difference is shown, or that there are none. */
    static String position(int index, int count) {
        return count <= 0 ? Bundle.DiffWindow_none() : Bundle.DiffWindow_position(Math.max(index, 0) + 1, count);
    }

    private void go(int delta) {
        int target = step(diff.getDifferenceIndex(), diff.getDifferenceCount(), delta);
        if (target >= 0) {
            diff.setLocation(DiffController.DiffPane.Modified, DiffController.LocationType.DifferenceIndex, target);
        }
        showWhere();
    }

    private void showWhere() {
        if (diff == null) {
            return;
        }
        int count = diff.getDifferenceCount();
        int index = diff.getDifferenceIndex();
        where.setText(PlainText.plain(binary && binaryDiffer == null ? ""
                : Boolean.TRUE.equals(binaryDiffer) ? Bundle.DiffWindow_binaryDiffer() : position(index, count)));
        int at = Math.max(index, 0);
        previous.setEnabled(count > 0 && at > 0);
        next.setEnabled(count > 0 && at < count - 1);
    }

    /** Tests: the bar brought up to date, and what it says. EDT. */
    void showWhereForTest() {
        showWhere();
    }

    String whereTextForTest() {
        return where.getText().strip();
    }

    /** Tests: waits until every pending byte comparison has run and painted. */
    static void awaitBytesForTest() throws Exception {
        BYTES.post(() -> { }).waitFinished();
        SwingUtilities.invokeAndWait(() -> { });
    }

    @Override
    protected void componentOpened() {
        if (diff != null) {
            diff.addPropertyChangeListener(differences);
            showWhere();
        }
    }

    @Override
    protected void componentClosed() {
        if (diff != null) {
            diff.removePropertyChangeListener(differences);
        }
    }

    @Override
    public int getPersistenceType() {
        return PERSISTENCE_NEVER;
    }

    @Override
    protected String preferredID() {
        return "NmoxDiff";
    }
}
