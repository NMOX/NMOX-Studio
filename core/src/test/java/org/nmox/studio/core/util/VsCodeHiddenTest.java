package org.nmox.studio.core.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataFolder;
import org.openide.loaders.DataObject;
import org.openide.nodes.Node;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * files.exclude over a real folder tree, through the platform's own
 * folder nodes: hidden from the view at every depth, the files themselves
 * untouched, never a disk read on the EDT, and a redraw when the settings
 * change.
 */
class VsCodeHiddenTest {

    @TempDir
    Path tmp;

    @BeforeEach
    @AfterEach
    void reset() {
        VsCodeHidden.awaitIdle();
        VsCodeHidden.clock = System::currentTimeMillis;
        VsCodeSettingsFile.forgetForTest();
    }

    private void write(String rel, String text) throws Exception {
        Path p = tmp.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, text);
    }

    private FileObject root() {
        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(tmp.toFile()));
        assertThat(fo).isNotNull();
        fo.refresh();
        return fo;
    }

    /** The names a folder node lists, once the platform has finished listing. */
    private static List<String> listed(Node folder) {
        List<String> names = new ArrayList<>();
        for (Node n : folder.getChildren().getNodes(true)) {
            DataObject d = n.getLookup().lookup(DataObject.class);
            names.add(d == null ? "?" + n.getName() : d.getPrimaryFile().getNameExt());
        }
        names.sort(null);
        return names;
    }

    private static Node child(Node folder, String name) {
        for (Node n : folder.getChildren().getNodes(true)) {
            DataObject d = n.getLookup().lookup(DataObject.class);
            if (d != null && d.getPrimaryFile().getNameExt().equals(name)) {
                return n;
            }
        }
        throw new AssertionError(name + " is not listed under " + folder.getName());
    }

    private void fixture() throws Exception {
        Files.createDirectories(tmp.resolve(".git"));
        write(".vscode/settings.json", "{\"files.exclude\": {\"**/dist\": true, \"secret.txt\": true}}");
        write("src/app.js", "app");
        write("src/dist/x.js", "x");
        write("src/secret.txt", "not the root's secret.txt");
        write("dist/y.js", "y");
        write("secret.txt", "hidden");
        write("keep.txt", "shown");
    }

    @Test
    @DisplayName("hidden from the tree at every depth; the files themselves are untouched")
    void hiddenFromTheViewOnly() throws Exception {
        fixture();
        FileObject root = root();
        VsCodeHidden hidden = new VsCodeHidden(root);
        hidden.refresh();
        Node tree = hidden.nodeFor(DataFolder.findFolder(root));

        assertThat(listed(tree)).containsExactly(".git", ".vscode", "keep.txt", "src");
        assertThat(listed(child(tree, "src"))).as("dist is hidden one level down too; secret.txt names the root's only")
                .containsExactly("app.js", "secret.txt");

        assertThat(tmp.resolve("dist/y.js")).hasContent("y");
        assertThat(tmp.resolve("src/dist/x.js")).hasContent("x");
        assertThat(tmp.resolve("secret.txt")).hasContent("hidden");
        assertThat(root.getFileObject("dist/y.js")).as("still a file the platform can open by name").isNotNull();
    }

    @Test
    @DisplayName("the control: without the filter the same tree lists everything")
    void control() throws Exception {
        fixture();
        Node tree = DataFolder.findFolder(root()).getNodeDelegate();
        assertThat(listed(tree)).containsExactly(".git", ".vscode", "dist", "keep.txt", "secret.txt", "src");
    }

    @Test
    @DisplayName("an edit to settings.json redraws the tree: the listeners the folder registered are told")
    void anEditRedraws() throws Exception {
        fixture();
        FileObject root = root();
        VsCodeHidden hidden = new VsCodeHidden(root);
        hidden.refresh();
        Node tree = hidden.nodeFor(DataFolder.findFolder(root));
        assertThat(listed(tree)).doesNotContain("dist", "secret.txt").contains("keep.txt");
        AtomicInteger told = new AtomicInteger();
        hidden.addChangeListener(e -> told.incrementAndGet());

        assertThat(hidden.refresh()).as("nothing changed: nobody is told").isFalse();
        assertThat(told).hasValue(0);

        Path settings = tmp.resolve(".vscode/settings.json");
        Files.writeString(settings, "{\"files.exclude\": {\"keep.txt\": true}}");
        Files.setLastModifiedTime(settings, FileTime.fromMillis(System.currentTimeMillis() + 5_000));
        assertThat(hidden.refresh()).isTrue();
        assertThat(told).hasValue(1);
        assertThat(listed(tree)).containsExactly(".git", ".vscode", "dist", "secret.txt", "src");

        Files.delete(settings);
        assertThat(hidden.refresh()).isTrue();
        assertThat(listed(tree)).containsExactly(".git", ".vscode", "dist", "keep.txt", "secret.txt", "src");
    }

    @Test
    @DisplayName("the EDT is answered from memory: the settings are read on a background lane, never on it")
    void neverTheDiskOnTheEdt() throws Exception {
        fixture();
        FileObject root = root();
        FileObject dist = root.getFileObject("dist");
        List<Boolean> readOnEdt = new CopyOnWriteArrayList<>();
        VsCodeExcludes excludes = VsCodeExcludes.parse("{\"files.exclude\": {\"**/dist\": true}}", false);
        long[] now = {1_000_000};
        VsCodeHidden.clock = () -> now[0];
        List<File> asked = new CopyOnWriteArrayList<>();
        VsCodeHidden hidden = new VsCodeHidden(root, dir -> {
            readOnEdt.add(SwingUtilities.isEventDispatchThread());
            asked.add(dir);
            return excludes;
        });

        AtomicReference<Boolean> first = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> first.set(hidden.acceptFileObject(dist)));
        assertThat(first.get()).as("nothing is known yet, so nothing is hidden").isTrue();
        VsCodeHidden.awaitIdle();
        assertThat(readOnEdt).as("the first ask queued one read, off the EDT").containsExactly(false);

        AtomicReference<Boolean> second = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> second.set(hidden.acceptFileObject(dist)));
        assertThat(second.get()).isFalse();
        VsCodeHidden.awaitIdle();
        assertThat(readOnEdt).as("still fresh: served from memory").hasSize(1);

        now[0] += VsCodeHidden.FRESH_MS;
        SwingUtilities.invokeAndWait(() -> hidden.acceptFileObject(dist));
        VsCodeHidden.awaitIdle();
        assertThat(readOnEdt).as("an aged answer asks for a re-read, off the EDT again").containsExactly(false, false);
        assertThat(asked).as("always about the tree's own root").containsOnly(FileUtil.toFile(root));
    }

    @Test
    @DisplayName("a file outside the tree, the root itself and a null are never hidden")
    void outsideTheTree() throws Exception {
        fixture();
        FileObject root = root();
        VsCodeHidden hidden = new VsCodeHidden(root,
                dir -> VsCodeExcludes.parse("{\"files.exclude\": {\"**\": true}}", false));
        hidden.refresh();
        assertThat(hidden.acceptFileObject(root.getFileObject("keep.txt"))).as("** hides everything inside").isFalse();
        assertThat(hidden.acceptFileObject(root)).isTrue();
        assertThat(hidden.acceptFileObject(root.getParent())).isTrue();
        assertThat(hidden.acceptFileObject(null)).isTrue();
        assertThat(hidden.acceptDataObject(null)).isTrue();
        assertThat(hidden.acceptDataObject(DataObject.find(root.getFileObject("keep.txt")))).isFalse();
    }

    @Test
    @DisplayName("a tree in no repository hides nothing")
    void noRepository() throws Exception {
        write(".vscode/settings.json", "{\"files.exclude\": {\"**/dist\": true}}");
        write("dist/y.js", "y");
        FileObject root = root();
        VsCodeHidden hidden = new VsCodeHidden(root);
        assertThat(hidden.refresh()).isFalse();
        assertThat(hidden.excludes()).isSameAs(VsCodeExcludes.NONE);
        assertThat(listed(hidden.nodeFor(DataFolder.findFolder(root)))).contains("dist");
    }
}
