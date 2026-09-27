package org.nmox.studio.rack.blockstudio;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javax.swing.SwingUtilities;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.service.RackService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A workspace that changes on disk under an open Block Studio — a
 * {@code git pull} with the IDE open (3.4, question 1; the hostile review).
 *
 * <p>Measured before the fix: a foreign edit that arrived while an edit was
 * waiting to be saved said "overridden by newer studio edits" and let the
 * pending save replace the teammate's components; {@code persist()} asked
 * only the lock, which is decided at load and is null for a file that loaded
 * cleanly; and every component switch rewrote the committed file although
 * which component is open is one person's state.
 */
class BlockPulledUnderTheStudioTest {

    private Locale before;

    @BeforeAll
    static void noRealPreferences() {
        BlockActiveMemory.useMemoryStoreForTests();
    }

    @BeforeEach
    void english() {
        before = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
    }

    @AfterEach
    void restore() {
        Locale.setDefault(before);
    }

    private static String workspace(String... tags) {
        BlockWorkspace ws = new BlockWorkspace();
        ws.activeDoc().root().setParam("tag", tags[0]);
        for (int i = 1; i < tags.length; i++) {
            ws.add().root().setParam("tag", tags[i]);
        }
        return ws.toJson().toString(2) + "\n";
    }

    private static BlockStudioTopComponent open(Path dir) throws Exception {
        RackService.getDefault().getRack().setProjectDir(dir.toFile());
        BlockStudioTopComponent[] tc = new BlockStudioTopComponent[1];
        SwingUtilities.invokeAndWait(() -> tc[0] = new BlockStudioTopComponent());
        tc[0].openBrowser = false;
        SwingUtilities.invokeAndWait(tc[0]::componentOpened);
        SwingUtilities.invokeAndWait(tc[0]::componentShowing);
        drain();
        return tc[0];
    }

    private static void close(BlockStudioTopComponent tc) throws Exception {
        SwingUtilities.invokeAndWait(tc::componentClosed);
        BlockStudioTopComponent.drainIoLane();
    }

    private static void drain() throws Exception {
        for (int i = 0; i < 3; i++) {
            BlockStudioTopComponent.drainIoLane();
            SwingUtilities.invokeAndWait(() -> { });
        }
    }

    @Test
    @DisplayName("a pull arriving while an edit waits to be saved: the file wins, the pending save is cancelled, and that is said")
    void foreignEditCancelsThePendingSave(@TempDir Path dir) throws Exception {
        Path f = dir.resolve(BlockIO.WORKSPACE_FILE);
        Files.writeString(f, workspace("my-thing"), StandardCharsets.UTF_8);
        BlockStudioTopComponent tc = open(dir);
        try {
            SwingUtilities.invokeAndWait(tc::addComponent); // an edit, waiting on the debounce
            String theirs = workspace("my-thing", "from-bob");
            Files.writeString(f, theirs, StandardCharsets.UTF_8);
            tc.onWorkspaceFileChanged(f.toFile().lastModified(), Files.size(f));
            drain();
            Thread.sleep(1_000); // past the debounce: a save still pending would have landed
            drain();
            assertThat(f).as("the teammate's components are still the file").hasContent(theirs);
            SwingUtilities.invokeAndWait(() -> {
                assertThat(tc.currentWorkspace().tags()).containsExactly("my-thing", "from-bob");
                assertThat(tc.statusText()).contains("changed on disk").contains("not written over");
            });
        } finally {
            close(tc);
        }
    }

    @Test
    @DisplayName("a save asks the disk: bytes that changed since the load are never written over, even before the pulse has looked")
    void persistAsksTheDisk(@TempDir Path dir) throws Exception {
        Path f = dir.resolve(BlockIO.WORKSPACE_FILE);
        Files.writeString(f, workspace("my-thing"), StandardCharsets.UTF_8);
        BlockStudioTopComponent tc = open(dir);
        try {
            String conflict = BlockMergeTest.conflicted();
            Files.writeString(f, conflict, StandardCharsets.UTF_8); // a pull, not yet seen
            SwingUtilities.invokeAndWait(tc::persist);
            drain();
            assertThat(f).as("git's conflict is left exactly as it is").hasContent(conflict);
            SwingUtilities.invokeAndWait(() -> assertThat(tc.lockedReason())
                    .as("read again, and bound read-only").contains("merge conflicts"));
        } finally {
            close(tc);
        }
    }

    @Test
    @DisplayName("switching components writes nothing to the committed file; the open component is remembered for this person")
    void aSwitchWritesNothing(@TempDir Path dir) throws Exception {
        Path f = dir.resolve(BlockIO.WORKSPACE_FILE);
        String bytes = workspace("first-one", "second-one");
        Files.writeString(f, bytes, StandardCharsets.UTF_8);
        long mtime = f.toFile().lastModified();
        BlockStudioTopComponent tc = open(dir);
        try {
            SwingUtilities.invokeAndWait(() -> tc.switchToComponent(1));
            drain();
            Thread.sleep(1_000); // past the debounce: a switch that armed a save would have written
            drain();
            assertThat(f).hasContent(bytes);
            assertThat(f.toFile().lastModified()).as("not even rewritten with the same bytes").isEqualTo(mtime);
            assertThat(BlockActiveMemory.recall(dir.toFile())).isEqualTo("second-one");
        } finally {
            close(tc);
        }
    }

    @Test
    @DisplayName("a workspace from a newer NMOX Studio is left untouched and said so, whatever its pieces")
    void newerVersionIsReadOnly(@TempDir Path dir) throws Exception {
        Path f = dir.resolve(BlockIO.WORKSPACE_FILE);
        JSONObject newer = new JSONObject(workspace("my-thing")).put("version", BlockWorkspace.FORMAT + 1)
                .put("addedLater", true);
        Files.writeString(f, newer.toString(2), StandardCharsets.UTF_8);
        BlockIO.Loaded loaded = BlockIO.loadForStudio(dir.toFile());
        assertThat(loaded.workspace()).as("nothing bound, so nothing saved over the newer file").isNull();
        assertThat(loaded.lockedReason()).contains(BlockIO.WORKSPACE_FILE).contains("newer NMOX Studio")
                .contains(String.valueOf(BlockWorkspace.FORMAT + 1));

        JSONObject newerDoc = new JSONObject(workspace("my-thing"));
        newerDoc.getJSONArray("components").getJSONObject(0).put("version", BlockDoc.FORMAT + 1);
        Files.writeString(f, newerDoc.toString(2), StandardCharsets.UTF_8);
        assertThat(BlockIO.loadForStudio(dir.toFile()).lockedReason()).contains("newer NMOX Studio");
    }

    @Test
    @DisplayName("the same project by any path is one project: the open component follows a symlink and a ..")
    void activeMemoryKeysOnTheCanonicalPath(@TempDir Path tmp) throws Exception {
        Path real = Files.createDirectories(tmp.resolve("work").resolve("app"));
        Path link = tmp.resolve("app-link");
        try {
            Files.createSymbolicLink(link, real);
        } catch (UnsupportedOperationException | java.io.IOException noLinks) {
            assumeTrue(false, "this file system makes no symbolic links");
        }
        BlockActiveMemory.remember(real.toFile(), "my-widget");
        assertThat(BlockActiveMemory.recall(link.toFile())).isEqualTo("my-widget");
        assertThat(BlockActiveMemory.recall(new File(real.toFile(), "../app"))).isEqualTo("my-widget");
    }
}
