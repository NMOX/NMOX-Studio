package org.nmox.studio.rack.blockstudio;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.service.RackService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The v1.82.0 review's studio-lifecycle regressions, each proven to
 * fail on the pre-review code: a reopened tab must reload for the
 * CURRENT aim (close removed the rack listener, so any re-aim while
 * closed was missed — the Infra-Designer inverted-lifecycle class),
 * and an unreadable workspace file must be kept as {@code .bak}
 * before the fresh-doc fallback can ever overwrite it.
 */
class BlockReviewFixesTest {

    private static void drain() throws Exception {
        BlockStudioTopComponent.drainIoLane();
        SwingUtilities.invokeAndWait(() -> { });
        // load applies on the EDT after a second RP hop; settle both lanes
        BlockStudioTopComponent.drainIoLane();
        SwingUtilities.invokeAndWait(() -> { });
    }

    private static void writeWorkspace(Path dir, String tag) throws Exception {
        BlockDoc doc = new BlockDoc();
        doc.root().setParam("tag", tag);
        Files.writeString(dir.resolve(BlockIO.WORKSPACE_FILE), doc.toJson().toString(2) + "\n");
    }

    @Test
    @DisplayName("Reopening the tab reloads for the aim that changed while it was closed")
    void reopenReloadsForCurrentAim(@TempDir Path a, @TempDir Path b) throws Exception {
        writeWorkspace(a, "tag-aaa");
        writeWorkspace(b, "tag-bbb");
        RackService.getDefault().getRack().setProjectDir(a.toFile());

        BlockStudioTopComponent[] tc = new BlockStudioTopComponent[1];
        SwingUtilities.invokeAndWait(() -> tc[0] = new BlockStudioTopComponent());
        SwingUtilities.invokeAndWait(tc[0]::componentOpened);
        SwingUtilities.invokeAndWait(tc[0]::componentShowing);
        drain();
        SwingUtilities.invokeAndWait(() ->
                assertThat(tc[0].currentDoc().root().param("tag")).isEqualTo("tag-aaa"));

        // close, re-aim while closed (the listener is gone), reopen
        SwingUtilities.invokeAndWait(tc[0]::componentClosed);
        RackService.getDefault().getRack().setProjectDir(b.toFile());
        SwingUtilities.invokeAndWait(tc[0]::componentOpened);
        SwingUtilities.invokeAndWait(tc[0]::componentShowing);
        drain();
        SwingUtilities.invokeAndWait(() ->
                assertThat(tc[0].currentDoc().root().param("tag"))
                        .as("the reopened studio must show the aim it missed, not project A")
                        .isEqualTo("tag-bbb"));
        SwingUtilities.invokeAndWait(tc[0]::componentClosed);
    }

    @Test
    @DisplayName("An unreadable workspace file is kept as .bak before the fresh-doc fallback")
    void corruptWorkspaceKeptAsBak(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve(BlockIO.WORKSPACE_FILE), "{ not json at all");
        RackService.getDefault().getRack().setProjectDir(dir.toFile());

        BlockStudioTopComponent[] tc = new BlockStudioTopComponent[1];
        SwingUtilities.invokeAndWait(() -> tc[0] = new BlockStudioTopComponent());
        SwingUtilities.invokeAndWait(tc[0]::componentOpened);
        SwingUtilities.invokeAndWait(tc[0]::componentShowing);
        drain();

        Path bak = dir.resolve(BlockIO.WORKSPACE_FILE + ".bak");
        assertThat(bak).as("the unreadable original survives as .bak").exists();
        assertThat(Files.readString(bak)).isEqualTo("{ not json at all");
        SwingUtilities.invokeAndWait(tc[0]::componentClosed);
    }
}
