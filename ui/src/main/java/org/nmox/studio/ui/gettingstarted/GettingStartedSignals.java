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
        if (!done.contains("oracle") && oracleAsked()) {
            tick(done, "oracle");
        }
        if (!done.contains("learn") && learningSpaceExists()) {
            tick(done, "learn");
        }
        return done;
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

    private static boolean oracleAsked() {
        try {
            if (org.nmox.studio.rack.service.OracleConsent.isGranted()) {
                return true;
            }
            // the CODE consent (Ask/Edit/Complete) is its own grant, same store
            return NbPreferences.forModule(org.nmox.studio.rack.service.OracleConsent.class)
                    .getBoolean("oracle.code.consent", false);
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
