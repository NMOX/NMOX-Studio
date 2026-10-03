package org.nmox.studio.core.util;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.LongSupplier;
import javax.swing.event.ChangeListener;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.ChangeableDataFilter;
import org.openide.loaders.DataFilter;
import org.openide.loaders.DataFolder;
import org.openide.loaders.DataObject;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.util.ChangeSupport;
import org.openide.util.RequestProcessor;

/**
 * Keeps what a project's {@code files.exclude} names out of one file
 * tree: the platform's own filter for a folder's children, so a tree
 * built on {@link #nodeFor(DataFolder)} never lists a
 * hidden file at any depth, and redraws by itself when the settings
 * change. Hidden from the VIEW only: the file is still on disk, still
 * opens by name, is still committed, and nothing here answers the
 * platform's visibility or sharability queries.
 *
 * <p><b>Never the disk on a paint.</b> The platform asks
 * {@link #acceptFileObject} while it lists a folder, on its own lane or
 * on the EDT; the answer comes from the {@link VsCodeExcludes} value held
 * in memory and from the two FileObjects' paths. The value is read by
 * {@link #refresh()} — which touches the disk and so runs on this
 * class's lane ({@link #refreshLater()}) or the caller's own background
 * one — and an answer older than {@link #FRESH_MS} asks for a re-read
 * while it is served. Until the first read lands nothing is hidden.
 *
 * <p><b>An edit takes effect without a restart</b>: when a read finds
 * different exclusions, the listeners the platform's folder children
 * registered are told, and they list again.
 */
public final class VsCodeHidden implements ChangeableDataFilter, DataFilter.FileBased {

    private static final long serialVersionUID = 1L;

    /** How long an answer is served before a re-read is asked for. */
    public static final long FRESH_MS = 2_000;

    private static final RequestProcessor RP = new RequestProcessor("VS Code files.exclude", 1, true);

    /** The clock; a seam so a test can age an answer without sleeping. */
    static volatile LongSupplier clock = System::currentTimeMillis;

    private final transient FileObject root;
    private final transient Function<File, VsCodeExcludes> reader;
    private final transient ChangeSupport changes = new ChangeSupport(this);
    private final transient AtomicBoolean inFlight = new AtomicBoolean();
    private transient volatile VsCodeExcludes excludes = VsCodeExcludes.NONE;
    private transient volatile long readAt = Long.MIN_VALUE;

    /** A filter for the tree rooted at {@code root}. Reads nothing yet. */
    public VsCodeHidden(FileObject root) {
        this(root, VsCodeSettingsFile::excludesFor);
    }

    VsCodeHidden(FileObject root, Function<File, VsCodeExcludes> reader) {
        this.root = root;
        this.reader = reader;
    }

    /**
     * Reads the settings that speak for the tree's root and, when what
     * they exclude changed, tells the tree. Touches the disk: never call
     * it on the EDT.
     *
     * @return whether the exclusions changed
     */
    public boolean refresh() {
        File dir = FileUtil.toFile(root);
        VsCodeExcludes now = dir == null ? VsCodeExcludes.NONE : reader.apply(dir);
        readAt = clock.getAsLong();
        if (now.equals(excludes)) {
            return false;
        }
        excludes = now;
        changes.fireChange();
        return true;
    }

    /** {@link #refresh()} on this class's own lane; one at a time per tree. */
    public void refreshLater() {
        if (inFlight.compareAndSet(false, true)) {
            RP.post(() -> {
                try {
                    refresh();
                } finally {
                    inFlight.set(false);
                }
            });
        }
    }

    /**
     * {@code folder}'s node with this filter on its children - and, because
     * the platform hands a folder's filter down to the folders inside it,
     * on every level beneath. The node is the folder's own in every other
     * respect: name, icon, actions, lookup.
     */
    public Node nodeFor(DataFolder folder) {
        return new FilterNode(folder.getNodeDelegate(), folder.createNodeChildren(this));
    }

    /** What is hidden right now, as last read. */
    public VsCodeExcludes excludes() {
        return excludes;
    }

    @Override
    public boolean acceptFileObject(FileObject fo) {
        if (readAt == Long.MIN_VALUE || clock.getAsLong() - readAt >= FRESH_MS) {
            refreshLater();
        }
        VsCodeExcludes current = excludes;
        if (fo == null || current.isEmpty()) {
            return true;
        }
        // both paths are already in memory: no stat
        String relative = FileUtil.getRelativePath(root, fo);
        return relative == null || relative.isEmpty() || !current.hides(relative);
    }

    @Override
    public boolean acceptDataObject(DataObject obj) {
        return obj == null || acceptFileObject(obj.getPrimaryFile());
    }

    @Override
    public void addChangeListener(ChangeListener listener) {
        changes.addChangeListener(listener);
    }

    @Override
    public void removeChangeListener(ChangeListener listener) {
        changes.removeChangeListener(listener);
    }

    /** For the tests: wait until every queued read has landed. */
    static void awaitIdle() {
        RP.post(() -> { }).waitFinished();
    }
}
