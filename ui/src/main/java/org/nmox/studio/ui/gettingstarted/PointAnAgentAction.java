package org.nmox.studio.ui.gettingstarted;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.Action;
import org.openide.awt.ActionID;
import org.openide.awt.ActionRegistration;
import org.openide.awt.Actions;
import org.openide.awt.StatusDisplayer;
import org.openide.util.NbBundle.Messages;

/**
 * The Welcome's door to the Agent Port (v2.84.0): the FIRST STEPS row
 * "Point an agent at the IDE" needs an action id in THIS module's layer
 * (the door gate reads it), while the port itself lives in the rack
 * module. So this thin action resolves the rack's action by id at click
 * time — the soft-dependency idiom — and says so on the status line when
 * the rack is not installed rather than doing nothing (a dead row was
 * the v2.69.9 find). It carries no menu reference: the menu item is the
 * rack's own.
 */
@ActionID(category = "Tools", id = "org.nmox.studio.ui.gettingstarted.PointAnAgentAction")
@ActionRegistration(displayName = "#CTL_PointAnAgentAction", lazy = true)
@Messages({
    "CTL_PointAnAgentAction=Point an agent at the IDE",
    "MSG_PointAnAgent_NoRack=The Agent Port lives in the rack module, which is not installed."
})
public final class PointAnAgentAction implements ActionListener {

    static final String RACK_CATEGORY = "Tools";
    static final String RACK_ACTION_ID = "org.nmox.studio.rack.mcp.AgentPortAction";

    /** How the rack's action is found; production is the platform registry, tests hand in an answer. */
    interface Resolver {
        Action resolve(String category, String id);
    }

    private final Resolver resolver;

    public PointAnAgentAction() {
        this(Actions::forID);
    }

    PointAnAgentAction(Resolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Action port = resolver.resolve(RACK_CATEGORY, RACK_ACTION_ID);
        if (port == null) {
            StatusDisplayer.getDefault().setStatusText(Bundle.MSG_PointAnAgent_NoRack());
            return;
        }
        port.actionPerformed(e);
    }
}
