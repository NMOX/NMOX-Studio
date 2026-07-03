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
 */
final class RequestTreeRenderer extends DefaultTreeCellRenderer {

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
