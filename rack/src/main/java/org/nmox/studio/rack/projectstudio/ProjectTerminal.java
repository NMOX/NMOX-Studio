package org.nmox.studio.rack.projectstudio;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import javax.swing.Action;
import org.openide.awt.ActionID;
import org.openide.awt.ActionRegistration;
import org.openide.awt.Actions;
import org.openide.awt.StatusDisplayer;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.nodes.Node;
import org.openide.util.ContextAwareAction;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.Lookups;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * The terminal, the way a VS Code user's ⌃` finds it (3.1.0): the first
 * press opens a shell IN THE AIMED PROJECT, and every later press brings
 * the open terminal forward instead of spawning another. Project Studio's
 * Terminal button rides the same code, so the chord and the button can
 * never disagree about where a shell starts.
 *
 * <p>The platform's own terminal action starts a shell in the IDE's
 * working directory - {@code $HOME} from a terminal launch, and before the
 * 3.1.0 launcher fix {@code /} for an app started from Finder. The terminal
 * module's context-aware {@code OpenInTerminalAction} takes a directory
 * from the node it is given, so the aimed project's folder node is handed
 * to it; when that module is absent or declines, the plain action runs and
 * the status line says where the other door is.
 *
 * <p>After a re-aim (3.2.0, ledger 120): ⌃` remembers the project it last
 * started a shell in, and when the aim has moved since, it starts a new
 * shell in the NEW project beside the old one rather than bringing forward
 * a terminal whose prompt sits in a folder the user has left - the VS Code
 * habit this chord comes from keeps one terminal per window, and a window
 * there IS a folder. A terminal ⌃` did not start (the platform's own menu,
 * the Terminal button) is simply brought forward: where it sits is not
 * known. And when a shell could not be started in the project, the status
 * line says so rather than leaving a home-folder prompt to be mistaken for
 * the project's.
 */
@ActionID(category = "Window", id = "org.nmox.studio.rack.projectstudio.ProjectTerminalAction")
@ActionRegistration(displayName = "#CTL_ProjectTerminalAction")
@Messages({
    "CTL_ProjectTerminalAction=Terminal in Project",
    "# {0} - the project folder's name",
    "ProjectTerminal_notInProject=The terminal could not start in {0}, so this one starts in your home folder"
})
public final class ProjectTerminal implements ActionListener {

    /**
     * The platform's "open a terminal in this folder" action, by the id it is
     * registered under in the assembled cluster. Until 3.1.0 this code asked
     * for {@code Tools/org.netbeans.modules.terminal.nodes.OpenInTerminalAction},
     * an id that does not exist, so every "terminal in the project" since
     * 1.212.0 silently fell back to a shell in the IDE's own directory.
     * {@code TerminalActionIdsTest} holds these ids against the cluster.
     */
    static final String OPEN_IN_CATEGORY = "Window";
    static final String OPEN_IN_ID = "org.netbeans.modules.dlight.terminal.action.OpenInTerminalAction";
    /** The plain local terminal, the fallback. */
    static final String LOCAL_CATEGORY = "Window";
    static final String LOCAL_ID = "LocalTerminalAction";

    /** The platform terminal container's window id (dlight.terminal, RELEASE310). */
    static final String CONTAINER_ID = "TerminalContainerTopComponent";

    /** What a press does. */
    enum Choice {
        /** A terminal is already open: bring it forward, start nothing. */
        FOCUS_EXISTING,
        /** Start a shell in the aimed project's folder. */
        OPEN_IN_PROJECT,
        /** No project to start in: the platform's plain terminal. */
        OPEN_PLAIN
    }

    /** The project ⌃` last started a shell in; null until it has (an AtomicReference: written from the action instance). */
    private static final java.util.concurrent.atomic.AtomicReference<File> LAST_STARTED_IN =
            new java.util.concurrent.atomic.AtomicReference<>();

    /** The rule, pure: an open terminal wins, then the aimed project, then the plain terminal. */
    static Choice decide(boolean terminalOpen, File projectDir) {
        return decide(terminalOpen, projectDir, null);
    }

    /**
     * The rule with the re-aim (3.2.0): an open terminal is brought forward
     * unless ⌃` started it in a project the aim has since left, in which
     * case a shell starts in the aimed project.
     */
    static Choice decide(boolean terminalOpen, File projectDir, File startedIn) {
        boolean inProject = projectDir != null && projectDir.isDirectory();
        if (terminalOpen && !(inProject && startedIn != null && !startedIn.equals(projectDir))) {
            return Choice.FOCUS_EXISTING;
        }
        return inProject ? Choice.OPEN_IN_PROJECT : Choice.OPEN_PLAIN;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        TopComponent container = WindowManager.getDefault().findTopComponent(CONTAINER_ID);
        boolean open = container != null && container.isOpened();
        File project = aimedProject();
        switch (decide(open, project, LAST_STARTED_IN.get())) {
            case FOCUS_EXISTING -> {
                container.requestActive();
            }
            case OPEN_IN_PROJECT -> {
                if (openIn(project, e.getSource())) {
                    LAST_STARTED_IN.set(project);
                } else {
                    openPlain(e.getSource());
                    StatusDisplayer.getDefault().setStatusText(org.nmox.studio.core.util.PlainStatus.text(
                            Bundle.ProjectTerminal_notInProject(project.getName())));
                }
            }
            default -> openPlain(e.getSource());
        }
    }

    /** Project Studio's Terminal button: always a new shell, in the project when it can be. */
    static void openNew(Object source) {
        File dir = aimedProject();
        if (decide(false, dir) == Choice.OPEN_IN_PROJECT) {
            if (openIn(dir, source)) {
                return;
            }
            openPlain(source);
            StatusDisplayer.getDefault().setStatusText(org.nmox.studio.core.util.PlainStatus.text(
                    Bundle.ProjectTerminal_notInProject(dir.getName())));
            return;
        }
        openPlain(source);
    }

    private static File aimedProject() {
        try {
            return org.nmox.studio.rack.service.RackService.getDefault().getRack().getProjectDir();
        } catch (RuntimeException noRack) {
            return null;
        }
    }

    /**
     * Hands the terminal module's context-aware action the folder's node so
     * the shell starts THERE. False when the action is absent or declines.
     */
    private static boolean openIn(File dir, Object source) {
        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(dir));
        if (fo == null) {
            return false;
        }
        try {
            Node node = DataObject.find(fo).getNodeDelegate();
            Action action = Actions.forID(OPEN_IN_CATEGORY, OPEN_IN_ID);
            if (action == null) {
                return false;
            }
            if (action instanceof ContextAwareAction ctx) {
                action = ctx.createContextAwareInstance(Lookups.singleton(node));
            }
            if (!action.isEnabled()) {
                return false;
            }
            action.actionPerformed(new ActionEvent(source, 0, "open"));
            return true;
        } catch (org.openide.loaders.DataObjectNotFoundException | RuntimeException notUsable) {
            return false;
        }
    }

    /**
     * The platform's own terminal, wherever it starts; the status line names
     * the menu door otherwise. The id is the one ActionIdsResolveTest checks
     * against the assembled cluster, so there is no guessing by name here.
     */
    private static void openPlain(Object source) {
        Action action = Actions.forID(LOCAL_CATEGORY, LOCAL_ID);
        if (action != null) {
            action.actionPerformed(new ActionEvent(source, 0, "open"));
            return;
        }
        StatusDisplayer.getDefault().setStatusText(Bundle.ProjectStudioTopComponent_terminalFallbackStatus());
    }
}
