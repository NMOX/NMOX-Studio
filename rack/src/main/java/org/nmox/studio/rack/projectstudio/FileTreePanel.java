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
import javax.swing.JOptionPane;
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

    private final JTree tree;
    private final DefaultTreeModel model;
    private File root;
    private FileWatcher watcher;

    public FileTreePanel() {
        super(new BorderLayout());
        model = new DefaultTreeModel(new DefaultMutableTreeNode("No project"));
        tree = new JTree(model);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new Renderer());

        tree.addTreeWillExpandListener(new javax.swing.event.TreeWillExpandListener() {
            @Override
            public void treeWillExpand(javax.swing.event.TreeExpansionEvent event) {
                loadChildren((DefaultMutableTreeNode) event.getPath().getLastPathComponent());
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
        rebuild();
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
        if (watcher != null) {
            watcher.stop();
            watcher = null;
        }
    }

    // ---- tree building ----

    private void rebuild() {
        if (root == null || !root.isDirectory()) {
            model.setRoot(new DefaultMutableTreeNode("No project"));
            return;
        }
        Set<String> expanded = expandedPaths();
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(root);
        addChildren(rootNode, root);
        model.setRoot(rootNode);
        tree.expandPath(new TreePath(rootNode.getPath()));
        reExpand(rootNode, expanded);
    }

    /** Fills in real children for a node (replacing the lazy placeholder). */
    private void loadChildren(DefaultMutableTreeNode node) {
        if (node.getChildCount() == 1
                && PLACEHOLDER.equals(((DefaultMutableTreeNode) node.getChildAt(0)).getUserObject())) {
            node.removeAllChildren();
            addChildren(node, (File) node.getUserObject());
            model.nodeStructureChanged(node);
        }
    }

    private void addChildren(DefaultMutableTreeNode node, File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        Arrays.sort(files, Comparator
                .comparing((File f) -> !f.isDirectory())
                .thenComparing(f -> f.getName().toLowerCase()));
        for (File f : files) {
            DefaultMutableTreeNode child = new DefaultMutableTreeNode(f);
            if (f.isDirectory()) {
                // lazy: a placeholder until the user expands
                if (hasAnyChild(f)) {
                    child.add(new DefaultMutableTreeNode(PLACEHOLDER));
                }
            }
            node.add(child);
        }
    }

    private static boolean hasAnyChild(File dir) {
        String[] names = dir.list();
        return names != null && names.length > 0;
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
        Object user = node.getUserObject();
        if (user instanceof File f && expanded.contains(f.getAbsolutePath())) {
            loadChildren(node);
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
                String name = JOptionPane.showInputDialog(this, "New name:", target.getName());
                if (name != null && !name.isBlank()) {
                    try {
                        FileOps.rename(target, name.trim());
                        rebuild();
                    } catch (IOException ex) {
                        error(ex);
                    }
                }
            });
            menu.add(rename);

            JMenuItem delete = new JMenuItem("Delete (to Trash)");
            delete.addActionListener(a -> {
                if (JOptionPane.showConfirmDialog(this,
                        "Delete \"" + target.getName() + "\"?", "Project Studio",
                        JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    try {
                        FileOps.delete(target);
                        rebuild();
                    } catch (IOException ex) {
                        error(ex);
                    }
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
        String name = JOptionPane.showInputDialog(this,
                directory ? "Folder name:" : "File name:", directory ? "" : "untitled.js");
        if (name == null || name.isBlank()) {
            return;
        }
        try {
            File created = directory
                    ? FileOps.createDirectory(parent, name.trim())
                    : FileOps.createFile(parent, name.trim());
            rebuild();
            if (created.isFile()) {
                openInEditor(created);
            }
        } catch (IOException ex) {
            error(ex);
        }
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
        JOptionPane.showMessageDialog(this, ex.getMessage(), "Project Studio", JOptionPane.ERROR_MESSAGE);
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
