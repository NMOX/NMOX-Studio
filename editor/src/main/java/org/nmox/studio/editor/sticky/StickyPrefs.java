package org.nmox.studio.editor.sticky;

import java.util.prefs.Preferences;
import org.openide.util.NbPreferences;

/**
 * The one preference behind sticky scroll: on by default, flipped by
 * View ▸ Sticky Scroll, observed live by every open bar.
 */
public final class StickyPrefs {

    /** Preference key, also the event key the bars listen for. */
    public static final String KEY = "sticky.enabled";

    private StickyPrefs() {
    }

    static Preferences prefs() {
        return NbPreferences.forModule(StickyPrefs.class);
    }

    /** Whether sticky scroll shows; the default is on. */
    public static boolean enabled() {
        return prefs().getBoolean(KEY, true);
    }

    /** Flips the preference; every open bar follows through the event. */
    public static void setEnabled(boolean on) {
        prefs().putBoolean(KEY, on);
    }
}
