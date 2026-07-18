package org.nmox.studio.rack.blockstudio;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.SwingUtilities;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.service.RackService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The v4 multi-component studio laws: a v1 workspace file migrates live
 * and persists as v2, a component switch is a patch boundary (fresh
 * undo history), and Open Component… lands imports in the workspace —
 * replacing a same-tag component, joining as new otherwise.
 */
class BlockMultiComponentTest {

    private static void drain() throws Exception {
        BlockStudioTopComponent.drainIoLane();
        SwingUtilities.invokeAndWait(() -> { });
        BlockStudioTopComponent.drainIoLane();
        SwingUtilities.invokeAndWait(() -> { });
    }

    private static BlockStudioTopComponent open(Path dir) throws Exception {
        RackService.getDefault().getRack().setProjectDir(dir.toFile());
        BlockStudioTopComponent[] tc = new BlockStudioTopComponent[1];
        SwingUtilities.invokeAndWait(() -> tc[0] = new BlockStudioTopComponent());
        SwingUtilities.invokeAndWait(tc[0]::componentOpened);
        SwingUtilities.invokeAndWait(tc[0]::componentShowing);
        drain();
        return tc[0];
    }

    @Test
    @DisplayName("A v1 single-doc file loads live and the next save writes v2")
    void v1FileMigratesLive(@TempDir Path dir) throws Exception {
        BlockDoc old = new BlockDoc();
        old.root().setParam("tag", "legacy-tag");
        Files.writeString(dir.resolve(BlockIO.WORKSPACE_FILE), old.toJson().toString(2) + "\n");

        BlockStudioTopComponent tc = open(dir);
        try {
            SwingUtilities.invokeAndWait(() -> {
                assertThat(tc.currentWorkspace().components()).hasSize(1);
                assertThat(tc.currentDoc().root().param("tag")).isEqualTo("legacy-tag");
                tc.persist();
            });
            drain();
            JSONObject onDisk = new JSONObject(
                    Files.readString(dir.resolve(BlockIO.WORKSPACE_FILE)));
            assertThat(onDisk.getInt("version")).isEqualTo(2);
            assertThat(onDisk.getJSONArray("components").length()).isEqualTo(1);
        } finally {
            SwingUtilities.invokeAndWait(tc::componentClosed);
        }
    }

    @Test
    @DisplayName("Switching components is a patch boundary: undo starts fresh, both persist")
    void switchIsAPatchBoundary(@TempDir Path dir) throws Exception {
        BlockStudioTopComponent tc = open(dir);
        try {
            SwingUtilities.invokeAndWait(() -> {
                tc.addComponent(); // my-widget-2, now active (index 1)
                assertThat(tc.currentWorkspace().active()).isEqualTo(1);

                // replace-import onto the active component leaves one undo entry
                BlockDoc edited = new BlockDoc();
                edited.root().setParam("tag", "my-widget-2");
                tc.importParsed(edited, "test");
                assertThat(tc.undoDepth()).isEqualTo(1);

                tc.switchToComponent(0);
                assertThat(tc.undoDepth())
                        .as("a switch must clear undo — ⌘Z may not peel the other component")
                        .isZero();
                assertThat(tc.currentDoc().root().param("tag")).isEqualTo("my-widget");
            });
            drain();
            JSONObject onDisk = new JSONObject(
                    Files.readString(dir.resolve(BlockIO.WORKSPACE_FILE)));
            assertThat(onDisk.getJSONArray("components").length())
                    .as("the switch force-saved the whole workspace")
                    .isEqualTo(2);
        } finally {
            SwingUtilities.invokeAndWait(tc::componentClosed);
        }
    }

    @Test
    @DisplayName("Import: same tag replaces that component, new tag joins the workspace")
    void importReplacesOrAppends(@TempDir Path dir) throws Exception {
        BlockStudioTopComponent tc = open(dir);
        try {
            SwingUtilities.invokeAndWait(() -> {
                BlockDoc fresh = new BlockDoc();
                fresh.root().setParam("tag", "brand-new");
                tc.importParsed(fresh, "brand-new.js");
                assertThat(tc.currentWorkspace().tags())
                        .containsExactly("my-widget", "brand-new");
                assertThat(tc.currentDoc()).isSameAs(fresh);

                BlockDoc replacement = new BlockDoc();
                replacement.root().setParam("tag", "my-widget");
                Block state = replacement.create(BlockKind.STATE);
                state.setParam("name", "n");
                replacement.insert(replacement.root(), state, 0);
                tc.importParsed(replacement, "my-widget.js");
                assertThat(tc.currentWorkspace().tags())
                        .as("no third component — same tag replaced in place")
                        .containsExactly("my-widget", "brand-new");
                assertThat(tc.currentWorkspace().active()).isZero();
                assertThat(tc.currentDoc().preorder()).hasSize(2);
            });
        } finally {
            SwingUtilities.invokeAndWait(tc::componentClosed);
        }
    }
}
