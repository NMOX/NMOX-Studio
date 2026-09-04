package org.nmox.studio.tools.npm;

import java.awt.event.ActionEvent;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.SwingUtilities;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.awt.StatusDisplayer;
import org.openide.util.NbBundle.Messages;

/**
 * The ■ beside the toolbar's ▶ (v2.69.10). Enabled while the IDE's own Run/
 * Build/Test/Clean has something running; pressing it kills every live one
 * (process tree) and says what it stopped on the status line. Eager, so the
 * toolbar button tracks {@link LiveRuns} from boot. The platform's own
 * Run ▸ Stop Build/Run also knows these runs (BuildExecutionSupport).
 */
@ActionID(category = "Run", id = "org.nmox.studio.tools.npm.StopRunAction")
@ActionRegistration(displayName = "#CTL_StopRun", iconBase = "org/nmox/studio/tools/npm/stop.png", lazy = false)
@ActionReference(path = "Toolbars/Build", position = 360)
@Messages("CTL_StopRun=Stop Running Command")
public final class StopRunAction extends AbstractAction {

    public StopRunAction() {
        super(Bundle.CTL_StopRun());
        putValue(SHORT_DESCRIPTION, Bundle.CTL_StopRun());
        putValue("iconBase", "org/nmox/studio/tools/npm/stop.png");
        setEnabled(!LiveRuns.live().isEmpty());
        LiveRuns.addListener(() -> SwingUtilities.invokeLater(
                () -> setEnabled(!LiveRuns.live().isEmpty())));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        StatusDisplayer.getDefault().setStatusText(LiveRuns.stoppedMessage(LiveRuns.stopAll()));
    }
}
