package org.nmox.studio.tools.npm;

import java.io.File;

/**
 * A wall deserves a door (v2.73.0): when a run is refused because the
 * project's dependencies are not installed, or a setup install was
 * stopped, the balloon's click starts the install itself — through
 * {@link NpmService#install}, which asks Workspace Trust before it spawns
 * and joins the ■ like every other run. Pure text here; the notify call
 * is the one Swing touch.
 */
final class InstallDoor {

    private InstallDoor() {
    }

    static String title(File projectDir) {
        return projectDir.getName() + " needs its dependencies installed";
    }

    static String detail(File projectDir) {
        return "Click to run the install now (NPM Explorer ▸ Install does the same). "
                + "Run again once it finishes.";
    }

    /** The balloon; the click installs on the NPM lane (trust-gated inside, ■-registered, progress-barred). */
    static void offer(File projectDir) {
        org.openide.awt.NotificationDisplayer.getDefault().notify(title(projectDir),
                javax.swing.UIManager.getIcon("OptionPane.informationIcon"), detail(projectDir), e -> {
                    NpmService npm = NpmService.getDefault();
                    org.openide.util.RequestProcessor.getDefault().post(() ->
                            npm.install(projectDir, npm.detectPackageManager(projectDir)));
                });
    }
}
