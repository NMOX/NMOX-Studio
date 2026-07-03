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
    private volatile boolean aimed;

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
            // the starter patch is not undoable; interactive edits from here are
            rack.enableUndoCapture();
            rack.addListener(new Rack.Listener() {
                @Override
                public void projectChanged() {
                    autoLoadPatch();
                    offerResume();
                }
            });
            followOpenProjects();
            startSessionSnapshots();
        }
        return rack;
    }

    // ---- session resurrection: the mosh principle ----

    private java.io.File sessionFile(java.io.File project) {
        String userdir = System.getProperty("netbeans.user");
        if (userdir == null) {
            return null; // plain unit tests: no session persistence
        }
        String key = Integer.toHexString(project.getAbsolutePath().hashCode());
        return new java.io.File(userdir, "var/nmox/sessions/" + key + ".json");
    }

    /**
     * Snapshots what is live every few seconds - continuously, not on
     * clean quit, so the session survives kill -9 and power loss. An
     * empty snapshot deletes the file: stopping your tools IS the
     * statement that there is nothing to resume.
     */
    private volatile boolean anyLiveThisSession;
    private javax.swing.Timer sessionSnapshotTimer;

    /** Stops the continuous snapshot; the service is then quiescent. */
    void stopSessionSnapshots() {
        if (sessionSnapshotTimer != null) {
            sessionSnapshotTimer.stop();
        }
    }

    private void startSessionSnapshots() {
        javax.swing.Timer snap = new javax.swing.Timer(5_000, e -> {
            java.io.File file = sessionFile(rack.getProjectDir());
            if (file == null) {
                return;
            }
            try {
                SessionState state = SessionState.capture(rack);
                if (!state.running().isEmpty()) {
                    anyLiveThisSession = true;
                    java.nio.file.Files.createDirectories(file.getParentFile().toPath());
                    java.nio.file.Files.writeString(file.toPath(), state.toJson(), java.nio.charset.StandardCharsets.UTF_8);
                } else if (anyLiveThisSession) {
                    // tools ran and were stopped: nothing to resume anymore.
                    // A session file from a PREVIOUS process stays untouched
                    // until then, so an ignored resume offer survives another
                    // restart instead of being silently consumed.
                    java.nio.file.Files.deleteIfExists(file.toPath());
                }
            } catch (Exception ignored) {
                // a failed snapshot must never disturb the rack
            }
        });
        snap.setRepeats(true);
        snap.start();
        sessionSnapshotTimer = snap;
    }

    /** After aiming: if the last session here died with tools running, offer them back. */
    private void offerResume() {
        java.io.File file = sessionFile(rack.getProjectDir());
        if (file == null || !file.isFile()) {
            return;
        }
        SessionState state;
        try {
            state = SessionState.fromJson(java.nio.file.Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return;
        }
        if (state == null || !state.fresh()
                || !state.project().equals(rack.getProjectDir().getAbsolutePath())) {
            return;
        }
        java.util.List<org.nmox.studio.rack.model.RackDevice> matches = state.matchAgainst(rack);
        if (matches.isEmpty()) {
            return;
        }
        StringBuilder names = new StringBuilder();
        for (var d : matches) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(d.getTitle());
        }
        javax.swing.SwingUtilities.invokeLater(() -> {
            try {
                org.openide.awt.NotificationDisplayer.getDefault().notify(
                        "Resume last session?",
                        javax.swing.UIManager.getIcon("OptionPane.informationIcon"),
                        names + (matches.size() == 1 ? " was" : " were")
                        + " running when the IDE closed — click to bring "
                        + (matches.size() == 1 ? "it" : "them") + " back",
                        e -> {
                            for (var d : matches) {
                                d.resume();
                            }
                        });
            } catch (RuntimeException | LinkageError ignored) {
                // notifications unavailable (tests, stripped platform)
            }
        });
    }

    /**
     * Aims the rack at a project directory: records it in the recent
     * list and lets the project's saved patch (if any) mount itself.
     * If the current project still has processes running (a dev server,
     * a tunnel, a watch build), the switch asks first - the patch swap
     * would kill them silently otherwise.
     */
    public void openProject(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        if (!stopLiveForSwitch(dir)) {
            return; // user chose to stay
        }
        aimed = true;
        addRecentProject(dir);
        getRack().setProjectDir(dir);
    }

    /**
     * Aims the rack without touching the recent-projects list - for
     * experiments, which must not evict real work from the ten slots.
     * The live-process guard still applies.
     */
    public void openProjectQuietly(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        if (!stopLiveForSwitch(dir)) {
            return;
        }
        aimed = true;
        getRack().setProjectDir(dir);
    }

    /**
     * Test seam: production asks via a platform dialog; tests inject an
     * answer. Returns true when the switch may proceed.
     */
    java.util.function.Predicate<String> switchConfirmer = this::askStopLive;

    /**
     * A project switch must never silently kill work in flight: the old
     * patch's dev server, watcher, or tunnel dies when the new patch
     * mounts (and lingers half-aimed when there is no patch). Name what
     * is running and ask; on consent, stop it cleanly BEFORE the swap so
     * both the patch and the no-patch paths end in the same state.
     */
    private boolean stopLiveForSwitch(File newDir) {
        Rack r = getRack();
        if (newDir.equals(r.getProjectDir())) {
            return true; // re-aiming the same project threatens nothing
        }
        List<org.nmox.studio.rack.model.RackDevice> live = new ArrayList<>();
        for (org.nmox.studio.rack.model.RackDevice d : r.getDevices()) {
            if (d.isLive()) {
                live.add(d);
            }
        }
        if (live.isEmpty()) {
            return true;
        }
        StringBuilder names = new StringBuilder();
        for (org.nmox.studio.rack.model.RackDevice d : live) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(d.getTitle());
        }
        String oldName = r.getProjectDir() != null ? r.getProjectDir().getName() : "the current project";
        String message = names + (live.size() == 1 ? " is" : " are") + " still running in "
                + oldName + ".\nStop and switch to " + newDir.getName() + "?";
        if (!switchConfirmer.test(message)) {
            return false;
        }
        for (org.nmox.studio.rack.model.RackDevice d : live) {
            d.panic();
        }
        return true;
    }

    private boolean askStopLive(String message) {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            return true; // no dialog possible; behave as before, but deterministically
        }
        Object answer = org.openide.DialogDisplayer.getDefault().notify(
                new org.openide.NotifyDescriptor.Confirmation(message, "Switch Project",
                        org.openide.NotifyDescriptor.OK_CANCEL_OPTION,
                        org.openide.NotifyDescriptor.WARNING_MESSAGE));
        return answer == org.openide.NotifyDescriptor.OK_OPTION;
    }

    /**
     * True once anything has explicitly aimed the rack this session.
     * Passive followers (persisted window state, the open-projects
     * listener) must never clobber an aim the user already made.
     */
    public boolean isAimed() {
        return aimed;
    }

    /**
     * A passive aim: points the rack without claiming user intent, so
     * later passive sources (e.g. the rack window's persisted project
     * restoring after the open-projects follower) may still re-aim.
     * Any explicit aim still outranks all of these.
     */
    public void openProjectPassively(File dir) {
        if (aimed || dir == null || !dir.isDirectory()) {
            return;
        }
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
        java.util.prefs.Preferences p = prefs();
        p.put(PREF_RECENT, sb.toString());
        try {
            p.flush(); // survive an abrupt quit, not just a clean one
        } catch (java.util.prefs.BackingStoreException ignore) {
            // best effort
        }
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
        // switching projects loads a different rack; undo starts fresh, and
        // must never peel a just-loaded patch apart device by device
        rack.clearUndoHistory();
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
        if (aimed) {
            return; // an explicit choice always outranks the follower
        }
        org.netbeans.api.project.Project[] projects =
                org.netbeans.api.project.ui.OpenProjects.getDefault().getOpenProjects();
        File fallback = null;
        for (org.netbeans.api.project.Project p : projects) {
            File dir = org.openide.filesystems.FileUtil.toFile(p.getProjectDirectory());
            if (dir == null) {
                continue;
            }
            if (new File(dir, "package.json").isFile()) {
                openProjectPassively(dir);
                return;
            }
            if (fallback == null) {
                fallback = dir;
            }
        }
        if (fallback != null) {
            openProjectPassively(fallback);
        }
    }
}
