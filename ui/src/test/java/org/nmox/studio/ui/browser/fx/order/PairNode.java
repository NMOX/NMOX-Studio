package org.nmox.studio.ui.browser.fx.order;

/** Stands in for javafx.scene.Node: its initializer needs its helper. */
public class PairNode {

    static {
        PairGate.nodeBegan();
        PairHelper.setAccessor("node");
    }

    static void touch() {
    }
}
