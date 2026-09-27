package org.nmox.studio.ui.a11y;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleComponent;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleSelection;
import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStateSet;
import javax.swing.SwingUtilities;

import org.netbeans.swing.tabcontrol.TabData;
import org.netbeans.swing.tabcontrol.TabbedContainer;
import org.openide.windows.OnShowing;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Every window's content reaches a screen reader (3.4, question 3).
 *
 * <p>Measured on the assembled app with the accessibility tree VoiceOver
 * walks: the window system's tab containers — the editor area, the
 * explorer on the left, the output area at the bottom — each exposed
 * <em>no children at all</em>. The Welcome's links, a studio's buttons, a
 * result table all existed as accessibility objects, and pointing at one
 * found it, but navigating could never reach it: a screen reader moves
 * through children, and the containers had none. The cause is in the
 * platform: {@code TabbedContainer} reports itself as a tab list
 * ({@code PAGE_TAB_LIST}) while none of its children is a tab
 * ({@code PAGE_TAB}), and macOS's Java bridge builds a tab group's children
 * from its tabs plus "the selected tab's contents" — with no tab found,
 * there is no selected tab, and so nothing.
 *
 * <p>The repair gives each container the structure its role promises, the
 * one {@code JTabbedPane} has always had: one {@code PAGE_TAB} child per
 * open window, titled as its tab is, the showing one marked selected, and
 * each tab's single child the window itself. Everything else — the name,
 * the states, bounds and hit-testing — is still the platform's own context.
 * Installed once the main window shows ({@code @OnShowing}), on every
 * container open now and every one a later window brings. A container that
 * cannot be repaired is left exactly as it was.
 */
@OnShowing
public final class WindowTabsAccessibility implements Runnable {

    private static final Logger LOG = Logger.getLogger(WindowTabsAccessibility.class.getName());
    private static final String INSTALLED = "nmox.a11y.windowTabs";

    @Override
    public void run() {
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event.getID() == WindowEvent.WINDOW_OPENED && event.getSource() instanceof Window w) {
                installUnder(w);
            }
        }, AWTEvent.WINDOW_EVENT_MASK);
        // a window opened inside an open window fires no WINDOW_OPENED (the
        // RightToLeft walk's lesson); the registry announces it
        WindowManager.getDefault().getRegistry().addPropertyChangeListener(ev -> {
            String p = ev.getPropertyName();
            if (TopComponent.Registry.PROP_OPENED.equals(p)
                    || TopComponent.Registry.PROP_TC_OPENED.equals(p)
                    || TopComponent.Registry.PROP_ACTIVATED.equals(p)) {
                SwingUtilities.invokeLater(WindowTabsAccessibility::installEverywhere);
            }
        });
        SwingUtilities.invokeLater(WindowTabsAccessibility::installEverywhere);
    }

    static void installEverywhere() {
        for (Window w : Window.getWindows()) {
            installUnder(w);
        }
    }

    /** Repairs every tab container under {@code root}; each one once. */
    static void installUnder(Component root) {
        if (root instanceof TabbedContainer tc) {
            install(tc);
        }
        if (root instanceof Container c) {
            for (Component child : c.getComponents()) {
                installUnder(child);
            }
        }
    }

    static void install(TabbedContainer tc) {
        if (tc.getClientProperty(INSTALLED) != null) {
            return;
        }
        tc.putClientProperty(INSTALLED, Boolean.TRUE);
        replaceContext(tc, new TabList(tc, tc.getAccessibleContext(), new ContainerTabs(tc)));
    }

    /**
     * Puts {@code context} where {@code c.getAccessibleContext()} reads it.
     * The field is {@code java.awt.Component}'s (the first cut looked it up
     * on {@code JComponent}, found nothing, and — falling back as designed —
     * repaired nothing, silently). {@code java.awt} is opened to the modules
     * by the launcher conf; a runtime that does not open it keeps the
     * platform's context, and the log says why.
     */
    static boolean replaceContext(Component c, AccessibleContext context) {
        try {
            Field field = Component.class.getDeclaredField("accessibleContext");
            field.setAccessible(true);
            field.set(c, context);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.log(Level.INFO, "a window tab container keeps the platform's accessible context,"
                    + " which exposes none of its windows to a screen reader", e);
            return false;
        }
    }

    /** What a tab list shows, read live: the real container, or a test's stand-in. */
    interface Tabs {
        int count();

        String title(int i);

        Component content(int i);

        int selected();

        void select(int i);

        Rectangle bounds(int i);

        Component host();
    }

    /** The platform's container as {@link Tabs}. */
    static final class ContainerTabs implements Tabs {

        private final TabbedContainer tc;

        ContainerTabs(TabbedContainer tc) {
            this.tc = tc;
        }

        @Override
        public int count() {
            return tc.getModel().size();
        }

        @Override
        public String title(int i) {
            TabData d = tc.getModel().getTab(i);
            return d == null ? "" : plain(d.getText());
        }

        @Override
        public Component content(int i) {
            TabData d = tc.getModel().getTab(i);
            return d == null ? null : d.getComponent();
        }

        @Override
        public int selected() {
            return tc.getSelectionModel().getSelectedIndex();
        }

        @Override
        public void select(int i) {
            if (i >= 0 && i < count()) {
                tc.getSelectionModel().setSelectedIndex(i);
            }
        }

        @Override
        public Rectangle bounds(int i) {
            return tc.getTabRect(i, new Rectangle());
        }

        @Override
        public Component host() {
            return tc;
        }
    }

    /**
     * A tab's title as words: the platform marks a modified file's tab up
     * in HTML ({@code <html><b>app.js}), which a screen reader would read
     * tag by tag.
     */
    static String plain(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length());
        boolean inTag = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '<') {
                inTag = true;
            } else if (c == '>' && inTag) {
                inTag = false;
            } else if (!inTag) {
                out.append(c);
            }
        }
        return out.toString().replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&nbsp;", " ").replace("&amp;", "&").strip();
    }

    /**
     * The container's context: a tab list whose children are its tabs. Name,
     * states, parent, bounds and hit-testing stay the platform's.
     */
    static final class TabList extends AccessibleContext implements AccessibleSelection {

        private final Accessible owner;
        private final AccessibleContext platform;
        private final Tabs tabs;
        /** One page per window, so a screen reader's place survives a re-query. */
        private final Map<Object, Page> pages = new WeakHashMap<>();

        TabList(Accessible owner, AccessibleContext platform, Tabs tabs) {
            this.owner = owner;
            this.platform = platform;
            this.tabs = tabs;
        }

        Page page(int i) {
            Component content = tabs.content(i);
            Object key = content != null ? content : tabs.title(i);
            return pages.computeIfAbsent(key, k -> new Page(this, content, tabs.title(i)));
        }

        int indexOf(Page p) {
            for (int i = 0; i < tabs.count(); i++) {
                Component c = tabs.content(i);
                if (p.content != null ? c == p.content : tabs.title(i).equals(p.fallbackTitle)) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public String getAccessibleName() {
            return platform.getAccessibleName();
        }

        @Override
        public String getAccessibleDescription() {
            return platform.getAccessibleDescription();
        }

        @Override
        public AccessibleRole getAccessibleRole() {
            return AccessibleRole.PAGE_TAB_LIST;
        }

        @Override
        public AccessibleStateSet getAccessibleStateSet() {
            return platform.getAccessibleStateSet();
        }

        @Override
        public Accessible getAccessibleParent() {
            return platform.getAccessibleParent();
        }

        @Override
        public int getAccessibleIndexInParent() {
            return platform.getAccessibleIndexInParent();
        }

        @Override
        public int getAccessibleChildrenCount() {
            return tabs.count();
        }

        @Override
        public Accessible getAccessibleChild(int i) {
            return i >= 0 && i < tabs.count() ? page(i) : null;
        }

        @Override
        public Locale getLocale() {
            return platform.getLocale();
        }

        @Override
        public AccessibleComponent getAccessibleComponent() {
            return platform.getAccessibleComponent();
        }

        @Override
        public AccessibleSelection getAccessibleSelection() {
            return this;
        }

        @Override
        public int getAccessibleSelectionCount() {
            int s = tabs.selected();
            return s >= 0 && s < tabs.count() ? 1 : 0;
        }

        @Override
        public Accessible getAccessibleSelection(int i) {
            int s = tabs.selected();
            return i == 0 && s >= 0 && s < tabs.count() ? page(s) : null;
        }

        @Override
        public boolean isAccessibleChildSelected(int i) {
            return i == tabs.selected();
        }

        @Override
        public void addAccessibleSelection(int i) {
            tabs.select(i);
        }

        @Override
        public void removeAccessibleSelection(int i) {
            // a tab list always shows one window; there is nothing to deselect to
        }

        @Override
        public void clearAccessibleSelection() {
            // as above
        }

        @Override
        public void selectAllAccessibleSelection() {
            // one window shows at a time
        }

        Accessible owner() {
            return owner;
        }

        Tabs tabs() {
            return tabs;
        }
    }

    /** One tab: titled as its tab is, selected when its window shows, the window its one child. */
    static final class Page implements Accessible {

        private final TabList list;
        final Component content;
        final String fallbackTitle;
        private AccessibleContext context;

        Page(TabList list, Component content, String fallbackTitle) {
            this.list = list;
            this.content = content;
            this.fallbackTitle = fallbackTitle;
        }

        @Override
        public AccessibleContext getAccessibleContext() {
            if (context == null) {
                context = new PageContext();
            }
            return context;
        }

        private int index() {
            return list.indexOf(this);
        }

        private final class PageContext extends AccessibleContext implements AccessibleComponent, AccessibleAction {

            @Override
            public String getAccessibleName() {
                int i = index();
                return i >= 0 ? list.tabs().title(i) : fallbackTitle;
            }

            @Override
            public AccessibleRole getAccessibleRole() {
                return AccessibleRole.PAGE_TAB;
            }

            @Override
            public AccessibleStateSet getAccessibleStateSet() {
                AccessibleStateSet s = new AccessibleStateSet();
                s.add(AccessibleState.ENABLED);
                s.add(AccessibleState.SELECTABLE);
                s.add(AccessibleState.VISIBLE);
                Component host = list.tabs().host();
                if (host != null && host.isShowing()) {
                    s.add(AccessibleState.SHOWING);
                }
                int i = index();
                if (i >= 0 && i == list.tabs().selected()) {
                    s.add(AccessibleState.SELECTED);
                }
                return s;
            }

            @Override
            public Accessible getAccessibleParent() {
                return list.owner();
            }

            @Override
            public int getAccessibleIndexInParent() {
                return index();
            }

            @Override
            public int getAccessibleChildrenCount() {
                return content instanceof Accessible ? 1 : 0;
            }

            @Override
            public Accessible getAccessibleChild(int i) {
                if (i != 0 || !(content instanceof Accessible a)) {
                    return null;
                }
                // the window's parent is this tab, as a JTabbedPane page's
                // component's is — a reader climbing up lands where it came in
                AccessibleContext ac = a.getAccessibleContext();
                if (ac != null && ac.getAccessibleParent() != Page.this) {
                    ac.setAccessibleParent(Page.this);
                }
                return a;
            }

            @Override
            public Locale getLocale() {
                return list.getLocale();
            }

            @Override
            public AccessibleComponent getAccessibleComponent() {
                return this;
            }

            @Override
            public AccessibleAction getAccessibleAction() {
                return this;
            }

            // ---- the tab as something to press ----

            @Override
            public int getAccessibleActionCount() {
                return 1;
            }

            @Override
            public String getAccessibleActionDescription(int i) {
                return i == 0 ? AccessibleAction.CLICK : null;
            }

            @Override
            public boolean doAccessibleAction(int i) {
                int index = index();
                if (i != 0 || index < 0) {
                    return false;
                }
                list.tabs().select(index);
                return true;
            }

            // ---- where the tab is ----

            private Rectangle rect() {
                int i = index();
                Rectangle r = i >= 0 ? list.tabs().bounds(i) : null;
                return r == null ? new Rectangle() : r;
            }

            @Override
            public Rectangle getBounds() {
                return rect();
            }

            @Override
            public Point getLocation() {
                return rect().getLocation();
            }

            @Override
            public Point getLocationOnScreen() {
                Component host = list.tabs().host();
                if (host == null || !host.isShowing()) {
                    return null;
                }
                Point p = host.getLocationOnScreen();
                Rectangle r = rect();
                return new Point(p.x + r.x, p.y + r.y);
            }

            @Override
            public Dimension getSize() {
                return rect().getSize();
            }

            @Override
            public boolean contains(Point p) {
                return rect().contains(p);
            }

            @Override
            public boolean isShowing() {
                Component host = list.tabs().host();
                return host != null && host.isShowing();
            }

            @Override
            public boolean isVisible() {
                return true;
            }

            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public Accessible getAccessibleAt(Point p) {
                return null;
            }

            @Override
            public boolean isFocusTraversable() {
                return false;
            }

            @Override
            public Color getBackground() {
                Component host = list.tabs().host();
                return host == null ? null : host.getBackground();
            }

            @Override
            public Color getForeground() {
                Component host = list.tabs().host();
                return host == null ? null : host.getForeground();
            }

            @Override
            public Cursor getCursor() {
                Component host = list.tabs().host();
                return host == null ? null : host.getCursor();
            }

            @Override
            public Font getFont() {
                Component host = list.tabs().host();
                return host == null ? null : host.getFont();
            }

            @Override
            public FontMetrics getFontMetrics(Font f) {
                Component host = list.tabs().host();
                return host == null || f == null ? null : host.getFontMetrics(f);
            }

            @Override
            public void setBackground(Color c) {
            }

            @Override
            public void setForeground(Color c) {
            }

            @Override
            public void setCursor(Cursor c) {
            }

            @Override
            public void setFont(Font f) {
            }

            @Override
            public void setEnabled(boolean b) {
            }

            @Override
            public void setVisible(boolean b) {
            }

            @Override
            public void setLocation(Point p) {
            }

            @Override
            public void setBounds(Rectangle r) {
            }

            @Override
            public void setSize(Dimension d) {
            }

            @Override
            public void requestFocus() {
                doAccessibleAction(0);
            }

            @Override
            public void addFocusListener(java.awt.event.FocusListener l) {
            }

            @Override
            public void removeFocusListener(java.awt.event.FocusListener l) {
            }
        }
    }

    /** The platform instantiates this through {@code @OnShowing}. */
    public WindowTabsAccessibility() {
    }
}
