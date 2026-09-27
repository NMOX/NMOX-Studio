package org.nmox.studio.ui.a11y;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Toolkit;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.AbstractButton;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;

import org.openide.windows.OnShowing;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Every toolbar button in NMOX's own windows can be reached with Tab (3.4,
 * question 3).
 *
 * <p>Measured on the assembled app under FlatDarkLaf: 58 visible toolbar
 * buttons across nine windows reported {@code isFocusable() == false}, so
 * DB Studio's RUN, the Infra Designer's DEPLOY, Contract Studio's Compile,
 * the Tests window's Run and the NPM Explorer's Install had no keyboard
 * route at all. The cause is the look and feel, not the windows: FlatLaf
 * ships {@code ToolBar.focusableButtons = false} and its toolbar UI turns
 * off focus on every button a toolbar holds, including buttons added later.
 *
 * <p>The repair is FlatLaf's own per-toolbar switch, its {@code FlatLaf.style}
 * {@code focusableButtons}, so the look and feel keeps buttons focusable as
 * they come and go and across a theme change, with the setting stated once
 * here instead of in nine windows. Each button also stops requesting focus
 * on a mouse press, so a click on RUN leaves the caret in the console where
 * it was; only the keyboard (and an assistive tool) moves focus onto it.
 * FlatLaf's arrow-keys-only toolbar navigation is turned off beside it: the
 * studios' toolbars mix buttons with combo boxes and fields whose own arrow
 * keys must keep working, so every button is its own Tab stop, as it is
 * under every other look and feel.
 *
 * <p><b>Which windows.</b> Every TopComponent whose class is NMOX's
 * ({@code org.nmox.}), found as it opens and as its toolbars are built, so
 * a studio that builds its toolbar on first show, or adds a button later,
 * is covered without doing anything. <b>The platform's main toolbar is left
 * as it is, deliberately</b>: every button on it is a menu row the keyboard
 * already reaches from the menu bar, most with a chord (New File, Open
 * Project, Save All, Run and Debug Main Project, Undo and Redo), and it sits
 * outside every window's focus cycle, so making its buttons focusable would
 * add Tab stops a keyboard user could never reach while taking nothing a
 * keyboard user lacks.
 */
@OnShowing
public final class ToolbarKeyboardAccess implements Runnable {

    static final String INSTALLED = "nmox.a11y.toolbarKeyboard";
    /** FlatLaf's per-component style property ({@code FlatClientProperties.STYLE}). */
    static final String FLATLAF_STYLE = "FlatLaf.style";
    static final String STYLE = "focusableButtons: true; arrowKeysOnlyNavigation: false";

    @Override
    public void run() {
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event instanceof ContainerEvent ce && ce.getID() == ContainerEvent.COMPONENT_ADDED) {
                added(ce.getContainer(), ce.getChild());
            }
        }, AWTEvent.CONTAINER_EVENT_MASK);
        WindowManager.getDefault().getRegistry().addPropertyChangeListener(ev -> {
            if (TopComponent.Registry.PROP_TC_OPENED.equals(ev.getPropertyName())
                    || TopComponent.Registry.PROP_OPENED.equals(ev.getPropertyName())) {
                SwingUtilities.invokeLater(ToolbarKeyboardAccess::installEverywhere);
            }
        });
        SwingUtilities.invokeLater(ToolbarKeyboardAccess::installEverywhere);
    }

    static void installEverywhere() {
        for (TopComponent tc : TopComponent.getRegistry().getOpened()) {
            if (isNmoxWindow(tc)) {
                installUnder(tc);
            }
        }
    }

    /** A component joined a container: repair its toolbars when it now sits in an NMOX window. */
    static void added(Container parent, Component child) {
        if (parent == null || child == null || !insideNmoxWindow(parent)) {
            return;
        }
        installUnder(child);
    }

    static boolean insideNmoxWindow(Component c) {
        for (Component p = c; p != null; p = p.getParent()) {
            if (p instanceof TopComponent tc) {
                return isNmoxWindow(tc);
            }
        }
        return false;
    }

    static boolean isNmoxWindow(TopComponent tc) {
        return tc.getClass().getName().startsWith("org.nmox.");
    }

    /** Repairs every toolbar under {@code root}. */
    static void installUnder(Component root) {
        if (root instanceof JToolBar bar) {
            install(bar);
        }
        if (root instanceof Container c) {
            for (Component child : c.getComponents()) {
                installUnder(child);
            }
        }
    }

    /** One toolbar: FlatLaf's switch on, every button focusable by keyboard only, now and as buttons arrive. */
    static void install(JToolBar bar) {
        if (bar.getClientProperty(INSTALLED) == null) {
            bar.putClientProperty(INSTALLED, Boolean.TRUE);
            bar.putClientProperty(FLATLAF_STYLE, withStyle(bar.getClientProperty(FLATLAF_STYLE)));
            // after the look and feel's own container listener, so a button
            // added later ends focusable whatever the UI did first
            bar.addContainerListener(new ContainerAdapter() {
                @Override
                public void componentAdded(ContainerEvent e) {
                    button(e.getChild());
                }
            });
        }
        for (Component c : bar.getComponents()) {
            button(c);
        }
    }

    private static void button(Component c) {
        if (c instanceof AbstractButton b) {
            b.setFocusable(true);
            b.setRequestFocusEnabled(false);
        }
    }

    /** Adds the switch to whatever style the toolbar already carries. */
    static Object withStyle(Object existing) {
        if (existing instanceof Map<?, ?> map) {
            Map<Object, Object> merged = new LinkedHashMap<>(map);
            merged.put("focusableButtons", Boolean.TRUE);
            merged.put("arrowKeysOnlyNavigation", Boolean.FALSE);
            return merged;
        }
        if (existing instanceof String s && !s.isBlank()) {
            return s.strip().endsWith(";") ? s + " " + STYLE : s + "; " + STYLE;
        }
        return STYLE;
    }

    /** The platform instantiates this through {@code @OnShowing}. */
    public ToolbarKeyboardAccess() {
    }
}
