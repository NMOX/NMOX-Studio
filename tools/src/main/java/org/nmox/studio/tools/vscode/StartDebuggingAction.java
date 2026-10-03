package org.nmox.studio.tools.vscode;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;

import org.nmox.studio.tools.npm.search.NpmScriptSearchProvider;
import org.nmox.studio.tools.vscode.VsCodeLaunch.Config;
import org.nmox.studio.tools.vscode.VsCodeTasks.EditorContext;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle;

/**
 * Debug ▸ Start Debugging… — the configurations of the aimed project's
 * {@code .vscode/launch.json} in a list; the chosen one starts as Enter
 * on it in Quick Search starts it ({@link VsCodeLaunchSearchProvider#run}),
 * its {@code preLaunchTask} first. VS Code's door is <i>Run ▸ Start
 * Debugging</i> with the configuration chosen in its Run view. See {@link
 * VsCodeMenuDoor} for the order of a press.
 */
@ActionID(category = "Debug", id = "org.nmox.studio.tools.vscode.StartDebuggingAction")
@ActionRegistration(displayName = "#CTL_StartDebuggingAction", lazy = true)
@ActionReference(path = "Menu/RunProject", position = 280)
public final class StartDebuggingAction implements ActionListener {

    static final VsCodeMenuDoor<Config> DOOR = new VsCodeMenuDoor<>(new VsCodeMenuDoor.Source<>() {
        @Override
        public List<Config> read(File project) {
            return VsCodeLaunch.read(project);
        }

        @Override
        public String row(Config config) {
            return StartDebuggingAction.row(config);
        }

        @Override
        public void start(File project, Config config, EditorContext editor) {
            VsCodeLaunchSearchProvider.run(project, config, editor);
        }
    }, () -> new VsCodeMenuDoor.Words(
            message("StartDebuggingAction_title"), message("StartDebuggingAction_debug"),
            message("StartDebuggingAction_noProject"), message("StartDebuggingAction_nothing"),
            message("StartDebuggingAction_aimMoved")),
            s -> VsCodeLaunchSearchProvider.statusSink.accept(s));

    /** {@code Launch Program — ${workspaceFolder}/server.js}: the name, then what it debugs as the file wrote it. */
    static String row(Config config) {
        String name = NpmScriptSearchProvider.oneLine(config.name());
        String target = NpmScriptSearchProvider.clip(
                NpmScriptSearchProvider.oneLine(VsCodeLaunch.display(config)), VsCodeLaunchSearchProvider.MAX_TARGET);
        return target.isEmpty() ? name : name + " — " + target;
    }

    private static String message(String key) {
        return NbBundle.getMessage(StartDebuggingAction.class, key);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        DOOR.press();
    }
}
