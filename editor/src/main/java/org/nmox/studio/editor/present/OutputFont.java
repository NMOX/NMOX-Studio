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

    /** EDT. Bumps the Output window's font while presenting; restores exactly the font it had on leaving. */
    static void follow(boolean on) {
        try {
            ClassLoader system = Lookup.getDefault().lookup(ClassLoader.class);
            Class<?> options = Class.forName("org.netbeans.core.output2.options.OutputOptions", true, system);
            Object instance = options.getMethod("getDefault").invoke(null);
            Font current = (Font) options.getMethod("getFont").invoke(instance);
            Font next;
            if (on) {
                before = current;
                next = bumped(current, DELTA_POINTS);
            } else {
                next = before != null ? before : current;
                before = null;
            }
            options.getMethod("setFont", Font.class).invoke(instance, next);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            StatusDisplayer.getDefault().setStatusText("Presentation Mode: the Output window font could not follow — " + ex.getMessage());
        }
    }
}
