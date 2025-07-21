package org.nmox.studio.project;

import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import java.util.List;
import java.util.Arrays;

/**
 * Factory for creating project child nodes in the Project Explorer.
 */
public class ProjectChildFactory extends ChildFactory<String> {

    @Override
    protected boolean createKeys(List<String> toPopulate) {
        // For now, add placeholder projects
        toPopulate.addAll(Arrays.asList("Sample Project", "Media Project"));
        return true;
    }

    @Override
    protected Node createNodeForKey(String key) {
        return new AbstractNode(Children.LEAF) {
            @Override
            public String getDisplayName() {
                return key;
            }
            
            @Override
            public String getShortDescription() {
                return "NMOX Studio project: " + key;
            }
        };
    }
}