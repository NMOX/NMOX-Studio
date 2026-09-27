package org.nmox.studio.ui.a11y;

import java.awt.Component;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleState;
import javax.swing.JButton;
import javax.swing.JPanel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A window-system tab container exposes its windows to a screen reader the
 * way a tab list must: one tab per window, the showing one selected, the
 * window the tab's one child. Before 3.4 the platform's container reported
 * a tab list with no tabs, and macOS exposed none of its content.
 */
class WindowTabsAccessibilityTest {

    /** A stand-in for the platform's container. */
    static final class FakeTabs implements WindowTabsAccessibility.Tabs {
        final List<String> titles = new ArrayList<>();
        final List<Component> contents = new ArrayList<>();
        int selected = -1;
        final JPanel host = new JPanel();

        FakeTabs add(String title, Component content) {
            titles.add(title);
            contents.add(content);
            if (selected < 0) {
                selected = 0;
            }
            return this;
        }

        @Override
        public int count() {
            return titles.size();
        }

        @Override
        public String title(int i) {
            return WindowTabsAccessibility.plain(titles.get(i));
        }

        @Override
        public Component content(int i) {
            return contents.get(i);
        }

        @Override
        public int selected() {
            return selected;
        }

        @Override
        public void select(int i) {
            selected = i;
        }

        @Override
        public Rectangle bounds(int i) {
            return new Rectangle(i * 80, 0, 80, 22);
        }

        @Override
        public Component host() {
            return host;
        }

        Runnable changed = () -> { };

        @Override
        public void onChange(Runnable changed) {
            this.changed = changed;
        }
    }

    private static WindowTabsAccessibility.TabList list(FakeTabs tabs) {
        return new WindowTabsAccessibility.TabList(tabs.host, tabs.host.getAccessibleContext(), tabs);
    }

    private static JPanel window(String button) {
        JPanel p = new JPanel();
        p.add(new JButton(button));
        return p;
    }

    @Test
    @DisplayName("a tab list's children are its tabs, each a PAGE_TAB titled as its tab is, the showing one selected")
    void tabsAreChildren() {
        FakeTabs tabs = new FakeTabs().add("Welcome", window("Task Rack")).add("<html><b>app.js</b>", window("x"));
        tabs.selected = 1;
        AccessibleContext ctx = list(tabs);
        assertThat(ctx.getAccessibleRole()).isEqualTo(AccessibleRole.PAGE_TAB_LIST);
        assertThat(ctx.getAccessibleChildrenCount()).isEqualTo(2);
        AccessibleContext welcome = ctx.getAccessibleChild(0).getAccessibleContext();
        AccessibleContext app = ctx.getAccessibleChild(1).getAccessibleContext();
        assertThat(welcome.getAccessibleRole()).isEqualTo(AccessibleRole.PAGE_TAB);
        assertThat(welcome.getAccessibleName()).isEqualTo("Welcome");
        assertThat(app.getAccessibleName()).as("a modified file's tab is read as words, not tags").isEqualTo("app.js");
        assertThat(app.getAccessibleStateSet().contains(AccessibleState.SELECTED)).isTrue();
        assertThat(welcome.getAccessibleStateSet().contains(AccessibleState.SELECTED)).isFalse();
    }

    @Test
    @DisplayName("each tab's one child is its window, whose parent is then that tab")
    void windowIsTheTabsChild() {
        JPanel welcome = window("Task Rack");
        FakeTabs tabs = new FakeTabs().add("Welcome", welcome);
        AccessibleContext ctx = list(tabs);
        Accessible page = ctx.getAccessibleChild(0);
        AccessibleContext pageCtx = page.getAccessibleContext();
        assertThat(pageCtx.getAccessibleChildrenCount()).isEqualTo(1);
        assertThat(pageCtx.getAccessibleChild(0)).isSameAs(welcome);
        assertThat(welcome.getAccessibleContext().getAccessibleParent()).isSameAs(page);
        assertThat(pageCtx.getAccessibleParent()).isSameAs(tabs.host);
        assertThat(pageCtx.getAccessibleIndexInParent()).isZero();
    }

    @Test
    @DisplayName("a tab keeps its identity across queries, and follows its window when tabs reorder")
    void stableIdentity() {
        JPanel a = window("a");
        JPanel b = window("b");
        FakeTabs tabs = new FakeTabs().add("A", a).add("B", b);
        AccessibleContext ctx = list(tabs);
        Accessible pageB = ctx.getAccessibleChild(1);
        assertThat(ctx.getAccessibleChild(1)).isSameAs(pageB);
        tabs.titles.add(0, tabs.titles.remove(1));
        tabs.contents.add(0, tabs.contents.remove(1));
        assertThat(ctx.getAccessibleChild(0)).isSameAs(pageB);
        assertThat(pageB.getAccessibleContext().getAccessibleIndexInParent()).isZero();
    }

    @Test
    @DisplayName("pressing a tab, or selecting it through the list, shows its window")
    void pressSelects() {
        FakeTabs tabs = new FakeTabs().add("A", window("a")).add("B", window("b"));
        AccessibleContext ctx = list(tabs);
        AccessibleAction press = ctx.getAccessibleChild(1).getAccessibleContext().getAccessibleAction();
        assertThat(press.getAccessibleActionCount()).isEqualTo(1);
        assertThat(press.doAccessibleAction(0)).isTrue();
        assertThat(tabs.selected).isEqualTo(1);
        ctx.getAccessibleSelection().addAccessibleSelection(0);
        assertThat(tabs.selected).isZero();
        assertThat(ctx.getAccessibleSelection().getAccessibleSelectionCount()).isEqualTo(1);
        assertThat(ctx.getAccessibleSelection().isAccessibleChildSelected(0)).isTrue();
    }

    @Test
    @DisplayName("name, states and bounds stay the platform's; an empty container has no children")
    void delegatesTheRest() {
        FakeTabs tabs = new FakeTabs();
        tabs.host.getAccessibleContext().setAccessibleName("Editor tabs");
        AccessibleContext ctx = list(tabs);
        assertThat(ctx.getAccessibleName()).isEqualTo("Editor tabs");
        assertThat(ctx.getAccessibleComponent()).isSameAs(tabs.host.getAccessibleContext().getAccessibleComponent());
        assertThat(ctx.getAccessibleChildrenCount()).isZero();
        assertThat(ctx.getAccessibleChild(0)).isNull();
        assertThat(ctx.getAccessibleSelection().getAccessibleSelectionCount()).isZero();
    }

    @Test
    @DisplayName("a tab's bounds are its tab's rectangle")
    void bounds() {
        FakeTabs tabs = new FakeTabs().add("A", window("a")).add("B", window("b"));
        AccessibleContext ctx = list(tabs);
        assertThat(ctx.getAccessibleChild(1).getAccessibleContext().getAccessibleComponent().getBounds())
                .isEqualTo(new Rectangle(80, 0, 80, 22));
    }

    @Test
    @DisplayName("titles lose their markup and keep their words")
    void plainTitles() {
        assertThat(WindowTabsAccessibility.plain("<html><font color=\"#888\">app.js</font>")).isEqualTo("app.js");
        assertThat(WindowTabsAccessibility.plain("a &amp; b")).isEqualTo("a & b");
        assertThat(WindowTabsAccessibility.plain("x &lt;y&gt;")).isEqualTo("x <y>");
        assertThat(WindowTabsAccessibility.plain(null)).isEmpty();
    }

    @Test
    @DisplayName("a closed window is not kept alive by the tab it once had (the review's leak)")
    void closedWindowIsCollectable() throws Exception {
        FakeTabs tabs = new FakeTabs().add("keep", window("k"));
        tabs.add("closed", window("c"));
        AccessibleContext ctx = list(tabs);
        ctx.getAccessibleChild(1).getAccessibleContext().getAccessibleChild(0); // a reader walked it
        java.lang.ref.WeakReference<Component> closed = new java.lang.ref.WeakReference<>(tabs.contents.get(1));
        tabs.titles.remove(1);
        tabs.contents.remove(1);
        for (int i = 0; i < 50 && closed.get() != null; i++) {
            System.gc();
            Thread.sleep(20);
        }
        assertThat(closed.get()).as("the closed window was collected").isNull();
        assertThat(ctx.getAccessibleChildrenCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("a switch is announced as JTabbedPane announces it: visible data, then the selected tab")
    void switchIsAnnounced() {
        FakeTabs tabs = new FakeTabs().add("A", window("a")).add("B", window("b"));
        AccessibleContext ctx = list(tabs);
        List<String> heard = new ArrayList<>();
        ctx.addPropertyChangeListener(e -> heard.add(e.getPropertyName() + "=" + (e.getNewValue() instanceof Accessible a
                ? a.getAccessibleContext().getAccessibleName() : e.getNewValue())));
        tabs.selected = 1;
        tabs.changed.run();
        assertThat(heard).containsExactly(
                AccessibleContext.ACCESSIBLE_VISIBLE_DATA_PROPERTY + "=true",
                AccessibleContext.ACCESSIBLE_SELECTION_PROPERTY + "=B");
    }

    @Test
    @DisplayName("the install really replaces a component's context (the first cut looked on the wrong class and repaired nothing)")
    void replaceContextReachesTheComponent() {
        JPanel host = new JPanel();
        FakeTabs tabs = new FakeTabs().add("A", window("a"));
        WindowTabsAccessibility.TabList list = new WindowTabsAccessibility.TabList(host, host.getAccessibleContext(), tabs);
        assertThat(WindowTabsAccessibility.replaceContext(host, list)).isTrue();
        assertThat(host.getAccessibleContext()).isSameAs(list);
        assertThat(host.getAccessibleContext().getAccessibleChildrenCount()).isEqualTo(1);
    }
}
