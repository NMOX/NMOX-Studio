package org.nmox.studio.ui.shots;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import javax.swing.SwingUtilities;
import org.nmox.studio.core.spi.ProjectAim;
import org.nmox.studio.core.util.Markdown;
import org.nmox.studio.core.util.PlainStatus;
import org.nmox.studio.core.util.Plural;
import org.nmox.studio.core.util.TreeText;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.awt.StatusDisplayer;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;

/**
 * Tools ▸ Copy Project Tree as Markdown (v2.87.0): the aimed project's
 * layout as the fenced box-drawing tree a README or a post shows —
 * directories first, {@code node_modules/ …} named but never entered,
 * depth and entry caps with the remainder counted. The walk is disk, so
 * it rides a named RequestProcessor; the clipboard and the status line
 * are set back on the EDT. No project aimed is a spoken refusal.
 */
@ActionID(category = "Tools", id = "org.nmox.studio.ui.shots.CopyProjectTreeAction")
@ActionRegistration(displayName = "#CTL_CopyProjectTree", lazy = true)
@ActionReference(path = "Menu/Tools", position = 103)
@Messages("CTL_CopyProjectTree=Copy Project Tree as Markdown")
public final class CopyProjectTreeAction implements ActionListener {

    static final int MAX_DEPTH = 4;
    static final int MAX_ENTRIES = 200;
    private static final RequestProcessor RP = new RequestProcessor("nmox-share-tree", 1, true);

    @Override
    public void actionPerformed(ActionEvent e) {
        ProjectAim aim = ProjectAim.find();
        File dir = aim == null ? null : aim.projectDir();
        if (dir == null || !dir.isDirectory()) {
            StatusDisplayer.getDefault().setStatusText("Copy Project Tree: no project is aimed");
            return;
        }
        RP.post(() -> {
            TreeText.Result tree = TreeText.render(dir.toPath(), MAX_DEPTH, MAX_ENTRIES);
            String fence = Markdown.fenceFor(tree.text());
            String block = fence + "text\n" + tree.text() + fence + "\n";
            SwingUtilities.invokeLater(() -> {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(block), null);
                long lines = tree.text().lines().count() - 1 - (tree.elided() > 0 ? 1 : 0);
                StatusDisplayer.getDefault().setStatusText(PlainStatus.text("Copied the project tree of " + dir.getName()
                        + " as Markdown — " + Plural.of(lines, "entry", "entries")
                        + (tree.elided() > 0 ? " (" + tree.elided() + " more not shown)" : "")));
            });
        });
    }
}
