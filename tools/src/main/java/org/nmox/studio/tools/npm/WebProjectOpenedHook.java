package org.nmox.studio.tools.npm;

import java.io.File;
import org.netbeans.spi.project.ui.ProjectOpenedHook;
import org.openide.filesystems.FileUtil;

/**
 * When the platform opens a web project, the rack follows — the
 * idiomatic replacement for hand-tracking a "current project". Aiming on
 * the open hook means every path that opens a project (the Projects
 * window, Open Project, recent projects, session restore) points the
 * rack the same way, instead of each entry point remembering to do it.
 */
final class WebProjectOpenedHook extends ProjectOpenedHook {

    private final WebProject project;

    WebProjectOpenedHook(WebProject project) {
        this.project = project;
    }

    @Override
    protected void projectOpened() {
        File dir = FileUtil.toFile(project.getProjectDirectory());
        if (dir == null) {
            return;
        }
        // soft aim lookup (ledger 30): no provider (plain tests) — the
        // project still opens normally
        org.nmox.studio.core.spi.ProjectAim aim =
                org.nmox.studio.core.spi.ProjectAim.find();
        if (aim != null) {
            aim.aim(dir);
        }
    }

    @Override
    protected void projectClosed() {
        // the rack keeps its last aim; nothing to tear down here
    }
}
