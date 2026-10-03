package org.nmox.studio.ui.keymap;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.Action;
import org.openide.awt.ActionID;
import org.openide.awt.ActionRegistration;
import org.openide.awt.Actions;
import org.openide.awt.StatusDisplayer;
import org.openide.util.NbBundle.Messages;

/**
 * F5 as VS Code means it: <i>Start Debugging</i> when nothing is being
 * debugged, <i>Continue</i> when the program is paused.
 *
 * <p>The platform has the two halves as two actions on two chords (Continue
 * on F5, Debug Project on Ctrl+F5), and in the VS Code keymap profile one key
 * has to be both. Which half applies is asked of the platform's own actions,
 * so this module needs no debugger API: <i>Continue</i> is enabled exactly
 * while a session is paused, and <i>Finish Debugger Session</i> exactly
 * while a session exists ({@code DebuggerAction} follows the current
 * engine's {@code ActionsManager}). A program that is running and not paused
 * has nothing to continue and nothing to start; VS Code's F5 does nothing
 * there, and here the status line says why.
 *
 * <p>Start is the action Quick Search already answers <i>Debug: Start
 * Debugging</i> with, Debug Project. Bound to F5 by the VS Code profile
 * alone ({@code scripts/vscode-keymap/chords.txt}); every other profile
 * keeps its own F5.
 */
@ActionID(category = "Debug", id = "org.nmox.studio.ui.keymap.StartOrContinueDebuggingAction")
@ActionRegistration(displayName = "#CTL_StartOrContinueDebuggingAction", lazy = true)
@Messages({
    "CTL_StartOrContinueDebuggingAction=Start Debugging or Continue",
    "StartOrContinueDebugging_running=The program is running. It can be continued once it has paused at a breakpoint.",
    "StartOrContinueDebugging_nothing=Nothing to debug: open a project first, then start debugging."
})
public final class StartOrContinueDebuggingAction implements ActionListener {

    enum Step { CONTINUE, START, RUNNING, NOTHING }

    /**
     * What one press does, from what the platform's actions say: continue a
     * paused session; else leave a running one alone; else start, where
     * there is something to start.
     */
    static Step decide(boolean canContinue, boolean sessionAlive, boolean canStart) {
        if (canContinue) {
            return Step.CONTINUE;
        }
        if (sessionAlive) {
            return Step.RUNNING;
        }
        return canStart ? Step.START : Step.NOTHING;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Action resume = Actions.forID("Debug", "org.netbeans.modules.debugger.ui.actions.ContinueAction");
        Action finish = Actions.forID("Debug", "org.netbeans.modules.debugger.ui.actions.KillAction");
        Action start = Actions.forID("Debug", "org.netbeans.modules.debugger.ui.actions.DebugMainProjectAction");
        switch (decide(enabled(resume), enabled(finish), enabled(start))) {
            case CONTINUE -> resume.actionPerformed(e);
            case START -> start.actionPerformed(e);
            case RUNNING -> StatusDisplayer.getDefault().setStatusText(Bundle.StartOrContinueDebugging_running());
            case NOTHING -> StatusDisplayer.getDefault().setStatusText(Bundle.StartOrContinueDebugging_nothing());
        }
    }

    private static boolean enabled(Action action) {
        return action != null && action.isEnabled();
    }
}
