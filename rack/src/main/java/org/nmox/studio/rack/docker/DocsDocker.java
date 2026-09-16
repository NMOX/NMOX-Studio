package org.nmox.studio.rack.docker;

import java.io.File;
import java.io.IOException;
import org.nmox.studio.core.spi.DocsScene;
import org.nmox.studio.core.util.DocsFixtures;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Stages the Docker Panel picture (v2.164.0) — English in every translated
 * tutorial until now, because the panel only means something with a real
 * container in it.
 *
 * <p>The container is real and the forge script starts it, but the panel
 * does not see the developer's daemon directly: the script points the app
 * at {@code scripts/docs-docker-proxy.py}, a read-only view holding only
 * containers labelled {@code org.nmox.docs=1}. A picture of the panel on
 * a working machine would otherwise publish every container the developer
 * runs. So this scene writes nothing and starts nothing; it opens the
 * Containers tab and waits for the daemon's answer.
 */
@ServiceProvider(service = DocsScene.class)
public final class DocsDocker implements DocsScene {

    /** The scene's name, as its picture is named. */
    public static final String ID = "docker-panel";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public File stage(File home, String fixtures, String lang) throws IOException {
        return DocsFixtures.projectDir(home); // the container is the script's
    }

    @Override
    public void arrange() {
        DockerPanelTopComponent panel = panel();
        if (panel != null) {
            if (!panel.isOpened()) {
                panel.open();
            }
            panel.requestActive();
            panel.docsShowContainers();
        }
    }

    @Override
    public boolean ready() {
        DockerPanelTopComponent panel = panel();
        return panel != null && panel.docsContainerRows() > 0;
    }

    private static DockerPanelTopComponent panel() {
        TopComponent tc = WindowManager.getDefault().findTopComponent("DockerPanelTopComponent");
        return tc instanceof DockerPanelTopComponent p ? p : null;
    }
}
