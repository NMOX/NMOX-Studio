package org.nmox.studio.ui.a11y;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.beans.PropertyChangeListener;
import javax.accessibility.AccessibleContext;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import org.openide.awt.Actions;
import org.openide.awt.ToolbarPool;
import org.openide.windows.OnShowing;

/**
 * The platform toolbar's buttons tell a screen reader their menu
 * mnemonic: {@code Actions.connect} sets a button's accessible name to the
 * action's raw NAME, ampersand included, while the tooltip beside it gets
 * the cleaned text (measured on RELEASE310: an action named
 * {@code &New File...} gives a button whose accessible name is
 * {@code &New File...} and whose tooltip is {@code New File...}). VoiceOver
 * read "ampersand New File" on the first toolbar button of every build, in
 * every language - ledger 90 translated the names and kept the ampersand.
 *
 * <p>Every button in the main window's toolbars is hooked once: its name
 * is cut now, and again whenever the platform sets it (a Main Project
 * verb renames itself as the main project changes). A toolbar that gains
 * buttons later - a configuration switch - hooks them as they arrive.
 * Buttons whose names carry no ampersand are left exactly as they are.
 */
@OnShowing
public final class ToolbarAccessibleNames implements Runnable {

    private static final String HOOKED = "nmox.a11y.cutAmpersand";

    @Override
    public void run() {
        ToolbarPool pool = ToolbarPool.getDefault();
        hook(pool);
        pool.addPropertyChangeListener(e -> hook(pool));
    }

    /** Hooks every button under {@code root}, and every one added to it later. */
    public static void hook(Container root) {
        if (root instanceof JComponent jc) {
            if (jc.getClientProperty(HOOKED) != null) {
                // a container is hooked once; its new children arrive by the listener
                hookChildren(root);
                return;
            }
            jc.putClientProperty(HOOKED, Boolean.TRUE);
        }
        root.addContainerListener(new ContainerAdapter() {
            @Override
            public void componentAdded(ContainerEvent e) {
                hookComponent(e.getChild());
            }
        });
        hookChildren(root);
    }

    private static void hookChildren(Container root) {
        for (Component c : root.getComponents()) {
            hookComponent(c);
        }
    }

    private static void hookComponent(Component c) {
        if (c instanceof AbstractButton b) {
            hookButton(b);
        } else if (c instanceof Container child) {
            hook(child);
        }
    }

    static void hookButton(AbstractButton b) {
        if (b.getClientProperty(HOOKED) != null) {
            return;
        }
        b.putClientProperty(HOOKED, Boolean.TRUE);
        AccessibleContext ctx = b.getAccessibleContext();
        PropertyChangeListener recut = e -> {
            if (AccessibleContext.ACCESSIBLE_NAME_PROPERTY.equals(e.getPropertyName())) {
                cut(ctx);
            }
        };
        ctx.addPropertyChangeListener(recut);
        cut(ctx);
    }

    /** Drops the mnemonic marker from a name that carries one; a no-op otherwise, so it cannot loop. */
    static void cut(AccessibleContext ctx) {
        String name = ctx.getAccessibleName();
        if (name != null && name.indexOf('&') >= 0) {
            String clean = Actions.cutAmpersand(name);
            if (!clean.equals(name)) {
                ctx.setAccessibleName(clean);
            }
        }
    }
}
