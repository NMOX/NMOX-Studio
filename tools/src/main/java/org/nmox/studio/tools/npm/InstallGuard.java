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

    /**
     * The third beginner wall (v2.73.0): a Node project that DECLARES
     * dependencies but has no node_modules — a clone, or a wizard project
     * whose install was unchecked or stopped — runs straight into
     * "Cannot find module". Pure over the filesystem: package.json with a
     * non-empty dependencies or devDependencies object, and no
     * node_modules directory beside it. A project with no dependencies
     * needs no install; a missing or malformed package.json is not this
     * wall (the run will say what it says).
     */
    static boolean needsInstall(File projectDir) {
        File pkg = new File(projectDir, "package.json");
        if (!pkg.isFile() || new File(projectDir, "node_modules").isDirectory()) {
            return false;
        }
        try {
            org.json.JSONObject json = new org.json.JSONObject(
                    java.nio.file.Files.readString(pkg.toPath(), java.nio.charset.StandardCharsets.UTF_8));
            return declares(json, "dependencies") || declares(json, "devDependencies");
        } catch (java.io.IOException | RuntimeException malformed) {
            return false;
        }
    }

    private static boolean declares(org.json.JSONObject json, String key) {
        return json.optJSONObject(key) != null && !json.getJSONObject(key).isEmpty();
    }

    static String needsInstallMessage(File projectDir) {
        return projectDir.getName() + " declares dependencies that aren't installed — "
                + "NPM Explorer ▸ Install first (or run npm install)";
    }

    static String message(File projectDir) {
        return "Dependencies are still installing for " + projectDir.getName()
                + " — wait for the install, or stop it with the toolbar ■";
    }
}
