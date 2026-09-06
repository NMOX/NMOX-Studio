// its own subpackage on purpose: @Messages and @EditorActionRegistration
// each generate the package Bundle, and two generators clobber one
// Bundle.properties (the v1.79.0 lesson) — RunFocusedTestAction owns
// org.nmox.studio.editor.testing's bundle
package org.nmox.studio.editor.testing.explorer;

import org.nmox.studio.core.spi.LiveRuns;
import java.awt.BorderLayout;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.nmox.studio.core.spi.ProjectAim;
import org.nmox.studio.editor.testing.RunFocusedTestAction;
import org.nmox.studio.editor.testing.TestIndex;
import org.nmox.studio.editor.testing.TestIndex.DiscoveredTest;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.StatusDisplayer;
import org.openide.cookies.LineCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.text.Line;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;
import org.openide.windows.TopComponent;

/**
 * The Tests window (competitive-lens R3): the aimed project's tests
 * VISIBLE before anything runs — a tree of every declaration the
 * focused runner knows how to run (one vocabulary: {@link TestIndex}
 * scans with {@link RunFocusedTestAction}'s own patterns), double-click
 * opens the declaration, Run executes exactly that test through the
 * same trust-gated machinery as the editor gesture. Zero boot cost
 * (openAtStartup false, all work behind componentShowing), index off
 * the EDT on a named RP with newest-wins, plain rendering on every
 * label (test names are file content — external text).
 */
@TopComponent.Registration(mode = "explorer", openAtStartup = false, position = 80)
@TopComponent.Description(preferredID = "TestsExplorerTopComponent",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS)
@ActionID(category = "Window", id = "org.nmox.studio.editor.testing.explorer.TestsExplorerTopComponent")
@ActionReferences({
    @ActionReference(path = "Menu/Window", position = 261),
    @ActionReference(path = "Shortcuts", name = "DA-2")
})
@TopComponent.OpenActionRegistration(displayName = "#CTL_TestsExplorerAction",
        preferredID = "TestsExplorerTopComponent")
@Messages({
    "CTL_TestsExplorerAction=Tests",
    "CTL_TestsExplorerTopComponent=Tests"
})
public final class TestsExplorerTopComponent extends TopComponent {

    private static final RequestProcessor RP =
            new RequestProcessor("nmox-tests-index", 1, true);

    private final TestIndex index = new TestIndex();
    private final DefaultMutableTreeNode rootNode =
            new DefaultMutableTreeNode("Tests");
    private final DefaultTreeModel model = new DefaultTreeModel(rootNode);
    private final JTree tree = new JTree(model);
    private final JLabel status = new JLabel(" ");
    private final ProjectAim.Listener aimListener =
            () -> java.awt.EventQueue.invokeLater(this::aimChanged);
    private volatile long refreshSeq;

    public TestsExplorerTopComponent() {
        setName(Bundle.CTL_TestsExplorerTopComponent());
        setToolTipText("Every test the focused runner can run, before anything runs");
        setLayout(new BorderLayout());

        // test names are FILE CONTENT — a name spelled <html><img src>
        // must paint as characters, never render (the v1.306.0 law; the
        // one spelling since v2.70.0's JTree gate)
        tree.setCellRenderer(org.nmox.studio.core.util.PlainTables.plain(new DefaultTreeCellRenderer()));
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.getAccessibleContext().setAccessibleName("Discovered tests");

        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> refreshAsync());
        refresh.getAccessibleContext().setAccessibleName("Refresh test list");
        JButton run = new JButton("Run");
        run.addActionListener(e -> runSelected());
        run.getAccessibleContext().setAccessibleName("Run selected test");
        // the window's own Stop (v2.73.0): enabled while a focused-test run
        // is live (the lane joined the ■ in v2.70.0; this stops ONLY test
        // runs, the toolbar ■ stops everything), following LiveRuns while
        // the window shows
        stop.addActionListener(e -> {
            int stopped = TestRunsStop.stopAll();
            StatusDisplayer.getDefault().setStatusText(org.nmox.studio.core.util.PlainStatus.text(stopped == 0
                    ? "No test run to stop" : "Stopped " + stopped + " test run" + (stopped == 1 ? "" : "s")));
        });
        stop.getAccessibleContext().setAccessibleName("Stop running test");
        stop.setEnabled(false);
        bar.add(refresh);
        bar.add(run);
        bar.add(stop);
        add(bar, BorderLayout.NORTH);
        add(new JScrollPane(tree), BorderLayout.CENTER);
        status.getAccessibleContext().setAccessibleName("Test discovery status");
        add(status, BorderLayout.SOUTH);

        tree.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelected();
                }
            }
        });
        tree.registerKeyboardAction(e -> openSelected(),
                javax.swing.KeyStroke.getKeyStroke("ENTER"),
                javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    @Override
    protected void componentShowing() {
        ProjectAim aim = Lookup.getDefault().lookup(ProjectAim.class);
        if (aim != null) {
            aim.addListener(aimListener);
        }
        LiveRuns.addListener(runsListener);
        followRuns();
        refreshAsync();
    }

    @Override
    protected void componentHidden() {
        ProjectAim aim = Lookup.getDefault().lookup(ProjectAim.class);
        if (aim != null) {
            aim.removeListener(aimListener);
        }
        LiveRuns.removeListener(runsListener);
    }

    private final JButton stop = new JButton("Stop");

    /** Any-thread listener: the Stop follows the test runs on the EDT. */
    private final Runnable runsListener = () -> javax.swing.SwingUtilities.invokeLater(this::followRuns);

    private void followRuns() {
        stop.setEnabled(TestRunsStop.anyLive());
    }

    private void aimChanged() {
        if (isShowing()) {
            refreshAsync();
        }
    }

    private void refreshAsync() {
        ProjectAim aim = Lookup.getDefault().lookup(ProjectAim.class);
        File dir = aim == null ? null : aim.projectDir();
        if (dir == null) {
            rootNode.removeAllChildren();
            model.reload();
            status.setText("Aim a project to list its tests.");
            return;
        }
        long seq = ++refreshSeq;
        status.setText("Scanning " + dir.getName() + "…");
        RP.post(() -> {
            Map<Path, List<DiscoveredTest>> found = index.refresh(dir.toPath(),
                    TestsExplorerTopComponent::mimeOf, v -> seq != refreshSeq);
            boolean truncated = index.wasTruncated();
            if (seq != refreshSeq) {
                return; // a newer aim or refresh superseded this walk
            }
            java.awt.EventQueue.invokeLater(() -> apply(dir.toPath(), found, truncated));
        });
    }

    private void apply(Path root, Map<Path, List<DiscoveredTest>> found,
            boolean truncated) {
        rootNode.removeAllChildren();
        int tests = 0;
        for (Map.Entry<Path, List<DiscoveredTest>> e : found.entrySet()) {
            String rel;
            try {
                rel = root.relativize(e.getKey()).toString()
                        .replace(File.separatorChar, '/');
            } catch (IllegalArgumentException ex) {
                rel = e.getKey().getFileName().toString();
            }
            DefaultMutableTreeNode fileNode = new DefaultMutableTreeNode(rel);
            for (DiscoveredTest t : e.getValue()) {
                fileNode.add(new TestNode(t));
                tests++;
            }
            rootNode.add(fileNode);
        }
        model.reload();
        for (int i = 0; i < tree.getRowCount() && i < 40; i++) {
            tree.expandRow(i);
        }
        status.setText(tests + (tests == 1 ? " test in " : " tests in ")
                + found.size() + (found.size() == 1 ? " file" : " files")
                + (truncated
                ? " — large project, first " + TestIndex.MAX_FILES + " files only"
                : ""));
    }

    /** A leaf that shows the test's NAME, not the record's toString —
     *  the walk's first find: DiscoveredTest[name=…] painted verbatim. */
    private static final class TestNode extends DefaultMutableTreeNode {

        TestNode(DiscoveredTest t) {
            super(t);
        }

        @Override
        public String toString() {
            DiscoveredTest t = (DiscoveredTest) getUserObject();
            return t.name() + "  \u00b7 line " + t.line();
        }
    }

    private DiscoveredTest selectedTest() {
        TreePath path = tree.getSelectionPath();
        Object last = path == null ? null : path.getLastPathComponent();
        Object user = last instanceof DefaultMutableTreeNode n
                ? n.getUserObject() : null;
        return user instanceof DiscoveredTest t ? t : null;
    }

    private void openSelected() {
        DiscoveredTest t = selectedTest();
        if (t == null) {
            return;
        }
        try {
            FileObject fo = FileUtil.toFileObject(
                    FileUtil.normalizeFile(t.file().toFile()));
            LineCookie lc = fo == null ? null
                    : DataObject.find(fo).getLookup().lookup(LineCookie.class);
            if (lc != null) {
                lc.getLineSet().getCurrent(Math.max(0, t.line() - 1))
                        .show(Line.ShowOpenType.OPEN, Line.ShowVisibilityType.FOCUS);
            }
        } catch (Exception ex) {
            StatusDisplayer.getDefault().setStatusText(
                    "Could not open " + t.file().getFileName() + ": " + ex.getMessage());
        }
    }

    private void runSelected() {
        DiscoveredTest t = selectedTest();
        if (t == null) {
            StatusDisplayer.getDefault().setStatusText(
                    "Select a test to run it.");
            return;
        }
        String mime = mimeOf(t.file());
        // the ONE execution path both surfaces share: command assembly,
        // the trust gate, and the spawn all live in the action
        boolean dispatched = RunFocusedTestAction.runDiscovered(
                t.file().toFile(), mime, t.name(), t.line());
        if (!dispatched) {
            StatusDisplayer.getDefault().setStatusText(
                    "No runner for " + t.name() + " (" + mime + ")");
        }
    }

    static String mimeOf(Path file) {
        FileObject fo = FileUtil.toFileObject(
                FileUtil.normalizeFile(file.toFile()));
        return fo == null ? null : fo.getMIMEType();
    }
}
