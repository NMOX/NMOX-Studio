package org.nmox.studio.infra.ops.ui;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;
import org.nmox.studio.core.process.ProcessSupport;
import org.openide.util.BaseUtilities;
import org.openide.util.NbPreferences;

/**
 * Whether the HUD may move. Three answers, in order: the user's own
 * switch in the Hub ({@code on}/{@code off}), the system property
 * {@code nmox.reducedMotion}, and on macOS the system's own Reduce
 * Motion setting, read once per session with {@code defaults} off the
 * EDT (a fixed command that reads one preference; it runs nothing of a
 * project's). Anywhere the answer cannot be read, motion stays on, and
 * the switch is one click away.
 */
public final class ReducedMotion {

    public enum Choice { AUTO, ON, OFF }

    private static final String KEY = "motion";
    private static final AtomicReference<Boolean> SYSTEM = new AtomicReference<>();

    private ReducedMotion() {
    }

    static Preferences prefs() {
        return NbPreferences.forModule(ReducedMotion.class).node("ops");
    }

    public static Choice choice() {
        try {
            return Choice.valueOf(prefs().get(KEY, Choice.AUTO.name()));
        } catch (IllegalArgumentException junk) {
            return Choice.AUTO;
        }
    }

    public static void setChoice(Choice c) {
        prefs().put(KEY, c.name());
    }

    /** True when the HUD should snap instead of glide. Cheap after the first system read. */
    public static boolean reduced() {
        return switch (choice()) {
            case ON -> false;
            case OFF -> true;
            case AUTO -> Boolean.getBoolean("nmox.reducedMotion") || Boolean.TRUE.equals(SYSTEM.get());
        };
    }

    /** Reads the system setting once, off the EDT; a later {@link #reduced()} sees it. */
    public static void readSystemSettingAsync() {
        if (SYSTEM.get() != null || !BaseUtilities.isMac()) {
            return;
        }
        org.openide.util.RequestProcessor.getDefault().post(() -> {
            boolean reduce = false;
            try {
                ProcessSupport.BoundedResult r = ProcessSupport.runBounded(
                        List.of("defaults", "read", "com.apple.universalaccess", "reduceMotion"),
                        null, Duration.ofSeconds(4));
                reduce = r.exitCode() == 0 && r.stdout().trim().equals("1");
            } catch (java.io.IOException | RuntimeException cannot) {
                // unreadable: motion stays on
            }
            SYSTEM.compareAndSet(null, reduce);
        });
    }

    /** Test seam. */
    static void systemSays(Boolean reduce) {
        SYSTEM.set(reduce);
    }
}
