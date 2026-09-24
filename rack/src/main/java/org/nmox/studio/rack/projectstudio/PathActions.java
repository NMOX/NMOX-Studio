package org.nmox.studio.rack.projectstudio;

import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.nmox.studio.core.util.PlainStatus;
import org.openide.awt.StatusDisplayer;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.nodes.Node;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;
import org.openide.util.Utilities;

/**
 * The three rows a switcher reaches for on a file tree's right-click (3.1.0):
 * VS Code's Explorer offers Copy Path, Copy Relative Path and Reveal in
 * Finder / File Explorer, and Project Studio's tree offered none of them -
 * the platform's Copy Path sits only on an editor tab, and nothing showed
 * a file in the operating system's file manager.
 *
 * <p>The relative path is relative to the aimed project, in the operating
 * system's own separators, as VS Code copies it. Revealing goes through
 * {@link Desktop} (select the file in Finder or File Explorer where the
 * platform can, else open its folder) on this class's own lane: the call
 * can block on the file manager, and it launches nothing the project
 * chose.
 */
@Messages({
    "PathActions_copyPath=Copy Path",
    "PathActions_copyRelativePath=Copy Relative Path",
    "PathActions_revealMac=Reveal in Finder",
    "PathActions_revealWindows=Reveal in File Explorer",
    "PathActions_revealOther=Open Containing Folder",
    "# {0} - the path copied",
    "PathActions_copied=Copied {0}",
    "# {0} - the file or folder",
    "PathActions_revealFailed=Could not show {0} in the file manager"
})
final class PathActions {

    private static final RequestProcessor RP = new RequestProcessor("Project Studio reveal", 1);

    enum Os { MAC, WINDOWS, OTHER }

    private PathActions() {
    }

    /** The three rows for {@code node}'s file, or none when it has no file on disk. */
    static Action[] forNode(Node node) {
        FileObject fo = node.getLookup().lookup(FileObject.class);
        File file = fo == null ? null : FileUtil.toFile(fo);
        if (file == null) {
            return new Action[0];
        }
        return new Action[] {
            copy(Bundle.PathActions_copyPath(), () -> file.getAbsolutePath()),
            copy(Bundle.PathActions_copyRelativePath(), () -> relativePath(aimedRoot(), file)),
            reveal(file),
        };
    }

    /**
     * {@code file} relative to {@code root}, in the platform's separators;
     * the absolute path when there is no root or the file is outside it
     * (a relative path that climbs out would name somewhere else).
     */
    static String relativePath(File root, File file) {
        if (root == null) {
            return file.getAbsolutePath();
        }
        Path r = root.toPath().toAbsolutePath().normalize();
        Path f = file.toPath().toAbsolutePath().normalize();
        if (!f.startsWith(r)) {
            return file.getAbsolutePath();
        }
        String rel = r.relativize(f).toString();
        return rel.isEmpty() ? "." : rel;
    }

    /** The reveal row's name, in the file manager's own name for the OS. */
    static String revealLabel(Os os) {
        return switch (os) {
            case MAC -> Bundle.PathActions_revealMac();
            case WINDOWS -> Bundle.PathActions_revealWindows();
            case OTHER -> Bundle.PathActions_revealOther();
        };
    }

    static Os os() {
        return Utilities.isMac() ? Os.MAC : Utilities.isWindows() ? Os.WINDOWS : Os.OTHER;
    }

    private static File aimedRoot() {
        org.nmox.studio.core.spi.ProjectAim aim = org.nmox.studio.core.spi.ProjectAim.find();
        return aim == null ? null : aim.projectDir();
    }

    private static Action copy(String name, java.util.function.Supplier<String> text) {
        return new AbstractAction(name) {
            @Override
            public void actionPerformed(ActionEvent e) {
                String value = text.get();
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(value), null);
                StatusDisplayer.getDefault().setStatusText(PlainStatus.text(Bundle.PathActions_copied(value)));
            }
        };
    }

    private static Action reveal(File file) {
        return new AbstractAction(revealLabel(os())) {
            @Override
            public void actionPerformed(ActionEvent e) {
                RP.post(() -> {
                    try {
                        Desktop desktop = Desktop.getDesktop();
                        if (desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
                            desktop.browseFileDirectory(file);
                        } else {
                            File folder = file.isDirectory() ? file : file.getParentFile();
                            desktop.open(folder);
                        }
                    } catch (IOException | RuntimeException failed) {
                        StatusDisplayer.getDefault().setStatusText(
                                PlainStatus.text(Bundle.PathActions_revealFailed(file.getName())));
                    }
                });
            }
        };
    }
}
