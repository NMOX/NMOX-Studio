package org.nmox.studio.editor.present;

import java.awt.Font;
import org.openide.awt.StatusDisplayer;
import org.openide.util.Lookup;

/**
 * Presentation Mode reaches the Output window (v2.87.0): a talk RUNS
 * things, and the run's output at 12 pt was the third thing the room
 * could not read (the code and the page already followed). The platform's
 * output2 module keeps one {@code OutputOptions} singleton every open
 * output tab listens to and re-reads its font from (decompiled:
 * {@code OutputTab} adds a PropertyChangeListener on
 * {@code OutputOptions.getDefault()} and calls {@code getFont()} on
 * change), so a live {@code setFont} re-renders them all. Nothing here is
 * ever persisted — {@code saveTo(Preferences)} is the Options panel's
 * verb and is never called (a test forbids the name in this file), so a
 * restart is back to the user's own font. The package is not public, so
 * the class is reached the way the git chip reaches History (v1.40.0):
 * the system class loader from Lookup, reflectively, degrading to a status
 * line — never a throw — when the platform changes shape.
 */
public final class OutputFont {

    /** The same bump the editors get. */
    static final int DELTA_POINTS = PresentationMode.DELTA_POINTS;

    private static Font before;

    private OutputFont() {
    }

    /** The presenting font: the user's own, {@code delta} points larger, family and style kept. */
    public static Font bumped(Font base, int delta) {
        return base.deriveFont(base.getSize2D() + delta);
    }

    /**
     * EDT. Bumps the Output window's font while presenting and restores
     * exactly the font it had on leaving. The push is the one the Options
     * panel makes (decompiled {@code OutputSettingsPanel.store}):
     * {@code Controller.updateOptions(copy)} assigns the copy into EVERY
     * open IO's own options — the object each {@code OutputTab} actually
     * listens to; the default singleton is set in memory too so a tab
     * created while presenting copies the presenting font — and
     * {@code storeDefault} is never called. Returns null when it followed,
     * else the reason (the caller puts it on the status line; a refusal
     * set here would be overwritten by the mode's own status a moment
     * later — the walk's find).
     */
    static String follow(boolean on) {
        try {
            ClassLoader system = Lookup.getDefault().lookup(ClassLoader.class);
            Class<?> options = Class.forName("org.netbeans.core.output2.options.OutputOptions", true, system);
            Class<?> controller = Class.forName("org.netbeans.core.output2.Controller", true, system);
            Object defaults = options.getMethod("getDefault").invoke(null);
            Font current = (Font) options.getMethod("getFont").invoke(defaults);
            Font next;
            if (on) {
                before = current;
                next = bumped(current, DELTA_POINTS);
            } else {
                next = before != null ? before : current;
                before = null;
            }
            Object copy = options.getMethod("makeCopy").invoke(defaults);
            options.getMethod("setFont", Font.class).invoke(copy, next);
            Object ctl = controller.getMethod("getDefault").invoke(null);
            controller.getMethod("updateOptions", options).invoke(ctl, copy); // every open tab's own copy
            options.getMethod("setFont", Font.class).invoke(defaults, next);   // tabs opened while presenting; in memory only
            return null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return "the Output window could not follow (" + ex.getClass().getSimpleName() + ")";
        }
    }
}
