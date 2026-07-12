package org.nmox.studio.rack.service;

import java.io.File;
import java.util.function.Consumer;
import java.util.function.Function;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.nodes.Node;
import org.openide.util.RequestProcessor;

/**
 * Publishes a file or directory as a platform selection (ledger 29,
 * v1.45.0; generalized from directories to any file in v1.48.0 for the
 * Project Studio tree selection): resolves the target's DataObject node
 * OFF the EDT (FileObject/DataObject resolution touches disk) and hands
 * it to the owning TopComponent ON the EDT, where {@code setActivatedNodes}
 * makes the global selection — and with it the platform's context-sensitive
 * actions (Team menu, git verbs, per-file Annotate) — finally see what
 * NMOX is aimed at or has selected.
 *
 * <p>A DataObject node works for every target: directories resolve to
 * their DataFolder node (no project manifest required, STATIC included),
 * plain files to their file-type node.
 *
 * <p>Equality-guarded: re-publishing the same target costs one
 * compare, so listener storms (100 projectChanged events for one aim)
 * resolve exactly once; distinct-target storms (arrow-key held down a
 * file tree) coalesce on the single lane — a request superseded before
 * it resolves delivers nothing and skips its disk touch. Callers gate
 * on their own visibility flag — a hidden tab publishes nothing, keeping
 * the v1.38.0 boot law (zero filesystem resolution behind hidden
 * default-open tabs) intact.
 */
public final class AimNodePublisher {

    /** One lane for all publishers: resolutions are rare and must not pile up. */
    private static final RequestProcessor RP = new RequestProcessor("nmox-aim-node", 1, true);

    /** Receives the resolved node, always on the EDT. */
    private final Consumer<Node> sink;

    /** Test seam: production resolves the real DataObject node. */
    Function<File, Node> resolver = AimNodePublisher::resolveNode;

    /**
     * The last directory a publish was requested for — the equality guard.
     * Volatile: publish() may be called from the EDT, the rack's router,
     * or the async-switch completion callback.
     */
    private volatile File requested;

    public AimNodePublisher(Consumer<Node> sink) {
        this.sink = sink;
    }

    /**
     * Requests publication of {@code dir}'s node. Same-dir requests after
     * the first are free (no post, no resolution). Distinct dirs queue on
     * one lane; a request superseded before it resolves delivers nothing.
     */
    public void publish(File dir) {
        if (dir == null || dir.equals(requested)) {
            return;
        }
        requested = dir;
        RP.post(() -> {
            if (!dir.equals(requested)) {
                return; // superseded while queued; the newer request owns the sink
            }
            Node node;
            try {
                node = resolver.apply(dir);
            } catch (RuntimeException | LinkageError ex) {
                return; // loaders unavailable (stripped platform): no selection, no harm
            }
            if (node == null) {
                return; // dir vanished between aim and resolve; keep the old selection
            }
            javax.swing.SwingUtilities.invokeLater(() -> {
                if (dir.equals(requested)) {
                    sink.accept(node);
                }
            });
        });
    }

    /**
     * Forgets the equality guard — called when the owning window closes,
     * so a reopen re-resolves and re-publishes even for the same aim.
     */
    public void reset() {
        requested = null;
    }

    /** The production resolver: the file or directory's DataObject node delegate. */
    static Node resolveNode(File dir) {
        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(dir));
        if (fo == null) {
            return null;
        }
        try {
            return DataObject.find(fo).getNodeDelegate();
        } catch (DataObjectNotFoundException ex) {
            return null;
        }
    }
}
