package org.nmox.studio.rack.projectstudio;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataFolder;
import org.openide.nodes.Node;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The default-workspace EDT law (1.195.0 smoke test): materializing the
 * file tree's children on the EDT must not force DataObject creation
 * there. The platform hands files over as FolderChildren delayed nodes
 * whose lookup forces {@code DataObject.find} inline for any
 * DataObject-assignable template — and logs
 * "Attempt to obtain DataObject for ... from EDT" when that happens on
 * the paint thread. {@code HeavyChildren.copyNode} used to classify
 * children by {@code lookup(DataFolder.class)}, which is exactly such a
 * template; a fresh launch aiming at a non-empty ~/NMOX warned once per
 * file.
 *
 * <p>This test drives the REAL platform machinery — a real DataFolder
 * node over a non-empty workspace fixture, wrapped the way the tree
 * wraps it, children forced on the EDT the way the view forces them —
 * and fails on the platform's own warning. An empty directory would
 * miss the bug (folders are created synchronously, only files ride the
 * delayed path), so the fixture mirrors the observed ~/NMOX: dotfiles,
 * a README, a plain subdirectory, and a heavy one.
 */
class FileTreeEdtDataObjectLawTest {

    private final List<LogRecord> edtWarnings = new CopyOnWriteArrayList<>();
    private final Logger folderChildrenLog = Logger.getLogger("org.openide.loaders.FolderChildren");
    private Handler handler;

    @AfterEach
    void detach() {
        if (handler != null) {
            folderChildrenLog.removeHandler(handler);
        }
    }

    @Test
    @DisplayName("Materializing children on the EDT never resolves a DataObject there")
    void edtMaterializationResolvesNoDataObjects(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve(".nmoxapi.json"), "{}");
        Files.writeString(dir.resolve(".nmoxdb.json"), "{}");
        Files.writeString(dir.resolve("README.md"), "# fixture");
        Files.createDirectories(dir.resolve("src"));
        Files.createDirectories(dir.resolve("node_modules"));

        handler = new Handler() {
            @Override
            public void publish(LogRecord r) {
                if (r.getLevel().intValue() >= Level.WARNING.intValue()
                        && String.valueOf(r.getMessage()).contains("from EDT")) {
                    edtWarnings.add(r);
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        folderChildrenLog.addHandler(handler);

        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(dir.toFile()));
        assertThat(fo).isNotNull();
        // wrap off the EDT, exactly as FileTreePanel's scanner lane does
        Node root = new FileTreePanel.HeavyAwareFilterNode(
                DataFolder.findFolder(fo).getNodeDelegate());

        // initialize the keys off the EDT (the platform computes the file
        // list on its own lane before the view ever paints) ...
        int count = root.getChildren().getNodesCount(true);
        assertThat(count).isEqualTo(5);

        // ... then materialize each child ON the EDT through the LAZY
        // snapshot — the exact path TreeView's layout takes
        // (VisualizerChildren.getChildAt → snapshot.get → createNodes →
        // copyNode). getNodes(true) would be unfaithful: its optimal
        // path waits every delayed node into a real DataNode first,
        // which is precisely what the paint path never does.
        java.util.List<Node> out0 = new java.util.ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            java.util.List<Node> snap = root.getChildren().snapshot();
            for (int i = 0; i < snap.size(); i++) {
                out0.add(snap.get(i));
            }
        });
        Node[] out = out0.toArray(Node[]::new);

        assertThat(edtWarnings)
                .as("FolderChildren 'Attempt to obtain DataObject ... from EDT' warnings")
                .isEmpty();

        // and the classification still holds: files are leaves, plain
        // dirs descend, heavy dirs are dark (childless)
        assertThat(out).extracting(Node::getName)
                .contains(".nmoxapi.json", ".nmoxdb.json", "README.md", "src", "node_modules");
        for (Node n : out) {
            if ("node_modules".equals(n.getName())) {
                assertThat(n.isLeaf()).as("heavy dir stays dark").isTrue();
            }
            if ("src".equals(n.getName())) {
                assertThat(n.isLeaf()).as("plain dir keeps its triangle").isFalse();
            }
        }
    }
}
