package org.nmox.studio.apiclient.ui;

import java.awt.Component;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import org.nmox.studio.apiclient.model.ApiModel.Collection;
import org.nmox.studio.apiclient.model.ApiModel.Request;

/**
 * Renders the collections tree: a collection by name, a request as
 * "METHOD name" so the verb is visible at a glance.
 *
 * <p>Those names are EXTERNAL text — a collection imported from
 * Postman, a HAR capture, or an OpenAPI spec names its own requests —
 * so the renderer disables HTML at construction (v1.311.0). Without it
 * a request named {@code <html><img src="http://…">} would render as
 * markup and make the IDE's own JVM fetch that URL at paint time: the
 * v1.208.0 class, swept through tables in v1.306.0 and the IRC client
 * in v1.307.0, and standing open in the cell renderers those sweeps
 * enumerated past.
 */
final class RequestTreeRenderer extends DefaultTreeCellRenderer {

    RequestTreeRenderer() {
        // set BEFORE any setText — BasicHTML installs the view on the text
        // change, so disabling afterwards cannot undo it (the v1.307.0 trap)
        org.nmox.studio.core.util.PlainTables.plain(this);
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
            boolean expanded, boolean leaf, int row, boolean focus) {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, focus);
        Object obj = value instanceof DefaultMutableTreeNode n ? n.getUserObject() : null;
        if (obj instanceof Collection c) {
            setText(c.name);
        } else if (obj instanceof Request r) {
            setText(r.method + "  " + r.name);
        }
        return this;
    }
}
