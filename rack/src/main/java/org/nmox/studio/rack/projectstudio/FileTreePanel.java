package org.nmox.studio.rack.projectstudio;

import java.awt.BorderLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import javax.swing.JPanel;
import org.nmox.studio.rack.engine.FileWatcher;
import javax.swing.SwingUtilities;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.view.BeanTreeView;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataFolder;
import org.openide.loaders.DataObject;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;

/**
 * The project file tree, as a platform citizen (ledger 36 closed): a
 * {@link BeanTreeView} over the root folder's real {@link DataFolder}
 * node. What the platform gives for free — and the old hand-rolled
 * JTree could not — is file-type icons from the DataObject loaders,
 * the node context menu (templates-aware New…, Cut/Copy/Paste/Rename/
 * Delete, the git-annotated verbs), lazy off-EDT child computation
 * with a "Please wait…" row, and expansion state that survives
 * refreshes because node identity is stable.
 *
 * <p>Laws preserved from the old tree, each with its original reason:
 * <ul>
 * <li><b>No filesystem I/O on the EDT</b> (the v1.33.1 TCC storm):
 * {@code setRootDirectory} resolves the root FileObject on the scanner
 * lane and only hands the finished node to the EDT; child listing is
 * the platform's own lazy machinery, off the EDT by design.</li>
 * <li><b>Heavy directories stay dark</b>: node_modules/.git/dist/build/
 * coverage render greyed and childless — expanding a 100k-file tree by
 * misclick was the original incident.</li>
 * <li><b>External edits arrive</b>: the {@link FileWatcher} that used
 * to drive a full rebuild now drives {@link FileUtil#refreshFor}, so
 * files written by builds appear without an expansion dance.</li>
 * </ul>
 */
public class FileTreePanel extends JPanel implements ExplorerManager.Provider {

    /** Directories that stay dark: huge, generated, or plumbing. */
    private static final java.util.Set<String> HEAVY_DIRS =
            java.util.Set.of("node_modules", ".git", "dist", "build", "coverage");

    /**
     * Test seam for the off-EDT law: resolves a directory to the node
     * the tree shows. The real resolver touches the filesystem
     * (FileUtil.toFileObject stats the disk), which is exactly the work
     * that must never run on the EDT — a wedged network mount or a
     * TCC-gated folder would freeze first paint.
     */
    interface RootResolver {
        /** The display node for {@code dir}, or null when unresolvable. */
        Node resolve(File dir);
    }

    private static final RootResolver REAL_RESOLVER = dir -> {
        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(dir));
        if (fo == null || !fo.isFolder()) {
            return null;
        }
        return new HeavyAwareFilterNode(DataFolder.findFolder(fo).getNodeDelegate());
    };

    private final ExplorerManager manager = new ExplorerManager();
    private final BeanTreeView view = new BeanTreeView();
    private final RootResolver resolver;
    /** Every filesystem walk we initiate runs here, never on the EDT. */
    private final org.openide.util.RequestProcessor scanner =
            new org.openide.util.RequestProcessor("nmox-filetree-scan", 1, true);
    private final PropertyChangeListener selectionRelay = this::relaySelection;

    private File root;
    private FileWatcher watcher;
    /**
     * Notified on the EDT with the selected File — or null when nothing
     * is selected. Ledger 29 remainder (v1.48.0): the owning studio
     * publishes this as the platform selection. The callback reads only
     * the already-resolved node lookup — no disk — so firing it per
     * keystroke of a held arrow key is free; the downstream
     * AimNodePublisher carries the equality guard and the resolve lane.
     */
    private java.util.function.Consumer<File> selectionListener;

    public FileTreePanel() {
        this(REAL_RESOLVER);
    }

    FileTreePanel(RootResolver resolver) {
        super(new BorderLayout());
        this.resolver = resolver;
        view.setRootVisible(true);
        manager.setRootContext(placeholder("No project"));
        manager.addPropertyChangeListener(selectionRelay);
        add(view, BorderLayout.CENTER);
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return manager;
    }

    // ---- root management ----

    public void setRootDirectory(File dir) {
        this.root = dir;
        restartWatcher();
        if (dir == null) {
            onEdt(() -> manager.setRootContext(placeholder("No project")));
            return;
        }
        // resolve off the EDT: a fresh launch aiming at a slow or
        // permission-gated directory must still draw its window promptly
        scanner.post(() -> {
            Node node = resolver.resolve(dir);
            onEdt(() -> {
                if (!java.util.Objects.equals(root, dir)) {
                    return; // aim changed while we resolved; the newer scan owns the tree
                }
                manager.setRootContext(node != null ? node
                        : placeholder(dir.getName() + " (unreadable)"));
            });
        });
    }

    public File getRootDirectory() {
        return root;
    }

    /** One listener is the whole contract; a second call replaces the first. */
    public void setSelectionListener(java.util.function.Consumer<File> listener) {
        this.selectionListener = listener;
    }

    /**
     * The selected File, or null when nothing (or a placeholder row) is
     * selected. EDT-only, and EDT-cheap: reads the node's already-resolved
     * lookup, never the disk.
     */
    public File selectedFile() {
        Node[] selected = manager.getSelectedNodes();
        if (selected.length == 0) {
            return null;
        }
        DataObject dob = selected[0].getLookup().lookup(DataObject.class);
        return dob == null ? null : FileUtil.toFile(dob.getPrimaryFile());
    }

    private void relaySelection(PropertyChangeEvent e) {
        if (ExplorerManager.PROP_SELECTED_NODES.equals(e.getPropertyName())
                && selectionListener != null) {
            selectionListener.accept(selectedFile());
        }
    }

    // ---- external-change refresh ----

    private void restartWatcher() {
        if (watcher != null) {
            watcher.stop();
            watcher = null;
        }
        if (root != null && root.isDirectory()) {
            File watched = root;
            // builds and generators write behind the platform's back;
            // refreshFor re-syncs the FileObject tree and the view keeps
            // its own expansion state — no rebuild, no re-expand dance
            watcher = new FileWatcher(watched, 1500, null,
                    changed -> scanner.post(() -> FileUtil.refreshFor(watched)));
            watcher.start();
        }
    }

    /**
     * Releases the filesystem watcher. Deliberately does NOT stop the
     * scanner or drop the selection relay: Project Studio is
     * PERSISTENCE_ALWAYS and reuses this one panel instance across
     * close/reopen. A permanently stopped RequestProcessor would silently
     * drop the root-resolve post on the next open (tree stuck at "No
     * project"); dropping the self-owned selection relay would stop the
     * reopened tree from publishing its selection to the aim (ledger 29).
     * Both the RP (a named pool that idles to zero threads) and the relay
     * (a listener on this panel's own ExplorerManager — no external leak)
     * are safe to keep across the panel's whole lifetime.
     */
    public void dispose() {
        if (watcher != null) {
            watcher.stop();
            watcher = null;
        }
    }

    // ---- helpers ----

    private static Node placeholder(String label) {
        AbstractNode n = new AbstractNode(Children.LEAF);
        n.setDisplayName(label);
        return n;
    }

    private static void onEdt(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }

    /**
     * The real folder node, with heavy directories kept dark: a child
     * named node_modules (or .git, dist, build, coverage) renders greyed
     * and childless. Expanding a 100k-file tree by misclick was the
     * incident that created the rule; the raw platform node would
     * happily enumerate it.
     */
    static final class HeavyAwareFilterNode extends FilterNode {

        HeavyAwareFilterNode(Node original) {
            super(original, original.isLeaf() ? Children.LEAF : new HeavyChildren(original));
        }

        /**
         * Folders (and the root) get the full platform node menu — New
         * (templates-aware), Find, Cut/Copy/Paste, Delete, Rename, Tools,
         * Properties — driven by the underlying DataNode's cookies. The
         * old hand-rolled tree offered only New/Rename/Delete/Open/Reveal;
         * this is a superset (Cut/Copy/Paste are new).
         */
        @Override
        public javax.swing.Action[] getActions(boolean context) {
            return new javax.swing.Action[]{
                org.openide.util.actions.SystemAction.get(org.openide.actions.NewAction.class),
                org.openide.util.actions.SystemAction.get(org.openide.actions.FindAction.class),
                null,
                org.openide.util.actions.SystemAction.get(org.openide.actions.CutAction.class),
                org.openide.util.actions.SystemAction.get(org.openide.actions.CopyAction.class),
                org.openide.util.actions.SystemAction.get(org.openide.actions.PasteAction.class),
                null,
                org.openide.util.actions.SystemAction.get(org.openide.actions.DeleteAction.class),
                org.openide.util.actions.SystemAction.get(org.openide.actions.RenameAction.class),
                null,
                org.openide.util.actions.SystemAction.get(org.openide.actions.ToolsAction.class),
                org.openide.util.actions.SystemAction.get(org.openide.actions.PropertiesAction.class),
            };
        }

        private static final class HeavyChildren extends FilterNode.Children {

            HeavyChildren(Node owner) {
                super(owner);
            }

            @Override
            protected Node copyNode(Node original) {
                boolean folder = original.getLookup().lookup(DataFolder.class) != null;
                if (folder && HEAVY_DIRS.contains(original.getName())) {
                    return new DarkNode(original);
                }
                return folder ? new HeavyAwareFilterNode(original)
                        : new FileLeafNode(original);
            }
        }

        /**
         * A file: the full platform file menu — Open, Cut/Copy, Delete,
         * Rename, Tools, Properties — driven by the DataObject's cookies.
         */
        private static final class FileLeafNode extends FilterNode {

            FileLeafNode(Node original) {
                super(original, Children.LEAF);
            }

            @Override
            public javax.swing.Action[] getActions(boolean context) {
                return new javax.swing.Action[]{
                    org.openide.util.actions.SystemAction.get(org.openide.actions.OpenAction.class),
                    null,
                    org.openide.util.actions.SystemAction.get(org.openide.actions.CutAction.class),
                    org.openide.util.actions.SystemAction.get(org.openide.actions.CopyAction.class),
                    null,
                    org.openide.util.actions.SystemAction.get(org.openide.actions.DeleteAction.class),
                    org.openide.util.actions.SystemAction.get(org.openide.actions.RenameAction.class),
                    null,
                    org.openide.util.actions.SystemAction.get(org.openide.actions.ToolsAction.class),
                    org.openide.util.actions.SystemAction.get(org.openide.actions.PropertiesAction.class),
                };
            }
        }

        /**
         * A heavy directory (node_modules, .git, …): present but inert.
         * Children.LEAF means no disclosure triangle — a stronger "you
         * cannot enter" signal than the old grey text, and the guarantee
         * that a 100k-file generated tree is never enumerated by misclick.
         */
        private static final class DarkNode extends FilterNode {

            DarkNode(Node original) {
                super(original, Children.LEAF);
            }

            @Override
            public javax.swing.Action[] getActions(boolean context) {
                // no New/Paste into a directory we refuse to descend into;
                // Reveal-in-files and Properties are the honest verbs
                return new javax.swing.Action[]{
                    org.openide.util.actions.SystemAction.get(org.openide.actions.PropertiesAction.class),
                };
            }
        }
    }
}
