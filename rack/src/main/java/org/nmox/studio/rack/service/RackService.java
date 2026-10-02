package org.nmox.studio.rack.service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import org.nmox.studio.rack.devices.DeviceType;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.RackIO;
import org.nmox.studio.rack.projectstudio.StarterRacks;
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
@org.openide.util.NbBundle.Messages({
    "RackService_resumeTitle=Resume last session?",
    "RackService_resumeOne={0} was running when the IDE closed \u2014 click to bring it back",
    "RackService_resumeMany={0} were running when the IDE closed \u2014 click to bring them back",
    "RackService_theCurrentProject=the current project",
    "RackService_switchOne={0} is still running in {1}.\nStop and switch to {2}?",
    "RackService_switchMany={0} are still running in {1}.\nStop and switch to {2}?",
    "RackService_stoppingOne=Stopping {0} tool…",
    "RackService_stoppingMany=Stopping {0} tools…",
    "RackService_switchTitle=Switch Project",
    "# {0} - file name, {1} - the reason, already a sentence",
    "# {0} - the patch file's name; {1} - the failure, in English from the engine",
    "RackService_patchNotLoaded=Could not load this project\u2019s saved rack, so the rack is empty: {0} \u2014 {1}",
    "# {0} - the patch file's name; {1} - its size in KiB; {2} - the cap in MiB",
    "RackService_patchTooLarge=Could not load this project\u2019s saved rack, so the rack is empty: {0} is {1} KiB, over the {2} MiB limit.",
    "# {0} - the patch file's name; {1} - the name it was kept under",
    "RackService_patchCorrupt=Could not load this project\u2019s saved rack, so the rack is empty: {0} is not valid JSON. Your file was kept as {1}.",
    "# {0} - the patch file's name",
    "RackService_patchConflicted=Could not load this project\u2019s saved rack, so the rack is empty: {0} has unresolved merge conflicts. Resolve them in git \u2014 NMOX Studio won\u2019t write it until then.",
    "# {0} - the patch file's name",
    "RackService_patchUnreadable=Could not load this project\u2019s saved rack, so the rack is empty: {0} could not be read. NMOX Studio won\u2019t write over it.",
    "# {0} - the patch file's name",
    "RackService_patchCorruptUnkept=Could not load this project\u2019s saved rack, so the rack is empty: {0} is not valid JSON and no copy of it could be kept. NMOX Studio won\u2019t write over it.",
    "# {0} - the patch file's name",
    "RackService_saveRefusedConflicted=Not saved: {0} has unresolved merge conflicts \u2014 resolve them in git first.",
    "# {0} - the patch file's name",
    "RackService_saveRefusedUnread=Not saved: {0} was not read in this session, and NMOX Studio does not write over a file it has not read.",
    "# {0} - the patch file's name",
    "RackService_saveRefusedChanged=Not saved: {0} changed on disk after this session read it (a pull, a checkout or another tool). Your rack stays on screen; Load Patch reads the file.",
    "# {0} - the patch file's name",
    "RackService_saveRefusedNewer=Not saved: {0} was saved by a newer NMOX Studio, and saving it here would drop what that version added.",
    "# {0} - the patch file's name",
    "RackService_patchChangedKept={0} changed on disk (a pull, a checkout or another tool). Your unsaved rack stays on screen and Save Patch won\u2019t write over the file; Load Patch reads it.",
    "# {0} - the patch file's name",
    "RackService_patchNowConflicted={0} now has unresolved merge conflicts. The rack on screen stays, and NMOX Studio won\u2019t write the file until they are resolved in git.",
    "# {0} - the patch file's name",
    "RackService_patchReloaded={0} changed on disk, so the rack was read again.",
    "# {0} - the patch file's name; {1} - the file's format number; {2} - the format this build writes",
    "RackService_patchNewer={0} was saved by a newer NMOX Studio (format {1}; this one writes {2}). It is shown, and NMOX Studio won\u2019t write over it.",
    "# {0} - the patch file's name; {1} - how many cables followed their device; {2} - how many were dropped",
    "RackService_cablesRestored={0}: cables that followed a moved device: {1} \u00b7 cables dropped: {2}"
})
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
            String json = live ? state.toJson() : null;
            Runnable io = () -> writeSnapshot(file, json);
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

    /**
     * One snapshot write, or its removal when {@code json} is null (3.4).
     * It used to be a plain {@code Files.writeString} with the error
     * swallowed: on a full 4 MB volume the snapshot went from 37 bytes to 0
     * (the truncating open succeeded, the write did not), and the next
     * launch's resume offer then failed to parse it — silently — so a crash
     * lost the one thing this file exists to keep. The write is atomic now
     * (a failed write leaves the last good snapshot), and a failure is
     * logged at WARNING. It still never disturbs the rack.
     */
    /**
     * The snapshot a resume offer is built from, or null. A snapshot that
     * cannot be read or parsed used to vanish silently with the offer it
     * carried; it is logged at WARNING now (3.4).
     */
    static SessionState readSnapshot(java.io.File file) {
        java.util.logging.Logger log = java.util.logging.Logger.getLogger(RackService.class.getName());
        try {
            SessionState state = SessionState.fromJson(java.nio.file.Files.readString(file.toPath(),
                    java.nio.charset.StandardCharsets.UTF_8));
            if (state == null) {
                log.log(java.util.logging.Level.WARNING,
                        "The session snapshot {0} is not a snapshot (empty or damaged), so no crash-resume offer can be made",
                        file);
            }
            return state;
        } catch (java.io.IOException | RuntimeException unreadable) {
            log.log(java.util.logging.Level.WARNING,
                    "The session snapshot " + file + " could not be read, so no crash-resume offer can be made",
                    unreadable);
            return null;
        }
    }

    /** Whether the last snapshot write failed: a failing disk is said once, not every five seconds. */
    static volatile boolean snapshotFailing;

    /**
     * Writes (or, given null, removes) the session snapshot, atomically. It
     * is rewritten every few seconds while anything runs, so a failure is
     * logged at WARNING once per failing streak and at FINE after that — a
     * full disk used to add a stack trace to the log every five seconds (the
     * 3.4 review) — and a success ends the streak.
     */
    static void writeSnapshot(java.io.File file, String json) {
        try {
            if (json != null) {
                java.nio.file.Files.createDirectories(file.getParentFile().toPath());
                org.nmox.studio.core.util.AtomicFiles.writeString(file.toPath(), json);
            } else {
                // stopped after running: nothing to resume anymore
                java.nio.file.Files.deleteIfExists(file.toPath());
            }
            snapshotFailing = false;
        } catch (java.io.IOException | RuntimeException failed) {
            java.util.logging.Level level = snapshotFailing ? java.util.logging.Level.FINE : java.util.logging.Level.WARNING;
            snapshotFailing = true;
            java.util.logging.Logger.getLogger(RackService.class.getName()).log(level,
                    "Could not " + (json != null ? "write" : "remove") + " the session snapshot " + file
                    + " (the crash-resume offer depends on it)", failed);
        }
    }

    /** After aiming: if the last session here died with tools running, offer them back. */
    private void offerResume() {
        java.io.File file = sessionFile(rack.getProjectDir());
        if (file == null || !file.isFile()) {
            return;
        }
        SessionState state = readSnapshot(file);
        if (state == null || !state.fresh()
                || !state.project().equals(rack.getProjectDir().getAbsolutePath())) {
            return;
        }
        java.util.List<org.nmox.studio.rack.model.RackDevice> matches = state.matchAgainst(rack);
        // The unsaved-patch case (found by the v1.95.2 night journey):
        // the snapshot is fresh but the devices it names were never
        // persisted — kill -9 before any Save Patch left nothing to
        // match. Re-creating them is deferred to the user's CLICK; the
        // offer itself just has to stop being silent about them.
        // Types the catalog no longer carries (uninstalled plugin,
        // ledger 44) stay out — a device we can't build can't resume.
        java.util.List<SessionState.Entry> recreate = new java.util.ArrayList<>();
        for (SessionState.Entry e : state.unmatchedAgainst(rack)) {
            if (org.nmox.studio.rack.devices.DeviceCatalog.byId(e.typeId()).isPresent()) {
                recreate.add(e);
            }
        }
        if (matches.isEmpty() && recreate.isEmpty()) {
            return;
        }
        StringBuilder names = new StringBuilder();
        for (var d : matches) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(d.getTitle());
        }
        for (var e : recreate) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(e.title());
        }
        int count = matches.size() + recreate.size();
        javax.swing.SwingUtilities.invokeLater(() -> {
            try {
                org.openide.awt.NotificationDisplayer.getDefault().notify(
                        Bundle.RackService_resumeTitle(),
                        javax.swing.UIManager.getIcon("OptionPane.informationIcon"),
                        count == 1 ? Bundle.RackService_resumeOne(names.toString())
                                : Bundle.RackService_resumeMany(names.toString()),
                        e -> resumeSession(rack, matches, recreate));
            } catch (RuntimeException | LinkageError ignored) {
                // notifications unavailable (tests, stripped platform)
            }
        });
    }

    /**
     * The resume click: re-create the devices the rack lost (unsaved
     * patch), then bring everything back. Package-private so plain
     * tests drive the exact click path.
     */
    static void resumeSession(Rack rack,
            java.util.List<org.nmox.studio.rack.model.RackDevice> matches,
            java.util.List<SessionState.Entry> recreate) {
        for (SessionState.Entry e : recreate) {
            var entry = org.nmox.studio.rack.devices.DeviceCatalog.byId(e.typeId());
            if (entry.isEmpty()) {
                continue; // catalog changed between offer and click
            }
            org.nmox.studio.rack.model.RackDevice d = entry.get().create();
            rack.addDevice(d, Math.min(e.index(), rack.getDevices().size()));
            d.resume();
        }
        for (var d : matches) {
            d.resume();
        }
    }

    /**
     * One directory, one spelling: the platform's.
     *
     * <p>The platform names a folder by its normalized path, which on
     * Windows expands an 8.3 short name and on a Mac repairs the letter
     * case, and it hands that spelling back when it echoes an aim through
     * the project-opened hook. The echo check below compares by equality, so
     * a folder aimed under any other spelling was not recognised as its own
     * echo: the project was aimed a second time (every studio reloading its
     * workspace again) and recorded a second time. The first staged walk on
     * Windows photographed it, every recent project listed twice, the
     * runner's temp directory being a short path. `nmox ~/code/App` on a
     * Mac whose folder is `app` takes the same route.
     *
     * <p>So an aim takes the platform's spelling before anything compares
     * or records it. One {@code normalizeFile}, the same call the bridge
     * makes; the explicit aim already asks the disk whether this is a
     * directory.
     */
    static File platformSpelling(File dir) {
        try {
            return org.openide.filesystems.FileUtil.normalizeFile(dir);
        } catch (RuntimeException | LinkageError ex) {
            return dir.getAbsoluteFile(); // filesystems API unavailable: plain tests
        }
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
        File at = platformSpelling(dir);
        if (at.equals(bridgePublishing)) {
            // the platform echoing our own publication back: OpenProjects.open
            // fires WebProjectOpenedHook, whose job is to aim the rack when the
            // PLATFORM opened a project — but this open originated here, and
            // re-running proceed (addRecent + another publish) would recurse
            // until only OpenProjects' idempotence stopped it (ledger 29)
            return;
        }
        guardedSwitch(at, () -> {
            aimed = true;
            addRecentProject(at);
            getRack().setProjectDir(at);
            publishToOpenProjects(at);
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
        File at = platformSpelling(dir);
        guardedSwitch(at, () -> {
            aimed = true;
            getRack().setProjectDir(at);
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
                // v1.233.0: open() completes ASYNCHRONOUSLY, and
                // setMainProject on a not-yet-open project throws
                // IllegalArgumentException — which the catch below then
                // swallowed at FINE, so the main project silently never
                // followed a newly-opened aim (found live: F6 kept running
                // the PREVIOUS project). openProjects() is the API's own
                // completion barrier; bounded so a wedged open can't hang
                // the bridge lane.
                try {
                    open.openProjects().get(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (java.lang.InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (java.util.concurrent.ExecutionException
                        | java.util.concurrent.TimeoutException ex) {
                    // fall through: setMainProject below tells the truth —
                    // but say the barrier gave up somewhere findable,
                    // because the v1.233.0 bug hid in exactly this kind of
                    // silence (v1.234.0 review: a timeout followed by the
                    // FINE swallow below is invisible twice over)
                    java.util.logging.Logger.getLogger(RackService.class.getName())
                            .info("OpenProjects barrier gave up waiting for "
                                    + dir + ": " + ex);
                }
            }
            open.setMainProject(project);
        } catch (java.io.IOException | IllegalArgumentException ex) {
            // a project the platform refuses to load is not our failure to
            // surface: the rack aim already succeeded. INFO, not FINE — a
            // main project that silently fails to follow the aim was the
            // v1.233.0 bug, and FINE is where it hid (v1.234.0 review).
            java.util.logging.Logger.getLogger(RackService.class.getName())
                    .info("OpenProjects bridge skipped " + dir + ": " + ex);
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
        String oldName = r.getProjectDir() != null ? r.getProjectDir().getName() : Bundle.RackService_theCurrentProject();
        String message = live.size() == 1
                ? Bundle.RackService_switchOne(names.toString(), oldName, newDir.getName())
                : Bundle.RackService_switchMany(names.toString(), oldName, newDir.getName());
        if (!switchConfirmer.test(message)) {
            return; // user chose to stay
        }
        status(live.size() == 1 ? Bundle.RackService_stoppingOne(String.valueOf(live.size())) : Bundle.RackService_stoppingMany(String.valueOf(live.size())));
        r.stopAsync(live, proceed);
    }

    /**
     * What a reader is told when the project's own patch did not load. Pure so
     * the sentence is tested without a status line: it names the FILE (the
     * thing they can look at), carries {@code RackIO}'s own reason — which
     * already says whether the file was kept as {@code .bak} or left untouched
     * and unread — and states what they are now looking at, because
     * {@code RackIO.load}'s contract is to replace the rack's contents and a
     * patch it refuses supplies none (v1.107.0).
     */
    static String patchNotLoadedText(File patch, Exception failure) {
        // ledger 106: the engine says WHAT HAPPENED, this says it in the
        // reader's language. The two refusals a reader can act on carry their
        // facts as data; anything else falls back to the engine's sentence,
        // which is English — honest, because there is nothing else to say.
        if (failure instanceof RackIO.PatchTooLargeException tooLarge) {
            return Bundle.RackService_patchTooLarge(patch.getName(),
                    tooLarge.kib(), RackIO.PatchTooLargeException.capMib());
        }
        if (failure instanceof RackIO.PatchConflictedException) {
            return Bundle.RackService_patchConflicted(patch.getName());
        }
        if (failure instanceof RackIO.PatchUnreadableException) {
            return Bundle.RackService_patchUnreadable(patch.getName());
        }
        if (failure instanceof RackIO.CorruptPatchException corrupt) {
            // the parser's own complaint is already in the WARNING below; it is
            // English and untranslatable, so it never reaches the status line
            if (corrupt.backupName() == null) {
                return Bundle.RackService_patchCorruptUnkept(patch.getName());
            }
            return Bundle.RackService_patchCorrupt(patch.getName(), corrupt.backupName());
        }
        String reason = failure.getMessage();
        if (reason == null || reason.isBlank()) {
            reason = failure.getClass().getSimpleName();
        }
        return Bundle.RackService_patchNotLoaded(patch.getName(), reason);
    }

    /** What a load says about cables that followed their device or were dropped (3.4). */
    public static String cablesSentence(File patch, RackIO.CableReport cables) {
        return Bundle.RackService_cablesRestored(patch.getName(),
                String.valueOf(cables.followed()), String.valueOf(cables.dropped()));
    }

    // ---- a patch this session must not write (3.4) ----

    /**
     * Why the project's patch may not be written right now. Each kind lifts
     * differently: a file this build could not read, a conflict or a newer
     * format lift when the file is read cleanly again; a file that CHANGED
     * under unsaved work on screen lifts when the user takes the file (Load
     * Patch) — never by itself, because either choice by itself loses one
     * person's rack.
     */
    enum LockKind {
        /** Could not be read at all, or is broken and no copy of it could be kept. */
        UNREAD,
        /** Holds git's unresolved merge conflict. */
        CONFLICTED,
        /** Written by a newer NMOX Studio; a save here would drop what it added. */
        NEWER,
        /** Changed on disk after this session read it, while the rack on screen had unsaved work. */
        CHANGED
    }

    /**
     * The project's patch file while it may not be written. {@code reason}
     * is the sentence a refused Save says.
     */
    record PatchLock(File file, String reason, LockKind kind) {
    }

    private volatile PatchLock patchLock;

    /**
     * The one watcher on the aimed project's patch, alive from every aim
     * (3.4). Until 3.4 the only watcher was the one a FAILED load created, so
     * a patch that loaded cleanly and then turned conflicted under a
     * {@code git pull} with the IDE open was never looked at again, and Save
     * Patch replaced both people's racks with the one on screen.
     */
    private org.nmox.studio.core.util.FilePulse patchPulse;
    /** The patch {@link #patchPulse} watches and {@link #patchStamp} describes. */
    private volatile File watchedPatch;
    /** The bytes this session last read from or wrote to {@link #watchedPatch}. */
    private final org.nmox.studio.core.util.SelfWriteTracker patchStamp =
            new org.nmox.studio.core.util.SelfWriteTracker();
    /** The rack as last read or written — what "unsaved work on screen" is measured against. EDT-confined. */
    private volatile String syncedJson;
    /**
     * One lane for every read and write of the project's patch, so the
     * disk's stamp is compared, and a write stamped, with nothing between.
     */
    private static final org.openide.util.RequestProcessor PATCH_RP =
            new org.openide.util.RequestProcessor("nmox-rack-patch", 1);
    /** Serialises the compare-then-write of a save against the watcher's compare-then-read. */
    private final Object patchIo = new Object();
    /** Newest-wins: a reload read earlier than a later one is never applied over it. */
    private final java.util.concurrent.atomic.AtomicLong reloadSeq = new java.util.concurrent.atomic.AtomicLong();

    private synchronized void bindPatchReadOnly(File patch, Exception refusal) {
        bindLock(patch, refusal instanceof RackIO.PatchConflictedException
                ? LockKind.CONFLICTED : LockKind.UNREAD);
    }

    private void bindLock(File patch, LockKind kind) {
        String reason = switch (kind) {
            case CONFLICTED -> Bundle.RackService_saveRefusedConflicted(patch.getName());
            case NEWER -> Bundle.RackService_saveRefusedNewer(patch.getName());
            case CHANGED -> Bundle.RackService_saveRefusedChanged(patch.getName());
            case UNREAD -> Bundle.RackService_saveRefusedUnread(patch.getName());
        };
        patchLock = new PatchLock(patch, reason, kind);
    }

    private synchronized void unbindPatch() {
        patchLock = null;
        watchedPatch = null;
        reloadSeq.incrementAndGet(); // a reload still in flight belongs to the old aim
        if (patchPulse != null) {
            patchPulse.stop();
            patchPulse = null;
        }
    }

    /**
     * Starts watching the aimed project's patch. The baseline is primed on
     * this thread, right after the read, and the lane is asked once at once:
     * a change that landed between the read and the prime would otherwise be
     * the pulse's baseline and never fire.
     */
    private synchronized void watchPatch(File patch) {
        watchedPatch = patch;
        patchPulse = new org.nmox.studio.core.util.FilePulse(patch, (mtime, size) -> askDisk(patch));
        patchPulse.tick();
        patchPulse.start(org.nmox.studio.core.util.FilePulse.DEFAULT_INTERVAL_MS);
        askDisk(patch);
    }

    /** Records the bytes on disk as this session's own — after a load or a write. */
    private void noteSynced(File patch, long mtime, long size) {
        patchStamp.noteSync(mtime, size);
    }

    private static long[] stampOf(File f) {
        return f.isFile() ? new long[]{f.lastModified(), f.length()} : new long[]{-1, -1};
    }

    private void askDisk(File patch) {
        long seq = reloadSeq.incrementAndGet();
        PATCH_RP.post(() -> readIfForeign(patch, seq));
    }

    /**
     * Lane: the file moved on disk — a pull, a checkout, a teammate's tool.
     * Our own writes are told apart by the stamp; anything else is read here,
     * off the EDT, and the verdict is applied there.
     */
    private void readIfForeign(File patch, long seq) {
        JSONOutcome outcome;
        synchronized (patchIo) {
            if (!patch.equals(watchedPatch)) {
                return;
            }
            long[] now = stampOf(patch);
            if (!patchStamp.isForeign(now[0], now[1])) {
                return;
            }
            if (now[0] < 0) {
                // gone (a checkout of a branch without it): nothing of anyone's
                // is on disk to overwrite, and the rack on screen stays
                noteSynced(patch, -1, -1);
                return;
            }
            outcome = JSONOutcome.read(patch, now);
        }
        java.awt.EventQueue.invokeLater(() -> applyForeign(patch, seq, outcome));
    }

    /** What the lane read: a document or a refusal, with the stamp it was read at. */
    record JSONOutcome(org.json.JSONObject doc, Exception refusal, long[] stamp) {
        static JSONOutcome read(File patch, long[] stamp) {
            try {
                return new JSONOutcome(RackIO.readDocument(patch), null, stamp);
            } catch (java.io.IOException | RuntimeException refused) {
                return new JSONOutcome(null, refused, stamp);
            }
        }
    }

    /**
     * EDT: a foreign change to the aimed patch. The file wins when the rack
     * on screen holds nothing unsaved; with unsaved work the rack stays and
     * Save refuses until the user chooses (Load Patch takes the file). A file
     * that is now conflicted, unreadable or from a newer build locks Save and
     * leaves the rack on screen alone. Every outcome is spoken.
     */
    void applyForeign(File patch, long seq, JSONOutcome outcome) {
        if (seq != reloadSeq.get() || !patch.equals(watchedPatch)) {
            return; // a newer read, or another aim, owns the answer now
        }
        if (outcome.refusal() != null) {
            Exception refusal = outcome.refusal();
            java.util.logging.Logger.getLogger(RackService.class.getName())
                    .warning("Rack patch changed on disk and could not be read: " + refusal);
            if (RackIO.mayOverwrite(refusal)) {
                // broken, and its bytes were copied aside: the copy is safe
                noteSynced(patch, outcome.stamp()[0], outcome.stamp()[1]);
            } else {
                bindPatchReadOnly(patch, refusal);
            }
            statusThatLingers(refusal instanceof RackIO.PatchConflictedException
                    ? Bundle.RackService_patchNowConflicted(patch.getName())
                    : patchNotLoadedText(patch, refusal));
            return;
        }
        if (unsavedOnScreen()) {
            bindLock(patch, LockKind.CHANGED);
            statusThatLingers(Bundle.RackService_patchChangedKept(patch.getName()));
            return;
        }
        RackIO.CableReport cables;
        try {
            cables = RackIO.fromJson(rack, outcome.doc());
        } catch (RuntimeException notAPatch) {
            java.util.logging.Logger.getLogger(RackService.class.getName())
                    .warning("Rack patch changed on disk and is not a patch: " + notAPatch);
            bindLock(patch, LockKind.UNREAD);
            statusThatLingers(patchNotLoadedText(patch, notAPatch));
            return;
        }
        adoptLoaded(patch, cables, outcome.stamp());
        statusThatLingers(cables.quiet() ? Bundle.RackService_patchReloaded(patch.getName())
                : cablesSentence(patch, cables));
    }

    /**
     * The file just became the rack on screen: its stamp is ours, its rack is
     * the baseline for unsaved work, and a newer format locks Save.
     */
    private void adoptLoaded(File patch, RackIO.CableReport cables, long[] stamp) {
        noteSynced(patch, stamp[0], stamp[1]);
        syncedJson = RackIO.toJson(rack).toString();
        if (cables.newerFormat()) {
            bindLock(patch, LockKind.NEWER);
            statusThatLingers(Bundle.RackService_patchNewer(patch.getName(),
                    String.valueOf(cables.format()), String.valueOf(RackIO.FORMAT)));
        } else {
            patchLock = null;
        }
    }

    /** EDT: the rack differs from what was last read or written. */
    private boolean unsavedOnScreen() {
        return syncedJson != null && !syncedJson.equals(RackIO.toJson(rack).toString());
    }

    /**
     * Test seam: one tick of the watcher, then the lane and the EDT drained,
     * so a test reads the outcome the watcher would have reached.
     */
    void tickPatchLock() throws Exception {
        org.nmox.studio.core.util.FilePulse pulse;
        synchronized (this) {
            pulse = patchPulse;
        }
        if (pulse != null) {
            pulse.tick();
        }
        awaitPatchIdle();
    }

    /** Test seam: every read and write queued on the patch lane has finished, and the EDT has applied it. */
    void awaitPatchIdle() throws Exception {
        PATCH_RP.post(() -> { }).waitFinished();
        if (!java.awt.EventQueue.isDispatchThread()) {
            java.awt.EventQueue.invokeAndWait(() -> { });
        }
    }

    /**
     * Why {@code target} may not be written by Save Patch, or null when it
     * may. The EDT's quick answer — the lock only, no disk; the write itself
     * asks the disk again ({@link #writePatch}).
     */
    public String saveRefusal(File target) {
        PatchLock lock = patchLock;
        return lock != null && lock.file().getAbsoluteFile().equals(target.getAbsoluteFile())
                ? lock.reason() : null;
    }

    /** A save the disk refused: the file is not the one this session read. The message is the sentence to show. */
    public static final class PatchWriteRefusedException extends java.io.IOException {
        private static final long serialVersionUID = 1L;

        PatchWriteRefusedException(String reason) {
            super(reason);
        }
    }

    /**
     * Writes the project's patch — on a lane, never the EDT — only over the
     * bytes this session last read or wrote (3.4). The lock is asked, then the
     * disk: a pull, a checkout or a teammate's tool that changed the file
     * since it was read refuses the write and locks Save, and a conflict git
     * left behind refuses it by name. Nothing is written in either case.
     */
    public void writePatch(File target, org.json.JSONObject snapshot) throws java.io.IOException {
        synchronized (patchIo) {
            String locked = saveRefusal(target);
            if (locked != null) {
                throw new PatchWriteRefusedException(locked);
            }
            if (!target.getAbsoluteFile().equals(watchedPatch == null ? null : watchedPatch.getAbsoluteFile())) {
                // not the patch this session read (the aim moved under the click):
                // anything already there is somebody's, never ours to replace
                if (target.exists()) {
                    throw new PatchWriteRefusedException(Bundle.RackService_saveRefusedUnread(target.getName()));
                }
            } else {
                switch (patchStamp.beforeWrite(target, RackIO.MAX_PATCH_BYTES)) {
                    case CONFLICTED -> {
                        bindLock(target, LockKind.CONFLICTED);
                        throw new PatchWriteRefusedException(Bundle.RackService_saveRefusedConflicted(target.getName()));
                    }
                    case CHANGED -> {
                        bindLock(target, LockKind.CHANGED);
                        throw new PatchWriteRefusedException(Bundle.RackService_saveRefusedChanged(target.getName()));
                    }
                    case OURS -> {
                        // the bytes on disk are the ones this session read or wrote
                    }
                }
            }
            String text = snapshot.toString(2);
            org.nmox.studio.core.util.AtomicFiles.writeString(target.toPath(), text);
            if (target.getAbsoluteFile().equals(watchedPatch == null ? null : watchedPatch.getAbsoluteFile())) {
                long[] now = stampOf(target);
                noteSynced(target, now[0], now[1]);
            }
        }
        String synced = snapshot.toString();
        java.awt.EventQueue.invokeLater(() -> {
            if (target.equals(watchedPatch)) {
                syncedJson = synced;
            }
        });
    }

    /**
     * The project's patch was read by an explicit gesture (Load Patch): the
     * file is now the rack on screen, so whatever lock a refused or foreign
     * read set on it no longer describes it — except a newer format, which
     * the document itself decides. {@code stamp} is the file's stamp taken
     * before the read. EDT.
     */
    public void patchLoaded(File patch, RackIO.CableReport cables, long[] stamp) {
        if (!patch.getAbsoluteFile().equals(watchedPatch == null ? null : watchedPatch.getAbsoluteFile())) {
            return;
        }
        adoptLoaded(watchedPatch, cables, stamp);
    }

    /** The stamp {@link #patchLoaded} wants: taken before the read, so a change during the read is still foreign. */
    public static long[] stampBeforeRead(File patch) {
        return stampOf(patch);
    }

    /**
     * The importance this refusal is set at. It must be ABOVE ZERO, and that
     * is not a preference — it is the whole fix (ledger 109). Read from the
     * shipped bytecode: {@code NbStatusDisplayer.setStatusText(String)} is
     * {@code add(text, 0)} followed by {@code clear(SURVIVING_TIME)}, where
     * SURVIVING_TIME is
     * {@code Integer.getInteger("org.openide.awt.StatusDisplayer.DISPLAY_TIME", 5000)}
     * — so a plain status message deletes itself after five seconds. The
     * two-argument form calls {@code add(text, importance)} and returns
     * WITHOUT scheduling a clear, which is why the sentence can outlive the
     * project opening that hides it.
     *
     * <p>The value sits well below the platform's own family
     * ({@code IMPORTANCE_ERROR_HIGHLIGHT} through
     * {@code IMPORTANCE_ANNOTATION}, 700–1000, read from the class file), so
     * an editor annotation or a find still wins the strip.
     */
    private static final int PATCH_REFUSAL_IMPORTANCE = 100;

    /**
     * How long the refusal stays. **Persisting costs something**, and the cost
     * is stated here rather than discovered later: a message with importance
     * above zero outranks every plain {@code setStatusText}, so for this long
     * it also HIDES ordinary status text. That is the trade the fix makes —
     * during a project open the plain traffic is progress noise and a refusal
     * that explains an empty rack matters more — but it is why the linger is
     * bounded rather than "until something replaces it".
     *
     * <p>Five seconds was measured too short in the v2.181.0 Hebrew walk: the
     * clock starts when the rack loads the patch, which is DURING project
     * opening, before the window has settled and while the reader is looking
     * anywhere but the status strip.
     */
    private static final int PATCH_REFUSAL_LINGER_MS = 15_000;

    /**
     * A status message that outlives the project opening which would otherwise
     * bury it — see {@link #PATCH_REFUSAL_IMPORTANCE} for why the plain
     * {@link #status} cannot do this. A second refusal replaces the first
     * rather than stacking: {@code NbStatusDisplayer.add} REPLACES an existing
     * message of equal importance, so successive failed aims never queue up.
     */
    /**
     * The message currently shown, held so it cannot be collected before its
     * time. **This field is the fix, and v2.182.0 shipped without it.**
     *
     * <p>`NbStatusDisplayer` keeps only a {@code WeakReference} to each
     * message AND gives {@code MessageImpl} a {@code finalize()} that calls
     * {@code run()}, which removes it from the strip. So a message nobody
     * holds disappears at the first garbage collection — and a project
     * opening allocates heavily, which is exactly when this one is set.
     * v2.182.0 reasoned that {@code clear(ms)}'s pending RequestProcessor task
     * would hold it and did not check; walked afterwards, the sentence died
     * between two and nine seconds instead of the fifteen it asked for.
     */
    private static volatile org.openide.awt.StatusDisplayer.Message patchRefusalShown;

    private static void statusThatLingers(String text) {
        try {
            // held in a field, not a local: see patchRefusalShown for why the
            // pending clear() task is not enough to keep it alive
            patchRefusalShown = org.openide.awt.StatusDisplayer.getDefault().setStatusText(
                    org.nmox.studio.core.util.PlainStatus.text(text), PATCH_REFUSAL_IMPORTANCE);
            patchRefusalShown.clear(PATCH_REFUSAL_LINGER_MS);
        } catch (RuntimeException | LinkageError ignored) {
            // status line unavailable (tests, stripped platform)
        }
    }

    /** Best-effort status line; unavailable in plain unit tests. */
    private static void status(String text) {
        try {
            org.openide.awt.StatusDisplayer.getDefault().setStatusText(org.nmox.studio.core.util.PlainStatus.text(text));
        } catch (RuntimeException | LinkageError ignored) {
            // status line unavailable (tests, stripped platform)
        }
    }

    private boolean askStopLive(String message) {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            return true; // no dialog possible; behave as before, but deterministically
        }
        Object answer = org.openide.DialogDisplayer.getDefault().notify(
                new org.openide.NotifyDescriptor.Confirmation(message, Bundle.RackService_switchTitle(),
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

    /**
     * Forget a project from the recent list on request (v1.288.0): the
     * learning spaces under ~/.nmox/learn exist on disk forever, so
     * without this gesture they crowd the Workbench's home list for the
     * life of the install. List-only — the directory is untouched, and
     * aiming at the project re-records it.
     */
    public void forgetRecentProject(File dir) {
        if (dir == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (File f : getRecentProjects()) {
            if (f.equals(dir)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(f.getAbsolutePath());
        }
        java.util.prefs.Preferences p = prefs();
        p.put(PREF_RECENT, sb.toString());
        try {
            p.flush();
        } catch (java.util.prefs.BackingStoreException ignore) {
            // best effort
        }
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
        unbindPatch();
        if (patch.exists()) {
            // the stamp BEFORE the read: a change landing during the read is
            // still somebody else's, and the watcher will see it
            long[] seen = stampOf(patch);
            try {
                RackIO.CableReport cables = RackIO.load(rack, patch);
                if (!cables.quiet()) {
                    // a teammate's device edits moved or removed what some
                    // cables were patched to: said, not only logged (3.4)
                    statusThatLingers(cablesSentence(patch, cables));
                }
                adoptLoaded(patch, cables, seen);
            } catch (Exception ex) {
                java.util.logging.Logger.getLogger(RackService.class.getName())
                        .warning("Could not load rack patch " + patch + ": " + ex);
                if (!RackIO.mayOverwrite(ex)) {
                    // never write a file this build could not read: a conflict
                    // git is waiting on, bytes it could not read, or a broken
                    // file no copy of exists (3.4 — Save Patch used to replace
                    // a mode-000 or conflicted patch with the rack on screen)
                    bindPatchReadOnly(patch, ex);
                }
                // the refused bytes are the ones this session has seen: the
                // watcher speaks again only when they CHANGE (a resolved
                // conflict reloads by itself), never twice about one file
                noteSynced(patch, seen[0], seen[1]);
                syncedJson = RackIO.toJson(rack).toString();
                // REFUSALS SPEAK (ledger 104): aiming a project whose patch is
                // corrupt or over the cap left the reader looking at an empty
                // rack with the reason in a log file they never open. The load
                // is not something they asked for, so this must not be a
                // dialog — the status line, where the rest of the aim speaks.
                statusThatLingers(patchNotLoadedText(patch, ex));
            }
        } else {
            // A project with NO patch used to keep the PREVIOUS project's
            // devices mounted — there was no else here (v1.278.0, the
            // Task Rack persona walk: aiming a plain Node project showed
            // a REPL dialed to `elm repl` from the learning space aimed
            // before it). Three consequences, worst last: the rack lied
            // about whose pipeline it was; Save Patch would have written
            // project A's devices into project B's .nmoxrack.json; and a
            // RUNNING device kept its process while every lane's
            // commandDir silently re-rooted to B, so GO would run A's
            // command in B's directory. A patchless project now gets the
            // same known state a fresh launch gets — removeDevice
            // disposes each device, so anything running stops first.
            resetToStarterRack();
            noteSynced(patch, -1, -1);
            syncedJson = RackIO.toJson(rack).toString();
        }
        // switching projects loads a different rack; undo starts fresh, and
        // must never peel a just-loaded patch apart device by device
        rack.clearUndoHistory();
        // watched from every aim, not only after a refusal (3.4): a pull with
        // the IDE open must reach the rack before a Save can overwrite it
        watchPatch(patch);
    }

    /**
     * Clears the rack and mounts the starter for the project's KIND (v2.176.0):
     * the rack the New Project wizard would have written beside it — a Rust
     * checkout gets IGNITION/VERITAS/INSPECTOR on REFLEX, an Angular one HALO
     * into SCOPE — read from the one home both doors share,
     * {@link StarterRacks}. A kind with no toolchain to wire (no manifest, a
     * learning directory) keeps the bare starter of {@link #loadDefaultRack()},
     * which is also what a first launch shows.
     *
     * <p>v1.278.0 made a patchless project reset to the bare rack because the
     * alternative then — inheriting the PREVIOUS project's devices — was a lie.
     * This keeps that law (the previous project's devices are gone before
     * anything mounts) and answers the question it left: not "the same as a
     * fresh launch" but "the same as this project's own template".
     */
    private void resetToStarterRack() {
        for (RackDevice d : new java.util.ArrayList<>(rack.getDevices())) {
            rack.removeDevice(d);
        }
        java.util.Optional<StarterRacks.Starter> starter = StarterRacks.forProject(rack.getProjectDir());
        if (starter.isEmpty()) {
            loadDefaultRack();
            return;
        }
        java.util.logging.Logger.getLogger(RackService.class.getName()).log(java.util.logging.Level.INFO,
                "no {0} in {1}: mounting the {2} starter rack",
                new Object[]{RackIO.DEFAULT_FILENAME, rack.getProjectDir().getName(), starter.get().id()});
        starter.get().wiring().accept(rack);
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
