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
                    restartManifestPulse();
                }
            });
            followOpenProjects();
            aimAtDefaultWorkspace();
            startSessionSnapshots();
            restartManifestPulse();
        }
        return rack;
    }

    // ---- manifest pulse: edited manifests re-sync what reads them ----

    private ManifestPulse manifestPulse;
    private final java.util.List<java.util.function.Consumer<java.util.List<java.nio.file.Path>>>
            manifestListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final long ENV_NOTE_MS = 5_000;
    private volatile long envChangedAt;

    /** One watcher for THE aimed project; re-aimed racks get a fresh one. */
    private synchronized void restartManifestPulse() {
        if (manifestPulse != null) {
            manifestPulse.stop();
        }
        manifestPulse = new ManifestPulse(rack.getProjectDir(), this::dispatchManifestBatch);
        manifestPulse.start();
    }

    private void dispatchManifestBatch(java.util.List<java.nio.file.Path> batch) {
        // devices react on the router thread (settle-drainable in tests);
        // .env deliberately reloads nothing — env is read at launch — but
        // the status line notes it so the honesty is visible
        rack.manifestChanged(batch);
        for (java.nio.file.Path p : batch) {
            if (p.getFileName() != null && ".env".equals(p.getFileName().toString())) {
                envChangedAt = System.currentTimeMillis();
                break;
            }
        }
        for (var listener : manifestListeners) {
            try {
                listener.accept(batch);
            } catch (RuntimeException ex) {
                java.util.logging.Logger.getLogger(RackService.class.getName())
                        .warning("Manifest listener failed: " + ex);
            }
        }
    }

    /**
     * Studio-facing manifest events (includes .env changes): the batch of
     * changed manifest paths, coalesced, delivered off-EDT. W2 studios
     * subscribe here instead of running their own watchers.
     */
    public void addManifestListener(java.util.function.Consumer<java.util.List<java.nio.file.Path>> l) {
        manifestListeners.add(l);
    }

    public void removeManifestListener(java.util.function.Consumer<java.util.List<java.nio.file.Path>> l) {
        manifestListeners.remove(l);
    }

    /**
     * True for a few seconds after a .env edit — the status line shows
     * "env changed — restarts pick it up" while this holds (running
     * processes honestly keep their launch-time env).
     */
    public boolean envNoteActive() {
        return System.currentTimeMillis() - envChangedAt < ENV_NOTE_MS;
    }

    /**
     * The dedicated home for a fresh launch: {@code <home>/NMOX}. Pure so a
     * test can assert the path without touching the real home directory or
     * creating anything.
     */
    static File defaultWorkspaceDir(String homePath) {
        return new File(homePath, "NMOX");
    }

    /**
     * When nothing has aimed the rack (no open project, no session to
     * resurrect, no recent project restored), point it at {@code ~/NMOX}
     * instead of $HOME. Scanning one initially-empty folder never touches a
     * TCC-protected directory (~/Desktop, ~/Downloads, the Photos library), so
     * a fresh launch draws its window without stacking macOS permission
     * prompts on the EDT. Creating the directory is a real side effect and is
     * done only inside the platform (netbeans.user set) — plain unit tests
     * that construct a RackService must never write to the real home.
     */
    private void aimAtDefaultWorkspace() {
        File workspace = defaultWorkspaceDir(System.getProperty("user.home"));
        // followOpenProjects() may already have PASSIVELY aimed at an open
        // project (which does not flip `aimed`). Only fall back to ~/NMOX when
        // nothing aimed the rack at all — i.e. it still holds its construction
        // default. Comparing against that default (also ~/NMOX) is the honest
        // "was I aimed?" check that works for both passive and explicit aims.
        if (aimed || !rack.getProjectDir().equals(workspace)) {
            return; // an open project (or an explicit choice) already aimed us
        }
        if (System.getProperty("netbeans.user") != null) {
            ensureWorkspace(workspace);
        }
        // passive: a later passive source (persisted rack window) may still
        // re-aim, and any explicit user aim always outranks this. setProjectDir
        // fires projectChanged only when the value actually changes, so this
        // stays quiet when the rack already holds ~/NMOX from construction.
        openProjectPassively(workspace);
    }

    /**
     * Creates the workspace directory on first run and, only when creating it,
     * drops a short README so the empty folder explains itself. Never
     * overwrites an existing README.
     */
    private static void ensureWorkspace(File workspace) {
        try {
            if (workspace.isDirectory()) {
                return; // already there; leave any existing README untouched
            }
            java.nio.file.Files.createDirectories(workspace.toPath());
            File readme = new File(workspace, "README.md");
            if (!readme.exists()) {
                java.nio.file.Files.writeString(readme.toPath(),
                        "# NMOX Studio workspace\n\n"
                        + "New projects you create land here. You can open any "
                        + "other folder from Workbench → Open.\n",
                        java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception ex) {
            // a workspace we cannot create is not fatal: the rack simply aims
            // at a path that does not resolve, which scans nothing
            java.util.logging.Logger.getLogger(RackService.class.getName())
                    .warning("Could not prepare workspace " + workspace + ": " + ex);
        }
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

    /**
     * Snapshot file IO runs here, never on the EDT (where the 5s timer
     * fires). Single-threaded so writes cannot interleave; latest-wins —
     * a snapshot that arrives while one is still queued replaces it, so
     * a slow disk never builds a backlog.
     */
    private static final org.openide.util.RequestProcessor SNAPSHOT_RP =
            new org.openide.util.RequestProcessor("nmox-session-snapshot", 1, true);
    private final java.util.concurrent.atomic.AtomicReference<Runnable> pendingSnapshotIo =
            new java.util.concurrent.atomic.AtomicReference<>();

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
            // capture stays on the EDT (cheap, needs live model state);
            // the disk write/delete moves to the background writer
            SessionState state = SessionState.capture(rack);
            boolean live = !state.running().isEmpty();
            if (live) {
                anyLiveThisSession = true;
            }
            boolean delete = !live && anyLiveThisSession;
            if (!live && !delete) {
                // tools never ran this session: a session file from a
                // PREVIOUS process stays untouched, so an ignored resume
                // offer survives another restart instead of being consumed
                return;
            }
            Runnable io = () -> {
                try {
                    if (live) {
                        java.nio.file.Files.createDirectories(file.getParentFile().toPath());
                        java.nio.file.Files.writeString(file.toPath(), state.toJson(),
                                java.nio.charset.StandardCharsets.UTF_8);
                    } else {
                        // stopped after running: nothing to resume anymore
                        java.nio.file.Files.deleteIfExists(file.toPath());
                    }
                } catch (Exception ignored) {
                    // a failed snapshot must never disturb the rack
                }
            };
            if (pendingSnapshotIo.getAndSet(io) == null) {
                SNAPSHOT_RP.post(() -> {
                    Runnable job = pendingSnapshotIo.getAndSet(null);
                    if (job != null) {
                        job.run();
                    }
                });
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
        if (dir.equals(bridgePublishing)) {
            // the platform echoing our own publication back: OpenProjects.open
            // fires WebProjectOpenedHook, whose job is to aim the rack when the
            // PLATFORM opened a project — but this open originated here, and
            // re-running proceed (addRecent + another publish) would recurse
            // until only OpenProjects' idempotence stopped it (ledger 29)
            return;
        }
        guardedSwitch(dir, () -> {
            aimed = true;
            addRecentProject(dir);
            getRack().setProjectDir(dir);
            publishToOpenProjects(dir);
        });
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
        guardedSwitch(dir, () -> {
            aimed = true;
            getRack().setProjectDir(dir);
            // deliberately NOT bridged to OpenProjects: experiments are
            // throwaway, and the platform persists its open-projects list —
            // a bridged experiment would resurrect at next boot (and resolve
            // projects during startup, against the v1.38.0 law)
        });
    }

    // ---- the OpenProjects bridge (ledger 29, v1.45.0) ----
    //
    // The rack is the context system, but the platform's context actions
    // (Team menu, project-sensitive verbs) read OpenProjects. After a real
    // aim, publish the aimed directory there too — when the platform
    // recognizes it as a project — so both worlds agree on "the current
    // project". Passive aims (fresh-boot ~/NMOX, persisted window state,
    // the open-projects follower itself) must NEVER reach this: the
    // v1.38.0 boot law forbids eager FileObject/ProjectManager resolution
    // at startup, and the follower feeding itself would loop.

    /**
     * Test seam: production resolves via ProjectManager/OpenProjects; tests
     * inject a recorder. Always invoked on {@link #BRIDGE_RP}, never the EDT.
     */
    interface BridgeHook {
        void publish(File dir);
    }

    BridgeHook bridgeHook = RackService::publishToPlatform;

    /**
     * One background lane: ProjectManager.findProject scans the directory for
     * manifests (disk IO) and OpenProjects.open touches platform persistence —
     * neither may run on the EDT, and publications must not interleave.
     */
    private static final org.openide.util.RequestProcessor BRIDGE_RP =
            new org.openide.util.RequestProcessor("nmox-project-bridge", 1, true);

    /**
     * The directory currently being published, visible to the re-entrancy
     * check in {@link #openProject}: OpenProjects.open synchronously (in
     * tests, and sometimes in the platform) fires ProjectOpenedHooks, and
     * ours calls openProject right back.
     */
    private volatile File bridgePublishing;

    /** Fires only from an explicit aim's completion callback — never a passive path. */
    private void publishToOpenProjects(File dir) {
        BRIDGE_RP.post(() -> {
            bridgePublishing = dir;
            try {
                bridgeHook.publish(dir);
            } catch (RuntimeException | LinkageError ex) {
                // project APIs unavailable (plain tests, stripped platform):
                // the rack aim stands on its own, exactly as before v1.45
            } finally {
                bridgePublishing = null;
            }
        });
    }

    /**
     * The real bridge. A directory without any recognized manifest (no
     * package.json … and no index.html) yields findProject == null — the rack
     * happily aims anywhere, the platform only at projects, so we no-op
     * silently. NEVER closes the previously open project: closing is the
     * user's call, and a rack aim is not a statement about other projects
     * (source-gated by OpenProjectsBridgeTest).
     */
    private static void publishToPlatform(File dir) {
        org.openide.filesystems.FileObject fo = org.openide.filesystems.FileUtil
                .toFileObject(org.openide.filesystems.FileUtil.normalizeFile(dir));
        if (fo == null) {
            return; // vanished between the aim and the publish
        }
        try {
            org.netbeans.api.project.Project project =
                    org.netbeans.api.project.ProjectManager.getDefault().findProject(fo);
            if (project == null) {
                return; // not a project the platform recognizes; the aim stands alone
            }
            org.netbeans.api.project.ui.OpenProjects open =
                    org.netbeans.api.project.ui.OpenProjects.getDefault();
            if (!open.isProjectOpen(project)) {
                open.open(new org.netbeans.api.project.Project[]{project}, false);
            }
            open.setMainProject(project);
        } catch (java.io.IOException | IllegalArgumentException ex) {
            // a project the platform refuses to load is not our failure to
            // surface: the rack aim already succeeded
            java.util.logging.Logger.getLogger(RackService.class.getName())
                    .fine("OpenProjects bridge skipped " + dir + ": " + ex);
        }
    }

    /** Test seam: blocks until every queued publication has completed. */
    void awaitBridgeIdle() {
        try {
            BRIDGE_RP.post(() -> { }).waitFinished(10_000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
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
     *
     * <p>The confirm dialog stays where it always was, but the stops run
     * on background workers — panic() can block ~2.5s per stubborn device
     * and this path is an EDT path (ledger item 15) — and {@code proceed}
     * runs on the EDT only in the completion callback, so the swap still
     * never races a dying dev server. With nothing live (or re-aiming the
     * same project) the switch completes synchronously, exactly as before.
     */
    private void guardedSwitch(File newDir, Runnable proceed) {
        Rack r = getRack();
        if (newDir.equals(r.getProjectDir())) {
            proceed.run(); // re-aiming the same project threatens nothing
            return;
        }
        List<org.nmox.studio.rack.model.RackDevice> live = new ArrayList<>();
        for (org.nmox.studio.rack.model.RackDevice d : r.getDevices()) {
            if (d.isLive()) {
                live.add(d);
            }
        }
        if (live.isEmpty()) {
            proceed.run();
            return;
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
            return; // user chose to stay
        }
        status("Stopping " + live.size() + (live.size() == 1 ? " tool…" : " tools…"));
        r.stopAsync(live, proceed);
    }

    /** Best-effort status line; unavailable in plain unit tests. */
    private static void status(String text) {
        try {
            org.openide.awt.StatusDisplayer.getDefault().setStatusText(text);
        } catch (RuntimeException | LinkageError ignored) {
            // status line unavailable (tests, stripped platform)
        }
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
