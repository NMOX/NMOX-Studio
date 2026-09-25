package org.nmox.studio.ui.actions;

import java.awt.BorderLayout;
import java.io.File;
import java.io.IOException;

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
 * graphical and textual panes, the change navigation and the colours the
 * Team menu's diffs use. Read-only: git hands a difftool temporary copies,
 * and an edit to one would be lost when the tool returns.
 *
 * <p>Never persisted: a comparison of two temporary files is meaningless
 * after a restart.
 */
@Messages({
    "# {0} - the left file's name, {1} - the right file's name",
    "DiffWindow_name={0} ↔ {1}",
    "# {0} - the left file's path, {1} - the right file's path",
    "DiffWindow_tooltip={0} compared with {1}"
})
final class DiffWindow extends TopComponent {

    private DiffWindow(DiffController diff, File left, File right) {
        setLayout(new BorderLayout());
        add(diff.getJComponent(), BorderLayout.CENTER);
        setName(Bundle.DiffWindow_name(left.getName(), right.getName()));
        setDisplayName(getName());
        setToolTipText(PlainText.plain(Bundle.DiffWindow_tooltip(left.getPath(), right.getPath())));
        getAccessibleContext().setAccessibleName(getName());
    }

    /** Opens {@code left} and {@code right} side by side in the editor area. On the EDT. */
    static DiffWindow open(File left, File right) throws IOException {
        DiffController diff = DiffController.createEnhanced(source(left), source(right));
        DiffWindow w = new DiffWindow(diff, left, right);
        Mode editor = WindowManager.getDefault().findMode("editor");
        if (editor != null) {
            editor.dockInto(w);
        }
        w.open();
        w.requestActive();
        return w;
    }

    private static StreamSource source(File f) throws IOException {
        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(f));
        if (fo == null) {
            throw new IOException(f.getPath() + ": no such file");
        }
        return StreamSource.createSource(f.getName(), f.getPath(), fo.getMIMEType(), f);
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
