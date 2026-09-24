package org.nmox.studio.rack.service;

import java.io.File;
import org.openide.modules.OnStart;

/**
 * The platform's "projects folder" is {@code ~/NMOX}, the workspace this
 * product creates, unless you chose another (3.1.0).
 *
 * <p>The platform asks {@code ProjectChooser.getProjectsFolder()} for the
 * default location of its own dialogs - the Clone Repository wizard's
 * "Clone into" among them - and answers, in order: the folder you chose
 * last, then the {@code netbeans.projects.dir} system property, then
 * {@code ~/NetBeansProjects}, which it creates (read from the RELEASE310
 * bytecode of {@code OpenProjectListSettings}). NMOX never uses that
 * folder: File ▸ Open Folder… and New Project start in {@code ~/NMOX}. So
 * the property is set here, before any dialog asks, and only when nobody
 * set it already; a choice you make in a dialog still wins.
 */
@OnStart
public final class ProjectsFolderDefault implements Runnable {

    static final String PROPERTY = "netbeans.projects.dir";

    @Override
    public void run() {
        String value = choose(System.getProperty(PROPERTY), System.getProperty("user.home"));
        if (value != null) {
            System.setProperty(PROPERTY, value);
        }
    }

    /** The value to set, or null to leave the property as it is. Pure. */
    static String choose(String existing, String home) {
        if (existing != null && !existing.isBlank()) {
            return null; // a -D on the command line is a decision
        }
        if (home == null || home.isBlank()) {
            return null;
        }
        return RackService.defaultWorkspaceDir(home).getAbsolutePath();
    }
}
