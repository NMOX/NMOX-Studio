package org.nmox.studio.rack.service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import org.nmox.studio.rack.devices.DeviceType;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackIO;
import org.openide.util.Lookup;
import org.openide.util.NbPreferences;
import org.openide.util.lookup.ServiceProvider;

/**
 * Owns THE rack. The Task Rack window renders it, the Project Studio
 * aims it, templates wire it - one shared instance, looked up rather
 * than constructed, so every tool in the IDE talks about the same
 * project and the same patch.
 */
@ServiceProvider(service = RackService.class)
public class RackService {

    private static final String PREF_RECENT = "recentProjects";
    private static final int MAX_RECENT = 10;

    private final Rack rack = new Rack();
    private boolean initialized;

    public static RackService getDefault() {
        RackService service = Lookup.getDefault().lookup(RackService.class);
        return service != null ? service : Holder.FALLBACK;
    }

    /** Outside the platform (plain unit tests) Lookup may be empty. */
    private static final class Holder {
        static final RackService FALLBACK = new RackService();
    }

    /** The shared rack; first access mounts the starter patch. */
    public synchronized Rack getRack() {
        if (!initialized) {
            initialized = true;
            loadDefaultRack();
            rack.addListener(new Rack.Listener() {
                @Override
                public void projectChanged() {
                    autoLoadPatch();
                }
            });
            followOpenProjects();
        }
        return rack;
    }

    /**
     * Aims the rack at a project directory: records it in the recent
     * list and lets the project's saved patch (if any) mount itself.
     */
    public void openProject(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        addRecentProject(dir);
        getRack().setProjectDir(dir);
    }

    // ---- recent projects ----

    public List<File> getRecentProjects() {
        List<File> result = new ArrayList<>();
        for (String path : prefs().get(PREF_RECENT, "").split("\n")) {
            if (!path.isBlank()) {
                File f = new File(path);
                if (f.isDirectory()) {
                    result.add(f);
                }
            }
        }
        return result;
    }

    private void addRecentProject(File dir) {
        List<File> recent = getRecentProjects();
        recent.remove(dir);
        recent.add(0, dir);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(recent.size(), MAX_RECENT); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(recent.get(i).getAbsolutePath());
        }
        prefs().put(PREF_RECENT, sb.toString());
    }

    private Preferences prefs() {
        return NbPreferences.forModule(RackService.class);
    }

    // ---- patch handling ----

    private void autoLoadPatch() {
        File patch = new File(rack.getProjectDir(), RackIO.DEFAULT_FILENAME);
        if (patch.isFile()) {
            try {
                RackIO.load(rack, patch);
            } catch (Exception ex) {
                java.util.logging.Logger.getLogger(RackService.class.getName())
                        .warning("Could not load rack patch " + patch + ": " + ex);
            }
        }
    }

    /**
     * Starter rack: a single MONITOR with its TAP on stderr - the first
     * thing a new user sees is one honest unit that shows every error
     * anything in the rack prints, before a single cable is patched.
     * The full pipeline lives one click away in Presets ("Web Pipeline").
     */
    private void loadDefaultRack() {
        rack.addDevice(DeviceType.CONSOLE.create());
    }

    /**
     * Aims the rack at whatever the IDE has open: the first open project
     * with a package.json wins, else the first open project.
     */
    private void followOpenProjects() {
        try {
            org.netbeans.api.project.ui.OpenProjects open =
                    org.netbeans.api.project.ui.OpenProjects.getDefault();
            open.addPropertyChangeListener(evt -> {
                if (org.netbeans.api.project.ui.OpenProjects.PROPERTY_OPEN_PROJECTS
                        .equals(evt.getPropertyName())) {
                    javax.swing.SwingUtilities.invokeLater(this::aimAtOpenProject);
                }
            });
            aimAtOpenProject();
        } catch (RuntimeException | LinkageError ex) {
            // project APIs unavailable (tests, stripped platform); manual choice only
        }
    }

    private void aimAtOpenProject() {
        org.netbeans.api.project.Project[] projects =
                org.netbeans.api.project.ui.OpenProjects.getDefault().getOpenProjects();
        File fallback = null;
        for (org.netbeans.api.project.Project p : projects) {
            File dir = org.openide.filesystems.FileUtil.toFile(p.getProjectDirectory());
            if (dir == null) {
                continue;
            }
            if (new File(dir, "package.json").isFile()) {
                openProject(dir);
                return;
            }
            if (fallback == null) {
                fallback = dir;
            }
        }
        if (fallback != null) {
            openProject(fallback);
        }
    }
}
