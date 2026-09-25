package org.nmox.studio.rack.projectstudio;

import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.nmox.studio.core.util.PlainStatus;
import org.nmox.studio.rack.service.GitHubLinks;
import org.openide.awt.StatusDisplayer;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.nodes.Node;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;
import org.openide.util.Utilities;

/**
 * The rows a switcher reaches for on a file tree's right-click (3.1.0):
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
 *
 * <p>3.2.0 adds Open on GitHub and Copy GitHub Link, the gestures a
 * developer reaches for to share a file or a folder as the repository
 * shows it; their ladder lives in {@link GitHubLinks}, shared with the
 * editor.
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

    /**
     * The path rows for {@code node}'s file or folder — Copy Path, Copy Relative
     * Path, Reveal, then (behind a separator) Open on GitHub and Copy GitHub
     * Link — or none when it has no file on disk.
     */
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
            null,
            github(GitHubLinks.Gesture.OPEN, file),
            github(GitHubLinks.Gesture.COPY, file),
        };
    }

    /**
     * Open on GitHub / Copy GitHub Link (3.2.0): a file links its blob as
     * pushed (a tree row has no lines), a folder its tree, the repository's
     * root folder {@code tree/<ref>}. The tree has no buffer, so there is
     * no unsaved-changes refusal here; every other rung of the ladder is
     * {@link GitHubLinks}', off the EDT.
     */
    private static Action github(GitHubLinks.Gesture gesture, File file) {
        return new AbstractAction(GitHubLinks.label(gesture)) {
            @Override
            public void actionPerformed(ActionEvent e) {
                GitHubLinks.perform(gesture, file, 0, 0);
            }
        };
    }

    /**
     * {@code file} relative to {@code root}, in the platform's separators;
     * the absolute path when there is no root or the file is outside it
     * (a relative path that climbs out would name somewhere else).
     */
    static String relativePath(File root, File file) {
        return org.nmox.studio.core.util.PathLabel.relative(root, file);
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
