package org.nmox.studio.apiclient.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.nmox.studio.core.spi.DocsScene;
import org.nmox.studio.core.util.DocsFixtures;
import org.nmox.studio.apiclient.api.WorkspaceIO;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Stages the API Studio picture (v2.163.0) — the most-referenced shot in
 * the documentation, and English in every translated guide until now.
 *
 * <p>It takes no fixture content at all. The collection, request and
 * environment are the PRODUCT's own starter names, which have been read
 * from the reader's bundle since v2.130.0, so writing this language's
 * starter workspace and sending its one request is enough: the window
 * fills with that language's words and a real response.
 *
 * <p>The request asks {@code {{base_url}}/health} on loopback, which the
 * forge script serves for the length of the run. No network leaves the
 * machine, and a run with no server still paints an honest failed send
 * rather than stalling.
 */
@ServiceProvider(service = DocsScene.class)
public final class DocsApi implements DocsScene {

    /** The scene's name, as its picture is named. */
    public static final String ID = "api-studio";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public File stage(File home, String fixtures, String lang) throws IOException {
        File dir = DocsFixtures.projectDir(home);
        Files.createDirectories(dir.toPath());
        WorkspaceIO.save(dir, ApiClientTopComponent.starterWorkspace());
        return dir;
    }

    @Override
    public void arrange() {
        TopComponent tc = WindowManager.getDefault().findTopComponent("ApiClientTopComponent");
        if (tc instanceof ApiClientTopComponent api) {
            api.docsSendStarter();
        }
    }
}
