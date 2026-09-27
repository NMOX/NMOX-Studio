package org.nmox.studio.ui.irc;

import java.awt.Point;
import java.util.function.BiConsumer;
import javax.swing.JPopupMenu;
import javax.swing.JTree;

/** Reaches the IRC window's package-private seams for the a11y tests. */
public final class IrcKeyboardProbe {

    private IrcKeyboardProbe() {
    }

    /** A built IRC window's network tree, its menus handed to {@code shower} instead of shown. */
    public static JTree treeWithShower(BiConsumer<JPopupMenu, Point> shower) {
        IrcTopComponent tc = new IrcTopComponent();
        tc.buildUiForTest();
        tc.treeMenuShower = shower;
        return tc.treeForTest();
    }

    public static String addNetworkLabel() {
        return Bundle.IrcTopComponent_addNetwork();
    }
}
