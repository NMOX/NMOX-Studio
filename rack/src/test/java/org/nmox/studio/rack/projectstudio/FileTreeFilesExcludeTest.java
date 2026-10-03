package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openide.loaders.DataObject;
import org.openide.nodes.Node;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Project Studio's tree and a repository's {@code files.exclude}: what the
 * project's .vscode/settings.json hides is not listed, at any depth; the
 * files are untouched; the heavy folders keep their own rule; and an edit
 * to the settings redraws the rows without re-rooting the tree.
 */
class FileTreeFilesExcludeTest {

    @TempDir
    Path tmp;

    private void write(String rel, String text) throws Exception {
        Path p = tmp.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, text);
    }

    private void fixture() throws Exception {
        Files.createDirectories(tmp.resolve(".git"));
        write(".vscode/settings.json", """
                {
                  "files.exclude": { "**/__pycache__": true, "**/*.pyc": true, "legacy": true },
                  "search.exclude": { "**/fixtures": true },
                }
                """);
        write("app.py", "print()");
        write("app.pyc", "bytes");
        write("__pycache__/app.cpython.pyc", "bytes");
        write("pkg/mod.py", "x");
        write("pkg/mod.pyc", "bytes");
        write("pkg/__pycache__/mod.pyc", "bytes");
        write("pkg/fixtures/a.json", "{}");
        write("legacy/old.py", "x");
        write("node_modules/dep/index.js", "x");
    }

    /** The names a folder row lists, once the platform has finished listing. */
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

    @Test
    @DisplayName("what files.exclude names is not listed, at any depth, and the files are untouched")
    void hiddenAtEveryDepth() throws Exception {
        fixture();
        Node root = FileTreePanel.REAL_RESOLVER.resolve(tmp.toFile());

        assertThat(listed(root)).containsExactly(".git", ".vscode", "app.py", "node_modules", "pkg");
        assertThat(listed(child(root, "pkg")))
                .as("search.exclude hides nothing from a tree: fixtures is listed")
                .containsExactly("fixtures", "mod.py");

        assertThat(tmp.resolve("app.pyc")).hasContent("bytes");
        assertThat(tmp.resolve("pkg/__pycache__/mod.pyc")).hasContent("bytes");
        assertThat(tmp.resolve("legacy/old.py")).hasContent("x");
    }

    @Test
    @DisplayName("the settings are already read when the resolver returns: no row is listed and then taken away")
    void readWhereTheRootIsResolved() throws Exception {
        fixture();
        FileTreePanel.HeavyAwareFilterNode root =
                (FileTreePanel.HeavyAwareFilterNode) FileTreePanel.REAL_RESOLVER.resolve(tmp.toFile());
        assertThat(root.hidden().excludes().hiddenPatterns())
                .containsExactly("**/*.pyc", "**/__pycache__", "legacy");
    }

    @Test
    @DisplayName("the heavy folders keep their own rule: listed, dark, never entered")
    void heavyFoldersStayDark() throws Exception {
        fixture();
        Node root = FileTreePanel.REAL_RESOLVER.resolve(tmp.toFile());
        assertThat(child(root, "node_modules").isLeaf()).isTrue();
        assertThat(child(root, ".git").isLeaf()).isTrue();
        assertThat(child(root, "pkg").isLeaf()).isFalse();
    }

    @Test
    @DisplayName("the root row is still the folder: its name, its DataObject, not deletable from its own tree")
    void rootIsStillTheFolder() throws Exception {
        fixture();
        Node root = FileTreePanel.REAL_RESOLVER.resolve(tmp.toFile());
        DataObject d = root.getLookup().lookup(DataObject.class);
        assertThat(d).isNotNull();
        assertThat(d.getPrimaryFile().getNameExt()).isEqualTo(tmp.getFileName().toString());
        assertThat(root.getName()).isEqualTo(tmp.getFileName().toString());
        assertThat(root.canDestroy()).isFalse();
    }

    @Test
    @DisplayName("a project with no settings lists everything")
    void control() throws Exception {
        fixture();
        Files.delete(tmp.resolve(".vscode/settings.json"));
        Node root = FileTreePanel.REAL_RESOLVER.resolve(tmp.toFile());
        assertThat(listed(root)).containsExactly(
                ".git", ".vscode", "__pycache__", "app.py", "app.pyc", "legacy", "node_modules", "pkg");
    }

    private static void drainEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }

    @Test
    @DisplayName("an edit to settings.json reaches the tree at its next recheck: same root row, new rows")
    void anEditRedrawsInPlace() throws Exception {
        fixture();
        File dir = tmp.toFile();
        FileTreePanel[] made = new FileTreePanel[1];
        SwingUtilities.invokeAndWait(() -> made[0] = new FileTreePanel());
        FileTreePanel panel = made[0];
        try {
            SwingUtilities.invokeAndWait(() -> panel.setRootDirectory(dir));
            panel.awaitScanner();
            drainEdt();
            Node root = panel.getExplorerManager().getRootContext();
            assertThat(listed(root)).doesNotContain("legacy", "app.pyc").contains("app.py");

            Path settings = tmp.resolve(".vscode/settings.json");
            Files.writeString(settings, "{ \"files.exclude\": { \"app.py\": true } }");
            Files.setLastModifiedTime(settings, FileTime.fromMillis(System.currentTimeMillis() + 5_000));
            panel.recheckHidden();
            drainEdt();

            assertThat(panel.getExplorerManager().getRootContext())
                    .as("the tree is not re-rooted, so what was expanded stays expanded").isSameAs(root);
            assertThat(listed(root)).contains("legacy", "app.pyc", "__pycache__").doesNotContain("app.py");
        } finally {
            SwingUtilities.invokeAndWait(panel::dispose);
        }
    }

    @Test
    @DisplayName("the recheck runs only while the tree watches its folder, and reads nothing on the EDT")
    void recheckLifecycle() throws Exception {
        String source = Files.readString(Path.of("src/main/java/org/nmox/studio/rack/projectstudio/FileTreePanel.java"));
        String restart = source.substring(source.indexOf("private void restartWatcher()"),
                source.indexOf("void recheckHidden()"));
        assertThat(restart).as("a re-aim stops the old recheck before anything else")
                .containsSubsequence("watching = false;", "hiddenRecheck.cancel();", "watcher.start();",
                        "watching = true;", "hiddenRecheck.schedule(HIDDEN_RECHECK_MS);");
        String dispose = source.substring(source.indexOf("public void dispose()"));
        assertThat(dispose).containsSubsequence("watching = false;", "hiddenRecheck.cancel();");
        assertThat(source).as("the recheck is a task of the scanner lane: never the EDT")
                .contains("hiddenRecheck = scanner.create(this::recheckHidden)");
    }
}
