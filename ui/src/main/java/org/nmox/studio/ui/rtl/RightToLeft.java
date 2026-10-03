package org.nmox.studio.ui.rtl;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Container;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.ContainerEvent;
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
 *
 * <p><b>And a sweep only sees what exists</b> (3.5.3). A component does not
 * take its parent's direction when it is added: it starts with none, which
 * reads as left-to-right. So a window that rebuilds its content after it
 * opened, as the Workbench does on every refresh, went back to left-to-right
 * rows inside a right-to-left window, until the next window happened to open
 * and the sweep ran again. The Hebrew and Arabic guides' own pictures of the
 * Workbench showed it. While the interface runs right-to-left, a second
 * toolkit listener gives every component the direction of the container it
 * is added to. It is not installed otherwise: a left-to-right build has
 * nothing to hand on, and the toolkit builds a container event for every
 * {@code add} in the JVM only while somebody is listening for them.
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

    private static final AWTEventListener ORIENT_NEW_CHILDREN = event -> {
        if (event.getID() == ContainerEvent.COMPONENT_ADDED && event instanceof ContainerEvent added) {
            adopt(added.getContainer(), added.getChild());
        }
    };

    /** Set while a tree is being oriented: what it adds on the way is already in that tree. */
    private static final ThreadLocal<Boolean> ORIENTING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static boolean listeningForChildren;

    @Override
    public void run() {
        Toolkit.getDefaultToolkit()
                .addAWTEventListener(ORIENT_NEW_WINDOWS, AWTEvent.WINDOW_EVENT_MASK);
        listenForChildren();
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
        UiLocale.addListener(() -> SwingUtilities.invokeLater(() -> {
            listenForChildren();
            orientEverythingOpen();
        }));
    }

    /** Listens for new children exactly while the interface runs right-to-left. */
    static synchronized void listenForChildren() {
        boolean wanted = TextDirection.isRightToLeft(Locale.getDefault());
        if (wanted == listeningForChildren) {
            return;
        }
        if (wanted) {
            Toolkit.getDefaultToolkit()
                    .addAWTEventListener(ORIENT_NEW_CHILDREN, AWTEvent.CONTAINER_EVENT_MASK);
        } else {
            Toolkit.getDefaultToolkit().removeAWTEventListener(ORIENT_NEW_CHILDREN);
        }
        listeningForChildren = wanted;
    }

    /**
     * A component added to a right-to-left container takes its direction,
     * with everything inside it; true when it did.
     *
     * <p>Nothing happens when the container itself runs left-to-right: it is
     * a painted surface that keeps its authored direction, or text marked as
     * left-to-right in every language, or a tree still being built that no
     * sweep has reached, and whatever is added to it belongs to it. Nothing
     * happens when the child already runs right-to-left. A child that must
     * stay left-to-right is put back by the same pass that follows a sweep.
     */
    static boolean adopt(Container parent, Component child) {
        if (parent == null || child == null || ORIENTING.get()) {
            return false;
        }
        ComponentOrientation o = parent.getComponentOrientation();
        if (o.isLeftToRight() || !child.getComponentOrientation().isLeftToRight()) {
            return false;
        }
        orient(child, o);
        return true;
    }

    /** Orients a tree and puts back what a blunt walk of it disturbs. Never throws. */
    private static void orient(Component c, ComponentOrientation o) {
        ORIENTING.set(Boolean.TRUE);
        try {
            // a split pane loses its divider (and, right-to-left, the order
            // of its children) to the runtime's own setComponentOrientation:
            // record the ones about to be disturbed, put them back after —
            // with their sides exchanged for a right-to-left reader where the
            // product marked the pane to follow its reader (3.5.12, 3.5.13)
            java.util.List<SplitShapes.Shape> splits = SplitShapes.disturbedBy(c, o);
            c.applyComponentOrientation(o);
            PaintedSurfaces.keepAuthoredDirection(c);
            SplitShapes.restore(splits);
        } catch (RuntimeException e) {
            // a component that refuses orientation keeps the one it had; this
            // sits inside the toolkit's own dispatch and never throws into
            // it (the v1.107.0 hot-path law, one layer up)
        } finally {
            ORIENTING.set(Boolean.FALSE);
        }
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
        orient(c, o);
        try {
            // orientation changes the LAYOUT, so the tree must be laid out
            // again — a repaint alone draws the old geometry in new colours
            if (c instanceof javax.swing.JComponent j) {
                j.revalidate();
            } else {
                c.validate();
            }
            c.repaint();
        } catch (RuntimeException e) {
            // as above: nothing here throws into the toolkit's dispatch
        }
    }

    /** The platform instantiates this through {@code @OnShowing}. */
    public RightToLeft() {
    }
}
