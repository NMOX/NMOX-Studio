package org.nmox.studio.core;

import java.awt.Color;
import java.util.prefs.Preferences;
import org.openide.modules.ModuleInfo;
import org.openide.modules.Modules;
import org.openide.modules.OnStart;
import org.openide.util.NbPreferences;

/**
 * Dresses the built-in Terminal in NMOX phosphor on first run: black
 * field, lime-green text, dim green selection - the PHOSPHOR device's
 * aesthetic, now in the real shell.
 *
 * TermOptions is a friend-only API, so it is reached through its own
 * module's classloader; its real storeTo() does the persisting, so the
 * preference format can never drift from the platform's.
 *
 * Applied exactly once (marker preference) - whatever the user later
 * picks in Tools &gt; Options &gt; Terminal sticks.
 */
@OnStart
public class TerminalPhosphor implements Runnable {

    static final Color PHOSPHOR_BG = Color.BLACK;
    static final Color PHOSPHOR_FG = new Color(0x32, 0xCD, 0x32);        // lime green
    static final Color PHOSPHOR_SELECTION = new Color(0x14, 0x3D, 0x14); // dim green

    private static final String TERM_MODULE = "org.netbeans.lib.terminalemulator";
    private static final String TERM_OPTIONS = TERM_MODULE + ".support.TermOptions";
    private static final String MARKER = "terminal.phosphor.applied";

    @Override
    public void run() {
        Preferences marker = NbPreferences.forModule(TerminalPhosphor.class);
        if (marker.getBoolean(MARKER, false)) {
            return;
        }
        try {
            ModuleInfo module = Modules.getDefault().findCodeNameBase(TERM_MODULE);
            if (module == null || !module.isEnabled()) {
                return; // terminal not part of this assembly
            }
            Class<?> termOptions = Class.forName(TERM_OPTIONS, true, module.getClassLoader());
            // same node TermOptions uses internally via NbPreferences.forModule
            Preferences termPrefs = NbPreferences.root().node(TERM_MODULE.replace('.', '/'));
            Object options = termOptions.getMethod("getDefault", Preferences.class)
                    .invoke(null, termPrefs);
            termOptions.getMethod("setBackground", Color.class).invoke(options, PHOSPHOR_BG);
            termOptions.getMethod("setForeground", Color.class).invoke(options, PHOSPHOR_FG);
            termOptions.getMethod("setSelectionBackground", Color.class)
                    .invoke(options, PHOSPHOR_SELECTION);
            termOptions.getMethod("storeTo", Preferences.class).invoke(options, termPrefs);
            marker.putBoolean(MARKER, true);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            // API moved or module absent; leave the terminal stock rather than break startup
        }
    }
}
