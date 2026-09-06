package org.nmox.studio.ui.gettingstarted;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.prefs.Preferences;
import java.util.stream.Stream;
import org.nmox.studio.core.spi.LiveServings;
import org.nmox.studio.core.spi.ProjectAim;
import org.openide.util.NbPreferences;

/**
 * Where the checklist's ticks come from: records the product already
 * keeps, read OFF the EDT, each behind its own guard so a missing seam
 * (no rack in this session) leaves that step unticked rather than
 * breaking the column. A tick is persisted the moment it is observed —
 * a server that was live once counts forever — so the checklist never
 * un-ticks.
 */
public final class GettingStartedSignals {

    private static final String DONE_PREFIX = "gettingstarted.done.";
    private static final String HIDDEN = "gettingstarted.hidden";

    private GettingStartedSignals() {
    }

    static Preferences prefs() {
        return NbPreferences.forModule(GettingStartedSignals.class);
    }

    /** Whether the user hid the column. */
    public static boolean hidden() {
        return prefs().getBoolean(HIDDEN, false);
    }

    /** Hides the column for good (until the userdir is reset). */
    public static void hide() {
        prefs().putBoolean(HIDDEN, true);
    }

    /** The persisted ticks. */
    public static Set<String> stored() {
        Set<String> done = new LinkedHashSet<>();
        for (GettingStarted.Step s : GettingStarted.STEPS) {
            if (prefs().getBoolean(DONE_PREFIX + s.key(), false)) {
                done.add(s.key());
            }
        }
        return done;
    }

    /**
     * Observes the live signals, persists any new tick, and returns the
     * merged set. Off the EDT: the learn home is a directory listing.
     */
    public static Set<String> observe() {
        Set<String> done = stored();
        if (!done.contains("project") && recentProjects()) {
            tick(done, "project");
        }
        if (!done.contains("run") && rackRan()) {
            tick(done, "run");
        }
        if (!done.contains("serve") && serverLive()) {
            tick(done, "serve");
        }
        if (!done.contains("kvasir") && kvasirAsked()) {
            tick(done, "kvasir");
        }
        if (!done.contains("learn") && learningSpaceExists()) {
            tick(done, "learn");
        }
        if (!done.contains("agent") && agentPointed()) {
            tick(done, "agent");
        }
        return done;
    }

    /**
     * A serving-registry change: if a server is live now and the serve
     * step is not yet done, tick it (persisted). Returns true when the
     * tick just happened so the caller repaints. Listener-driven, so a
     * server that lives three seconds still counts (v2.69.11).
     */
    public static boolean serverAppeared() {
        if (prefs().getBoolean(DONE_PREFIX + "serve", false) || !serverLive()) {
            return false;
        }
        prefs().putBoolean(DONE_PREFIX + "serve", true);
        return true;
    }

    private static void tick(Set<String> done, String key) {
        done.add(key);
        prefs().putBoolean(DONE_PREFIX + key, true);
    }

    private static boolean recentProjects() {
        try {
            ProjectAim aim = ProjectAim.find();
            return aim != null && !aim.recentProjects().isEmpty();
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    private static boolean rackRan() {
        try {
            for (org.nmox.studio.rack.engine.FlightRecorder.Event e
                    : org.nmox.studio.rack.engine.FlightRecorder.getDefault().timeline()) {
                if (e.kind() == org.nmox.studio.rack.engine.FlightRecorder.Kind.LAUNCH) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    private static boolean serverLive() {
        try {
            LiveServings live = LiveServings.find();
            return live != null && !live.snapshot().isEmpty();
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /**
     * The Agent Port has been started at least once — the rack records it
     * on start (v2.84.0) in its own module node. Read by PATH, not by class:
     * the rack's mcp package is not exported, and forModule(Class) is just
     * root().node(package path). Absent rack, absent record, no tick.
     */
    static final String AGENT_PORT_NODE = "org/nmox/studio/rack/mcp";

    private static boolean agentPointed() {
        try {
            return NbPreferences.root().node(AGENT_PORT_NODE).getBoolean("agentport.started", false);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean kvasirAsked() {
        try {
            if (org.nmox.studio.rack.service.KvasirConsent.isGranted()) {
                return true;
            }
            // the CODE consent (Ask/Edit/Complete) is its own grant, same store
            return NbPreferences.forModule(org.nmox.studio.rack.service.KvasirConsent.class)
                    .getBoolean("kvasir.code.consent", false);
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    private static boolean learningSpaceExists() {
        Path home = new File(new File(System.getProperty("user.home"), ".nmox"), "learn").toPath();
        if (!Files.isDirectory(home)) {
            return false;
        }
        try (Stream<Path> kids = Files.list(home)) {
            return kids.anyMatch(Files::isDirectory);
        } catch (IOException e) {
            return false;
        }
    }
}
