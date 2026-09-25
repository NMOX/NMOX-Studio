package org.nmox.studio.editor.blame;

import java.util.prefs.Preferences;
import org.openide.util.NbPreferences;

/**
 * The one preference behind the line-blame note: on by default, flipped by
 * View ▸ Line Blame, observed live by the status-line note through the
 * preference-change event (the {@code MinimapPrefs} shape).
 */
public final class BlamePrefs {

    /** Preference key, also the event key the note listens for. */
    public static final String KEY = "lineblame.enabled";

    private BlamePrefs() {
    }

    static Preferences prefs() {
        return NbPreferences.forModule(BlamePrefs.class);
    }

    /** Whether the note shows; the default is on. */
    public static boolean enabled() {
        return prefs().getBoolean(KEY, true);
    }

    /** Flips the preference; the note follows through the event. */
    public static void setEnabled(boolean on) {
        prefs().putBoolean(KEY, on);
    }
}
