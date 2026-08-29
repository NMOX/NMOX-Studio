package org.nmox.studio.editor.testing;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.editor.testing.TestIndex.DiscoveredTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Tests window's discovery laws: one vocabulary with Run Focused
 * Test (patternFor is the single source), bounded walk that SPEAKS
 * when clipped, exact 1-based lines (the runners' own convention),
 * and the mtime cache.
 */
class TestIndexTest {

    private static final Function<Path, String> MIMES = p -> {
        String n = p.toString();
        if (n.endsWith(".js")) {
            return "text/javascript";
        }
        if (n.endsWith(".py")) {
            return "text/x-python";
        }
        return "text/plain";
    };

    private static Map<Path, List<DiscoveredTest>> refresh(TestIndex ix, Path root) {
        return ix.refresh(root, MIMES, v -> false);
    }

    @Test
    @DisplayName("Discovery speaks the focused runner's own vocabulary")
    void discoversAcrossMimes(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("app.test.js"),
                "// header\nit('adds numbers', () => {});\n\ntest('greets', () => {});\n");
        Files.writeString(root.resolve("test_app.py"),
                "def helper():\n    pass\n\ndef test_greets():\n    pass\n");
        Files.writeString(root.resolve("notes.txt"), "it('is prose')\n");
        Map<Path, List<DiscoveredTest>> found = refresh(new TestIndex(), root);
        assertThat(found.get(root.resolve("app.test.js")))
                .extracting(DiscoveredTest::name)
                .containsExactly("adds numbers", "greets");
        assertThat(found.get(root.resolve("test_app.py")))
                .extracting(DiscoveredTest::name)
                .containsExactly("test_greets");
        // a mime with no focused-test pattern contributes nothing — the
        // window lists exactly what the runner can run
        assertThat(found).doesNotContainKey(root.resolve("notes.txt"));
    }

    @Test
    @DisplayName("Lines are 1-based and exact — the runners' convention")
    void linesAreExact(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("a.test.js"),
                "// one\n// two\nit('third line', () => {});\n// four\ntest('fifth line', () => {});\n");
        Map<Path, List<DiscoveredTest>> found = refresh(new TestIndex(), root);
        assertThat(found.get(root.resolve("a.test.js")))
                .extracting(DiscoveredTest::line)
                .containsExactly(3, 5);
    }

    @Test
    @DisplayName("Heavy dirs contribute nothing")
    void heavyDirsSkipped(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("mine.test.js"), "it('mine', () => {});\n");
        Path heavy = Files.createDirectories(root.resolve("node_modules").resolve("dep"));
        Files.writeString(heavy.resolve("dep.test.js"), "it('vendor', () => {});\n");
        Map<Path, List<DiscoveredTest>> found = refresh(new TestIndex(), root);
        assertThat(found.keySet()).containsExactly(root.resolve("mine.test.js"));
    }

    @Test
    @DisplayName("The breadth cap trips wasTruncated — a partial listing says so")
    void breadthCapSpeaks(@TempDir Path root) throws Exception {
        for (int i = 0; i < TestIndex.MAX_FILES + 5; i++) {
            Files.writeString(root.resolve("f" + i + ".js"), "it('t" + i + "', () => {});\n");
        }
        TestIndex ix = new TestIndex();
        refresh(ix, root);
        assertThat(ix.wasTruncated()).isTrue();
    }

    @Test
    @DisplayName("A pathologically deep tree trips the cap and says so")
    void depthCapSpeaks(@TempDir Path root) throws Exception {
        Path deep = root;
        for (int i = 0; i <= 33; i++) {
            deep = Files.createDirectories(deep.resolve("d" + i));
        }
        Files.writeString(deep.resolve("far.test.js"), "it('far', () => {});\n");
        TestIndex ix = new TestIndex();
        Map<Path, List<DiscoveredTest>> found = refresh(ix, root);
        assertThat(found).isEmpty();
        assertThat(ix.wasTruncated()).isTrue();
    }

    @Test
    @DisplayName("Unchanged files ride the cache; a changed file re-reads")
    void cacheHonorsMtime(@TempDir Path root) throws Exception {
        Path f = root.resolve("a.test.js");
        Files.writeString(f, "it('first', () => {});\n");
        TestIndex ix = new TestIndex();
        assertThat(refresh(ix, root).get(f))
                .extracting(DiscoveredTest::name).containsExactly("first");
        Files.writeString(f, "it('second', () => {});\n");
        Files.setLastModifiedTime(f, java.nio.file.attribute.FileTime.fromMillis(
                System.currentTimeMillis() + 5_000));
        assertThat(refresh(ix, root).get(f))
                .extracting(DiscoveredTest::name).containsExactly("second");
    }
}
