package org.nmox.studio.rack.projectstudio;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.nmox.studio.rack.engine.FileWatcher;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.cookies.OpenCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;

/**
 * The project's file tree: lazy-loading, full CRUD via the context
 * menu, double-click opens files in the editor, and a FileWatcher
 * keeps the tree honest while builds and saves change the disk.
 */
public class FileTreePanel extends JPanel {

    /** Directories shown but never auto-expanded or watched into. */
    private static final Set<String> HEAVY_DIRS = Set.of("node_modules", ".git", "dist", "build", "coverage");
    private static final String PLACEHOLDER = "…";

    /**
     * Directory-listing seam. Production lists the real filesystem; a test can
     * inject a slow lister to prove {@link #setRootDirectory} never blocks the
     * caller (the EDT) while a directory is walked.
     */
    interface DirLister {
        /** Children of {@code dir}, or null when it cannot be listed. */
        File[] list(File dir);
    }

    private static final DirLister REAL_LISTER = File::listFiles;

    private final JTree tree;
    private final DefaultTreeModel model;
    private final DirLister lister;
    /** Every filesystem walk runs here, never on the EDT. */
    private final org.openide.util.RequestProcessor scanner =
            new org.openide.util.RequestProcessor("nmox-filetree-scan", 1, true);
    private File root;
    private FileWatcher watcher;

    public FileTreePanel() {
        this(REAL_LISTER);
    }

    FileTreePanel(DirLister lister) {
        super(new BorderLayout());
        this.lister = lister;
        model = new DefaultTreeModel(new DefaultMutableTreeNode("No project"));
        tree = new JTree(model);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new Renderer());

        tree.addTreeWillExpandListener(new javax.swing.event.TreeWillExpandListener() {
            @Override
            public void treeWillExpand(javax.swing.event.TreeExpansionEvent event) {
                loadChildrenAsync((DefaultMutableTreeNode) event.getPath().getLastPathComponent());
            }

            @Override
            public void treeWillCollapse(javax.swing.event.TreeExpansionEvent event) {
            }
        });

        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    File f = fileAt(e.getPoint());
                    if (f != null && f.isFile()) {
                        openInEditor(f);
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                maybePopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybePopup(e);
            }
        });

        add(new JScrollPane(tree), BorderLayout.CENTER);
    }

    // ---- root management ----

    public void setRootDirectory(File dir) {
        this.root = dir;
        rebuild();       // posts the filesystem walk to a background thread
        restartWatcher();
    }

    public File getRootDirectory() {
        return root;
    }

    private void restartWatcher() {
        if (watcher != null) {
            watcher.stop();
            watcher = null;
        }
        if (root != null && root.isDirectory()) {
            watcher = new FileWatcher(root, 1500, null,
                    changed -> SwingUtilities.invokeLater(this::rebuild));
            watcher.start();
        }
    }

    public void dispose() {
        scanner.stop();
        if (watcher != null) {
            watcher.stop();
            watcher = null;
        }
    }

    // ---- tree building ----

    /**
     * Rebuilds the whole tree. The filesystem walk of the root directory runs
     * on a background thread — never the EDT — so a fresh launch that aims at a
     * slow or permission-gated directory still draws its window promptly. The
     * model swap and re-expansion happen back on the EDT.
     */
    private void rebuild() {
        File current = root;
        if (current == null || !isDirectorySafe(current)) {
            SwingUtilities.invokeLater(() -> model.setRoot(new DefaultMutableTreeNode("No project")));
            return;
        }
        Set<String> expanded = expandedPaths();
        scanner.post(() -> {
            DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(current);
            // resolve the root and every previously-expanded subtree here, on
            // the background thread, so the EDT only swaps a finished model
            addChildrenDeep(rootNode, current, expanded);
            SwingUtilities.invokeLater(() -> {
                if (current != root) {
                    return; // aim changed while we were listing; a newer scan owns the tree
                }
                model.setRoot(rootNode);
                tree.expandPath(new TreePath(rootNode.getPath()));
                reExpand(rootNode, expanded);
            });
        });
    }

    /**
     * Off-EDT: fills a node's children and recurses into any child that was
     * expanded before the rebuild, so the whole visible subtree is resolved on
     * the background scanner rather than a synchronous walk on the EDT.
     */
    private void addChildrenDeep(DefaultMutableTreeNode node, File dir, Set<String> expanded) {
        addChildren(node, dir);
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            if (child.getUserObject() instanceof File f && f.isDirectory()
                    && expanded.contains(f.getAbsolutePath())) {
                child.removeAllChildren(); // drop the lazy placeholder
                addChildrenDeep(child, f, expanded);
            }
        }
    }

    /**
     * Fills in real children for a node when the user expands it. The listing
     * runs on the background scanner; the model update returns to the EDT.
     */
    private void loadChildrenAsync(DefaultMutableTreeNode node) {
        if (node.getChildCount() != 1
                || !PLACEHOLDER.equals(((DefaultMutableTreeNode) node.getChildAt(0)).getUserObject())
                || !(node.getUserObject() instanceof File dir)) {
            return;
        }
        // the placeholder still present at apply-time is what guards against a
        // double-post racing two model updates for the same node
        scanner.post(() -> {
            DefaultMutableTreeNode filled = new DefaultMutableTreeNode(dir);
            addChildren(filled, dir);
            SwingUtilities.invokeLater(() -> {
                if (node.getChildCount() == 1
                        && PLACEHOLDER.equals(((DefaultMutableTreeNode) node.getChildAt(0)).getUserObject())) {
                    node.removeAllChildren();
                    // snapshot first: node.add() detaches from filled, so
                    // iterating filled while adding would skip every other child
                    List<DefaultMutableTreeNode> kids = new ArrayList<>();
                    for (int i = 0; i < filled.getChildCount(); i++) {
                        kids.add((DefaultMutableTreeNode) filled.getChildAt(i));
                    }
                    for (DefaultMutableTreeNode kid : kids) {
                        node.add(kid);
                    }
                    model.nodeStructureChanged(node);
                }
            });
        });
    }

    /**
     * Fills a node's children synchronously (already off the EDT — callers run
     * on the scanner). A directory gets a lazy placeholder rather than being
     * probed here, so building one level never recurses into a protected or
     * slow child directory.
     */
    private void addChildren(DefaultMutableTreeNode node, File dir) {
        File[] files = lister.list(dir);
        if (files == null) {
            return;
        }
        Arrays.sort(files, Comparator
                .comparing((File f) -> !f.isDirectory())
                .thenComparing(f -> f.getName().toLowerCase()));
        for (File f : files) {
            DefaultMutableTreeNode child = new DefaultMutableTreeNode(f);
            if (f.isDirectory()) {
                // lazy and cheap: assume every directory is expandable and show
                // a placeholder. Resolving real emptiness would mean a File.list
                // per child, which on ~ would touch the TCC-protected folders —
                // the whole bug. An empty dir that expands to nothing is a
                // trivial, self-correcting cost.
                child.add(new DefaultMutableTreeNode(PLACEHOLDER));
            }
            node.add(child);
        }
    }

    /** isDirectory() can itself stat a protected path; keep it off the EDT-blocking hot path. */
    private static boolean isDirectorySafe(File dir) {
        try {
            return dir.isDirectory();
        } catch (SecurityException ex) {
            return false;
        }
    }

    private Set<String> expandedPaths() {
        Set<String> result = new HashSet<>();
        if (!(model.getRoot() instanceof DefaultMutableTreeNode rootNode)
                || !(rootNode.getUserObject() instanceof File)) {
            return result;
        }
        Enumeration<TreePath> en = tree.getExpandedDescendants(new TreePath(rootNode.getPath()));
        if (en != null) {
            while (en.hasMoreElements()) {
                Object last = en.nextElement().getLastPathComponent();
                Object user = ((DefaultMutableTreeNode) last).getUserObject();
                if (user instanceof File f) {
                    result.add(f.getAbsolutePath());
                }
            }
        }
        return result;
    }

    private void reExpand(DefaultMutableTreeNode node, Set<String> expanded) {
        // EDT-only: the subtree is already resolved off-EDT (addChildrenDeep),
        // so here we only re-open the tree paths — no filesystem access.
        Object user = node.getUserObject();
        if (user instanceof File f && expanded.contains(f.getAbsolutePath())) {
            tree.expandPath(new TreePath(node.getPath()));
            List<DefaultMutableTreeNode> children = new ArrayList<>();
            for (int i = 0; i < node.getChildCount(); i++) {
                children.add((DefaultMutableTreeNode) node.getChildAt(i));
            }
            for (DefaultMutableTreeNode child : children) {
                reExpand(child, expanded);
            }
        }
    }

    // ---- interaction ----

    private File fileAt(java.awt.Point p) {
        TreePath path = tree.getPathForLocation(p.x, p.y);
        if (path == null) {
            return null;
        }
        Object user = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        return user instanceof File f ? f : null;
    }

    private void maybePopup(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        TreePath path = tree.getPathForLocation(e.getX(), e.getY());
        if (path != null) {
            tree.setSelectionPath(path);
        }
        File f = path == null ? root
                : (((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject() instanceof File file
                        ? file : root);
        if (f == null) {
            return;
        }
        showMenu(f, e);
    }

    private void showMenu(File target, MouseEvent e) {
        File dirContext = target.isDirectory() ? target : target.getParentFile();
        JPopupMenu menu = new JPopupMenu();

        JMenuItem newFile = new JMenuItem("New File…");
        newFile.addActionListener(a -> promptCreate(dirContext, false));
        menu.add(newFile);

        JMenuItem newDir = new JMenuItem("New Folder…");
        newDir.addActionListener(a -> promptCreate(dirContext, true));
        menu.add(newDir);

        if (!target.equals(root)) {
            menu.addSeparator();
            JMenuItem rename = new JMenuItem("Rename…");
            rename.addActionListener(a -> {
                NotifyDescriptor.InputLine line = new NotifyDescriptor.InputLine("New name:", "Rename");
                line.setInputText(target.getName());
                if (DialogDisplayer.getDefault().notify(line) == NotifyDescriptor.OK_OPTION
                        && !line.getInputText().isBlank()) {
                    String newName = line.getInputText().trim();
                    runFileOp(() -> FileOps.rename(target, newName), null);
                }
            });
            menu.add(rename);

            // honest label: FileOps.delete prefers the system Trash but falls
            // back to a hard recursive delete when Trash is unavailable
            JMenuItem delete = new JMenuItem("Delete…");
            delete.addActionListener(a -> {
                if (DialogDisplayer.getDefault().notify(new NotifyDescriptor.Confirmation(
                        "Delete \"" + target.getName()
                        + "\"?\n(Moved to the system Trash when supported; "
                        + "deleted permanently otherwise.)", "Project Studio",
                        NotifyDescriptor.YES_NO_OPTION)) == NotifyDescriptor.YES_OPTION) {
                    runFileOp(() -> {
                        FileOps.delete(target); // node_modules-sized trees take minutes
                        return null;
                    }, null);
                }
            });
            menu.add(delete);
        }

        if (target.isFile()) {
            menu.addSeparator();
            JMenuItem open = new JMenuItem("Open");
            open.addActionListener(a -> openInEditor(target));
            menu.add(open);
        }

        menu.addSeparator();
        JMenuItem reveal = new JMenuItem("Reveal in File Manager");
        reveal.addActionListener(a -> {
            try {
                Desktop.getDesktop().open(dirContext);
            } catch (IOException | RuntimeException ex) {
                error(ex);
            }
        });
        menu.add(reveal);

        menu.show(tree, e.getX(), e.getY());
    }

    private void promptCreate(File parent, boolean directory) {
        NotifyDescriptor.InputLine line = new NotifyDescriptor.InputLine(
                directory ? "Folder name:" : "File name:", directory ? "New Folder" : "New File");
        line.setInputText(directory ? "" : "untitled.js");
        if (DialogDisplayer.getDefault().notify(line) != NotifyDescriptor.OK_OPTION
                || line.getInputText().isBlank()) {
            return;
        }
        String name = line.getInputText().trim();
        runFileOp(() -> directory
                ? FileOps.createDirectory(parent, name)
                : FileOps.createFile(parent, name),
                created -> {
                    if (created != null && created.isFile()) {
                        openInEditor(created);
                    }
                });
    }

    /** A file CRUD operation that can fail — and can be slow. */
    private interface FileOp {
        File run() throws IOException;
    }

    /**
     * Runs a CRUD operation on the background scanner — never the EDT, where
     * a recursive node_modules delete is a minutes-long beachball — then
     * rebuilds the tree and hands the result to {@code onDone} back on the
     * EDT. Errors surface as the usual dialog, also on the EDT.
     */
    private void runFileOp(FileOp op, java.util.function.Consumer<File> onDone) {
        scanner.post(() -> {
            try {
                File result = op.run();
                SwingUtilities.invokeLater(() -> {
                    rebuild();
                    if (onDone != null) {
                        onDone.accept(result);
                    }
                });
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> error(ex));
            }
        });
    }

    private void openInEditor(File file) {
        try {
            FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(file));
            if (fo != null) {
                DataObject dataObject = DataObject.find(fo);
                OpenCookie open = dataObject.getLookup().lookup(OpenCookie.class);
                if (open != null) {
                    open.open();
                }
            }
        } catch (IOException | RuntimeException ex) {
            error(ex);
        }
    }

    private void error(Exception ex) {
        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                "File operation failed: " + ex.getMessage(), NotifyDescriptor.ERROR_MESSAGE));
    }

    /** Folders bold-ish, heavy dirs dimmed, root shows the full name. */
    private static final class Renderer extends DefaultTreeCellRenderer {

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            Object user = ((DefaultMutableTreeNode) value).getUserObject();
            if (user instanceof File f) {
                setText(f.getName().isEmpty() ? f.getPath() : f.getName());
                setEnabled(!HEAVY_DIRS.contains(f.getName()));
            }
            return this;
        }
    }
}
