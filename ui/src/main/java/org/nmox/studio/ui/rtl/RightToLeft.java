package org.nmox.studio.ui.rtl;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.WindowEvent;
import java.util.Locale;

import javax.swing.SwingUtilities;

import org.openide.windows.OnShowing;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

import org.nmox.studio.core.util.TextDirection;
import org.nmox.studio.core.util.UiLocale;

/**
 * The interface runs the way the reader's language runs.
 *
 * <p>Swing gives this away for free in exactly one respect and no other: the
 * TEXT inside a component shapes and reorders correctly, because bidi belongs
 * to the text layout engine. Everything structural — which side a label sits
 * on, which way a tree indents, the order of a toolbar, where a scrollbar
 * lands — keeps the direction it was authored in until somebody calls
 * {@code applyComponentOrientation}. Measured before this was written
 * ({@link TextDirection}), so this class exists because of a probe, not a
 * belief.
 *
 * <p><b>One seam, not 160.</b> The product builds dialogs at 160 call sites and
 * will build more. Editing each one would make correctness a thing every
 * future author has to remember, which is the shape of every defect this
 * codebase has a law about. Instead a single toolkit listener answers
 * {@code WINDOW_OPENED} for every window the JVM ever opens — ours, the
 * platform's, and any a plugin adds — and orients it once. A window that
 * cannot be oriented is left exactly as it was; nothing here throws into
 * somebody else's event dispatch.
 *
 * <p>The already-open windows at startup, and every window open when the user
 * switches language, are swept directly: a listener only sees what opens next.
 */
@OnShowing
public final class RightToLeft implements Runnable {

    private static final AWTEventListener ORIENT_NEW_WINDOWS = event -> {
        if (event.getID() != WindowEvent.WINDOW_OPENED
                || !(event.getSource() instanceof Window w)) {
            return;
        }
        apply(w);
    };

    @Override
    public void run() {
        Toolkit.getDefaultToolkit()
                .addAWTEventListener(ORIENT_NEW_WINDOWS, AWTEvent.WINDOW_EVENT_MASK);
        // A window listener is not enough, and the walk proved it: a
        // TopComponent opening inside a window that is already open fires no
        // WINDOW_OPENED, so every panel opened after startup kept its authored
        // direction while the platform's own toolbar mirrored. The registry is
        // where a window opening is actually announced.
        WindowManager.getDefault().getRegistry().addPropertyChangeListener(ev -> {
            if (TopComponent.Registry.PROP_OPENED.equals(ev.getPropertyName())
                    || TopComponent.Registry.PROP_TC_OPENED.equals(ev.getPropertyName())) {
                SwingUtilities.invokeLater(RightToLeft::orientEverythingOpen);
            }
        });
        SwingUtilities.invokeLater(RightToLeft::orientEverythingOpen);
        // the live language switch (v2.103.0) can change the direction too
        UiLocale.addListener(() ->
                SwingUtilities.invokeLater(RightToLeft::orientEverythingOpen));
    }

    /** Every window and every open editor/window, in the direction now current. */
    static void orientEverythingOpen() {
        for (Window w : Window.getWindows()) {
            apply(w);
        }
        for (TopComponent tc : WindowManager.getDefault().getRegistry().getOpened()) {
            apply(tc);
        }
    }

    /**
     * Orient one component tree. Painted surfaces opt OUT by name: see
     * {@link PaintedSurfaces}.
     */
    static void apply(Component c) {
        if (c == null) {
            return;
        }
        ComponentOrientation o = TextDirection.orientation(Locale.getDefault());
        try {
            c.applyComponentOrientation(o);
            PaintedSurfaces.keepAuthoredDirection(c);
            // orientation changes the LAYOUT, so the tree must be laid out
            // again — a repaint alone draws the old geometry in new colours
            if (c instanceof javax.swing.JComponent j) {
                j.revalidate();
            } else {
                c.validate();
            }
            c.repaint();
        } catch (RuntimeException e) {
            // a component that refuses orientation keeps the one it had; this
            // listener sits inside the toolkit's own dispatch and never throws
            // into it (the v1.107.0 hot-path law, one layer up)
        }
    }

    /** The platform instantiates this through {@code @OnShowing}. */
    public RightToLeft() {
    }
}
