package org.nmox.studio.ui.browser.fx.order;

/** Stands in for NodeHelper: its initializer forces the node class to initialize. */
public class PairHelper {

    static {
        PairGate.helperBegan();
        PairNode.touch();
    }

    static void setAccessor(String accessor) {
    }
}
