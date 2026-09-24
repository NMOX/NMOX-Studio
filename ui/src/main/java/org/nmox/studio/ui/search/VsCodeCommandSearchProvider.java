package org.nmox.studio.ui.search;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Action;
import javax.swing.KeyStroke;
import org.netbeans.spi.quicksearch.SearchProvider;
import org.netbeans.spi.quicksearch.SearchRequest;
import org.netbeans.spi.quicksearch.SearchResponse;
import org.nmox.studio.core.search.SearchTerms;
import org.nmox.studio.core.util.PlainText;
import org.openide.awt.Actions;
import org.openide.util.NbBundle.Messages;

/**
 * Quick Search answers in VS Code's words (3.1.0). Someone who switched
 * types the command-palette title their hands know - "Toggle Terminal",
 * "Git: Commit", "Open Settings" - and the platform's own actions provider
 * matches the whole query as ONE substring of an action's name (read from
 * {@code ActionsSearchProvider}'s bytecode), so "toggle terminal" finds
 * nothing and "settings" finds nothing where the action is called
 * Options. Each row here is a VS Code title and the action that does the
 * same thing here; a hit shows both, so the next time the NMOX name is the
 * one they type.
 *
 * <p>Only rows whose action is registered and enabled are offered, as the
 * platform's provider does: a row that ran nothing would be the silent
 * kind of failure. Every {@code cmd(...)} names an action id that
 * {@code ActionIdsResolveTest} checks against the assembled cluster.
 */
@Messages({
    "# {0} - the VS Code command-palette title, as VS Code writes it",
    "# {1} - the NMOX Studio action that does the same thing, in the UI's language",
    "VsCodeCommand_row={0} — {1}"
})
public class VsCodeCommandSearchProvider implements SearchProvider {

    /** A VS Code command-palette title and the action that answers it here. */
    record Cmd(String title, String category, String id) {
    }

    private static Cmd cmd(String title, String category, String id) {
        return new Cmd(title, category, id);
    }

    /**
     * The category of a row that runs an action of the focused editor's own
     * kit, named as the kit names it ({@code BaseKit}/{@code ExtKit}
     * constants, read from RELEASE310's editor-lib) - not an id in the
     * Actions folder, so {@code ActionIdsResolveTest} has nothing to check.
     */
    static final String EDITOR_KIT = "editor-kit";

    private static Cmd inEditor(String title, String kitAction) {
        return new Cmd(title, EDITOR_KIT, kitAction);
    }

    /** VS Code's titles, category prefix included, as its palette shows them. */
    static final List<Cmd> COMMANDS = List.of(
            cmd("File: New File", "Project", "org.netbeans.modules.project.ui.NewFile"),
            cmd("File: Open Folder...", "File", "org.nmox.studio.ui.actions.OpenFolderAction"),
            cmd("File: Open Recent...", "File", "org.nmox.studio.ui.actions.SwitchProjectAction"),
            cmd("File: Save All", "System", "org.openide.actions.SaveAllAction"),
            cmd("Go to File...", "Tools", "org.netbeans.modules.jumpto.file.FileSearchAction"),
            cmd("Go to Symbol in Workspace...", "Edit", "org.netbeans.modules.jumpto.symbol.GoToSymbol"),
            cmd("Search: Find in Files", "Edit", "org.netbeans.modules.search.FindInFilesAction"),
            cmd("Search: Replace in Files", "Edit", "org.netbeans.modules.search.ReplaceInFilesAction"),
            cmd("Preferences: Open Settings", "Window", "org.netbeans.modules.options.OptionsWindowAction"),
            cmd("Preferences: Open Keyboard Shortcuts", "Help", "org.nmox.studio.ui.shortcuts.KeyboardShortcutsAction"),
            cmd("Extensions: Install Extensions", "System", "org.netbeans.modules.autoupdate.ui.actions.PluginManagerAction"),
            cmd("View: Toggle Terminal", "Window", "org.nmox.studio.rack.projectstudio.ProjectTerminalAction"),
            cmd("Terminal: Create New Terminal", "Window", "org.nmox.studio.rack.projectstudio.ProjectTerminalAction"),
            cmd("View: Show Explorer", "Window", "org.nmox.studio.rack.projectstudio.ProjectStudioTopComponent"),
            cmd("View: Toggle Problems", "Window", "org.netbeans.modules.tasklist.ui.TaskListAction"),
            cmd("View: Toggle Output", "Window", "org.netbeans.core.io.ui.IOWindowAction"),
            cmd("View: Toggle Full Screen", "Window", "org.netbeans.core.windows.actions.ToggleFullScreenAction"),
            cmd("View: Toggle Minimap", "View", "org.nmox.studio.editor.minimap.ToggleMinimapAction"),
            cmd("View: Toggle Sticky Scroll", "View", "org.nmox.studio.editor.sticky.ToggleStickyScrollAction"),
            cmd("View: Close All Editors", "Window", "org.netbeans.core.windows.actions.CloseAllDocumentsAction"),
            cmd("View: Close Other Editors", "Window", "org.netbeans.core.windows.actions.CloseAllButThisAction"),
            cmd("Developer: Toggle Screencast Mode", "View", "org.nmox.studio.editor.present.ShowKeystrokesAction"),
            cmd("Rename Symbol", "Refactoring", "org.netbeans.modules.refactoring.api.ui.RenameAction"),
            cmd("Tasks: Run Build Task", "Project", "org.netbeans.modules.project.ui.BuildMainProject"),
            cmd("Tasks: Terminate Task", "Run", "org.nmox.studio.tools.npm.StopRunAction"),
            cmd("Debug: Start Debugging", "Debug", "org.netbeans.modules.debugger.ui.actions.DebugMainProjectAction"),
            cmd("Debug: Start Without Debugging", "Project", "org.netbeans.modules.project.ui.RunMainProject"),
            cmd("Debug: Toggle Breakpoint", "Debug", "org.netbeans.modules.debugger.ui.actions.ToggleBreakpointAction"),
            cmd("Debug: Continue", "Debug", "org.netbeans.modules.debugger.ui.actions.ContinueAction"),
            cmd("Debug: Stop", "Debug", "org.netbeans.modules.debugger.ui.actions.KillAction"),
            cmd("Debug: Step Over", "Debug", "org.netbeans.modules.debugger.ui.actions.StepOverAction"),
            cmd("Debug: Step Into", "Debug", "org.netbeans.modules.debugger.ui.actions.StepIntoAction"),
            cmd("Debug: Step Out", "Debug", "org.netbeans.modules.debugger.ui.actions.StepOutAction"),
            cmd("Git: Clone", "Git", "org.netbeans.modules.git.ui.clone.CloneAction"),
            cmd("Git: Commit", "Git", "org.netbeans.modules.git.ui.commit.CommitAction"),
            cmd("Git: Pull", "Git", "org.netbeans.modules.git.ui.fetch.PullAction"),
            cmd("Git: Push", "Git", "org.netbeans.modules.git.ui.push.PushAction"),
            cmd("Git: Fetch", "Git", "org.netbeans.modules.git.ui.fetch.FetchAction"),
            cmd("Git: Checkout to...", "Git", "org.netbeans.modules.git.ui.checkout.SwitchBranchAction"),
            cmd("Git: Create Branch...", "Git", "org.netbeans.modules.git.ui.branch.CreateBranchAction"),
            cmd("Help: Welcome", "Window", "org.nmox.studio.ui.MainWindow"),
            cmd("Help: Show Release Notes", "Help", "org.nmox.studio.ui.whatsnew.WhatsNewAction"),
            cmd("Help: Report Issue...", "Help", "org.nmox.studio.ui.report.ReportProblemAction"),
            inEditor("Format Document", "format"),
            inEditor("Toggle Line Comment", "toggle-comment"),
            inEditor("Go to Line/Column...", "goto"),
            inEditor("Go to Definition", "goto-declaration"),
            inEditor("Delete Line", "remove-line"),
            inEditor("Move Line Up", "move-selection-else-line-up"),
            inEditor("Move Line Down", "move-selection-else-line-down"),
            inEditor("Copy Line Up", "copy-selection-else-line-up"),
            inEditor("Copy Line Down", "copy-selection-else-line-down"),
            inEditor("Trim Trailing Whitespace", "remove-trailing-spaces"));

    /** Resolves an action by id; a seam so the matching is testable off the platform. */
    interface Resolver {
        Action find(String category, String id);
    }

    /** One hit, before it reaches the platform's response. */
    record Hit(Cmd cmd, Action action, String label, List<KeyStroke> shortcut) {
    }

    @Override
    public void evaluate(SearchRequest request, SearchResponse response) {
        for (Hit h : hits(request.getText(), VsCodeCommandSearchProvider::resolve)) {
            if (!response.addResult(runner(h), PlainText.escape(h.label), null, h.shortcut)) {
                return;
            }
        }
    }

    /**
     * The rows {@code query} matches, in table order, each with its action
     * and the label shown: VS Code's title, then the action's own name
     * (plain text; {@link #evaluate} escapes it where it meets the
     * platform's HTML renderer). A row whose action is missing or disabled
     * is left out.
     */
    static List<Hit> hits(String query, Resolver resolver) {
        List<Hit> out = new ArrayList<>();
        if (query == null || query.strip().length() < 2) {
            return out;
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Cmd c : COMMANDS) {
            if (!SearchTerms.matches(query, c.title())) {
                continue;
            }
            Action a = resolver.find(c.category(), c.id());
            if (a == null || !a.isEnabled() || !seen.add(c.title())) {
                continue;
            }
            String label = Bundle.VsCodeCommand_row(c.title(), nameOf(a));
            Object key = a.getValue(Action.ACCELERATOR_KEY);
            List<KeyStroke> shortcut = key instanceof KeyStroke ks ? List.of(ks) : List.of();
            out.add(new Hit(c, a, label, shortcut));
        }
        return out;
    }

    /** An Actions-folder id, or for {@link #EDITOR_KIT} the focused editor's own action. */
    static Action resolve(String category, String id) {
        return EDITOR_KIT.equals(category) ? editorAction(id) : Actions.forID(category, id);
    }

    /**
     * The activated editor's kit action {@code name}, bound to its pane, or
     * null when no editor is activated or its kit has no such action. The
     * panes are asked on the EDT, as {@code EditorCookie} requires; the
     * search itself runs on a background lane.
     */
    static Action editorAction(String name) {
        return org.openide.util.Mutex.EVENT.readAccess(() -> {
            org.openide.windows.TopComponent tc = org.openide.windows.TopComponent.getRegistry().getActivated();
            org.openide.cookies.EditorCookie ec = tc == null ? null
                    : tc.getLookup().lookup(org.openide.cookies.EditorCookie.class);
            javax.swing.JEditorPane[] panes = ec == null ? null : ec.getOpenedPanes();
            if (panes == null || panes.length == 0) {
                return null;
            }
            javax.swing.JEditorPane pane = panes[0];
            for (Action a : pane.getEditorKit().getActions()) {
                if (name.equals(a.getValue(Action.NAME))) {
                    return bound(a, pane);
                }
            }
            return null;
        });
    }

    /**
     * {@code kitAction} run against {@code pane}: the event's source is the
     * pane, which is how an editor action finds its text, and the name
     * shown is the kit's own description of it.
     */
    static Action bound(Action kitAction, javax.swing.JEditorPane pane) {
        Object described = kitAction.getValue(Action.SHORT_DESCRIPTION);
        String name = described instanceof String s && !s.isBlank() ? s : String.valueOf(kitAction.getValue(Action.NAME));
        return new javax.swing.AbstractAction(name) {
            @Override
            public boolean isEnabled() {
                return kitAction.isEnabled() && pane.isShowing();
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                pane.requestFocusInWindow();
                kitAction.actionPerformed(new ActionEvent(pane, ActionEvent.ACTION_PERFORMED,
                        String.valueOf(kitAction.getValue(Action.NAME))));
            }
        };
    }

    /** The action's display name without its mnemonic marker or trailing ellipsis. */
    static String nameOf(Action a) {
        Object n = a.getValue(Action.NAME);
        String s = n == null ? "" : Actions.cutAmpersand(n.toString()).strip();
        if (s.endsWith("...")) {
            s = s.substring(0, s.length() - 3);
        } else if (s.endsWith("…")) {
            s = s.substring(0, s.length() - 1);
        }
        return s.strip();
    }

    /**
     * What choosing a hit does: the action runs on the EDT, as the
     * platform's own provider runs its rows - unless it went disabled
     * between the listing and the choice (the focus moved), when running it
     * would do nothing anyway.
     */
    static Runnable runner(Hit h) {
        return () -> EventQueue.invokeLater(() -> {
            if (h.action.isEnabled()) {
                h.action.actionPerformed(new ActionEvent(VsCodeCommandSearchProvider.class,
                        ActionEvent.ACTION_PERFORMED, h.cmd.id()));
            }
        });
    }
}
