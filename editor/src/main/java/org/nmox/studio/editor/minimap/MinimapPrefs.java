package org.nmox.studio.editor.minimap;

import java.util.prefs.Preferences;
import org.openide.util.NbPreferences;

/**
 * The one preference behind the minimap: on by default, flipped by
 * View ▸ Minimap, observed live by every open strip through the
 * preference-change event (no editor reopen).
 */
public final class MinimapPrefs {

    /** Preference key, also the event key the strips listen for. */
    public static final String KEY = "minimap.enabled";

    private MinimapPrefs() {
    }

    static Preferences prefs() {
        return NbPreferences.forModule(MinimapPrefs.class);
    }

    /** Whether the minimap shows; the default is on. */
    public static boolean enabled() {
        return prefs().getBoolean(KEY, true);
    }

    /** Flips the preference; every open strip follows through the event. */
    public static void setEnabled(boolean on) {
        prefs().putBoolean(KEY, on);
    }
}
