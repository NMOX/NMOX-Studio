package org.nmox.studio.editor.share;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.function.Consumer;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.nmox.studio.core.spi.ProjectAim;
import org.nmox.studio.core.util.PathLabel;
import org.nmox.studio.core.util.PlainStatus;
import org.openide.awt.StatusDisplayer;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.NbBundle.Messages;

/**
 * Edit ▸ Copy Path and Copy Relative Path, ⌥⌘C and ⇧⌥⌘C as in VS Code
 * (3.2.0): the path of the file in the editor you were last typing in,
 * onto the clipboard — for a PR comment, a test command, an agent's prompt.
 * Project Studio's tree had both rows since 3.1.0; the editor had only the
 * platform's Copy Path on a tab's right-click, and no relative path at all.
 *
 * <p>"The editor you were last typing in" is {@link
 * EditorRegistry#lastFocusedComponent()}, so the chord works from the
 * Terminal or the tree beside it, as VS Code's active file does. The
 * relative path is relative to the aimed project when the file is inside
 * it (the tree's rule), else to the project that owns the file, else it is
 * the absolute path — {@link PathLabel#relative}, one rule for both.
 */
@Messages({
    "# {0} - the path copied",
    "CopyFilePathAction_copied=Copied {0}",
    "CopyFilePathAction_noFile=Copy Path: no editor holds a file on disk"
})
abstract class CopyFilePathAction implements ActionListener {

    /** Where the path goes; a seam for tests. */
    static Consumer<String> clipboard = text -> Toolkit.getDefaultToolkit().getSystemClipboard()
            .setContents(new StringSelection(text), null);

    /** Where it is said; a seam for tests. */
    static Consumer<String> status = text -> StatusDisplayer.getDefault()
            .setStatusText(PlainStatus.text(text));

    /** The path this gesture copies for {@code file}. */
    abstract String pathOf(File file);

    @Override
    public final void actionPerformed(ActionEvent e) {
        File file = fileOf(EditorRegistry.lastFocusedComponent());
        if (file == null) {
            status.accept(Bundle.CopyFilePathAction_noFile());
            return;
        }
        // the relative path may ask FileOwnerQuery, which walks the disk: off
        // the EDT, and the clipboard and the status line back on it
        LANE.post(() -> {
            String path = pathOf(file);
            java.awt.EventQueue.invokeLater(() -> {
                clipboard.accept(path);
                status.accept(Bundle.CopyFilePathAction_copied(path));
            });
        });
    }

    private static final org.openide.util.RequestProcessor LANE =
            new org.openide.util.RequestProcessor("nmox-copy-path", 1);

    /** The file on disk behind an editor, or null. */
    static File fileOf(JTextComponent editor) {
        FileObject fo = org.nmox.studio.core.util.EditedFile.of(editor == null ? null : editor.getDocument());
        return fo == null ? null : FileUtil.toFile(fo);
    }

    /** The folder a relative path starts from: the aimed project holding it, else its owner, else none. */
    static File rootFor(File file) {
        ProjectAim aim = ProjectAim.find();
        return rootFor(file, aim == null ? null : aim.projectDir());
    }

    /** {@link #rootFor(File)} with the aimed project handed in. */
    static File rootFor(File file, File aimed) {
        if (aimed != null && file.toPath().toAbsolutePath().normalize()
                .startsWith(aimed.toPath().toAbsolutePath().normalize())) {
            return aimed;
        }
        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(file));
        Project owner = fo == null ? null : FileOwnerQuery.getOwner(fo);
        return owner == null ? null : FileUtil.toFile(owner.getProjectDirectory());
    }

    /** Edit ▸ Copy Path: the absolute path. */
    @org.openide.awt.ActionID(category = "Edit", id = "org.nmox.studio.editor.share.CopyFilePathAction.Absolute")
    @org.openide.awt.ActionRegistration(displayName = "#CTL_CopyFilePath", lazy = true)
    @org.openide.awt.ActionReference(path = "Menu/Edit", position = 1374)
    @Messages("CTL_CopyFilePath=Copy Path")
    public static final class Absolute extends CopyFilePathAction {
        @Override
        String pathOf(File file) {
            return file.getAbsolutePath();
        }
    }

    /** Edit ▸ Copy Relative Path: relative to the project. */
    @org.openide.awt.ActionID(category = "Edit", id = "org.nmox.studio.editor.share.CopyFilePathAction.Relative")
    @org.openide.awt.ActionRegistration(displayName = "#CTL_CopyRelativeFilePath", lazy = true)
    @org.openide.awt.ActionReference(path = "Menu/Edit", position = 1375)
    @Messages("CTL_CopyRelativeFilePath=Copy Relative Path")
    public static final class Relative extends CopyFilePathAction {
        @Override
        String pathOf(File file) {
            return PathLabel.relative(rootFor(file), file);
        }
    }
}
