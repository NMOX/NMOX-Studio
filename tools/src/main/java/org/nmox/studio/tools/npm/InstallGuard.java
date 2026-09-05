package org.nmox.studio.tools.npm;

import java.io.File;
import org.nmox.studio.core.spi.LiveRuns;

/**
 * Refuses a run while the project's OWN dependency install is still live
 * (v2.72.0). The New Project wizard and the New Experiment dialog start
 * an install so the first Run succeeds (v2.36.0); a ▶ or an Explorer
 * double-click pressed before it finishes ran `npm start` against a
 * half-written node_modules and failed with a wall of MODULE_NOT_FOUND.
 * The installs joined the ■'s registry in v2.71.0 under ids the wizard
 * and the dialog own, so the question is now answerable: pure over
 * {@link LiveRuns}, refusals out loud.
 */
final class InstallGuard {

    /** The id prefixes the setup installs register under (NewProjectDialog, NewExperimentAction). */
    static final String[] SETUP_PREFIXES = {"project-setup:", "experiment-setup:"};

    private InstallGuard() {
    }

    static boolean installing(File projectDir) {
        return installing(projectDir, LiveRuns.live());
    }

    static boolean installing(File projectDir, java.util.List<LiveRuns.Run> live) {
        String dir = projectDir.getAbsolutePath() + "#";
        for (LiveRuns.Run r : live) {
            for (String prefix : SETUP_PREFIXES) {
                if (r.id().startsWith(prefix + dir)) {
                    return true;
                }
            }
        }
        return false;
    }

    static String message(File projectDir) {
        return "Dependencies are still installing for " + projectDir.getName()
                + " — wait for the install, or stop it with the toolbar ■";
    }
}
