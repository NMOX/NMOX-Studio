package org.nmox.studio.rack.projectstudio;

import java.awt.BorderLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import javax.swing.JPanel;
import org.nmox.studio.core.util.VsCodeHidden;
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
 *
 * <p><b>What the project hides stays hidden</b> (3.5.13): the tree's
 * folders are listed through {@link VsCodeHidden}, the platform's own
 * folder filter over the {@code files.exclude} of the project's
 * {@code .vscode/settings.json}. Hidden from this view only - the file
 * still opens by name and is still committed. The settings are read where
 * the root is resolved (off the EDT) and asked again every
 * {@link #HIDDEN_RECHECK_MS} while the tree watches its folder, so an edit
 * to them redraws the rows in place: no re-root, the expansion kept.
 */
@org.openide.util.NbBundle.Messages({
    "FileTreePanel_noProject=No project",
    "FileTreePanel_unreadable={0} (unreadable)"
})
public class FileTreePanel extends JPanel implements ExplorerManager.Provider {

    /** Directories that stay dark: huge, generated, or plumbing. */
    private static final java.util.Set<String> HEAVY_DIRS = org.nmox.studio.core.util.HeavyDirs.NAMES; // one home since v2.87.0

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

    static final RootResolver REAL_RESOLVER = dir -> {
        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(dir));
        if (fo == null || !fo.isFolder()) {
            return null;
        }
        // this lane is off the EDT by the law above, so the project's
        // settings are read here; every row after that is judged from memory
        VsCodeHidden hidden = new VsCodeHidden(fo);
        hidden.refresh();
        return new HeavyAwareFilterNode(hidden.nodeFor(DataFolder.findFolder(fo)), true, hidden);
    };

    /** How often a watched tree asks whether the project's files.exclude changed. */
    static final int HIDDEN_RECHECK_MS = 2_000;

    private final ExplorerManager manager = new ExplorerManager();
    private final SpokenTreeView view = new SpokenTreeView();
    private final RootResolver resolver;
    /** Every filesystem walk we initiate runs here, never on the EDT. */
    private final org.openide.util.RequestProcessor scanner =
            new org.openide.util.RequestProcessor("nmox-filetree-scan", 1, true);
    private final PropertyChangeListener selectionRelay = this::relaySelection;
    /** The files.exclude filter of the tree in place, or null (no project, a test's resolver). */
    private volatile VsCodeHidden hidden;
    /** Whether the tree is watching its folder; the recheck below runs only while it is. */
    private volatile boolean watching;
    private final org.openide.util.RequestProcessor.Task hiddenRecheck = scanner.create(this::recheckHidden);

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
        manager.setRootContext(placeholder(Bundle.FileTreePanel_noProject()));
        manager.addPropertyChangeListener(selectionRelay);
        add(view, BorderLayout.CENTER);
        speakRowsAsWords(view);
    }

    /**
     * The platform's node renderer paints a file's git state in HTML, and a
     * screen reader was given that markup as the row's name
     * ({@code <font color="#ff6464">a.txt</font><font color="#ffffff"> [UU]</font>},
     * read from the AX tree in the 3.4 walk). The renderer is wrapped so each
     * row is heard as the words it paints: {@code a.txt [UU]}.
     */
    static void speakRowsAsWords(SpokenTreeView view) {
        javax.swing.JTree tree = view.tree();
        if (tree != null) {
            javax.swing.tree.TreeCellRenderer platform = tree.getCellRenderer();
            if (platform != null && !(platform instanceof SpokenRows)) {
                tree.setCellRenderer(new SpokenRows(platform));
            }
        }
    }

    /** The platform's view, with its tree in reach: TreeView keeps it in a protected field. */
    static final class SpokenTreeView extends BeanTreeView {
        private static final long serialVersionUID = 1L;

        javax.swing.JTree tree() {
            return tree;
        }
    }

    /** Delegates the paint; names the painted component with its words. */
    static final class SpokenRows implements javax.swing.tree.TreeCellRenderer {
        private final javax.swing.tree.TreeCellRenderer platform;

        SpokenRows(javax.swing.tree.TreeCellRenderer platform) {
            this.platform = platform;
        }

        @Override
        public java.awt.Component getTreeCellRendererComponent(javax.swing.JTree tree, Object value,
                boolean selected, boolean expanded, boolean leaf, int row, boolean focus) {
            java.awt.Component c = platform.getTreeCellRendererComponent(tree, value, selected, expanded,
                    leaf, row, focus);
            if (c instanceof javax.swing.JLabel label) {
                label.getAccessibleContext().setAccessibleName(
                        org.nmox.studio.core.util.PlainText.words(label.getText()));
            }
            return c;
        }
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return manager;
    }

    /**
     * Where keyboard focus lands when the studio is activated — the tree
     * itself, so ⇧⌘E (VS Code's Explorer chord, 3.1.0) leaves the arrow
     * keys walking files rather than the window frame.
     * {@link org.openide.explorer.view.TreeView} forwards a focus
     * request to its inner {@code JTree}.
     */
    java.awt.Component focusTarget() {
        return view;
    }

    /**
     * Bind the explorer's Cut/Copy/Paste/Delete into {@code map} — the
     * owning TopComponent's ActionMap, which its default lookup already
     * exposes to the platform's global actions.
     *
     * <p>The context menu has advertised Cut/Copy/Delete since the
     * v1.64.0 platform-tree rewrite, but those are
     * {@code CallbackSystemAction}s: they only enable when the
     * activated component's ActionMap carries their keys, and nothing
     * ever bound them — so every one of those items was permanently
     * grey for 220 releases (v1.285.0, the project-starter walk; the
     * v1.38.1 pattern — an affordance documented but never exercised
     * is untested). Delete passes {@code confirmDelete=true}, so the
     * platform's own confirmation guards the irreversible verb per the
     * v1.98.0 safe-default law.
     */
    void installExplorerActions(javax.swing.ActionMap map) {
        map.put(javax.swing.text.DefaultEditorKit.copyAction,
                org.openide.explorer.ExplorerUtils.actionCopy(manager));
        map.put(javax.swing.text.DefaultEditorKit.cutAction,
                org.openide.explorer.ExplorerUtils.actionCut(manager));
        map.put(javax.swing.text.DefaultEditorKit.pasteAction,
                org.openide.explorer.ExplorerUtils.actionPaste(manager));
        map.put("delete", org.openide.explorer.ExplorerUtils.actionDelete(manager, true));
    }

    /**
     * Forwarded from the owning TopComponent's activation lifecycle so
     * cut/copy enablement tracks focus the way ExplorerUtils expects.
     */
    void activateExplorerActions(boolean activate) {
        org.openide.explorer.ExplorerUtils.activateActions(manager, activate);
    }

    // ---- root management ----

    public void setRootDirectory(File dir) {
        this.root = dir;
        restartWatcher();
        if (dir == null) {
            hidden = null;
            onEdt(() -> manager.setRootContext(placeholder(Bundle.FileTreePanel_noProject())));
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
                hidden = node instanceof HeavyAwareFilterNode real ? real.hidden() : null;
                manager.setRootContext(node != null ? node
                        : placeholder(Bundle.FileTreePanel_unreadable(dir.getName())));
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
        watching = false;
        hiddenRecheck.cancel();
        if (watcher != null) {
            watcher.stop();
            watcher = null;
        }
        if (root != null && root.isDirectory()) {
            File watched = root;
            // builds and generators write behind the platform's back;
            // refreshFor re-syncs the FileObject tree and the view keeps
            // its own expansion state — no rebuild, no re-expand dance
            // the tree needs its SHAPE promptly (a directory's time moves
            // when git, a generator or an atomic save writes in it) and an
            // in-place edit eventually: stat every file only each 10th poll
            watcher = new FileWatcher(watched, 1500, null,
                    changed -> scanner.post(() -> FileUtil.refreshFor(watched))).contentEvery(10);
            watcher.start();
            watching = true;
            hiddenRecheck.schedule(HIDDEN_RECHECK_MS);
        }
    }

    /**
     * Asks the project's settings again, on the scanner lane: a few stats,
     * and a parse only when the file's time or size moved. When what they
     * hide changed, the filter tells the platform's folder children and the
     * rows are listed again in place.
     */
    void recheckHidden() {
        VsCodeHidden filter = hidden;
        if (filter != null) {
            filter.refresh();
        }
        if (watching) {
            hiddenRecheck.schedule(HIDDEN_RECHECK_MS);
        }
    }

    /** For the tests: wait until the scanner lane has run everything posted so far. */
    void awaitScanner() {
        scanner.post(() -> { }).waitFinished();
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
        watching = false;
        hiddenRecheck.cancel();
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

        /** True only for the project root the tree is aimed at. */
        private final boolean root;
        /** On the root only: the files.exclude filter its folders are listed through. */
        private final VsCodeHidden hidden;

        HeavyAwareFilterNode(Node original) {
            this(original, false);
        }

        HeavyAwareFilterNode(Node original, boolean root) {
            this(original, root, null);
        }

        HeavyAwareFilterNode(Node original, boolean root, VsCodeHidden hidden) {
            super(original, original.isLeaf() ? Children.LEAF : new HeavyChildren(original));
            this.root = root;
            this.hidden = hidden;
        }

        VsCodeHidden hidden() {
            return hidden;
        }

        /**
         * The aimed project must not be deletable or cuttable from its
         * own tree: the underlying DataFolder node would happily
         * destroy the whole checkout, and with the explorer actions
         * wired (v1.285.0) that verb would otherwise light up on the
         * root row like on any other folder.
         */
        @Override
        public boolean canDestroy() {
            return !root && super.canDestroy();
        }

        @Override
        public boolean canCut() {
            return !root && super.canCut();
        }

        /**
         * Folders (and the root) get the full platform node menu — New
         * (templates-aware), Find, Cut/Copy/Paste, Delete, Rename, Tools,
         * Properties — driven by the underlying DataNode's cookies. The
         * old hand-rolled tree offered only New/Rename/Delete/Open/Reveal;
         * this is a superset (Cut/Copy/Paste are new).
         *
         * <p>New is {@code NewTemplateAction}, the templates submenu (recent
         * templates, then All Templates…). It was {@code NewAction} until
         * 3.1.0, which offers a node's NewTypes, and a DataFolder's node has
         * none ({@code FolderNode.getNewTypes()} returns an empty array), so
         * the row read "Add" and was grey on every folder since v1.64.0.
         * Find is bound by the search module at activation (its
         * ActionManager puts Find in Projects into any non-editor window's
         * ActionMap), not here.
         */
        @Override
        public javax.swing.Action[] getActions(boolean context) {
            return withPathRows(this, new javax.swing.Action[]{
                org.openide.util.actions.SystemAction.get(org.openide.actions.NewTemplateAction.class),
                org.openide.util.actions.SystemAction.get(org.openide.actions.FindAction.class),
                null,
                org.openide.util.actions.SystemAction.get(org.openide.actions.CutAction.class),
                org.openide.util.actions.SystemAction.get(org.openide.actions.CopyAction.class),
                org.openide.util.actions.SystemAction.get(org.openide.actions.PasteAction.class),
                null,
                org.openide.util.actions.SystemAction.get(org.openide.actions.DeleteAction.class),
                org.openide.util.actions.SystemAction.get(org.openide.actions.RenameAction.class),
            }, new javax.swing.Action[]{
                org.openide.util.actions.SystemAction.get(org.openide.actions.ToolsAction.class),
                org.openide.util.actions.SystemAction.get(org.openide.actions.PropertiesAction.class),
            });
        }

        /**
         * {@code head}, then Copy Path / Copy Relative Path / Reveal (3.1.0,
         * {@link PathActions}), then {@code tail}, each group behind a
         * separator.
         */
        static javax.swing.Action[] withPathRows(Node node, javax.swing.Action[] head, javax.swing.Action[] tail) {
            java.util.List<javax.swing.Action> out = new java.util.ArrayList<>(java.util.Arrays.asList(head));
            javax.swing.Action[] path = PathActions.forNode(node);
            if (path.length > 0) {
                out.add(null);
                out.addAll(java.util.Arrays.asList(path));
            }
            out.add(null);
            out.addAll(java.util.Arrays.asList(tail));
            return out.toArray(new javax.swing.Action[0]);
        }

        private static final class HeavyChildren extends FilterNode.Children {

            HeavyChildren(Node owner) {
                super(owner);
            }

            @Override
            protected Node copyNode(Node original) {
                // copyNode runs on the EDT when the view materializes a
                // lazy child — and the platform hands FILES over as
                // FolderChildren.DelayedNodes whose lookup forces
                // DataObject.find INLINE for any DataObject-assignable
                // template (DataFolder is one). That was the 1.195.0
                // "Attempt to obtain DataObject ... from EDT" warning.
                // The FileObject sits in the delayed lookup by
                // construction and answers folder-vs-file for free.
                FileObject fo = original.getLookup().lookup(FileObject.class);
                boolean folder = fo != null
                        ? fo.isFolder()
                        : original.getLookup().lookup(DataFolder.class) != null;
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
        static final class FileLeafNode extends FilterNode {

            FileLeafNode(Node original) {
                super(original, Children.LEAF);
            }

            @Override
            public javax.swing.Action[] getActions(boolean context) {
                return withPathRows(this, new javax.swing.Action[]{
                    org.openide.util.actions.SystemAction.get(org.openide.actions.OpenAction.class),
                    null,
                    org.openide.util.actions.SystemAction.get(org.openide.actions.CutAction.class),
                    org.openide.util.actions.SystemAction.get(org.openide.actions.CopyAction.class),
                    null,
                    org.openide.util.actions.SystemAction.get(org.openide.actions.DeleteAction.class),
                    org.openide.util.actions.SystemAction.get(org.openide.actions.RenameAction.class),
                }, new javax.swing.Action[]{
                    org.openide.util.actions.SystemAction.get(org.openide.actions.ToolsAction.class),
                    org.openide.util.actions.SystemAction.get(org.openide.actions.PropertiesAction.class),
                });
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
